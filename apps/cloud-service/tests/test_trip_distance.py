"""Local authoritative distance/consent regressions; no live accounts or GPS service."""
import concurrent.futures
import datetime as dt
import json
import math
import pathlib
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import uuid

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from common import ApiError, clone, dumps, now, stamp
from domain import Service
from admin_console import AdminConsole, ROLES
from seed import initial_profile, initial_state
from trip_distance import distance_meters
import test_passenger as http_fixture


class TripDistanceRules(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.svc = Service(pathlib.Path(self.tmp.name) / 'trip.sqlite')
        profile = initial_profile('13800000001'); profile['driver']['id'] = 'D1'
        self.svc.db.execute('INSERT INTO accounts VALUES (?,?,?,?,?,?,?,?)',
                            ('D1', '13800000001', dumps(initial_state()), dumps(profile), None, stamp(), 2, None))
        other = initial_profile('13800000002'); other['driver']['id'] = 'D2'
        self.svc.db.execute('INSERT INTO accounts VALUES (?,?,?,?,?,?,?,?)',
                            ('D2', '13800000002', dumps(initial_state()), dumps(other), None, stamp(), 2, None))
        for pid, mobile in [('P1', '13800000101'), ('P2', '13800000102')]:
            p = {'id': pid, 'mobile': mobile, 'name': pid, 'city': '420500', 'favorites': [], 'feedback': []}
            self.svc.db.execute('INSERT INTO passengers VALUES (?,?,?)', (pid, mobile, dumps(p)))
        self.da('driver.online', {'online': True})
        self.oid = None

    def tearDown(self):
        self.svc.close(); self.tmp.cleanup()

    def da(self, action, p, rid=None):
        return self.svc.action('D1', action, p, rid or str(uuid.uuid4()))

    def pa(self, action, p, pid='P1', rid=None):
        return self.svc.passenger_action(pid, action, p, rid or str(uuid.uuid4()))

    def order(self):
        return self.svc.shared_order(self.oid)

    def create(self, prepaid=False, waypoints=None):
        payload = {'kind': 'BID' if prepaid else 'STANDARD', 'origin': '真实选择上车点', 'destination': '真实选择目的地',
                   'originLat': 30.0, 'originLng': 111.0, 'destinationLat': 30.02, 'destinationLng': 111.02,
                   'waypoints': waypoints or []}
        self.oid = self.pa('order.create', payload)['order']['id']
        if prepaid:
            self.da('order.bid', {'id': self.oid, 'amount': 2000})
            self.pa('order.selectDriver', {'id': self.oid, 'driverId': 'D1'})
            self.pa('order.pay', {'id': self.oid})
        else:
            self.da('order.accept', {'id': self.oid})
        self.da('order.advance', {'id': self.oid, 'expectedStage': 'PICKUP'})
        self.assertEqual('ARRIVED', self.order()['stage'])

    def locate(self, meters=0, passenger=True):
        target = {'latitude': 30.0, 'longitude': 111.0, 'accuracy': 5, 'time': stamp(), 'coordinateSystem': 'WGS84'}
        if passenger:
            self.pa('location.report', {'id': self.oid, **target})
        self.da('location.report', {**target, 'latitude': 30 + math.degrees(meters / 6371000)})

    def start(self):
        self.locate()
        self.da('order.advance', {'id': self.oid, 'expectedStage': 'ARRIVED', 'phoneTail': '0101'})
        self.assertEqual('TRIP', self.order()['stage'])

    def pending(self, stage='ARRIVED'):
        p = {'id': self.oid, 'expectedStage': stage}
        if stage == 'ARRIVED': p['phoneTail'] = '0101'
        self.da('order.advance', p)
        self.assertEqual(stage, self.order()['stage'])
        return self.order()['distanceConsent']

    def decide(self, request, approve=True, **kw):
        return self.pa('order.distanceConsent', {'id': self.oid, 'consentId': request['id'], 'approve': approve}, **kw)

    def test_pickup_exactly_300_meters_is_allowed(self):
        self.create(); self.locate(300)
        self.da('order.advance', {'id': self.oid, 'phoneTail': '0101'})
        self.assertEqual('TRIP', self.order()['stage'])

    def test_pickup_below_300_meters_is_allowed(self):
        self.create(); self.locate(299.9)
        self.da('order.advance', {'id': self.oid, 'phoneTail': '0101'})
        self.assertEqual('TRIP', self.order()['stage'])

    def test_pickup_above_300_meters_waits_for_actual_passenger(self):
        self.create(); self.locate(300.01); request = self.pending()
        self.assertEqual('PICKUP', request['type']); self.assertEqual(300, request['thresholdMeters'])
        self.assertGreater(request['distanceMeters'], 300); self.assertFalse(self.order()['fareConfirmed'])
        self.decide(request)
        self.assertEqual('TRIP', self.order()['stage']); self.assertTrue(self.order()['fareConfirmed'])

    def test_missing_driver_or_passenger_location_never_becomes_zero(self):
        self.create(); request = self.pending()
        self.assertEqual('DRIVER_LOCATION_MISSING', request['reason']); self.assertIsNone(request['distanceMeters'])
        self.decide(request, False); self.locate(passenger=False); request = self.pending()
        self.assertEqual('PASSENGER_LOCATION_MISSING', request['reason']); self.assertIsNone(request['distanceMeters'])

    def test_stale_samples_receipts_invalid_accuracy_and_coordinates_cannot_allow(self):
        self.create(); self.locate()
        good = json.loads(self.svc.account('D1')['location'])
        for patch, expected in [({'deviceTime': stamp(now()-dt.timedelta(seconds=121))}, 'STALE'),
                                ({'receivedAt': stamp(now()-dt.timedelta(seconds=121))}, 'STALE'),
                                ({'deviceTime': None}, 'INVALID'), ({'accuracy': 101}, 'POOR_ACCURACY'),
                                ({'accuracy': 0}, 'POOR_ACCURACY'), ({'latitude': float('nan')}, 'INVALID'),
                                ({'coordinateSystem': 'GCJ02'}, 'INVALID')]:
            with self.subTest(patch=patch):
                self.svc.db.execute('UPDATE accounts SET location=? WHERE id=?', (dumps({**good, **patch}), 'D1'))
                evidence = self.svc.shared_distance_evidence(self.order())
                self.assertFalse(evidence['withinDistance']); self.assertIsNone(evidence['distanceMeters'])
                self.assertEqual('DRIVER_LOCATION_' + expected, evidence['reason'])
        self.svc.db.execute('UPDATE accounts SET location=? WHERE id=?', (dumps(good), 'D1'))
        o = self.order(); o['passengerLocation']['deviceTime'] = stamp(now()-dt.timedelta(seconds=121))
        self.assertEqual('PASSENGER_LOCATION_STALE', self.svc.shared_distance_evidence(o)['reason'])

    def test_location_upload_validation_old_driver_compatibility_and_no_attestation_claim(self):
        self.create()
        base = {'latitude': 30, 'longitude': 111, 'accuracy': 5, 'time': stamp()}
        for patch in [{'latitude': True}, {'longitude': float('inf')}, {'accuracy': float('nan')},
                      {'accuracy': -1}, {'longitude': 181}, {'time': stamp(now()+dt.timedelta(seconds=31))},
                      {'coordinateSystem': 'GCJ02'}, {'time': '2026-10-26T00:00:00'}]:
            with self.subTest(patch=patch), self.assertRaises(ApiError): self.da('location.report', {**base, **patch})
        result = self.da('location.report', {'latitude': 30, 'longitude': 111})
        self.assertFalse(result['location']['gpsAttested'])
        self.assertFalse(self.svc.usable_trip_location(result['location'])[0])
        self.da('location.report', {**base, 'time': int(now().timestamp()*1000)})
        self.assertTrue(self.svc.usable_trip_location(json.loads(self.svc.account('D1')['location']))[0])
        with self.assertRaises(ApiError): self.pa('location.report', {'id': self.oid, 'latitude': 30, 'longitude': 111})
        with self.assertRaises(ApiError) as e: self.pa('location.report', {'id': self.oid, **base}, pid='P2')
        self.assertEqual(404, e.exception.status)

    def test_tail_destination_and_waypoint_checks_run_before_creating_consent(self):
        self.create(waypoints=[{'name': '真实途经点', 'latitude': 30.01, 'longitude': 111.01}])
        with self.assertRaises(ApiError): self.da('order.advance', {'id': self.oid, 'phoneTail': '9999'})
        self.assertNotIn('distanceConsent', self.order())
        o = self.order(); o['destinationPending'] = True; self.svc.save_shared(o)
        with self.assertRaises(ApiError): self.da('order.advance', {'id': self.oid, 'phoneTail': '0101'})
        self.assertNotIn('distanceConsent', self.order())
        o['destinationPending'] = False; self.svc.save_shared(o); self.start()
        with self.assertRaises(ApiError): self.da('order.advance', {'id': self.oid})
        self.assertNotIn('distanceConsent', self.order())
        self.da('order.waypoint', {'id': self.oid, 'index': 0}); self.pending('TRIP')

    def test_expected_stage_and_forged_driver_authorization_rejected(self):
        self.create()
        with self.assertRaises(ApiError): self.da('order.advance', {'id': self.oid, 'expectedStage': 'PICKUP'})
        for key in ('approved', 'approve', 'consentApproved', 'distance', 'distanceMeters', 'thresholdMeters'):
            with self.subTest(key=key), self.assertRaises(ApiError):
                self.da('order.advance', {'id': self.oid, 'phoneTail': '0101', key: True})
        request = self.pending()
        with self.assertRaises(ApiError) as e: self.da('order.distanceConsent', {'id': self.oid, 'consentId': request['id'], 'approve': True})
        self.assertEqual(403, e.exception.status)
        with self.assertRaises(ApiError): self.decide(request, pid='P2')
        with self.assertRaises(ApiError): self.decide({**request, 'id': 'unknown'})
        with self.assertRaises(ApiError): self.decide(request, 1)

    def test_pending_reused_and_decision_idempotent_across_distinct_requests(self):
        self.create(); request = self.pending()
        self.assertEqual(request['id'], self.pending()['id'])
        self.decide(request, rid='same-decision'); self.decide(request, rid='same-decision')
        self.assertTrue(self.decide(request)['idempotent'])
        self.assertEqual('TRIP', self.order()['stage'])
        with self.assertRaises(ApiError): self.decide(request, False)
        self.assertEqual(1, self.svc.db.execute("SELECT count(*) FROM audits WHERE action='trip.distanceConsent.decide'").fetchone()[0])

    def test_reject_then_new_request_archives_previous_evidence(self):
        self.create(); first = self.pending(); self.decide(first, False)
        self.assertEqual('ARRIVED', self.order()['stage'])
        second = self.pending(); self.assertNotEqual(first['id'], second['id'])
        self.assertEqual(first['id'], self.order()['distanceConsentHistory'][0]['id'])
        self.assertTrue(self.decide(first, False)['idempotent'])

    def test_expired_cancelled_changed_driver_or_destination_invalidates(self):
        self.create(); request = self.pending()
        o = self.order(); o['distanceConsent']['expiresAt'] = stamp(now()-dt.timedelta(seconds=1))
        self.svc.db.execute('UPDATE passenger_orders SET payload=? WHERE id=?', (dumps(o), self.oid))
        self.assertEqual('EXPIRED', self.svc.passenger_view(self.order())['distanceConsent']['status'])
        with self.assertRaises(ApiError): self.decide(request)
        request = self.pending(); self.pa('order.cancel', {'id': self.oid, 'reason': '取消测试订单'})
        self.assertEqual('INVALIDATED', self.order()['distanceConsent']['status'])
        with self.assertRaises(ApiError): self.decide(request)

    def test_driver_cancel_and_continue_invalidate_old_confirmation(self):
        self.create(); request = self.pending(); self.da('order.cancel', {'id': self.oid, 'reason': '车辆故障'})
        self.assertEqual('WAIT_CONTINUE', self.order()['stage'])
        self.assertEqual('INVALIDATED', self.order()['distanceConsent']['status'])
        self.pa('order.continue', {'id': self.oid, 'decision': 1})
        self.assertIsNone(self.order()['driverId'])
        with self.assertRaises(ApiError): self.decide(request)

    def test_driver_reassignment_and_destination_edit_invalidate_binding(self):
        self.create(); request = self.pending()
        o = self.order(); o['driverId'] = 'D2'; self.svc.save_shared(o)
        self.assertEqual('INVALIDATED', self.order()['distanceConsent']['status'])
        with self.assertRaises(ApiError): self.decide(request)
        o = self.order(); o['driverId'] = 'D1'; self.svc.save_shared(o); self.start()
        request = self.pending('TRIP')
        o = self.order(); o['destinationLat'] += .01; self.svc.save_shared(o)
        self.assertEqual('INVALIDATED', self.order()['distanceConsent']['status'])
        with self.assertRaises(ApiError): self.decide(request)

    def test_legacy_missing_destination_requires_confirmation_not_zero(self):
        self.create(); self.start()
        o = self.order(); o.pop('destinationLat'); self.svc.save_shared(o)
        request = self.pending('TRIP')
        self.assertIsNone(request['distanceMeters']); self.decide(request)
        self.assertEqual('WAIT_PAY', self.order()['stage'])

    def test_dropoff_old_or_synthetic_destination_requires_agreement(self):
        self.create(); self.start()
        o = self.order(); o['coordinatesSynthetic'] = True; self.svc.save_shared(o)
        request = self.pending('TRIP')
        self.assertEqual('DESTINATION_INVALID_OR_SYNTHETIC', request['reason'])
        self.assertIsNone(request['distanceMeters']); self.decide(request)
        self.assertEqual('WAIT_PAY', self.order()['stage'])
        with self.assertRaises(ApiError): self.pa('location.report', {'id': self.oid, 'latitude': 30, 'longitude': 111, 'accuracy': 5, 'time': stamp()})

    def test_dropoff_exact_boundary_and_wait_pay_does_not_block_next_accept(self):
        self.create(); self.start()
        self.da('location.report', {'latitude': 30.02+math.degrees(300/6371000), 'longitude': 111.02, 'accuracy': 5, 'time': stamp()})
        self.da('order.advance', {'id': self.oid, 'expectedStage': 'TRIP'})
        self.assertEqual('WAIT_PAY', self.order()['stage'])
        other = self.pa('order.create', {'kind': 'STANDARD', 'origin': '新上车点', 'destination': '新目的地',
                    'originLat': 30, 'originLng': 111, 'destinationLat': 30.01, 'destinationLng': 111.01}, pid='P2')['order']
        self.da('order.accept', {'id': other['id']})

    def test_paid_consent_completion_and_replayed_payment_credit_only_once(self):
        self.create(prepaid=True); self.start()
        before = json.loads(self.svc.account('D1')['state'])['balance']
        request = self.pending('TRIP'); self.decide(request)
        self.assertEqual('DONE', self.order()['stage']); self.decide(request); self.decide(request)
        self.pa('order.pay', {'id': self.oid})
        state = json.loads(self.svc.account('D1')['state'])
        self.assertEqual(before+2000, state['balance']); self.assertEqual(3, state['todayCompleted'])
        self.assertEqual(1, sum(r.get('title') == '行程收入 ' + self.oid for r in state['entries']))

    def test_rule_snapshot_not_weakened_and_hierarchical_config_audited(self):
        self.create(); request = self.pending()
        console = AdminConsole(self.svc); user = {'username': 'test', 'role': 'SUPER_ADMIN', 'permissions': sorted(ROLES['SUPER_ADMIN'])}
        for scope, target, rules in [('national', 'CN', {'pickupMaxMeters': 400, 'dropoffMaxMeters': 600}),
                                      ('region', 'CENTRAL', {'pickupMaxMeters': 500}),
                                      ('city', '420500', {'pickupMaxMeters': 5000})]:
            revision = self.svc.config_row(scope, target)['revision']
            console.action(user, 'config.draft', {'scope': scope, 'target': target, 'expectedRevision': revision, 'patch': {'tripDistance': rules}}, str(uuid.uuid4()))
            revision = self.svc.config_row(scope, target)['revision']
            console.action(user, 'config.publish', {'scope': scope, 'target': target, 'expectedRevision': revision}, str(uuid.uuid4()))
        effective = self.svc.trip_distance_rules('420500')
        self.assertEqual(5000, effective['pickupMaxMeters']); self.assertEqual(600, effective['dropoffMaxMeters'])
        self.assertEqual('city', effective['sources']['pickupMaxMeters']['scope'])
        self.assertEqual(request['id'], self.pending()['id']); self.assertEqual(300, self.order()['distanceConsent']['thresholdMeters'])
        city = next(c for c in console.state(user)['cities'] if c['id'] == '420500')
        self.assertEqual(effective, city['tripDistanceRules'])
        self.assertEqual(3, self.svc.db.execute("SELECT count(*) FROM audits WHERE action='config.publish'").fetchone()[0])

    def test_config_validation_defaults_for_legacy_and_passenger_privacy(self):
        for rules in [{'pickupMaxMeters': True}, {'dropoffMaxMeters': 49}, {'pickupMaxMeters': 5001}, {'pickupMaxMeters': 300.0}, {'x': 300}]:
            with self.subTest(rules=rules), self.assertRaises(ApiError): self.svc.validate_patch({'tripDistance': rules}, 'city')
        published = json.loads(self.svc.config_row('national', 'CN')['published']); published.pop('tripDistance')
        self.svc.db.execute('UPDATE configs SET published=? WHERE scope=? AND target=?', (dumps(published), 'national', 'CN'))
        self.assertEqual(300, self.svc.trip_distance_rules('420500')['pickupMaxMeters'])
        self.create(); self.locate(301); self.pending()
        driver_order = next(o for o in self.svc.data('D1')['state']['orders'] if o['id'] == self.oid)
        self.assertNotIn('passengerLocation', driver_order); self.assertNotIn('context', driver_order['distanceConsent'])
        self.assertNotIn('passengerId', driver_order)
        cached = self.svc.driver_result_view({'order': self.order()})['order']
        self.assertNotIn('passengerLocation', cached); self.assertNotIn('context', cached['distanceConsent'])


class TripDistanceHttp(unittest.TestCase):
    setUp = http_fixture.PassengerIntegration.setUp
    tearDown = http_fixture.PassengerIntegration.tearDown
    start = http_fixture.PassengerIntegration.start
    request = http_fixture.PassengerIntegration.request
    login = http_fixture.PassengerIntegration.login
    passenger_login = http_fixture.PassengerIntegration.passenger_login
    pa = http_fixture.PassengerIntegration.pa

    def driver_action(self, token, action, payload):
        return self.request('/api/action', {'action': action, 'payload': payload, 'requestId': str(uuid.uuid4())}, token=token)

    def test_real_http_ownership_pending_consent_atomic_progress_and_cold_reload(self):
        p = self.passenger_login(); d = self.login(); pt, token = p['token'], d['token']
        self.driver_action(token, 'driver.online', {'online': True})
        order = self.pa(pt, 'order.create', {'kind': 'STANDARD', 'origin': '接口上车点', 'destination': '接口目的地',
                  'originLat': 30, 'originLng': 111, 'destinationLat': 30.02, 'destinationLng': 111.02})['result']['order']
        oid = order['id']
        self.driver_action(token, 'order.accept', {'id': oid})
        self.driver_action(token, 'order.advance', {'id': oid, 'expectedStage': 'PICKUP'})
        pending = self.driver_action(token, 'order.advance', {'id': oid, 'expectedStage': 'ARRIVED', 'phoneTail': '0101'})['result']['order']
        self.assertEqual('ARRIVED', pending['stage']); consent = pending['distanceConsent']
        self.assertIsNone(consent['distanceMeters'])
        other = self.passenger_login('13800000102')['token']
        self.pa(other, 'order.distanceConsent', {'id': oid, 'consentId': consent['id'], 'approve': True}, expected=404)
        self.pa(token, 'order.distanceConsent', {'id': oid, 'consentId': consent['id'], 'approve': True}, expected=401)
        approved = self.pa(pt, 'order.distanceConsent', {'id': oid, 'consentId': consent['id'], 'approve': True})
        self.assertEqual('TRIP', approved['result']['order']['stage'])
        self.pa(pt, 'order.distanceConsent', {'id': oid, 'consentId': consent['id'], 'approve': True})
        reboot = self.start()
        reloaded = next(o for o in self.request('/api/passenger/state', token=pt, server=reboot)['orders'] if o['id'] == oid)
        self.assertEqual('TRIP', reloaded['stage']); self.assertEqual('APPROVED', reloaded['distanceConsent']['status'])
        self.assertEqual(300, reloaded['distanceRules']['pickupMaxMeters'])
        location = {'id': oid, 'latitude': 30.02, 'longitude': 111.02, 'accuracy': 5, 'time': stamp(), 'coordinateSystem': 'WGS84'}
        self.pa(pt, 'location.report', location)
        self.driver_action(token, 'location.report', {k:v for k,v in location.items() if k != 'id'})
        finished = self.driver_action(token, 'order.advance', {'id': oid, 'expectedStage': 'TRIP'})
        self.assertEqual('WAIT_PAY', finished['result']['order']['stage'])
        self.pa(pt, 'order.pay', {'id': oid}); self.pa(pt, 'order.pay', {'id': oid})
        state = self.request('/api/state', token=token)['state']
        self.assertEqual(1, sum(r.get('title') == '行程收入 ' + oid for r in state['entries']))

    def test_simultaneous_passenger_agreements_complete_paid_trip_once(self):
        p = self.passenger_login(); d = self.login(); pt, token = p['token'], d['token']
        self.driver_action(token, 'driver.online', {'online': True})
        order = self.pa(pt, 'order.create', {'kind': 'BID', 'origin': '竞价上车点', 'destination': '竞价目的地',
                  'originLat': 30, 'originLng': 111, 'destinationLat': 30.02, 'destinationLng': 111.02})['result']['order']
        oid = order['id']; self.driver_action(token, 'order.bid', {'id': oid, 'amount': 2000})
        self.pa(pt, 'order.selectDriver', {'id': oid, 'driverId': d['profile']['driver']['id']}); self.pa(pt, 'order.pay', {'id': oid})
        self.driver_action(token, 'order.advance', {'id': oid, 'expectedStage': 'PICKUP'})
        first = self.driver_action(token, 'order.advance', {'id': oid, 'expectedStage': 'ARRIVED', 'phoneTail': '0101'})['result']['order']['distanceConsent']
        self.pa(pt, 'order.distanceConsent', {'id': oid, 'consentId': first['id'], 'approve': True})
        second = self.driver_action(token, 'order.advance', {'id': oid, 'expectedStage': 'TRIP'})['result']['order']['distanceConsent']
        payload = {'id': oid, 'consentId': second['id'], 'approve': True}
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(self.pa, pt, 'order.distanceConsent', payload) for _ in range(2)]
            results = [future.result() for future in futures]
        self.assertTrue(all(r['result']['order']['stage'] == 'DONE' for r in results))
        state = self.request('/api/state', token=token)['state']
        self.assertEqual(3, state['todayCompleted'])
        self.assertEqual(1, sum(r.get('title') == '行程收入 ' + oid for r in state['entries']))

    def test_admin_configuration_session_permissions_and_rules_readback(self):
        import test_admin_http
        helper = test_admin_http.AdminHttpIntegration()
        helper.setUp()
        try:
            session = helper.login()['session']
            helper.action(session, 'user.save', {'username': 'tripviewer', 'password': test_admin_http.PASSWORD, 'role': 'VIEWER', 'enabled': True})
            viewer = helper.login('tripviewer')['session']
            before = helper.request('state', session=session)
            row = next(r for r in before['configs'] if r['scope'] == 'city' and r['target'] == '420500')
            change = {'scope': 'city', 'target': '420500', 'expectedRevision': row['revision'], 'patch': {'tripDistance': {'pickupMaxMeters': 250, 'dropoffMaxMeters': 450}}}
            helper.action(viewer, 'config.draft', change, expected=403)
            drafted = helper.action(session, 'config.draft', change)['state']
            row = next(r for r in drafted['configs'] if r['scope'] == 'city' and r['target'] == '420500')
            published = helper.action(session, 'config.publish', {'scope': 'city', 'target': '420500', 'expectedRevision': row['revision']})['state']
            rules = next(r for r in published['cities'] if r['id'] == '420500')['tripDistanceRules']
            self.assertEqual(250, rules['pickupMaxMeters']); self.assertEqual(450, rules['dropoffMaxMeters'])
            self.assertEqual('city', rules['sources']['pickupMaxMeters']['scope'])
            helper.request('state', session=session, outer=None, expected=401)
        finally:
            helper.tearDown()

    def test_cross_instance_cas_loser_retry_cannot_double_complete_or_credit(self):
        p = self.passenger_login(); d = self.login(); pt, token = p['token'], d['token']
        self.driver_action(token, 'driver.online', {'online': True})
        oid = self.pa(pt, 'order.create', {'kind': 'BID', 'origin': '并发上车点', 'destination': '并发目的地',
                  'originLat': 30, 'originLng': 111, 'destinationLat': 30.02, 'destinationLng': 111.02})['result']['order']['id']
        self.driver_action(token, 'order.bid', {'id': oid, 'amount': 2000})
        self.pa(pt, 'order.selectDriver', {'id': oid, 'driverId': d['profile']['driver']['id']}); self.pa(pt, 'order.pay', {'id': oid})
        self.driver_action(token, 'order.advance', {'id': oid, 'expectedStage': 'PICKUP'})
        pickup = self.driver_action(token, 'order.advance', {'id': oid, 'expectedStage': 'ARRIVED', 'phoneTail': '0101'})['result']['order']['distanceConsent']
        self.pa(pt, 'order.distanceConsent', {'id': oid, 'consentId': pickup['id'], 'approve': True})
        dropoff = self.driver_action(token, 'order.advance', {'id': oid, 'expectedStage': 'TRIP'})['result']['order']['distanceConsent']
        other = self.start()
        self.request('/api/passenger/state', token=pt, server=other)
        operations = [(self.driver, str(uuid.uuid4())), (other, str(uuid.uuid4()))]
        payload = {'id': oid, 'consentId': dropoff['id'], 'approve': True}
        def send(operation):
            server, rid = operation
            request = urllib.request.Request(f'http://127.0.0.1:{server.server_port}/api/passenger/action',
                data=json.dumps({'action': 'order.distanceConsent', 'payload': payload, 'requestId': rid}).encode(),
                headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + pt})
            try:
                with urllib.request.urlopen(request, timeout=20) as response:
                    return response.status
            except urllib.error.HTTPError as exc:
                status = exc.code; exc.close(); return status
        self.remote.barrier = threading.Barrier(2)
        try:
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
                statuses = list(pool.map(send, operations))
        finally:
            self.remote.barrier = None
        self.assertEqual([200, 503], sorted(statuses))
        server, rid = operations[statuses.index(503)]
        replay = self.pa(pt, 'order.distanceConsent', payload, rid=rid, server=server)
        self.assertTrue(replay['result']['idempotent'])
        state = self.request('/api/state', token=token)['state']
        self.assertEqual(3, state['todayCompleted'])
        self.assertEqual(1, sum(r.get('title') == '行程收入 ' + oid for r in state['entries']))


if __name__ == '__main__': unittest.main()
