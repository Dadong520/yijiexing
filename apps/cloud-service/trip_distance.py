"""Server-calculated shared-trip distance gates and order-bound passenger consent.

Device locations are unverified reports, not cryptographic GPS attestation.
All mutations stay in the existing SQLite/service lock/snapshot CAS transaction.
"""
import math
from common import *

TRIP_DEFAULTS = {'pickupMaxMeters': 300, 'dropoffMaxMeters': 300}
LOCATION_AGE = 120
LOCATION_ACCURACY = 100
CONSENT_TTL = 180
LOCATION_STAGES = {'AVAILABLE', 'PREPAY', 'RESERVED', 'PICKUP', 'ARRIVED', 'TRIP'}


def sample_time(value):
    if type(value) is int:
        require(0 < value < 253402300799000, '定位时间须为Unix毫秒或带时区ISO8601')
        try:
            return dt.datetime.fromtimestamp(value / 1000, TZ)
        except (ValueError, OverflowError, OSError):
            raise ApiError(400, '定位时间无效')
    return parse_time(value)


def distance_meters(a, b):
    lat1, lat2 = math.radians(a['latitude']), math.radians(b['latitude'])
    dl = math.radians(b['latitude'] - a['latitude'])
    dn = math.radians(b['longitude'] - a['longitude'])
    h = math.sin(dl/2)**2 + math.cos(lat1)*math.cos(lat2)*math.sin(dn/2)**2
    return 6371000 * 2 * math.asin(min(1, math.sqrt(max(0, h))))


class TripDistanceMixin:
    def trip_distance_rules(self, city):
        cfg, sources, version = self.effective(city)
        configured = cfg.get('tripDistance', {})
        rules = {k: configured.get(k, v) for k, v in TRIP_DEFAULTS.items()}
        require(all(type(v) is int and 50 <= v <= 5000 for v in rules.values()), '距离规则配置无效', 409)
        return {**rules, 'locationMaxAgeSeconds': LOCATION_AGE,
                'locationMaxAccuracyMeters': LOCATION_ACCURACY, 'consentTtlSeconds': CONSENT_TTL,
                'coordinateSystem': 'WGS84', 'distanceMethod': 'SERVER_HAVERSINE',
                'gpsAttested': False, 'version': version,
                'sources': {k: sources.get('tripDistance.' + k, {'scope': 'built-in', 'label': '内建默认300米'}) for k in TRIP_DEFAULTS}}

    def trip_location(self, p, passenger=False):
        require(set(p) <= {'id', 'latitude', 'longitude', 'accuracy', 'time', 'coordinateSystem'}, '定位含未知字段')
        self.validate_point(p.get('latitude'), p.get('longitude'))
        require(p.get('coordinateSystem', 'WGS84') == 'WGS84', '定位坐标须为WGS84')
        accuracy = p.get('accuracy', 0)
        require(type(accuracy) in (int, float) and math.isfinite(accuracy) and 0 <= accuracy <= 100000, '定位精度无效')
        if passenger:
            require(accuracy > 0 and p.get('time') is not None, '乘客定位须提供采样时间和有效精度')
        sampled = sample_time(p['time']) if p.get('time') is not None else None
        received = now()
        require(sampled is None or (sampled - received).total_seconds() <= 30, '定位采样时间不能在未来超过30秒')
        return {'latitude': p['latitude'], 'longitude': p['longitude'], 'accuracy': accuracy,
                'deviceTime': stamp(sampled) if sampled is not None else None,
                'receivedAt': stamp(received), 'coordinateSystem': 'WGS84', 'source': 'DEVICE_GPS',
                'gpsAttested': False}

    def usable_trip_location(self, location):
        if not isinstance(location, dict):
            return False, 'MISSING'
        try:
            self.validate_point(location.get('latitude'), location.get('longitude'))
            accuracy = location.get('accuracy')
            if location.get('coordinateSystem') != 'WGS84':
                return False, 'INVALID'
            if type(accuracy) not in (int, float) or not math.isfinite(accuracy) or not 0 < accuracy <= LOCATION_ACCURACY:
                return False, 'POOR_ACCURACY'
            sampled, received = sample_time(location.get('deviceTime')), parse_time(location.get('receivedAt'))
            ages = [(now()-t).total_seconds() for t in (sampled, received)]
            if not all(-30 <= age <= LOCATION_AGE for age in ages):
                return False, 'STALE'
        except (ApiError, ValueError, OverflowError, TypeError):
            return False, 'INVALID'
        return True, None

    def passenger_report_location(self, pid, p):
        o = self.own_passenger_order(pid, p.get('id'))
        require(o['stage'] in LOCATION_STAGES, '仅活动订单允许上传乘客定位', 409)
        location = self.trip_location(p, passenger=True)
        o['passengerLocation'] = location
        self.save_shared(o)
        return {'location': location}

    def distance_context(self, o):
        context = {'orderId': o['id'], 'passengerId': o.get('passengerId'),
                   'driverId': o.get('driverId'), 'expectedStage': o['stage']}
        if o['stage'] == 'ARRIVED':
            context.update(phoneTail=o.get('phoneTail'), destinationPending=bool(o.get('destinationPending')))
        elif o['stage'] == 'TRIP':
            context.update(destinationLat=o.get('destinationLat'), destinationLng=o.get('destinationLng'),
                           coordinateSystem=o.get('coordinateSystem'), coordinatesSynthetic=bool(o.get('coordinatesSynthetic')))
        return context

    def refresh_distance_consent(self, o, persist=True):
        request = o.get('distanceConsent')
        if not request or request.get('status') != 'PENDING':
            return False
        status, reason = None, None
        if request.get('context') != self.distance_context(o):
            status, reason = 'INVALIDATED', 'ORDER_CONTEXT_CHANGED'
        elif parse_time(request['expiresAt']) <= now():
            status, reason = 'EXPIRED', 'CONSENT_EXPIRED'
        if not status:
            return False
        request.update(status=status, decidedAt=stamp(), invalidationReason=reason)
        self.audit('SYSTEM', 'trip.distanceConsent.' + status.lower(), None,
                   {'orderId': o['id'], 'consentId': request['id'], 'reason': reason})
        if persist:
            self.save_shared(o, 'DISTANCE_CONSENT_' + status)
        return True

    def shared_distance_evidence(self, o):
        rules = self.trip_distance_rules(o['city'])
        driver_row = self.account(o['driverId'])
        location = json.loads(driver_row['location']) if driver_row['location'] else None
        valid_driver, driver_reason = self.usable_trip_location(location)
        pickup = o['stage'] == 'ARRIVED'
        target = o.get('passengerLocation') if pickup else None
        if pickup:
            valid_target, target_reason = self.usable_trip_location(target)
        else:
            try:
                self.validate_point(o.get('destinationLat'), o.get('destinationLng'))
                valid_target = o.get('coordinateSystem') == 'WGS84' and not o.get('coordinatesSynthetic')
            except ApiError:
                valid_target = False
            target_reason = None if valid_target else 'INVALID_OR_SYNTHETIC'
            if valid_target:
                target = {'latitude': o['destinationLat'], 'longitude': o['destinationLng'],
                          'coordinateSystem': 'WGS84', 'source': 'ORDER_DESTINATION'}
        distance = distance_meters(location, target) if valid_driver and valid_target else None
        threshold = rules['pickupMaxMeters' if pickup else 'dropoffMaxMeters']
        reason = 'DRIVER_LOCATION_' + driver_reason if not valid_driver else (
            ('PASSENGER_LOCATION_' if pickup else 'DESTINATION_') + target_reason if not valid_target else 'OUTSIDE_DISTANCE')
        return {'type': 'PICKUP' if pickup else 'DROPOFF', 'distanceMeters': round(distance, 3) if distance is not None else None,
                'thresholdMeters': threshold, 'reason': reason, 'driverLocation': clone(location),
                'targetLocation': clone(target), 'rules': rules,
                'withinDistance': distance is not None and distance <= threshold + 0.000001}

    def advance_shared_trip(self, o):
        stage = o['stage']
        if stage == 'ARRIVED':
            require(not o.get('destinationPending'), '请等待乘客补充目的地后再开始行程', 409)
            o['fareConfirmed'] = True
            o['fareConfirmedAt'] = stamp()
        if stage == 'TRIP':
            require(len(o.get('arrivedWaypoints', [])) == len(o.get('waypoints', [])), '请先确认全部途经点', 409)
        o['stage'] = {'RESERVED': 'PICKUP', 'PICKUP': 'ARRIVED', 'ARRIVED': 'TRIP',
                      'TRIP': 'DONE' if o.get('paid') else 'WAIT_PAY'}[stage]

    def gate_shared_advance(self, o, p):
        require(not (set(p) & {'distance', 'distanceMeters', 'threshold', 'thresholdMeters', 'approved', 'approve', 'consentApproved'}), '距离和同意由服务端校验，不能由司机指定')
        if o['stage'] not in ('ARRIVED', 'TRIP'):
            self.advance_shared_trip(o)
            return
        self.refresh_distance_consent(o, persist=False)
        pending = o.get('distanceConsent')
        # An outstanding consent is bound to its original evidence/rule snapshot.
        # Repeated driver clicks reuse it; a config edit cannot weaken that request.
        if pending and pending['status'] == 'PENDING':
            return
        evidence = self.shared_distance_evidence(o)
        if evidence.pop('withinDistance'):
            self.advance_shared_trip(o)
            return
        if pending:
            o['distanceConsentHistory'] = (o.get('distanceConsentHistory', []) + [clone(pending)])[-20:]
        request = {**evidence, 'id': uid('DC'), 'orderId': o['id'], 'driverId': o['driverId'],
                   'status': 'PENDING', 'expectedStage': o['stage'],
                   'nextStage': 'TRIP' if o['stage'] == 'ARRIVED' else 'DONE' if o.get('paid') else 'WAIT_PAY',
                   'createdAt': stamp(), 'expiresAt': stamp(now()+dt.timedelta(seconds=CONSENT_TTL)),
                   'context': self.distance_context(o)}
        o['distanceConsent'] = request
        self.audit('DRIVER:' + o['driverId'], 'trip.distanceConsent.request', None, clone(request))

    def passenger_distance_consent(self, pid, p):
        require(set(p) == {'id', 'consentId', 'approve'} and type(p.get('approve')) is bool, '确认参数须包含id、consentId、布尔approve')
        o = self.own_passenger_order(pid, p.get('id'))
        self.refresh_distance_consent(o)
        request = next((r for r in [o.get('distanceConsent')] + o.get('distanceConsentHistory', [])
                        if r and r['id'] == p.get('consentId')), None)
        require(request is not None, '此订单没有对应距离确认', 404)
        if request['status'] in ('APPROVED', 'REJECTED'):
            require((request['status'] == 'APPROVED') == p['approve'], '该确认已按另一决定处理', 409)
            return {'order': self.passenger_view(o), 'idempotent': True}
        require(request is o.get('distanceConsent') and request['status'] == 'PENDING', '确认已过期或订单状态已变化', 409,
                {'reason': request['status'], 'consentId': request['id']})
        require(request['context'] == self.distance_context(o), '确认绑定已失效', 409)
        # Tail/destination checks were completed before the driver created this
        # bound request; context changes invalidate it. Recheck waypoints here.
        if p['approve'] and o['stage'] == 'TRIP':
            require(len(o.get('arrivedWaypoints', [])) == len(o.get('waypoints', [])), '途经点状态已变化', 409)
        request.update(status='APPROVED' if p['approve'] else 'REJECTED', decidedAt=stamp(), decidedBy='PASSENGER')
        if p['approve']:
            self.advance_shared_trip(o)
            self.shared_credit(o)
        self.save_shared(o, 'DISTANCE_CONSENT_' + request['status'])
        if o.get('driverId'):
            self.sync_shared_orders(o['driverId'])
        self.audit('PASSENGER:' + pid, 'trip.distanceConsent.decide', None,
                   {'orderId': o['id'], 'consentId': request['id'], 'approved': p['approve'], 'stage': o['stage']})
        return {'order': self.passenger_view(o)}

    def trip_order_view(self, o):
        value = clone(o)
        # The canonical context contains passenger identity for authorization;
        # clients receive the evidence fields without that internal binding.
        for r in [value.get('distanceConsent')] + value.get('distanceConsentHistory', []):
            if r:
                r.pop('context', None)
        value.pop('passengerLocation', None)
        value.pop('distanceEnforcementNotice', None)
        if value.get('shared'):
            value['synthetic'] = False
        value['distanceRules'] = self.trip_distance_rules(o['city']) if o.get('city') else None
        value['distanceEnforcement'] = 'PASSENGER_CONSENT_REQUIRED'
        return value
