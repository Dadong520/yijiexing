"""Independent three-role domain integration, temporary DB only.
Not emulator, browser, deployed Cloud Run or Antigravity acceptance evidence.
Run: python3 yijiexing-admin-20261023/qa/test_three_party.py
"""
import json
import pathlib
import sys
import tempfile
import unittest
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT/'yijiexing-cloud-20261023/cloud-service'), str(ROOT/'yijiexing-admin-20261023/backend')]
from domain import Service
from admin_console import AdminConsole
from common import ApiError


class ThreePartyIntegration(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = pathlib.Path(self.temp.name)/'isolated.sqlite'
        self.s = Service(self.path)
        self.c = AdminConsole(self.s, 'temporary-test-password-2026')
        self.admin = self.c.login({'username':'admin','password':'temporary-test-password-2026'})['user']
        code = self.s.passenger_code('13800000103')['verificationCode']
        p = self.s.passenger_login({'mobile':'13800000103','code':code})
        self.pid = p['passenger']['id']
        code = self.s.code('13800000003')['verificationCode']
        d = self.s.login({'mobile':'13800000003','code':code})
        self.did = d['profile']['driver']['id']
        self.da('driver.online', {'online':True})

    def tearDown(self):
        self.s.close()
        self.temp.cleanup()

    def pa(self, action, payload, rid=None):
        return self.s.passenger_action(self.pid, action, payload, rid or str(uuid.uuid4()))

    def da(self, action, payload, rid=None):
        return self.s.action(self.did, action, payload, rid or str(uuid.uuid4()))

    def aa(self, action, payload, rid=None, user=None):
        return self.c.action(user or self.admin, action, payload, rid or str(uuid.uuid4()))

    def create(self, kind='STANDARD', rid=None):
        return self.pa('order.create', {'kind':kind,'origin':'万达广场','destination':'宜昌东站','originLat':30.6687,'originLng':111.3226,'destinationLat':30.6645,'destinationLng':111.3694}, rid)['order']['id']

    def triple(self, oid, stage, amount=None):
        p = next(x for x in self.s.passenger_data(self.pid)['orders'] if x['id']==oid)
        a = next(x for x in self.c.state(self.admin)['orders'] if x['id']==oid)
        d = next(x for x in self.s.data(self.did)['state']['orders'] if x['id']==oid)
        self.assertEqual([p['stage'],a['stage'],d['stage']], [stage]*3)
        self.assertEqual([p['fare'],d['fare']], [a['fare']]*2)
        if amount is not None:self.assertEqual(a['fare'],amount)
        return p,a,d

    def reject(self, status, function, *args):
        with self.assertRaises(ApiError) as err:function(*args)
        self.assertEqual(err.exception.status,status)

    def bid_paid(self):
        oid=self.create('BID')
        self.da('order.bid',{'id':oid,'amount':2300})
        self.assertEqual(self.c.state(self.admin)['orders'][0]['bids'][0]['amount'],2300)
        self.pa('order.selectDriver',{'id':oid,'driverId':self.did})
        self.triple(oid,'PREPAY',2300)
        self.pa('order.pay',{'id':oid,'outcome':'SUCCESS'},'pay-bid-once')
        self.triple(oid,'PICKUP',2300)
        return oid

    def finish(self,oid):
        self.da('order.advance',{'id':oid})
        self.triple(oid,'ARRIVED')
        self.reject(400,self.da,'order.advance',{'id':oid,'phoneTail':'0000'})
        self.triple(oid,'ARRIVED')
        self.da('order.advance',{'id':oid,'phoneTail':'0103'})
        self.triple(oid,'TRIP')
        self.da('order.advance',{'id':oid})

    def test_bid_full_flow_evaluation_complaint_reply_and_restart(self):
        oid=self.bid_paid()
        before=self.s.data(self.did)['state']['balance']
        self.finish(oid)
        self.triple(oid,'DONE',2300)
        self.assertEqual(self.s.data(self.did)['state']['balance'],before+2300)
        self.pa('order.pay',{'id':oid,'outcome':'SUCCESS'},'pay-bid-once')
        self.assertEqual(self.s.data(self.did)['state']['balance'],before+2300)
        self.pa('order.evaluate',{'id':oid,'rating':4,'content':'独立三方接口评价'})
        self.pa('order.complaint',{'id':oid,'reason':'服务态度','content':'独立三方接口投诉核查'})
        complaint=self.s.shared_order(oid)['complaints'][0]
        self.aa('support.reply',{'source':'ORDER_COMPLAINT','ownerId':oid,'id':complaint['id'],'reply':'已核查该体验订单并反馈处理结果'})
        for view in self.triple(oid,'DONE'):
            self.assertEqual(view['evaluation']['rating'],4)
            self.assertEqual(view['complaints'][0]['status'],'REPLIED')
            self.assertEqual(view['complaints'][0]['handledBy'],'ADMIN:admin')
        self.s.db.commit()
        self.s.close()
        self.s=Service(self.path)
        self.c=AdminConsole(self.s)
        self.admin=self.c.login({'username':'admin','password':'temporary-test-password-2026'})['user']
        self.triple(oid,'DONE',2300)
        state=self.c.state(self.admin)
        self.assertEqual(state['statistics']['orderCount'],1)
        self.assertEqual(state['statistics']['paidFare'],2300)
        self.assertTrue(any(x['action']=='support.reply' for x in state['audit']))

    def test_admin_prepaid_cancel_refund_once_and_both_clients(self):
        oid=self.bid_paid()
        balance=self.s.data(self.did)['state']['balance']
        payload={'id':oid,'expectedStage':'PICKUP','confirmed':True,'reason':'独立测试预付取消'}
        first=self.aa('order.cancel',payload,'cancel-idempotent')
        self.assertEqual(first,self.aa('order.cancel',payload,'cancel-idempotent'))
        for view in self.triple(oid,'CANCELLED',2300):
            self.assertEqual(view['paymentStatus'],'REFUNDED')
            self.assertEqual(view['refundAmount'],2300)
            self.assertFalse(view['paid'])
        self.assertEqual(self.s.data(self.did)['state']['balance'],balance)
        self.reject(409,self.aa,'order.cancel',{**payload,'reason':'更换取消原因'},'cancel-idempotent')
        self.assertEqual(self.c.state(self.admin)['statistics']['refundAmount'],2300)
        self.assertEqual(sum(x['action']=='order.cancel' for x in self.c.state(self.admin)['audit']),1)

    def test_standard_adjust_failed_payment_recovery_and_completed_refund(self):
        oid=self.create()
        self.da('order.accept',{'id':oid})
        self.finish(oid)
        self.triple(oid,'WAIT_PAY')
        self.aa('order.adjustFare',{'id':oid,'expectedStage':'WAIT_PAY','confirmed':True,'reason':'体验费用核对','amount':1567})
        self.triple(oid,'WAIT_PAY',1567)
        self.pa('order.pay',{'id':oid,'outcome':'FAILED'})
        self.triple(oid,'WAIT_PAY',1567)
        self.pa('order.pay',{'id':oid,'outcome':'SUCCESS'},'standard-pay')
        self.triple(oid,'DONE',1567)
        balance=self.s.data(self.did)['state']['balance']
        payload={'id':oid,'expectedStage':'DONE','confirmed':True,'reason':'体验全额退款'}
        self.aa('order.refund',payload,'refund-idempotent')
        self.aa('order.refund',payload,'refund-idempotent')
        self.assertEqual(self.s.data(self.did)['state']['balance'],balance-1567)
        for view in self.triple(oid,'DONE',1567):self.assertEqual(view['paymentStatus'],'REFUNDED')
        self.reject(409,self.aa,'order.refund',payload)
        self.reject(409,self.aa,'order.adjustFare',{'id':oid,'expectedStage':'DONE','amount':1,'reason':'非法已完成改价','confirmed':True})

    def test_permissions_api_enforcement_and_session_revocation(self):
        self.aa('user.save',{'username':'qa_viewer','password':'qa-viewer-password-123','role':'VIEWER','enabled':True})
        auth=self.c.login({'username':'qa_viewer','password':'qa-viewer-password-123'})
        viewer=self.c.authenticate(auth['session'])
        oid=self.create()
        self.reject(403,self.aa,'order.cancel',{'id':oid,'expectedStage':'AVAILABLE','confirmed':True,'reason':'只读越权'},'viewer-action',viewer)
        self.reject(403,self.c.export,viewer,'orders')
        self.assertEqual(self.s.shared_order(oid)['stage'],'AVAILABLE')
        self.assertEqual(self.c.state(viewer)['users'],[])
        self.aa('user.save',{'username':'qa_viewer','role':'OPERATOR','enabled':True})
        self.reject(401,self.c.authenticate,auth['session'])
        self.assertNotIn('password_hash',json.dumps(self.c.state(self.admin)))

    def test_duplicate_create_and_failed_admin_retry_without_partial_mutation(self):
        oid=self.create(rid='same-create')
        self.assertEqual(oid,self.create(rid='same-create'))
        self.assertEqual(self.c.state(self.admin)['statistics']['orderCount'],1)
        payload={'id':oid,'expectedStage':'AVAILABLE','reason':'恢复后重试取消'}
        self.reject(400,self.aa,'order.cancel',payload,'retry-after-failure')
        self.assertEqual(self.s.shared_order(oid)['stage'],'AVAILABLE')
        self.aa('order.cancel',{**payload,'confirmed':True},'retry-after-failure')
        self.assertEqual(self.s.shared_order(oid)['stage'],'CANCELLED')

    def test_unfinished_cancel_denied_and_atomic_rollback(self):
        oid=self.create()
        self.da('order.accept',{'id':oid})
        self.da('order.advance',{'id':oid})
        self.da('order.advance',{'id':oid,'phoneTail':'0103'})
        self.reject(409,self.aa,'order.cancel',{'id':oid,'expectedStage':'TRIP','reason':'途中禁止取消','confirmed':True})
        self.triple(oid,'TRIP')
        self.da('order.advance',{'id':oid})
        self.s.db.commit()
        old=self.s.shared_order(oid)['fare']
        self.aa('order.adjustFare',{'id':oid,'expectedStage':'WAIT_PAY','reason':'事务失败回滚','confirmed':True,'amount':999})
        self.s.db.rollback()
        self.triple(oid,'WAIT_PAY',old)
        self.assertFalse(any(x['action']=='order.adjustFare' for x in self.c.state(self.admin)['audit']))

if __name__=='__main__':
    unittest.main(verbosity=2)
