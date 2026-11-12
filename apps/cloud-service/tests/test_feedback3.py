"""修改意见3.0 regression tests on the isolated HTTP/SQLite/MemoryCAS harness."""
import uuid
from policy_helpers import PolicyCase


class Feedback3(PolicyCase):
    def complete(self, p, d, **overrides):
        o = self.create(p['token'], **overrides)
        self.da(d['token'], 'order.accept', {'id': o['id']})
        self.finish(d['token'], o['id'])
        order = self.get_order(p['token'], o['id'])
        self.pa(p['token'], 'order.pay', {'id': o['id'], 'expectedFare': order['fare'], 'fareVersion': order['fareVersion']})
        return self.get_order(p['token'], o['id'])

    def test_blacklisted_passenger_orders_are_hidden_and_cannot_be_accepted(self):
        p, d = self.setup_pair()
        done = self.complete(p, d)
        self.assertEqual(done['stage'], 'DONE')
        self.da(d['token'], 'order.complaint', {'id': done['id'], 'reason': '乘客爽约', 'content': '乘客多次爽约不上车', 'blockUser': True})
        # Same booker, different rider phone tail: the block follows the passenger account.
        rider = self.pa(p['token'], 'rider.save', {'name': '家人', 'mobile': '13900009999'})['result']['rider']
        nxt = self.create(p['token'], riderId=rider['id'])
        visible = [o['id'] for o in self.state(d['token'])['orders']]
        self.assertNotIn(nxt['id'], visible)
        self.request('/api/action', {'action': 'order.accept', 'payload': {'id': nxt['id'], 'competitionRound': 1},
                                     'requestId': str(uuid.uuid4())}, token=d['token'], expected=404)

    def admin(self, action, payload):
        return self.mutate_fixture(lambda svc: svc.operations.admin_action(action, payload, 'ADMIN:test'))

    def test_block_passenger_action_is_private_and_removable(self):
        p, d = self.setup_pair()
        done = self.complete(p, d)
        events_before = len(self.get_order(p['token'], done['id'])['events'])
        entry = self.da(d['token'], 'order.blockPassenger', {'id': done['id'], 'reason': '态度恶劣'})['result']['blacklist']
        self.assertEqual(len(self.get_order(p['token'], done['id'])['events']), events_before)
        nxt = self.create(p['token'])
        self.assertNotIn(nxt['id'], [o['id'] for o in self.state(d['token'])['orders']])
        self.da(d['token'], 'blacklist.remove', {'id': entry['id']})
        self.assertIn(nxt['id'], [o['id'] for o in self.state(d['token'])['orders']])

    def test_invoice_can_be_applied_with_inline_title_without_default(self):
        p, d = self.setup_pair()
        done = self.complete(p, d)
        self.assertEqual(self.request('/api/passenger/state', token=p['token'])['invoiceTitles'], [])
        self.pa(p['token'], 'invoice.apply', {'orderId': done['id'], 'type': 'COMPANY', 'title': '宜昌测试公司', 'taxNumber': '12345'}, expected=400)
        reply = self.pa(p['token'], 'invoice.apply', {'orderId': done['id'], 'type': 'COMPANY', 'title': '宜昌测试公司',
                                                       'taxNumber': '91420500ma4abcde1x', 'email': 'a@b.cn', 'saveTitle': True})
        invoice = reply['result']['invoice']
        self.assertEqual((invoice['title'], invoice['titleSnapshot']['taxNumber']), ('宜昌测试公司', '91420500MA4ABCDE1X'))
        self.assertEqual([t['title'] for t in reply['invoiceTitles']], ['宜昌测试公司'])
        self.assertFalse(reply['invoiceTitles'][0]['isDefault'])
        again = self.pa(p['token'], 'invoice.apply', {'orderId': done['id'], 'titleId': reply['invoiceTitles'][0]['id']})
        self.assertEqual(again['result']['invoice']['id'], invoice['id'])
        other = self.complete(p, d)
        personal = self.pa(p['token'], 'invoice.apply', {'orderId': other['id'], 'title': '张三'})
        self.assertEqual(len(personal['invoiceTitles']), 1)
        self.assertIsNone(personal['result']['invoice']['titleId'])
        rows = self.mutate_fixture(lambda svc: __import__('admin_extras').invoice_rows(svc))
        self.assertEqual(sorted(r['title'] for r in rows), ['宜昌测试公司', '张三'])

    def test_points_mall_earn_redeem_and_ship(self):
        p, d = self.setup_pair()
        self.admin('ops.pointsRule.save', {'audience': 'PASSENGER', 'perOrder': 30, 'enabled': True, 'expectedVersion': 0})
        virtual = self.admin('ops.mallProduct.save', {'audience': 'PASSENGER', 'name': '5元打车券', 'description': '演示', 'points': 20,
                                                      'stock': 1, 'type': 'VIRTUAL', 'enabled': True})['mallProduct']
        gift = self.admin('ops.mallProduct.save', {'audience': 'DRIVER', 'name': '车载香薰', 'points': 1, 'stock': 5,
                                                   'type': 'PHYSICAL', 'enabled': True})['mallProduct']
        before = self.request('/api/state', token=d['token'])['profile']['points']['balance']
        self.pa(p['token'], 'points.redeem', {'productId': virtual['id']}, expected=409)
        self.complete(p, d)
        state = self.request('/api/passenger/state', token=p['token'])
        self.assertEqual(state['points']['balance'], 30)
        self.assertEqual([x['id'] for x in state['pointsMall']['products']], [virtual['id']])
        reply = self.pa(p['token'], 'points.redeem', {'productId': virtual['id']})
        self.assertEqual((reply['points']['balance'], reply['result']['redemption']['status']), (10, 'COMPLETED'))
        self.pa(p['token'], 'points.redeem', {'productId': virtual['id']}, expected=409)  # sold out
        self.pa(p['token'], 'points.redeem', {'productId': gift['id']}, expected=404)     # wrong audience
        driver = self.request('/api/state', token=d['token'])
        self.assertEqual(driver['profile']['points']['balance'], before + 1)
        self.da(d['token'], 'points.redeem', {'productId': gift['id']}, expected=400)     # receiver required
        r = self.da(d['token'], 'points.redeem', {'productId': gift['id'], 'receiverName': '王师傅', 'receiverMobile': '13800000001',
                                                  'receiverAddress': '宜昌市西陵区测试路1号'})
        record = r['result']['redemption']
        self.assertEqual((record['status'], r['profile']['points']['balance']), ('PENDING_SHIPMENT', before))
        shipped = self.admin('ops.mallRedemption.ship', {'id': record['id'], 'expectedVersion': 1, 'shipNote': '顺丰 SF123'})['mallRedemption']
        self.assertEqual(shipped['status'], 'SHIPPED')
        mine = self.request('/api/state', token=d['token'])['profile']['pointsMall']['redemptions']
        self.assertEqual(mine[0]['status'], 'SHIPPED')

    def test_ranking_counts_same_city_drivers_by_period_and_online_time(self):
        p, d = self.setup_pair()
        d2 = self.login('13800000002'); self.da(d2['token'], 'driver.online', {'online': True})
        self.complete(p, d)
        day = self.request('/api/ranking?metric=orders&period=day', token=d['token'])
        self.assertEqual((day['myRank'], day['myValue'], day['peerCount']), (1, 1, 2))
        self.assertEqual([e['isMe'] for e in day['entries']], [True])
        self.assertEqual(self.request('/api/ranking?metric=orders&period=year', token=d2['token'])['myRank'], None)
        self.request('/api/ranking?metric=orders&period=week', token=d['token'], expected=400)
        def backdate(svc):
            svc.db.execute("UPDATE driver_online_log SET started='2020-01-01T00:00:00+08:00' WHERE driver=?", (d2['profile']['driver']['id'],))
        self.mutate_fixture(backdate)
        online = self.request('/api/ranking?metric=online&period=week', token=d['token'])
        self.assertEqual(len(online['entries']), 2)
        self.assertFalse(online['entries'][0]['isMe'])
        self.da(d2['token'], 'driver.online', {'online': False})
        closed = self.mutate_fixture(lambda svc: svc.db.execute('SELECT count(*) FROM driver_online_log WHERE ended IS NULL').fetchone()[0])
        self.assertEqual(closed, 1)
        legacy = self.request('/api/ranking', token=d['token'])
        self.assertIn('period', legacy); self.assertIn('entries', legacy)

    def test_heatmap_uses_city_thresholds_and_plain_map_when_unset(self):
        p, d = self.setup_pair()
        self.create(p['token'])
        plain = self.request('/api/heatmap', token=d['token'])
        self.assertEqual((plain['render'], plain['points'], plain['thresholdsConfigured']), (False, [], False))
        self.mutate_fixture(lambda svc: self.assertRaises(Exception, svc.operations.admin_action, 'ops.heatmap.save',
            {'city': '420500', 'expectedVersion': 0, 'passenger': {'low': 3, 'mid': 2, 'high': 9}, 'driver': None}, 'ADMIN:test'))
        self.admin('ops.heatmap.save', {'city': '420500', 'expectedVersion': 0, 'passenger': {'low': 1, 'mid': 2, 'high': 3}, 'driver': None})
        hot = self.request('/api/heatmap?type=passenger', token=d['token'])
        self.assertTrue(hot['render']); self.assertEqual([(x['count'], x['level']) for x in hot['points']], [(1, 1)])
        drivers = self.request('/api/heatmap?type=driver', token=d['token'])
        self.assertEqual((drivers['render'], drivers['thresholdsConfigured'], drivers['points']), (False, True, []))
        self.admin('ops.heatmap.save', {'city': '420500', 'expectedVersion': 1, 'passenger': None, 'driver': {'low': 1, 'mid': 5, 'high': 9}})
        self.request('/api/action', {'action': 'location.report', 'requestId': str(uuid.uuid4()), 'payload': dict(
            latitude=30.6697, longitude=111.3226, accuracy=5, time=__import__('common').stamp(), coordinateSystem='WGS84')}, token=d['token'])
        self.assertEqual(len(self.request('/api/heatmap?type=driver', token=d['token'])['points']), 1)

    def test_admin_order_progress_fees_wallet_and_complaint_image(self):
        import admin_extras
        from common import SAMPLE_IMAGE
        p, d = self.setup_pair()
        done = self.complete(p, d, remark='带宠物，请开后备箱')
        self.assertEqual(self.projected(d['token'], done['id'])['remark'], '带宠物，请开后备箱')
        self.pa(p['token'], 'order.complaint', {'id': done['id'], 'reason': '绕路', 'content': '司机没有按导航行驶'})
        complaint = self.get_order(p['token'], done['id'])['complaints'][0]
        fid = self.pa(p['token'], 'order.complaint.attach', {'id': done['id'], 'complaintId': complaint['id'],
                                                              'imagesBase64': [SAMPLE_IMAGE]})['result']['attachments'][0]['id']
        def read(svc):
            o = svc.shared_order(done['id'])
            return (admin_extras.order_timeline(svc, o), admin_extras.fee_breakdown(o), admin_extras.wallet_rows(svc, [o]),
                    admin_extras.support_file(svc, 'ORDER_COMPLAINT', done['id'], fid))
        timeline, fees, wallets, image = self.mutate_fixture(read)
        self.assertEqual([s['key'] for s in timeline['steps']], ['created', 'paid', 'accepted', 'arrived', 'boarded', 'delivered'])
        self.assertTrue(all(s['done'] for s in timeline['steps'])); self.assertFalse(timeline['inferred'])
        self.assertIn('公里', timeline['steps'][2]['mileage'])
        amounts = {l['key']: l['amount'] for l in fees['lines']}
        self.assertEqual((amounts['fare'], amounts['paid'], amounts['driverIncome'], amounts['refund']), (done['fare'], done['fare'], done['settlement']['driverAmount'], 0))
        daily = self.mutate_fixture(admin_extras.daily_balance_rows)
        today = next(r for r in daily if r['driverId'] == d['profile']['driver']['id'] and r['date'] == __import__('common').now().date().isoformat())
        self.assertGreaterEqual(today['income'], done['settlement']['driverAmount'])
        self.assertEqual(today['closingBalance'], self.state(d['token'])['balance'])
        mine = next(w for w in wallets if w['driverId'] == d['profile']['driver']['id'])
        self.assertEqual((mine['pendingAmount'], mine['withdrawingAmount'], mine['bankCardCount'] >= 0), (0, 0, True))
        self.assertEqual(image['mimeType'], 'image/png')
        self.mutate_fixture(lambda svc: self.assertRaises(Exception, admin_extras.support_file, svc, 'ORDER_COMPLAINT', 'PORDother', fid))
