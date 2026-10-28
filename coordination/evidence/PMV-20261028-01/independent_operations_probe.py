"""Coordinator non-author HTTP/SQLite/CAS acceptance. Only local synthetic data.

Run after PMVO has been integrated; importing this file runs no test/server.
"""
import concurrent.futures
import base64
import datetime as dt
import json
import os
import pathlib
import sys
import unittest
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path(os.environ.get('YIJ_PMV_SOURCE', '/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027'))
sys.path[:0] = [str(ROOT/'apps/cloud-service'), str(ROOT/'apps/cloud-service/tests')]
import test_admin_http as fixture
from common import now, stamp


class IndependentOperations(unittest.TestCase):
    start = fixture.AdminHttpIntegration.start
    request = fixture.AdminHttpIntegration.request
    login = fixture.AdminHttpIntegration.login
    action = fixture.AdminHttpIntegration.action

    def setUp(self):
        fixture.AdminHttpIntegration.setUp(self)
        self.session = self.login()['session']

    def tearDown(self):
        fixture.AdminHttpIntegration.tearDown(self)

    def http(self, path, payload=None, token=None, server=None, expected=200, retry_conflict=False):
        headers = {'Content-Type': 'application/json', 'X-Demo-Access': fixture.ACCESS}
        if token:
            headers['Authorization'] = 'Bearer '+token
        req = urllib.request.Request(
            f'http://127.0.0.1:{(server or self.driver).server_port}{path}',
            data=None if payload is None else json.dumps(payload).encode(), headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=20) as response:
                status, body = response.status, response.read()
        except urllib.error.HTTPError as exc:
            status, body = exc.code, exc.read()
            exc.close()
        value = json.loads(body)
        if retry_conflict and status == 503 and value.get('details', {}).get('reason') == 'DEMO_STORAGE_CONFLICT':
            # A losing snapshot writer is explicitly retryable. Reuse the exact
            # payload/requestId once; this tests deduplication after the retry.
            return self.http(path, payload, token, server, expected, retry_conflict=False)
        self.assertEqual(expected, status, body.decode(errors='replace')[:1000])
        return value.get('data', value)

    def passenger(self, mobile='13800000101'):
        code = self.http('/api/passenger/auth/code', {'mobile': mobile})['verificationCode']
        return self.http('/api/passenger/auth/login', {'mobile': mobile, 'code': code,
                         'client': 'MINIPROGRAM'})

    def driver_login(self):
        code = self.http('/api/auth/code', {'mobile': '13800000001'})['verificationCode']
        return self.http('/api/auth/login', {'mobile': '13800000001', 'code': code})

    def pa(self, token, action, payload=None, rid=None, expected=200, server=None, retry_conflict=False):
        return self.http('/api/passenger/action', {
            'action': action, 'payload': payload or {},
            'requestId': rid or str(uuid.uuid4()), 'client': 'MINIPROGRAM'
        }, token=token, expected=expected, server=server, retry_conflict=retry_conflict)

    def da(self, token, action, payload=None):
        return self.http('/api/action', {'action': action, 'payload': payload or {},
                         'requestId': str(uuid.uuid4())}, token=token)

    def aa(self, action, payload, **kw):
        reply = self.action(self.session, action, payload, **kw)
        return reply.get('result', reply)

    def state(self, token, **kw):
        return self.http('/api/passenger/state?client=MINIPROGRAM', token=token, **kw)

    def order_payload(self):
        return {'kind': 'STANDARD', 'origin': '本地起点', 'destination': '本地终点',
                'originLat': 30.6687, 'originLng': 111.3226,
                'destinationLat': 30.6645, 'destinationLng': 111.3694}

    def mutate_fixture(self, callback):
        with self.admin.request_lock:
            service = self.admin.coordinator.begin()
            try:
                result = callback(service)
                service.db.commit()
                self.admin.coordinator.finish()
                return result
            except Exception:
                service.db.rollback()
                raise

    def set_fixture_certificate_expiry(self, driver_id, days):
        def mutate(service):
            profile = json.loads(service.account(driver_id)['profile'])
            certificate = next(c for c in profile['certificates'] if c['type'] == 'insurance')
            certificate.update(validFrom=(now().date()-dt.timedelta(days=365)).isoformat(),
                               validUntil=(now().date()+dt.timedelta(days=days)).isoformat())
            service.save_profile(driver_id, profile)
        self.mutate_fixture(mutate)

    def insurance(self, token):
        return next(c for c in self.http('/api/state', token=token)['profile']['certificates']
                    if c['type'] == 'insurance')

    def location(self, driver_token, passenger_token, order, prefix):
        point = {'latitude': order[prefix+'Lat'], 'longitude': order[prefix+'Lng'],
                 'accuracy': 5, 'time': stamp(), 'coordinateSystem': 'WGS84'}
        self.pa(passenger_token, 'location.report', {'id': order['id'], **point})
        self.da(driver_token, 'location.report', point)

    def announcement(self):
        a = self.aa('ops.announcement.save', {
            'title': '独立验收公告 <b>纯文本</b>', 'content': '本地合成测试，不发送外部消息',
            'scope': 'CITY', 'target': '420500', 'audiences': ['MINIPROGRAM'],
            'startAt': stamp(now()-dt.timedelta(minutes=5)),
            'endAt': stamp(now()+dt.timedelta(minutes=30))})['announcement']
        return self.aa('ops.announcement.publish', {
            'id': a['id'], 'expectedVersion': a['version']})['announcement']

    def test_reads_do_not_consume_ack_or_replay_first_display(self):
        passenger = self.passenger(); token = passenger['token']; a = self.announcement()
        for _ in range(3):
            notices = self.state(token)['operations']['announcements']
            item = next(x for x in notices if x['id'] == a['id'])
        payload = {'id': item['id'], 'version': item['version'], 'client': 'MINIPROGRAM'}
        first = self.pa(token, 'ops.announcement.ack', payload, rid='one-physical-display')['result']
        self.assertTrue(first['firstDisplay'])
        same_request = self.pa(token, 'ops.announcement.ack', payload, rid='one-physical-display')['result']
        self.assertFalse(same_request['firstDisplay'], 'cached idempotency result must not re-open UI')
        other_request = self.pa(token, 'ops.announcement.ack', payload)['result']
        self.assertFalse(other_request['firstDisplay'])
        reboot = self.start('driver')
        self.assertFalse(any(x['id'] == a['id'] for x in self.state(token, server=reboot)['operations']['announcements']))
        other = self.passenger('13800000102')
        self.assertTrue(any(x['id'] == a['id'] for x in self.state(other['token'])['operations']['announcements']))

    def test_two_instances_only_one_announcement_claim(self):
        token = self.passenger()['token']; a = self.announcement()
        item = next(x for x in self.state(token)['operations']['announcements'] if x['id'] == a['id'])
        payload = {'id': item['id'], 'version': item['version'], 'client': 'MINIPROGRAM'}
        second = self.start('driver')
        def claim(server):
            return self.pa(token, 'ops.announcement.ack', payload, server=server, retry_conflict=True)['result']['firstDisplay']
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(claim, [self.driver, second]))
        self.assertEqual(sorted(results), [False, True])

    def test_feature_draft_not_live_api_gate_and_history_survives(self):
        old_token = self.passenger()['token']
        old = self.pa(old_token, 'order.create', self.order_payload())['result']['order']
        new_token = self.passenger('13800000102')['token']
        config = self.aa('ops.passengerFeatures.save', {
            'scope': 'CITY', 'target': '420500', 'expectedVersion': 0,
            'features': {'instant': False}})['passengerFeatureConfig']
        self.assertTrue(self.state(new_token)['operations']['passengerFeatures']['features']['instant'])
        live = self.aa('ops.passengerFeatures.publish', {
            'scope': 'CITY', 'target': '420500', 'expectedVersion': config['version']})['passengerFeatureConfig']
        self.assertFalse(self.state(new_token)['operations']['passengerFeatures']['features']['instant'])
        self.pa(new_token, 'order.create', self.order_payload(), expected=403)
        self.assertEqual(self.state(new_token)['orders'], [])
        self.assertTrue(any(x['id'] == old['id'] for x in self.state(old_token)['orders']))
        self.pa(old_token, 'order.cancel', {'id': old['id'], 'reason': '本地验收取消'})
        self.aa('ops.passengerFeatures.publish', {'scope': 'CITY', 'target': '420500',
                'expectedVersion': config['version']}, expected=409)
        reset = self.aa('ops.passengerFeatures.save', {'scope': 'CITY', 'target': '420500',
            'expectedVersion': live['version'], 'features': {}})['passengerFeatureConfig']
        self.aa('ops.passengerFeatures.publish', {'scope': 'CITY', 'target': '420500',
                'expectedVersion': reset['version']})
        self.assertTrue(self.state(new_token)['operations']['passengerFeatures']['features']['instant'])
        self.pa(new_token, 'order.create', self.order_payload())

    def test_company_history_tracks_assignment_not_current_display_name(self):
        companies = [self.aa('ops.company.save', {'name': name,
            'supportPhone': phone, 'enabled': True})['company'] for name, phone in
            [('本地甲公司', '010-00000001'), ('本地乙公司', '010-00000002')]]
        driver = self.driver_login(); did = driver['profile']['driver']['id']; dtoken = driver['token']
        mapping = self.aa('ops.driverCompany.set', {'driverId': did,
            'companyId': companies[0]['id'], 'expectedVersion': 0})['driverCompany']
        token = self.passenger()['token']
        self.pa(token, 'ops.company.select', {'companyId': companies[1]['id']})
        self.assertEqual(self.state(token)['operations']['company']['id'], companies[1]['id'])
        self.da(dtoken, 'driver.online', {'online': True})
        order = self.pa(token, 'order.create', self.order_payload())['result']['order']
        self.da(dtoken, 'order.accept', {'id': order['id']})
        owned = next(x for x in self.state(token)['orders'] if x['id'] == order['id'])
        self.assertEqual(owned['company']['id'], companies[0]['id'])
        self.aa('ops.driverCompany.set', {'driverId': did, 'companyId': companies[1]['id'],
                'expectedVersion': mapping['version']})
        reboot = self.start('driver')
        owned = next(x for x in self.state(token, server=reboot)['orders'] if x['id'] == order['id'])
        self.assertEqual(owned['company']['id'], companies[0]['id'])
        driver_state = self.http('/api/state', token=dtoken, server=reboot)
        self.assertEqual(driver_state['operations']['company']['id'], companies[1]['id'])

    def test_valid_old_certificate_pending_rejected_expired_then_approved(self):
        driver = self.driver_login(); did = driver['profile']['driver']['id']; token = driver['token']
        self.set_fixture_certificate_expiry(did, 61)
        certificate = self.insurance(token)
        self.assertFalse(certificate['canUpdate'])
        self.assertFalse(certificate['reminder'])
        image = base64.b64encode((ROOT/'apps/cloud-service/static/sandbox-certificate-sample.png').read_bytes()).decode()
        payload = {'type': 'insurance', 'validFrom': now().date().isoformat(),
                   'validUntil': (now().date()+dt.timedelta(days=365)).isoformat(),
                   'imageBase64': image, 'expectedVersion': certificate['version']}
        self.http('/api/action', {'action': 'certificate.submit', 'payload': payload,
                  'requestId': str(uuid.uuid4())}, token=token, expected=409)
        self.set_fixture_certificate_expiry(did, 60)
        self.assertTrue(self.insurance(token)['canUpdate'])
        self.da(token, 'certificate.submit', payload)
        pending = self.insurance(token)
        self.assertEqual(pending['daysRemaining'], 60)
        self.assertEqual(pending['status'], 'APPROVED')
        self.assertFalse(pending['canUpdate'])
        self.assertIsNotNone(pending['pending'])
        review = self.request('certificate-file?driverId='+did+'&type=insurance&source=REVIEW', session=self.session)
        self.assertEqual(review['source'], 'REVIEW')
        self.assertEqual(review['submissionId'], pending['pending']['submissionId'])
        self.da(token, 'driver.online', {'online': True})
        self.aa('certificate.audit', {'driverId': did, 'type': 'insurance', 'approved': False,
                'reason': '本地独立验收退回', 'expectedVersion': pending['version'],
                'submissionId': pending['pending']['submissionId']})
        rejected = self.insurance(token)
        self.assertEqual(rejected['status'], 'APPROVED')
        self.assertIsNone(rejected['pending'])
        self.assertTrue(rejected['canUpdate'])
        self.da(token, 'certificate.submit', {**payload, 'expectedVersion': rejected['version']})
        pending = self.insurance(token)
        passenger_token = self.passenger()['token']
        order = self.pa(passenger_token, 'order.create', self.order_payload())['result']['order']
        self.da(token, 'order.accept', {'id': order['id']})
        self.da(token, 'order.advance', {'id': order['id']})
        active = next(o for o in self.http('/api/state', token=token)['state']['orders'] if o['id'] == order['id'])
        self.location(token, passenger_token, order, 'origin')
        self.da(token, 'order.advance', {'id': order['id'], 'phoneTail': active['phoneTail']})
        self.set_fixture_certificate_expiry(did, -1)
        self.assertEqual(self.insurance(token)['validity'], 'EXPIRED')
        self.http('/api/action', {'action': 'driver.online', 'payload': {'online': True},
                  'requestId': str(uuid.uuid4())}, token=token, expected=403)
        self.location(token, passenger_token, order, 'destination')
        self.da(token, 'order.advance', {'id': order['id']})
        self.pa(passenger_token, 'order.pay', {'id': order['id'], 'outcome': 'SUCCESS'})
        finished = next(o for o in self.state(passenger_token)['orders'] if o['id'] == order['id'])
        self.assertEqual(finished['stage'], 'DONE')
        self.aa('certificate.audit', {'driverId': did, 'type': 'insurance', 'approved': True,
                'reason': '本地合成材料审核通过说明',
                'expectedVersion': pending['version'], 'submissionId': pending['pending']['submissionId']})
        approved = self.insurance(token)
        self.assertIsNone(approved['pending'])
        self.assertFalse(approved['reminder'])
        self.assertEqual(approved['validUntil'], payload['validUntil'])
        self.assertEqual(approved['auditRecords'][-1]['reason'], '本地合成材料审核通过说明')
        self.assertFalse(approved.get('rejectionReason'))
        self.da(token, 'driver.online', {'online': True})

    def test_new_operations_permissions_versions_and_failed_commit(self):
        company = {'name': '本地权限验收公司', 'supportPhone': None, 'enabled': True}
        for role in ('VIEWER', 'OPERATOR', 'FINANCE'):
            name = 'probe_'+role.lower()
            self.aa('user.save', {'username': name, 'password': fixture.PASSWORD,
                                 'role': role, 'enabled': True})
            session = self.login(name)['session']
            for action, payload in [('ops.company.save', company),
                                    ('ops.announcement.publish', {}),
                                    ('ops.passengerFeatures.publish', {})]:
                self.action(session, action, payload, expected=403)
        self.aa('ops.company.save', {**company, 'expectedVersion': True}, expected=400)
        self.assertEqual(self.request('state', session=self.session)['operations']['companies'], [])
        self.remote.fail_write = True
        self.aa('ops.company.save', company, request_id='failed-then-retried', expected=503)
        self.remote.fail_write = False
        self.assertEqual(self.request('state', session=self.session)['operations']['companies'], [])
        self.aa('ops.company.save', company, request_id='failed-then-retried')
        self.aa('ops.company.save', company, request_id='failed-then-retried')
        self.assertEqual(len(self.request('state', session=self.session)['operations']['companies']), 1)

    def test_invoice_projection_and_api_follow_order_city_after_city_switch(self):
        token = self.passenger()['token']
        order = self.pa(token, 'order.create', self.order_payload())['result']['order']
        def complete_fixture(service):
            row = service.shared_order(order['id'])
            row.update(stage='DONE', paid=True, paymentStatus='PAID', completedAt=stamp())
            service.save_shared(row)
        self.mutate_fixture(complete_fixture)
        self.pa(token, 'profile.update', {'city': '420100'})
        versions = {}
        def publish(city, enabled):
            row = self.aa('ops.passengerFeatures.save', {
                'scope': 'CITY', 'target': city, 'expectedVersion': versions.get(city, 0),
                'features': {'invoice': enabled}})['passengerFeatureConfig']
            row = self.aa('ops.passengerFeatures.publish', {
                'scope': 'CITY', 'target': city, 'expectedVersion': row['version']})['passengerFeatureConfig']
            versions[city] = row['version']
        publish('420500', False)
        title = self.pa(token, 'invoice.title.save', {'type': 'PERSONAL', 'title': '本地验收个人'})['result']['invoiceTitle']
        current = self.state(token)
        row = next(x for x in current['orders'] if x['id'] == order['id'])
        self.assertTrue(current['operations']['passengerFeatures']['features']['invoice'])
        self.assertFalse(row['invoiceApplicationEnabled'])
        args = {'orderId': order['id'], 'titleId': title['id']}
        self.pa(token, 'invoice.apply', args, expected=403)
        publish('420500', True)
        publish('420100', False)
        current = self.state(token)
        self.assertFalse(current['operations']['passengerFeatures']['features']['invoice'])
        self.assertTrue(next(x for x in current['orders'] if x['id'] == order['id'])['invoiceApplicationEnabled'])
        issued = self.pa(token, 'invoice.apply', args)['result']['invoice']
        self.assertEqual(issued['status'], 'SIMULATED_ISSUED')
        publish('420500', False)
        reread = self.pa(token, 'invoice.apply', args)['result']['invoice']
        self.assertEqual(reread['id'], issued['id'])
        self.assertTrue(any(x['id'] == issued['id'] for x in self.state(token)['invoices']))


if __name__ == '__main__':
    unittest.main(verbosity=2)
