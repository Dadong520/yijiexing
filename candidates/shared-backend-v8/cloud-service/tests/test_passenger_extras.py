import json
import pathlib
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from common import ApiError, require
from passenger_extras import PassengerExtrasMixin


class ExtrasService(PassengerExtrasMixin):
    def __init__(self, path):
        self.db = sqlite3.connect(path)
        self.db.row_factory = sqlite3.Row
        self.orders = {
            'O1': {'id': 'O1', 'passengerId': 'P1', 'stage': 'DONE', 'paid': True, 'fare': 1234},
            'O2': {'id': 'O2', 'passengerId': 'P2', 'stage': 'DONE', 'paid': True, 'fare': 2045},
            'O3': {'id': 'O3', 'passengerId': 'P1', 'stage': 'WAIT_PAY', 'paid': False, 'fare': 2200},
        }
    def passenger_profile(self, pid):
        require(pid in ('P1', 'P2'), 'unknown passenger', 401)
        return {'id': pid}
    def own_passenger_order(self, pid, oid):
        order = self.orders.get(oid)
        require(order is not None and order['passengerId'] == pid, 'unknown order', 404)
        return order


class PassengerExtrasTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = str(pathlib.Path(self.tmp.name) / 'test.sqlite')
        self.s = ExtrasService(self.path)
    def tearDown(self):
        self.s.db.close(); self.tmp.cleanup()
    def act(self, action, p, pid='P1'):
        return self.s.passenger_extra_action(pid, action, p)
    def assert_error(self, status, action, p, pid='P1'):
        with self.assertRaises(ApiError) as ctx:
            self.act(action, p, pid)
        self.assertEqual(status, ctx.exception.status)

    def test_rider_edit_delete_isolation_and_persistence(self):
        row = self.act('rider.save', {'name': '测试乘车人', 'mobile': '13800000201'})['rider']
        self.assertEqual([], self.s.passenger_extra_data('P2')['riders'])
        self.assert_error(404, 'rider.save', {'id': row['id'], 'name': '越权', 'mobile': '13800000202'}, 'P2')
        self.assert_error(404, 'rider.remove', {'id': row['id']}, 'P2')
        self.act('rider.save', {'id': row['id'], 'name': '更新姓名', 'mobile': '13800000202'})
        self.s.db.commit(); self.s.db.close(); self.s = ExtrasService(self.path)
        self.assertEqual('更新姓名', self.s.passenger_rider('P1', row['id'])['name'])
        self.act('rider.remove', {'id': row['id']})
        self.assertEqual([], self.s.passenger_extra_data('P1')['riders'])

    def test_rider_input_duplicate_and_record_limit(self):
        self.assert_error(400, 'rider.save', {'name': '姓名', 'mobile': '123'})
        self.assert_error(400, 'rider.save', {'name': '', 'mobile': '13800000201'})
        for i in range(20):
            self.act('rider.save', {'name': f'乘车人{i}', 'mobile': f'13800000{i:03d}'})
        self.assert_error(409, 'rider.save', {'name': '重复', 'mobile': '13800000000'})
        self.assert_error(409, 'rider.save', {'name': '上限', 'mobile': '13800000299'})
        self.assertEqual(20, len(self.s.passenger_extra_data('P1')['riders']))

    def test_invoice_only_owned_completed_paid_and_unique(self):
        title = self.act('invoice.title.save', {'title': '测试个人', 'type': 'PERSONAL'})['invoiceTitle']
        self.assert_error(404, 'invoice.apply', {'orderId': 'O2', 'titleId': title['id']})
        self.assert_error(409, 'invoice.apply', {'orderId': 'O3', 'titleId': title['id']})
        receipt = self.act('invoice.apply', {'orderId': 'O1', 'titleId': title['id']})['invoice']
        self.assertEqual(1234, receipt['amount'])
        self.assertTrue(receipt['simulated'])
        self.assertIn('不是税务发票', receipt['notice'])
        duplicate = self.act('invoice.apply', {'orderId': 'O1', 'titleId': title['id']})['invoice']
        self.assertEqual(receipt['id'], duplicate['id'])
        other = self.act('invoice.title.save', {'title': '另一个抬头'})['invoiceTitle']
        self.assert_error(409, 'invoice.apply', {'orderId': 'O1', 'titleId': other['id']})
        self.assertEqual(1, len(self.s.passenger_extra_data('P1')['invoices']))
        self.assertEqual([], self.s.passenger_extra_data('P2')['invoices'])

    def test_invoice_title_validation_ownership_and_snapshot(self):
        self.assert_error(400, 'invoice.title.save', {'title': '测试企业', 'type': 'COMPANY', 'taxNumber': '123'})
        self.assert_error(400, 'invoice.title.save', {'title': '测试', 'email': 'bad@@example.com'})
        title = self.act('invoice.title.save', {'title': '原始企业', 'type': 'COMPANY', 'taxNumber': '123456789012345678'})['invoiceTitle']
        self.assert_error(404, 'invoice.title.remove', {'id': title['id']}, 'P2')
        self.assert_error(404, 'invoice.apply', {'orderId': 'O2', 'titleId': title['id']}, 'P2')
        receipt = self.act('invoice.apply', {'orderId': 'O1', 'titleId': title['id']})['invoice']
        self.act('invoice.title.save', {'id': title['id'], 'title': '修改企业', 'type': 'COMPANY', 'taxNumber': '123456789012345678'})
        self.act('invoice.title.remove', {'id': title['id']})
        saved = self.s.passenger_extra_data('P1')['invoices'][0]
        self.assertEqual('原始企业', saved['titleSnapshot']['title'])
        self.assertEqual(receipt, saved)

    def test_support_truthful_unknown_actions_rejected(self):
        support = self.s.passenger_extra_data('P1')['support']
        self.assertIsNone(support['phone'])
        self.assertFalse(support['phoneConfigured'])
        caps = {r['key']: r for r in support['capabilities']}
        self.assertEqual('SIMULATED', caps['invoice']['status'])
        self.assertEqual('UNCONFIGURED', caps['routing']['status'])
        self.assert_error(404, 'unknown', {})
        self.assert_error(401, 'rider.save', {'name': '测试', 'mobile': '13800000001'}, 'P3')


if __name__ == '__main__':
    unittest.main()
