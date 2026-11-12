"""修改意见3.0: regional tariff, trip meter, regional service fee, cancellation
(空车费) and the settlement (分账) identity.

All amounts are integer cents of the simulated ledger. Nothing here contacts a
payment acquirer; the scan-pay figures are what the split *would* be.
"""
import math
from decimal import Decimal, ROUND_HALF_UP, ROUND_FLOOR
from common import *

DEFAULT_TARIFF = {
    'startPrice': 800, 'startMeters': 2000,          # 8元 / 2公里
    'unitPrice': 80, 'unitMeters': 500,              # 每500米0.8元（1.6元/公里），不足500米按500米
    'returnAfterMeters': 9000, 'returnRate': 50,     # 从第10公里起加收50%回空费
    'nightStart': '23:00', 'nightEnd': '05:00', 'nightRate': 40,  # 23时后至次日5时（含）加收40%
    'freeWaitMinutes': 5, 'waitPrice': 50,           # 免费等候5分钟，之后每分钟0.5元，不足1分钟按1分钟
    'lowSpeedKmh': 12,                               # 时速低于12公里的时间并入等候
    'emptyFee': 0, 'freeCancelMinutes': 2,           # 空车费（后台配置），接单后免责取消分钟数
}
TARIFF_INT_FIELDS = [k for k, v in DEFAULT_TARIFF.items() if isinstance(v, int)]
PAY_FEE_RATE = Decimal('0.006')       # 微信/扫呗手续费 0.6%
SPLIT_FEE_RATE = Decimal('0.0015')    # 分账费 0.15%
METERED_KINDS = ('STANDARD', 'RESERVED')
PRICED_KINDS = ('STANDARD', 'RESERVED', 'BID')
MAX_SPEED_KMH = 150


def round_cents(value):
    return int(Decimal(value).quantize(Decimal('1'), rounding=ROUND_HALF_UP))


def clock_minutes(text):
    require(isinstance(text, str) and re.fullmatch(r'([01]\d|2[0-3]):[0-5]\d', text) is not None, '时间须为HH:MM')
    h, m = text.split(':')
    return int(h) * 60 + int(m)


def meters_between(a_lat, a_lng, b_lat, b_lng):
    la, lb = math.radians(a_lat), math.radians(b_lat)
    h = math.sin(math.radians(b_lat - a_lat) / 2) ** 2 + math.cos(la) * math.cos(lb) * math.sin(math.radians(b_lng - a_lng) / 2) ** 2
    return 12742000 * math.asin(min(1, math.sqrt(max(0, h))))



def settlement_split(trip_fare, service_fee, coupon, map_fee, platform_rate):
    """The 修改意见3.0 identity, exact in integer cents.

    订单费用 F = 运价费 A + 服务费 S；实付 P = F - 优惠券 C。
    平台承担差额费 D = (P手续费+P分账费) - (A手续费+A分账费)：>0 由服务费承担，<=0 补给平台。
    可抽佣金额 K = S - max(D,0)；公司佣金 = floor(K*公司比例)；平台佣金 = M + K - 公司佣金 - min(D,0)
    （四舍五入余数由平台多得）。司机分账 = A - A手续费 - A分账费 - M。
    扫呗入账 = P - P手续费 + C；扫呗分账 = 入账 - P分账费 = 司机 + 公司 + 平台。
    """
    A, S, C, M = trip_fare, service_fee, coupon, map_fee
    F = A + S
    P = F - C
    fee = lambda v, r: round_cents(Decimal(v) * r)
    fee_a, split_a = fee(A, PAY_FEE_RATE), fee(A, SPLIT_FEE_RATE)
    fee_s, split_s = fee(S, PAY_FEE_RATE), fee(S, SPLIT_FEE_RATE)
    fee_p, split_p = fee(P, PAY_FEE_RATE), fee(P, SPLIT_FEE_RATE)
    D = (fee_p + split_p) - (fee_a + split_a)
    K = S - max(D, 0)
    company = int((Decimal(K) * Decimal(100 - platform_rate) / 100).quantize(Decimal('1'), rounding=ROUND_FLOOR))
    platform = M + K - company - min(D, 0)
    driver = A - fee_a - split_a - M
    scan_in = P - fee_p + C
    scan_split = scan_in - split_p
    assert scan_split == driver + company + platform, 'settlement identity broken'
    # 订单费用 = 平台佣金 + 公司佣金 - 地图服务费 + 司机分账 + 地图服务费 + 平台承担差额费 + 运价费手续费 + 运价费分账费
    assert F == platform + company - M + driver + M + D + fee_a + split_a, 'order identity broken'
    return {'orderFee': F, 'tripFare': A, 'serviceFee': S, 'couponAmount': C, 'paidAmount': P,
            'tripPayFee': fee_a, 'tripSplitFee': split_a, 'servicePayFee': fee_s, 'serviceSplitFee': split_s,
            'paidPayFee': fee_p, 'paidSplitFee': split_p, 'platformDifference': D,
            'differenceBorneBy': 'SERVICE_FEE' if D > 0 else 'PLATFORM', 'commissionable': K,
            'mapServiceFee': M, 'platformRate': platform_rate, 'companyRate': 100 - platform_rate,
            'platformCommission': platform, 'companyCommission': company, 'driverAmount': driver,
            'scanPayIncome': scan_in, 'scanPaySplit': scan_split}


class PricingMixin:
    # ---- configuration --------------------------------------------------
    def tariff(self, city):
        row = self.operations.get('tariff', city) or {}
        return {**DEFAULT_TARIFF, **{k: v for k, v in row.items() if k in DEFAULT_TARIFF}}

    def region_service_fee(self, city):
        row = self.operations.get('serviceFee', city)
        return row['amount'] if row and row.get('enabled') else 0

    def is_night(self, tariff, moment):
        t = moment.astimezone(TZ)
        m = t.hour * 60 + t.minute
        start, end = clock_minutes(tariff['nightStart']), clock_minutes(tariff['nightEnd'])
        # End minute is inclusive ("至次日5时前（含5时）").
        return (start <= m or m <= end) if start > end else (start <= m <= end)

    def unit_price(self, tariff, unit_start_meters, moment):
        rate = 100 + (tariff['returnRate'] if unit_start_meters >= tariff['returnAfterMeters'] else 0) \
                   + (tariff['nightRate'] if self.is_night(tariff, moment) else 0)
        return round_cents(Decimal(tariff['unitPrice'] * rate) / 100)

    def distance_charge(self, tariff, meters, moment):
        units = math.ceil(max(0, meters - tariff['startMeters']) / tariff['unitMeters'])
        return sum(self.unit_price(tariff, tariff['startMeters'] + i * tariff['unitMeters'], moment) for i in range(units)), units

    def estimate_trip_fare(self, city, meters, moment=None):
        tariff = self.tariff(city)
        charge, units = self.distance_charge(tariff, meters, moment or now())
        return tariff['startPrice'] + charge, {'startPrice': tariff['startPrice'], 'distanceFee': charge, 'units': units,
            'waitFee': 0, 'waitMinutes': 0, 'meters': int(round(meters)), 'source': 'ESTIMATE',
            'night': self.is_night(tariff, moment or now())}

    # ---- order pricing --------------------------------------------------
    def apply_order_price(self, o, trip_fare, detail=None):
        """Recompute payable fare from 运价费 + 区域服务费 - 优惠券 and bump the
        confirmation version when the passenger-visible amount changes."""
        if o.get('kind') not in PRICED_KINDS:
            return
        service = o.get('serviceFee', 0)
        coupon = min(o.get('couponFaceValue', 0), trip_fare) if trip_fare >= o.get('couponThreshold', 0) else 0
        before = o.get('fare')
        o.update(tripFare=trip_fare, couponAmount=coupon, fare=trip_fare + service - coupon)
        if detail is not None:
            o['fareDetail'] = detail
        if before is not None and before != o['fare'] and o.get('stage') not in ('AVAILABLE',):
            o['fareVersion'] = self.order_fare_version(o) + 1

    def price_new_order(self, o, meters, moment):
        o['serviceFee'] = self.region_service_fee(o['city']) if o['kind'] in PRICED_KINDS else 0
        trip, detail = self.estimate_trip_fare(o['city'], meters, moment)
        o['fare'] = None
        self.apply_order_price(o, trip, detail)
        o['estimatedFare'] = o['fare']
        o['estimatedTripFare'] = trip

    # ---- meter ----------------------------------------------------------
    def meter_location(self, driver, location):
        """Feed a usable driver fix into the meter of that driver's active shared order."""
        usable, _ = self.usable_trip_location(location)
        if not usable:
            return
        for row in self.db.execute("SELECT payload FROM passenger_orders WHERE driver=?", (driver,)).fetchall():
            o = json.loads(row['payload'])
            if o.get('stage') not in ('PICKUP', 'ARRIVED', 'TRIP'):
                continue
            meter = o.setdefault('meter', {'pickupMeters': 0, 'tripMeters': 0, 'chargedMeters': 0, 'units': 0,
                                           'unitCost': 0, 'lowSpeedSeconds': 0, 'tripSamples': 0, 'night': False,
                                           'returnApplied': False})
            at = parse_time(location['receivedAt'])
            last = meter.get('last')
            meter['last'] = {'lat': location['latitude'], 'lng': location['longitude'], 'at': location['receivedAt'], 'stage': o['stage']}
            if o['stage'] == 'TRIP':
                meter['tripSamples'] += 1
            if not last or last['stage'] != o['stage']:
                self.db.execute('UPDATE passenger_orders SET payload=? WHERE id=?', (dumps(o), o['id']))
                continue
            dt_s = (at - parse_time(last['at'])).total_seconds()
            if dt_s <= 0:
                continue
            d = meters_between(last['lat'], last['lng'], location['latitude'], location['longitude'])
            speed = d / dt_s * 3.6
            if speed > MAX_SPEED_KMH:
                self.db.execute('UPDATE passenger_orders SET payload=? WHERE id=?', (dumps(o), o['id']))
                continue  # GPS jump; the fix still becomes the new reference point
            if o['stage'] == 'PICKUP':
                meter['pickupMeters'] += d
            elif o['stage'] == 'TRIP':
                tariff = self.tariff(o['city'])
                meter['tripMeters'] += d
                if speed < tariff['lowSpeedKmh']:
                    meter['lowSpeedSeconds'] += dt_s
                else:
                    meter['chargedMeters'] += d
                    while meter['chargedMeters'] > tariff['startMeters'] + meter['units'] * tariff['unitMeters']:
                        start = tariff['startMeters'] + meter['units'] * tariff['unitMeters']
                        meter['unitCost'] += self.unit_price(tariff, start, at)
                        meter['units'] += 1
                        meter['night'] = meter['night'] or self.is_night(tariff, at)
                        meter['returnApplied'] = meter['returnApplied'] or start >= tariff['returnAfterMeters']
            self.db.execute('UPDATE passenger_orders SET payload=? WHERE id=?', (dumps(o), o['id']))

    def straight_route_meters(self, o):
        pts = [(o['originLat'], o['originLng'])] + [(w['latitude'], w['longitude']) for w in o.get('waypoints', [])] \
              + [(o['destinationLat'], o['destinationLng'])]
        return sum(meters_between(a[0], a[1], b[0], b[1]) for a, b in zip(pts, pts[1:]))

    def finalize_trip_fare(self, o):
        """Called when the driver ends the trip. 按实际里程收费；定位样本不足或明显
        少于直线距离一半时回退到下单预估里程。"""
        o['tripEndedAt'] = stamp()
        if o.get('kind') not in METERED_KINDS or o.get('paid') or o.get('destinationPending'):
            return
        tariff = self.tariff(o['city'])
        meter = o.get('meter') or {}
        started = parse_time(o['tripStartedAt']) if o.get('tripStartedAt') else now()
        metered = meter.get('tripSamples', 0) >= 2 and meter.get('tripMeters', 0) >= .5 * self.straight_route_meters(o)
        if metered:
            distance_fee, units, meters = meter['unitCost'], meter['units'], meter['tripMeters']
            night, ret = meter['night'], meter['returnApplied']
        else:
            try:
                meters = float(o.get('mileage')) * 1000
            except (TypeError, ValueError):
                meters = self.straight_route_meters(o) * 1.3
            distance_fee, units = self.distance_charge(tariff, meters, started)
            night = self.is_night(tariff, started)
            ret = meters > tariff['returnAfterMeters']
        wait_seconds = meter.get('lowSpeedSeconds', 0)
        if o.get('arrivedAt') and o.get('tripStartedAt'):
            wait_seconds += max(0, (parse_time(o['tripStartedAt']) - parse_time(o['arrivedAt'])).total_seconds())
        wait_minutes = math.ceil(max(0, wait_seconds - tariff['freeWaitMinutes'] * 60) / 60)
        wait_fee = wait_minutes * tariff['waitPrice']
        detail = {'startPrice': tariff['startPrice'], 'distanceFee': distance_fee, 'units': units, 'meters': int(round(meters)),
                  'waitMinutes': wait_minutes, 'waitFee': wait_fee, 'waitSeconds': int(wait_seconds),
                  'lowSpeedSeconds': int(meter.get('lowSpeedSeconds', 0)), 'night': night, 'returnApplied': ret,
                  'source': 'METER' if metered else 'ESTIMATE_FALLBACK', 'tariff': {k: tariff[k] for k in TARIFF_INT_FIELDS + ['nightStart', 'nightEnd']}}
        self.apply_order_price(o, tariff['startPrice'] + distance_fee + wait_fee, detail)
        o['fareSource'] = 'METERED' if metered else 'METER_FALLBACK_ESTIMATE'

    # ---- settlement -----------------------------------------------------
    def order_settlement(self, o):
        company = self.operations.get('company', o.get('companyId')) if o.get('companyId') else None
        map_fee = company.get('mapServiceFee', 0) if company else 0
        rate = company.get('platformRate', 50) if company else 100
        if o.get('kind') in PRICED_KINDS and 'tripFare' in o:
            trip, service, coupon = o['tripFare'], o.get('serviceFee', 0), o.get('couponAmount', 0)
        else:  # legacy / negotiated: the whole agreed fare is 运价费
            trip, service, coupon = o['fare'], 0, 0
        value = settlement_split(trip, service, coupon, map_fee, rate)
        value.update(orderId=o['id'], driverId=o.get('driverId'), companyId=o.get('companyId'),
                     companyName=company.get('name') if company else None, settledAt=stamp(), simulated=True)
        return value

    def order_settlement_rows(self):
        rows = []
        for r in self.db.execute('SELECT id,payload FROM passenger_orders'):
            o = json.loads(r['payload'])
            if o.get('settlement'):
                rows.append({**o['settlement'], 'id': o['id'], 'city': o.get('city'),
                             'settlementStatus': 'REFUNDED' if o.get('refundAmount') else 'CREDITED',
                             'refundAmount': o.get('refundAmount', 0)})
        return rows

    # ---- cancellation ---------------------------------------------------
    def accepted_at(self, o):
        if o.get('acceptedAt'):
            return parse_time(o['acceptedAt'])
        for e in reversed(o.get('events') or []):
            if e.get('type') in ('COMPETITION_ASSIGNED', 'DRIVER_ACCEPT', 'PASSENGER_SELECTDRIVER'):
                return parse_time(e['at'])
        return None

    def passenger_cancel_responsible(self, o):
        if not o.get('driverId') or o['stage'] not in ('PICKUP', 'ARRIVED', 'RESERVED'):
            return False
        if o['stage'] == 'ARRIVED':
            return True
        if o['stage'] == 'RESERVED':
            return False
        accepted = self.accepted_at(o)
        return bool(accepted and (now() - accepted).total_seconds() > self.tariff(o['city'])['freeCancelMinutes'] * 60)

    def apply_cancellation(self, o, by, responsible):
        """Refund or bill the 空车费. Coupons return via save_shared (stage CANCELLED)."""
        fee = self.tariff(o['city'])['emptyFee'] if responsible and o.get('driverId') else 0
        if o.get('paid'):
            deduct = min(fee, o['fare'])
            o.update(paid=False, paymentStatus='REFUNDED', refundAmount=o['fare'] - deduct, refundedAt=stamp(), refundMode='SIMULATED')
            if deduct:
                o['cancelFee'] = {'amount': deduct, 'status': 'PAID', 'responsible': by, 'paidAt': stamp(), 'source': 'PREPAID_DEDUCTION'}
                self.credit_cancel_fee(o)
        elif fee:
            o['cancelFee'] = {'amount': fee, 'status': 'UNPAID', 'responsible': by, 'createdAt': stamp()}
        o['cancelResponsible'] = by if responsible else None

    def credit_cancel_fee(self, o):
        if o['cancelFee'].get('credited'):
            return
        driver = o['driverId']
        st = json.loads(self.account(driver)['state'])
        amount = o['cancelFee']['amount']
        st['balance'] += amount
        st['entries'].insert(0, {'id': uid('WE'), 'title': '空车费 ' + o['id'], 'amount': amount,
                                 'note': '乘客有责取消补偿（模拟）', 'createdAt': stamp(), 'time': stamp()})
        self.save_state(driver, st)
        o['cancelFee']['credited'] = True
