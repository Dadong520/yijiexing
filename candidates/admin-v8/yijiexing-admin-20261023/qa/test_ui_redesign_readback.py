"""Pure fixtures only: no accounts file, localhost, cloud or business mutation."""
import copy
import json
import unittest
from unittest.mock import patch
from ui_redesign_readback import inspect_state


def fixture():
    standard={'id':'STANDARD_FIXTURE','kind':'STANDARD','stage':'DONE','fare':2926,'paid':True,'credited':True,'paymentStatus':'SUCCESS','passengerId':'PRIVATE_PASSENGER','driverId':'PRIVATE_DRIVER','evaluation':{'rating':5},'complaints':[{'id':'COMPLAINT_FIXTURE','status':'REPLIED','reply':'PRIVATE_REPLY'}],
              'events':[{'type':x} for x in ['PASSENGER_CREATED','DRIVER_ACCEPT','DRIVER_ADVANCE','DRIVER_ADVANCE','DRIVER_ADVANCE','PASSENGER_PAY','PASSENGER_PAY','PASSENGER_EVALUATE','PASSENGER_COMPLAINT','ADMIN_COMPLAINT_REPLIED']]}
    bid={'id':'BID_FIXTURE','kind':'BID','stage':'CANCELLED','fare':3600,'paid':False,'credited':False,'paymentStatus':'REFUNDED','refundAmount':3600,'refundedAt':'fixture-time','passengerId':'PRIVATE_PASSENGER','driverId':'PRIVATE_DRIVER',
         'events':[{'type':x} for x in ['PASSENGER_CREATED','PASSENGER_SELECTDRIVER','DRIVER_CANCEL','PASSENGER_CONTINUE']]}
    orders=[standard,bid]
    payments=[{**{k:o.get(k) for k in ('fare','paid','paymentStatus','driverId','passengerId')},'orderId':o['id'],'refundAmount':o.get('refundAmount',0),'currentRefundAmount':o.get('refundAmount',0),'historicalRefundAmount':0} for o in orders]
    return {'orders':orders,'payments':payments,'support':[{'id':'COMPLAINT_FIXTURE','source':'ORDER_COMPLAINT','orderId':'STANDARD_FIXTURE','status':'REPLIED','reply':'PRIVATE_REPLY'}],
            'statistics':{'orderCount':2,'completedCount':1,'cancelledCount':1,'activeCount':0,'paidFare':2926,'refundAmount':3600}}


class PureReadbackTests(unittest.TestCase):
    def run_check(self,state,complaint='COMPLAINT_FIXTURE'):
        with patch('ui_redesign_readback.request',side_effect=AssertionError('NETWORK_NOT_ALLOWED')):
            return inspect_state(state,'STANDARD_FIXTURE','BID_FIXTURE',complaint,2926,3600)
    def test_success_accepts_multiple_payment_attempt_events(self):
        result=self.run_check(fixture());self.assertTrue(result['passed'])
        encoded=json.dumps(result)
        for secret in ('PRIVATE_PASSENGER','PRIVATE_DRIVER','PRIVATE_REPLY'):self.assertNotIn(secret,encoded)
    def test_duplicate_cancel_event_fails(self):
        state=fixture();state['orders'][1]['events'].append({'type':'DRIVER_CANCEL'})
        self.assertFalse(self.run_check(state)['passed'])
    def test_ledger_or_refund_mismatch_fails(self):
        state=fixture();state['payments'][1]['refundAmount']=7200
        self.assertFalse(self.run_check(state)['passed'])
    def test_missing_target_not_guessed(self):
        state=fixture();state['orders'][0]['id']='DIFFERENT_FIXTURE'
        result=self.run_check(state);self.assertFalse(result['passed']);self.assertNotIn('standard',result['orders'])
    def test_optional_complaint_is_explicitly_skipped(self):
        result=self.run_check(fixture(),None);self.assertTrue(result['passed'])
        self.assertTrue(any('未提供投诉ID' in n for n in result['notes']))
    def test_source_fixture_unchanged(self):
        state=fixture();before=copy.deepcopy(state);self.run_check(state);self.assertEqual(state,before)

if __name__=='__main__':unittest.main(verbosity=2)
