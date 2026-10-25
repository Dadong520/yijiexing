"""Read-only management projections with local persistent fixtures and real HTTP.

Dates/multiple equal refund rounds are purpose-built fixtures, never live orders.
"""
import csv
import io
import pathlib
import sys
import tempfile
import unittest
from urllib.parse import urlencode

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from admin_console import AdminConsole, ROLES
from common import ApiError, clone, dumps, now, stamp
from domain import Service
from seed import initial_profile, initial_state
import test_admin_http as http_fixture


def seed_query_data(svc):
    for driver, mobile, name, city, plate in [
            ('D1', '13800000001', '张司机', '420500', '鄂E·A1001'),
            ('D2', '13800000002', '李司机', '420100', '鄂A·B1002')]:
        profile = initial_profile(mobile)
        profile['driver'].update(id=driver, name=name, city=city, plate=plate)
        if driver == 'D1':
            profile['wallet']['withdrawals'] = [
                {'id': 'W1', 'amount': 300, 'status': 'PENDING', 'createdAt': '2026-10-24T12:00:00+08:00', 'sandbox': True},
                {'id': 'W2', 'amount': 400, 'status': 'SANDBOX_APPROVED', 'createdAt': '2026-10-25T12:00:00+08:00', 'sandbox': True}]
            profile['feedback'] = [{'id': 'DF1', 'content': '司机反馈', 'status': 'PENDING', 'createdAt': '2026-10-24T12:00:00+08:00'}]
        svc.db.execute('INSERT INTO accounts VALUES (?,?,?,?,?,?,?,?)',
                       (driver, mobile, dumps(initial_state()), dumps(profile), None, stamp(now()), 2, None))
    for pid, mobile, city in [('P1', '13800000101', '420500'), ('P2', '13800000102', '420100')]:
        p = {'id': pid, 'mobile': mobile, 'name': '乘客' + pid, 'city': city, 'feedback': [], 'favorites': []}
        if pid == 'P1':
            p['feedback'] = [{'id': 'PF1', 'content': '车内整洁问题', 'status': 'REPLIED',
                              'reply': '已处理', 'createdAt': '2026-10-24T12:00:00+08:00'}]
        svc.db.execute('INSERT INTO passengers VALUES (?,?,?)', (pid, mobile, dumps(p)))
    specs = [('A', '2026-10-23T15:59:59+00:00', 'AVAILABLE', 'P2', 'D2', 'BID'),
             ('B', '2026-10-23T16:00:00+00:00', 'CANCELLED', 'P1', 'D1', 'STANDARD'),
             ('C', '2026-10-23T16:00:00+00:00', 'WAIT_CONTINUE', 'P1', 'D1', 'STANDARD'),
             ('D', '2026-10-24T15:59:59.999+00:00', 'DONE', 'P1', 'D1', 'STANDARD'),
             ('E', '2026-10-24T16:00:00+00:00', 'AVAILABLE', 'P2', 'D2', 'RESERVED'),
             ('LEGACY', None, 'DONE', 'P1', None, None),
             ('ORPHAN', 'bad-legacy-time', 'AVAILABLE', 'PMISSING', 'DMISSING', 'STANDARD')]
    for oid, created, stage, pid, did, kind in specs:
        o = {'id': oid, 'passengerId': pid, 'driverId': did, 'stage': stage, 'kind': kind,
             'fare': 2000 if oid == 'D' else 900, 'paid': oid in ('D', 'LEGACY'),
             'paymentStatus': 'SUCCESS' if oid in ('D', 'LEGACY') else 'UNPAID',
             'paymentMode': 'SIMULATED', 'origin': '=CMD(A1)' if oid == 'D' else '宜昌站',
             'destination': '商场', 'city': '420500' if pid == 'P1' else '420100',
             'createdAt': created, 'updatedAt': '2026-10-25T18:00:00+08:00',
             'fareSource': 'EXPERIENCE_ESTIMATE', 'events': [], 'complaints': [], 'evaluation': None,
             'bids': [], 'shared': True}
        if oid == 'B':
            o.update(cancelledBy='PASSENGER', cancelReason='行程有变', paymentStatus='REFUNDED',
                     refundAmount=900, refundedAt='2026-10-24T16:00:00+08:00', refundMode='SIMULATED',
                     refundHistory=[{'amount': 900, 'at': '2026-10-24T16:00:00+08:00', 'driverId': 'D2', 'mode': 'SIMULATED'},
                                    {'amount': 900, 'at': '2026-10-24T16:00:00+08:00', 'driverId': 'D2', 'mode': 'SIMULATED'}],
                     events=[{'type': 'PASSENGER_CANCEL', 'at': '2026-10-24T16:00:00+08:00'}])
        if oid == 'C':
            o.update(cancelledBy='DRIVER', cancelReason='车辆故障')
        if oid == 'D':
            o['paidAt'] = '2026-10-24T23:59:59+08:00'
            o['evaluation'] = {'rating': 5, 'content': '不错', 'createdAt': '2026-10-24T23:59:59+08:00'}
            o['complaints'] = [{'id': 'OC1', 'reason': '费用', 'content': '费用说明请求', 'status': 'PENDING', 'createdAt': '2026-10-24T20:00:00+08:00'}]
        svc.db.execute('INSERT INTO passenger_orders VALUES (?,?,?,?,?)', (oid, pid, did, dumps(o), created or ''))
    for index in range(205):
        svc.db.execute('INSERT INTO audits(actor,action,before_value,after_value,created) VALUES (?,?,?,?,?)',
                       ('ADMIN:old' if index == 0 else 'ADMIN:current', 'query.fixture', '{}', '{}', '2026-10-24T12:00:00+08:00'))
    svc.db.commit()


class AdminQueryProjection(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.svc = Service(pathlib.Path(self.tmp.name) / 'query.sqlite')
        seed_query_data(self.svc)
        self.console = AdminConsole(self.svc)
        self.user = {'username': 'local-test', 'role': 'SUPER_ADMIN', 'permissions': sorted(ROLES['SUPER_ADMIN'])}

    def tearDown(self):
        self.svc.close()
        self.tmp.cleanup()

    def query(self, dataset='orders', **params):
        return self.console.query(self.user, {'dataset': dataset, **params})

    def test_combined_order_filters_join_persistent_accounts(self):
        result = self.query(kind='STANDARD', passengerMobile='0000101', driverId='D1',
                            driverName='张', driverMobile='0000001', plate='A1001', city='420500',
                            startDate='2026-10-24', endDate='2026-10-24')
        self.assertEqual(['D', 'B', 'C'], [r['id'] for r in result['items']])
        self.assertEqual(3, result['total'])
        self.assertEqual('13800000101', result['items'][0]['passengerMobile'])
        self.assertEqual(2700, result['summary']['refundAmount'])
        self.assertEqual(2000, result['summary']['paidFare'])
        self.assertEqual('即时用车', next(v['label'] for v in result['options']['kind'] if v['value'] == 'STANDARD'))

    def test_utc8_inclusive_calendar_days_are_closed_open_instants(self):
        result = self.query(startDate='2026-10-24', endDate='2026-10-24')
        self.assertEqual({'B', 'C', 'D'}, {r['id'] for r in result['items']})
        self.assertEqual('2026-10-24T00:00:00+08:00', result['dateRange']['startInclusive'])
        self.assertEqual('2026-10-25T00:00:00+08:00', result['dateRange']['endExclusive'])
        self.assertEqual(2, result['summary']['missingDateCount'])
        self.assertEqual({'E'}, {r['id'] for r in self.query(startDate='2026-10-25')['items']})

    def test_pagination_stable_ties_and_summary_before_slice(self):
        p1 = self.query(page='1', pageSize='2')
        p2 = self.query(page='2', pageSize='2')
        all_rows = self.query(pageSize='100')
        self.assertEqual(all_rows['items'][:4], p1['items'] + p2['items'])
        self.assertEqual(p1['summary'], p2['summary'])
        self.assertEqual(7, p1['total'])
        self.assertEqual([], self.query(page='999')['items'])
        self.assertEqual(['B', 'C'], [r['id'] for r in self.query(startDate='2026-10-24', endDate='2026-10-24', page='2', pageSize='1')['items']] +
                         [r['id'] for r in self.query(startDate='2026-10-24', endDate='2026-10-24', page='3', pageSize='1')['items']])

    def test_missing_old_fields_not_fabricated_and_unknown_dates_visible(self):
        rows = {r['id']: r for r in self.query()['items']}
        self.assertIsNone(rows['ORPHAN']['passengerMobile'])
        self.assertIsNone(rows['ORPHAN']['driverMobile'])
        self.assertIsNone(rows['LEGACY']['kind'])
        self.assertEqual(2, self.query()['summary']['missingDateCount'])
        self.assertNotIn('LEGACY', [r['id'] for r in self.query(kind='STANDARD')['items']])

    def test_unsupported_original_fields_disabled_and_nonempty_rejected(self):
        schema = {r['name']: r for r in self.query()['schema']}
        for key in ('company', 'assigned', 'billingMode'):
            self.assertEqual('NOT_IMPLEMENTED', schema[key]['availability'])
            with self.assertRaises(ApiError) as caught:
                self.query(**{key: '1'})
            self.assertEqual(400, caught.exception.status)
        self.assertEqual(7, self.query(company='', assigned='', billingMode='')['total'])

    def test_invalid_query_inputs_rejected(self):
        for params in [{'page': '0'}, {'page': '-1'}, {'page': '1.5'}, {'pageSize': '101'},
                       {'page': '1000001'}, {'page': '1e3'}, {'search': 'a'*101}, {'x': ''},
                       {'startDate': '2026-02-30'}, {'endDate': '9999-12-31'},
                       {'startDate': '2026-10-25', 'endDate': '2026-10-24'},
                       {'startDate': '2026-10-24T00:00:00Z'}, {'kind': 'PHONE_BOOKING'},
                       {'kind': ['STANDARD', 'BID']}, {'dataset': ['orders', 'audit']}]:
            with self.subTest(params=params), self.assertRaises(ApiError):
                self.console.query(self.user, params)

    def test_account_filters_persistent_and_date_explicitly_unsupported(self):
        result = self.query('drivers', certificateStatus='APPROVED', city='420500', mobile='000001', plate='A1001')
        self.assertEqual(['D1'], [r['id'] for r in result['items']])
        self.assertEqual(['P2'], [r['id'] for r in self.query('passengers', city='420100', passengerId='P2')['items']])
        for dataset in ('drivers', 'passengers'):
            self.assertTrue(all(f['availability'] == 'NOT_IMPLEMENTED' for f in self.query(dataset)['schema'] if f['type'] == 'date'))
            with self.assertRaises(ApiError):
                self.query(dataset, startDate='2026-10-24')

    def test_cancellation_report_current_scope_and_missing_event_date(self):
        rows = {r['id']: r for r in self.query('cancellations')['items']}
        self.assertEqual({'B', 'C'}, set(rows))
        self.assertEqual('CLOSED', rows['B']['cancellationStatus'])
        self.assertEqual('AWAITING_PASSENGER', rows['C']['cancellationStatus'])
        self.assertIsNone(rows['C']['cancelledAt'])
        self.assertEqual(1, self.query('cancellations', startDate='2026-10-24')['total'])
        self.assertEqual(['C'], [r['id'] for r in self.query('cancellations', cancellationType='DRIVER')['items']])

    def test_equal_refund_rounds_all_counted_and_event_dates_used(self):
        result = self.query('refunds', startDate='2026-10-24', endDate='2026-10-24', orderId='B', accountId='P1')
        self.assertEqual(3, result['total'])
        self.assertEqual(3, len({r['id'] for r in result['items']}))
        self.assertEqual(2700, result['summary']['refundAmount'])
        self.assertEqual(2, self.query('refunds', refundSource='HISTORY', driverId='D2')['total'])

    def test_payments_order_refund_and_withdrawal_filters_and_separate_totals(self):
        result = self.query('payments')
        self.assertEqual(9, result['total'])
        self.assertEqual(700, result['summary']['withdrawalAmount'])
        self.assertEqual(2700, result['summary']['refundAmount'])
        self.assertEqual(['B'], [r['orderId'] for r in self.query('payments', recordType='ORDER', refundStatus='HAS_REFUND')['items']])
        rows = self.query('payments', recordType='WITHDRAWAL', withdrawalStatus='PENDING', accountId='D1', endDate='2026-10-24')['items']
        self.assertEqual(1, len(rows))
        self.assertEqual('W1', rows[0]['withdrawalId'])
        self.assertEqual([], self.query('payments', recordType='WITHDRAWAL', refundStatus='HAS_REFUND')['items'])

    def test_statistics_cohorts_and_summary_not_page_or_duplicate_refunds(self):
        result = self.query('statistics', startDate='2026-10-24', endDate='2026-10-24')
        self.assertEqual(1, result['total'])
        row = result['items'][0]
        self.assertEqual('2026-10-24', row['date'])
        self.assertEqual(3, row['orderCount'])
        self.assertEqual(2000, row['paidFare'])
        self.assertEqual(2700, row['refundAmount'])
        all_result = self.query('statistics', pageSize='1')
        self.assertEqual(7, all_result['summary']['orderCount'])
        self.assertEqual(4, all_result['total'])
        self.assertEqual('UNKNOWN', self.query('statistics', pageSize='100')['items'][-1]['date'])

    def test_support_all_sources_and_real_account_join(self):
        rows = self.query('support')['items']
        self.assertEqual(4, len(rows))
        self.assertEqual(4, len({r['rowId'] for r in rows}))
        result = self.query('support', source='ORDER_COMPLAINT', accountId='P1', status='PENDING', search='费用')
        self.assertEqual(['OC1'], [r['id'] for r in result['items']])

    def test_audit_query_reads_beyond_old_state_200_limit(self):
        self.assertEqual(200, len(self.console.state(self.user)['audit']))
        result = self.query('audit', actor='old', action='query.fixture', startDate='2026-10-24')
        self.assertEqual(1, result['total'])
        self.assertEqual('ADMIN:old', result['items'][0]['actor'])

    def test_permissions_and_related_account_fields(self):
        user = {**self.user, 'permissions': ['orders.read']}
        result = self.console.query(user, {'dataset': 'orders'})
        self.assertNotIn('passengerMobile', result['items'][0])
        self.assertNotIn('driverMobile', result['items'][0])
        for key in ('passengerMobile', 'driverMobile', 'driverName', 'plate'):
            with self.assertRaises(ApiError) as caught:
                self.console.query(user, {'dataset': 'orders', key: 'x'})
            self.assertEqual(403, caught.exception.status)
        with self.assertRaises(ApiError):
            self.console.query(user, {'dataset': 'refunds'})
        viewer = {**self.user, 'role': 'VIEWER', 'permissions': sorted(ROLES['VIEWER'])}
        self.assertEqual(7, self.console.query(viewer, {})['total'])
        with self.assertRaises(ApiError) as caught:
            self.console.export(viewer)
        self.assertEqual(403, caught.exception.status)

    def test_csv_exact_selection_all_pages_and_formula_protection(self):
        params = {'dataset': 'orders', 'kind': 'STANDARD', 'startDate': '2026-10-24',
                  'endDate': '2026-10-24', 'page': '2', 'pageSize': '1'}
        csv_rows = list(csv.DictReader(io.StringIO(self.console.export(self.user, params=params).lstrip('\ufeff'))))
        self.assertEqual(['D', 'B', 'C'], [r['id'] for r in csv_rows])
        self.assertEqual("'=CMD(A1)", csv_rows[0]['origin'])
        self.assertEqual(7, len(list(csv.DictReader(io.StringIO(self.console.export(self.user, 'orders').lstrip('\ufeff'))))))
        for dataset in ('drivers', 'passengers', 'payments', 'support', 'refunds', 'cancellations', 'statistics'):
            params = {'dataset': dataset, 'search': 'B' if dataset in ('refunds', 'cancellations') else '', 'pageSize': '1'}
            exported = list(csv.DictReader(io.StringIO(self.console.export(self.user, params=params).lstrip('\ufeff'))))
            self.assertEqual(self.console.query(self.user, params)['total'], len(exported), dataset)

    def test_query_projection_does_not_write_domain_rows(self):
        before = {table: [tuple(r) for r in self.svc.db.execute('SELECT * FROM ' + table)]
                  for table in ('passenger_orders', 'passengers', 'accounts', 'audits')}
        for dataset in ('orders', 'drivers', 'passengers', 'payments', 'support', 'audit', 'statistics', 'cancellations', 'refunds'):
            self.query(dataset)
        after = {table: [tuple(r) for r in self.svc.db.execute('SELECT * FROM ' + table)] for table in before}
        self.assertEqual(before, after)


class AdminQueryHttp(unittest.TestCase):
    # Reuse the existing real HTTP + SQLite + memory snapshot CAS fixture without
    # inheriting its test methods or introducing another server implementation.
    setUp = http_fixture.AdminHttpIntegration.setUp
    tearDown = http_fixture.AdminHttpIntegration.tearDown
    start = http_fixture.AdminHttpIntegration.start
    request = http_fixture.AdminHttpIntegration.request
    login = http_fixture.AdminHttpIntegration.login
    action = http_fixture.AdminHttpIntegration.action

    def test_query_auth_session_role_and_public_driver_boundaries(self):
        self.request('query?dataset=orders', expected=401)
        session = self.login()['session']
        self.request('query?dataset=orders', session=session, outer=None, expected=401)
        self.request('query?dataset=orders', session=session, server=self.driver, expected=404)
        self.assertEqual(0, self.request('query?dataset=orders', session=session)['total'])
        self.action(session, 'user.save', {'username': 'viewer', 'password': http_fixture.PASSWORD, 'role': 'VIEWER', 'enabled': True})
        viewer = self.login('viewer')['session']
        for dataset in ('orders', 'drivers', 'passengers', 'payments', 'support', 'audit', 'statistics', 'cancellations', 'refunds'):
            self.request('query?dataset=' + dataset, session=viewer)
            self.request('export?dataset=' + dataset, session=viewer, expected=403)
        self.request('query?dataset=orders&dataset=payments', session=session, expected=400)
        self.request('query?dataset=orders&pageSize=101', session=session, expected=400)
        self.request('query?dataset=orders&assigned=1', session=session, expected=400)
        self.request('query?dataset=orders&unknown=', session=session, expected=400)
        self.request('query?dataset=orders&page=&page=1', session=session, expected=400)
        self.request('export?dataset=orders&unknown=', session=session, expected=400)

    def test_filtered_http_query_export_cold_reload_and_failure_recovery(self):
        session = self.login()['session']
        with self.admin.request_lock:
            svc = self.admin.coordinator.begin()
            seed_query_data(svc)
            self.admin.coordinator.finish()
        params = {'dataset': 'orders', 'kind': 'STANDARD', 'city': '420500',
                  'startDate': '2026-10-24', 'endDate': '2026-10-24', 'page': '2', 'pageSize': '1'}
        query = urlencode(params)
        result = self.request('query?' + query, session=session)
        self.assertEqual(['B'], [r['id'] for r in result['items']])
        self.assertEqual(3, result['total'])
        self.assertEqual(2700, result['summary']['refundAmount'])
        exported = self.request('export?' + query, session=session)
        rows = list(csv.DictReader(io.StringIO(exported['body'].decode('utf-8-sig'))))
        self.assertEqual(['D', 'B', 'C'], [r['id'] for r in rows])
        self.assertEqual("'=CMD(A1)", rows[0]['origin'])
        replacement = self.start('admin')
        reloaded = self.request('query?' + query, session=session, server=replacement)
        self.assertEqual(result, reloaded)
        self.remote.fail_read = True
        failure = self.request('query?' + query, session=session, server=replacement, expected=503)
        self.assertEqual('DEMO_STORAGE_UNAVAILABLE', failure['details']['reason'])
        self.remote.fail_read = False
        self.assertEqual(3, self.request('query?' + query, session=session, server=replacement)['total'])
        refund = self.request('query?' + urlencode({'dataset': 'refunds', 'orderId': 'B', 'startDate': '2026-10-24'}), session=session)
        self.assertEqual(3, refund['total'])
        self.assertEqual(2700, refund['summary']['refundAmount'])


if __name__ == '__main__':
    unittest.main()
