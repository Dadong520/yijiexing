"""Expected integer fare + monotonic fareVersion protect payment/admin decisions."""
import json
import uuid
from policy_helpers import PolicyCase
from admin_console import AdminConsole
from common import ApiError

class FareConfirmationTests(PolicyCase):
    def admin_order(self,action,oid,stage='WAIT_PAY',rid=None,**fields):
        p={'id':oid,'expectedStage':stage,'reason':'本地费用核对','confirmed':True,**fields}
        return self.mutate_fixture(lambda svc:AdminConsole(svc).action({'username':'local-finance','role':'SUPER_ADMIN'},action,p,rid or str(uuid.uuid4())))['order']
    def assert_rejected_admin(self,status,*args,**kwargs):
        with self.assertRaises(ApiError) as ctx:self.admin_order(*args,**kwargs)
        self.assertEqual(ctx.exception.status,status)

    def test_unadjusted_115_payment_compatible_and_116_values_strict(self):
        p,d,o=self.shared_wait_pay();balance=self.state(d['token'])['balance']
        for outcome in ('SUCCESS','FAILED','PENDING','CANCEL'):
            self.pa(p['token'],'order.pay',{'id':o['id'],'outcome':outcome,'expectedFare':o['fare']+1,'fareVersion':0},expected=409)
        for fields in ({'expectedFare':o['fare']},{'fareVersion':0},{'expectedFare':True,'fareVersion':0},{'expectedFare':o['fare'],'fareVersion':False}):
            self.pa(p['token'],'order.pay',{'id':o['id'],**fields},expected=400)
        self.assertEqual(self.get_order(p['token'],o['id'])['paymentStatus'],'UNPAID');self.assertEqual(self.state(d['token'])['balance'],balance)
        self.assertEqual(self.pa(p['token'],'order.pay',{'id':o['id']})['result']['order']['stage'],'DONE')

    def test_adjustment_aba_stale_payment_and_duplicate_credit(self):
        p,d,o=self.shared_wait_pay();oid=o['id'];balance=self.state(d['token'])['balance'];amount=o['fare']
        first=self.admin_order('order.adjustFare',oid,amount=amount+100,expectedFare=amount,fareVersion=0,rid='adjust-first');self.assertEqual(first['fareVersion'],1)
        self.assert_rejected_admin(409,'order.adjustFare',oid,amount=amount+200,expectedFare=amount,fareVersion=0)
        self.assert_rejected_admin(409,'order.adjustFare',oid,amount=amount+200)
        second=self.admin_order('order.adjustFare',oid,amount=amount,expectedFare=amount+100,fareVersion=1);self.assertEqual(second['fareVersion'],2)
        self.pa(p['token'],'order.pay',{'id':oid,'expectedFare':amount,'fareVersion':0},expected=409)
        missing=self.pa(p['token'],'order.pay',{'id':oid},expected=409);self.assertEqual(missing['details']['reason'],'FARE_CONFIRMATION_REQUIRED')
        current=self.get_order(p['token'],oid);self.assertEqual(current['stage'],'WAIT_PAY');self.assertFalse(current['paid']);self.assertEqual(self.state(d['token'])['balance'],balance)
        payload={'id':oid,'expectedFare':amount,'fareVersion':2,'outcome':'SUCCESS'}
        self.pa(p['token'],'order.pay',payload,rid='confirmed-pay');self.pa(p['token'],'order.pay',payload,rid='confirmed-pay');self.pa(p['token'],'order.pay',payload)
        self.assertEqual(self.state(d['token'])['balance'],balance+amount)
        self.assertEqual(sum(e['title']=='行程收入 '+oid for e in self.state(d['token'])['entries']),1)

    def test_admin_same_amount_adjustment_still_versions_and_replay_does_not_increment(self):
        p,d,o=self.shared_wait_pay();oid=o['id'];fields={'amount':o['fare'],'expectedFare':o['fare'],'fareVersion':0}
        first=self.admin_order('order.adjustFare',oid,rid='same-amount',**fields);replay=self.admin_order('order.adjustFare',oid,rid='same-amount',**fields)
        self.assertEqual(first['fareVersion'],1);self.assertEqual(replay['fareVersion'],1)
        self.assertEqual(self.get_order(p['token'],oid)['fareVersion'],1)
        exported=self.mutate_fixture(lambda svc:AdminConsole(svc).orders());self.assertEqual(next(x for x in exported if x['id']==oid)['fareVersion'],1)

    def test_legacy_adjustments_without_version_require_explicit_confirmation(self):
        p,d,o=self.shared_wait_pay();oid=o['id']
        def legacy(svc):
            value=svc.shared_order(oid);value['adjustments']=[{'before':o['fare']+1,'after':o['fare']}];value.pop('fareVersion',None);svc.save_shared(value)
        self.mutate_fixture(legacy);self.assertEqual(self.get_order(p['token'],oid)['fareVersion'],0)
        self.pa(p['token'],'order.pay',{'id':oid},expected=409)
        self.pa(p['token'],'order.pay',{'id':oid,'expectedFare':o['fare'],'fareVersion':0})

    def test_admin_cancel_and_refund_validate_expectations_and_refund_does_not_revive(self):
        p,d=self.setup_pair();oid=self.create(p['token'])['id'];o=self.get_order(p['token'],oid)
        self.assert_rejected_admin(409,'order.cancel',oid,stage='AVAILABLE',expectedFare=o['fare']+1,fareVersion=0)
        self.assert_rejected_admin(400,'order.cancel',oid,stage='AVAILABLE',expectedFare=o['fare'])
        self.admin_order('order.cancel',oid,stage='AVAILABLE') # untouched legacy admin remains compatible
        oid=self.create(p['token'])['id'];self.da(d['token'],'order.accept',{'id':oid});self.finish(d['token'],oid);o=self.get_order(p['token'],oid)
        adjusted=self.admin_order('order.adjustFare',oid,amount=1234) # first old admin adjustment remains compatible
        pay={'id':oid,'expectedFare':1234,'fareVersion':1};self.pa(p['token'],'order.pay',pay,rid='before-refund');balance=self.state(d['token'])['balance']
        self.assert_rejected_admin(409,'order.refund',oid,stage='DONE')
        self.assert_rejected_admin(409,'order.refund',oid,stage='DONE',expectedFare=o['fare'],fareVersion=0)
        self.admin_order('order.refund',oid,stage='DONE',expectedFare=1234,fareVersion=1,rid='one-refund')
        self.admin_order('order.refund',oid,stage='DONE',expectedFare=1234,fareVersion=1,rid='one-refund')
        self.pa(p['token'],'order.pay',pay,rid='before-refund');self.pa(p['token'],'order.pay',pay,expected=409)
        final=self.get_order(p['token'],oid);self.assertEqual(final['paymentStatus'],'REFUNDED');self.assertFalse(final['paid']);self.assertEqual(self.state(d['token'])['balance'],balance-1234)

    def test_bid_prepay_expected_quote_strict(self):
        p,d=self.setup_pair();oid=self.create(p['token'],'BID')['id'];self.da(d['token'],'order.bid',{'id':oid,'amount':3600});self.pa(p['token'],'order.selectDriver',{'id':oid,'driverId':d['profile']['driver']['id']})
        self.pa(p['token'],'order.pay',{'id':oid,'expectedFare':2926,'fareVersion':0},expected=409)
        result=self.pa(p['token'],'order.pay',{'id':oid,'expectedFare':3600,'fareVersion':0})['result']['order'];self.assertEqual(result['stage'],'PICKUP');self.assertEqual(result['fare'],3600)
