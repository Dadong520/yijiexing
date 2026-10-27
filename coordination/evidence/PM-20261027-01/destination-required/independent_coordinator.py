"""Non-author BIZ06 HTTP/CAS tests; synthetic local records only."""
import sys,pathlib,json,unittest
SRC=pathlib.Path('/Users/dongge/.codex/tmp/yijiexing-admin-drafts-20261027/apps/cloud-service');sys.path[:0]=[str(SRC),str(SRC/'tests')]
from policy_helpers import PolicyCase
class RequiredDestinationReview(PolicyCase):
 def inspect(self,fn):
  with self.driver.request_lock:
   svc=self.driver.coordinator.begin()
   try:r=fn(svc);svc.db.commit();self.driver.coordinator.finish();return r
   except Exception:svc.db.rollback();raise
 def test_direct_old_app_requests_cannot_create_no_destination_orders(self):
  p,d=self.setup_pair();before=self.inspect(lambda s:(s.db.execute('SELECT count(*) FROM passenger_orders').fetchone()[0],s.db.execute('SELECT count(*) FROM passenger_actions').fetchone()[0]))
  for kind in ['STANDARD','BID','RESERVED']:
   r=self.pa(p['token'],'order.create',{'kind':kind,'origin':'公共起点','originLat':30.6,'originLng':111.3,'destinationPending':True},expected=400)
  self.assertEqual(self.inspect(lambda s:(s.db.execute('SELECT count(*) FROM passenger_orders').fetchone()[0],s.db.execute('SELECT count(*) FROM passenger_actions').fetchone()[0])),before)
 def test_false_and_absent_are_still_ordinary_complete_orders(self):
  p,d=self.setup_pair();a=self.create(p['token'],destinationPending=False);self.assertFalse(a['destinationPending']);self.assertTrue(a['destination']);self.pa(p['token'],'order.cancel',{'id':a['id'],'reason':'本地独立测试收尾'})
  b=self.create(p['token']);self.assertFalse(b['destinationPending']);self.assertGreater(b['fare'],0)
 def test_invalid_destination_is_not_replaced_by_origin(self):
  p,d=self.setup_pair()
  for extra in [{'destination':None},{'destination':''},{'destinationLat':None},{'destination':'万达广场','destinationLat':30.6687,'destinationLng':111.3226}]:
   base={'kind':'STANDARD','origin':'万达广场','destination':'宜昌东站','originLat':30.6687,'originLng':111.3226,'destinationLat':30.6645,'destinationLng':111.3694};base.update(extra);self.pa(p['token'],'order.create',base,expected=400)
 def test_existing_pending_destination_record_can_be_completed_without_recreating(self):
  p,d=self.setup_pair();o=self.create(p['token']);oid=o['id']
  def legacy(s):
   value=s.shared_order(oid);value.update(destinationPending=True,destination='待补充目的地',destinationLat=value['originLat'],destinationLng=value['originLng'],fare=0,estimatedFare=0,fareSource='PENDING_DESTINATION');s.save_shared(value)
  self.inspect(legacy);self.da(d['token'],'order.accept',{'id':oid});self.da(d['token'],'order.advance',{'id':oid});self.da(d['token'],'order.advance',{'id':oid,'phoneTail':'0101'},expected=409)
  result=self.pa(p['token'],'order.update',{'id':oid,'destination':'已补齐目的地','destinationLat':30.68,'destinationLng':111.33})['result']['order'];self.assertEqual(result['id'],oid);self.assertFalse(result['destinationPending']);self.assertGreater(result['fare'],0);self.assertEqual(self.inspect(lambda s:s.db.execute('SELECT count(*) FROM passenger_orders').fetchone()[0]),1)
if __name__=='__main__':unittest.main(verbosity=2)
