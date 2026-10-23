"""Independent GPT-6.1 probes using frozen local HTTP/SQLite/MemoryCAS fixtures."""
import json
from policy_helpers import PolicyCase

class Independent61(PolicyCase):
    def test_pay_rejects_negative_fraction_string_and_version_types(self):
        p,d,a=self.shared_wait_pay(); before=self.state(d['token'])['balance']
        for fields in ({'expectedFare':-1,'fareVersion':0},{'expectedFare':1.5,'fareVersion':0},{'expectedFare':str(a['fare']),'fareVersion':0},{'expectedFare':a['fare'],'fareVersion':-1},{'expectedFare':a['fare'],'fareVersion':0.0}):
            self.pa(p['token'],'order.pay',{'id':a['id'],**fields},expected=400)
        self.assertEqual(self.get_order(p['token'],a['id'])['stage'],'WAIT_PAY')
        self.assertEqual(self.state(d['token'])['balance'],before)

    def test_request_id_payload_collision_does_not_advance(self):
        p,d=self.setup_pair();a=self.create(p['token']);dt=d['token']
        self.da(dt,'order.accept',{'id':a['id']},rid='independent-collision')
        self.da(dt,'order.advance',{'id':a['id']},rid='independent-collision',expected=409)
        self.assertEqual(self.get_order(p['token'],a['id'])['stage'],'PICKUP')
        self.request('/api/action',{'action':'order.advance','payload':{'id':a['id']},'requestId':'x'*121},token=dt,expected=400)
        self.assertEqual(self.get_order(p['token'],a['id'])['stage'],'PICKUP')

    def test_payment_request_id_collision_cannot_change_outcome(self):
        p,d,a=self.shared_wait_pay();before=self.state(d['token'])['balance']
        self.pa(p['token'],'order.pay',{'id':a['id'],'outcome':'FAILED'},rid='independent-payment')
        self.pa(p['token'],'order.pay',{'id':a['id'],'outcome':'SUCCESS'},rid='independent-payment',expected=409)
        self.assertEqual(self.get_order(p['token'],a['id'])['stage'],'WAIT_PAY');self.assertEqual(self.state(d['token'])['balance'],before)

    def test_late_payment_preserves_new_reserved_order_and_old_advance_rejected(self):
        p,d,a=self.shared_wait_pay();p2=self.passenger_login('13800000102');b=self.create(p2['token'],'RESERVED');dt=d['token']
        self.da(dt,'order.accept',{'id':b['id']});before=self.get_order(p2['token'],b['id'])
        self.pa(p['token'],'order.pay',{'id':a['id'],'expectedFare':a['fare'],'fareVersion':0},rid='late-reserved')
        self.da(dt,'order.advance',{'id':a['id'],'expectedStage':'TRIP'},expected=409)
        self.assertEqual(self.get_order(p2['token'],b['id']),before)
        self.assertEqual(self.get_order(p['token'],a['id'])['stage'],'DONE')
