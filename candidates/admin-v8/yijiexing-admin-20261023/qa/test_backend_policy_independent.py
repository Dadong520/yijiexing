"""Independent probes against shared owner source, local HTTP/CAS fixtures only."""
import hashlib,json,pathlib,sys,unittest,uuid,threading,concurrent.futures,urllib.request,urllib.error

ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'yijiexing-cloud-20261023/cloud-service/tests'))
import policy_helpers
from common import dumps,ApiError
from admin_console import AdminConsole

class IndependentPolicy(policy_helpers.PolicyCase):
 def admin(self,action,payload):
  def apply(svc):
   console=AdminConsole(svc,'independent-fixture-admin')
   user=console.user(svc.db.execute("SELECT * FROM console_users WHERE username='admin'").fetchone())
   return console.action(user,action,payload,str(uuid.uuid4()))
  return self.mutate_fixture(apply)
 def durable(self):
  def read(svc):
   return {t:[tuple(row) for row in svc.db.execute('SELECT * FROM '+t+' ORDER BY rowid')] for t in ('accounts','passengers','passenger_orders','actions','passenger_actions','audits')}
  return self.mutate_fixture(read)
 def test_old_115_unadjusted_pay_works_but_adjusted_aba_rejected(self):
  p,d,o=self.shared_wait_pay();oid=o['id'];original=o['fare']
  self.admin('order.adjustFare',{'id':oid,'expectedStage':'WAIT_PAY','amount':original+100,'expectedFare':original,'fareVersion':0,'reason':'独立探针调价','confirmed':True})
  self.admin('order.adjustFare',{'id':oid,'expectedStage':'WAIT_PAY','amount':original,'expectedFare':original+100,'fareVersion':1,'reason':'ABA回到旧值','confirmed':True})
  before=self.durable()
  self.pa(p['token'],'order.pay',{'id':oid},expected=409)
  self.pa(p['token'],'order.pay',{'id':oid,'expectedFare':original,'fareVersion':0},expected=409)
  self.assertEqual(self.durable(),before)
  self.pa(p['token'],'order.pay',{'id':oid,'expectedFare':original,'fareVersion':2})
  p2=self.passenger_login('13800000102');new=self.create(p2['token']);self.da(d['token'],'order.accept',{'id':new['id']});self.finish(d['token'],new['id'],'0102')
  self.pa(p2['token'],'order.pay',{'id':new['id']})
  self.assertEqual(self.get_order(p2['token'],new['id'])['stage'],'DONE')
 def test_cross_driver_same_rid_does_not_replay_owner_and_rejection_writes_nothing(self):
  p,d,o=self.shared_wait_pay();d2=self.login('13800000002');rid='cross-driver-key'
  self.da(d['token'],'order.complaint',{'id':o['id'],'reason':'独立测试','content':'本司机测试投诉记录'},rid=rid)
  before=self.durable()
  self.da(d2['token'],'order.complaint',{'id':o['id'],'reason':'独立测试','content':'本司机测试投诉记录'},rid=rid,expected=404)
  self.assertEqual(self.durable(),before)
 def test_legacy_idempotent_order_snapshot_scrubbed_without_changing_canonical(self):
  p,d=self.setup_pair();o=self.create(p['token']);oid=o['id'];rid='legacy-accept-snapshot'
  self.da(d['token'],'order.accept',{'id':oid},rid=rid);self.finish(d['token'],oid)
  self.pa(p['token'],'order.complaint',{'id':oid,'reason':'乘客隐私','content':'敏感乘客投诉不可给司机'})
  did=d['profile']['driver']['id']
  def old_cache(svc):
   raw={'order':svc.shared_order(oid)}
   svc.db.execute('UPDATE actions SET result=? WHERE driver=? AND request_id=?',(dumps(raw),did,rid))
  self.mutate_fixture(old_cache)
  replay=self.da(d['token'],'order.accept',{'id':oid},rid=rid)['result']['order']
  for key in ('complaints','passengerId','bids'):self.assertNotIn(key,replay)
  projected=self.projected(d['token'],oid)
  self.assertNotIn('complaints',projected)
  self.assertEqual(len(self.get_order(p['token'],oid)['complaints']),1)
 def test_payment_adjustment_refund_interleavings_preserve_once_balance(self):
  p,d,o=self.shared_wait_pay();oid=o['id'];fare=o['fare'];before=self.state(d['token'])['balance']
  self.pa(p['token'],'order.pay',{'id':oid,'expectedFare':fare,'fareVersion':0},rid='pay-wins')
  with self.assertRaises(ApiError) as err:self.admin('order.adjustFare',{'id':oid,'expectedStage':'WAIT_PAY','amount':fare+500,'reason':'晚到调价请求','confirmed':True})
  self.assertEqual(err.exception.status,409)
  self.admin('order.refund',{'id':oid,'expectedStage':'DONE','reason':'独立模拟退款','confirmed':True})
  after=self.durable()
  self.pa(p['token'],'order.pay',{'id':oid,'expectedFare':fare,'fareVersion':0},expected=409)
  with self.assertRaises(ApiError):self.admin('order.refund',{'id':oid,'expectedStage':'DONE','reason':'重复退款探针','confirmed':True})
  self.assertEqual(self.durable(),after)
  self.assertEqual(self.state(d['token'])['balance'],before)
  current=self.get_order(p['token'],oid);self.assertEqual(current['paymentStatus'],'REFUNDED');self.assertEqual(current['refundAmount'],fare)
  # Retrying an earlier successful request may return its historical result,
  # but must not re-credit after refund; refreshed canonical is authoritative.
  self.pa(p['token'],'order.pay',{'id':oid,'expectedFare':fare,'fareVersion':0},rid='pay-wins')
  self.assertEqual(self.state(d['token'])['balance'],before);self.assertEqual(self.get_order(p['token'],oid)['paymentStatus'],'REFUNDED')

 def test_neg_claim_rechecks_membership_expiry_without_consuming_offer(self):
  from common import stamp,now,dt
  p,d=self.setup_pair();self.enable_negotiation();o=self.negotiate(d);offer=self.da(d['token'],'order.pairOffer',{'id':o['id']})['result']
  did=d['profile']['driver']['id']
  self.mutate_fixture(lambda svc:svc.db.execute('UPDATE accounts SET trial_start=? WHERE id=?',(stamp(now()-dt.timedelta(days=366)),did)))
  before=self.durable();self.pa(p['token'],'negotiation.claim',{'pairCode':offer['pairCode']},expected=403);self.assertEqual(self.durable(),before)
  def offer_status(svc):return svc.db.execute('SELECT status FROM negotiation_offers WHERE driver=? AND order_id=?',(did,o['id'])).fetchone()[0]
  self.assertEqual(self.mutate_fixture(offer_status),'ACTIVE')

 def test_cross_instance_pay_vs_adjust_cas_and_stale_retry(self):
  from test_cloud import ADMIN,ACCESS
  p,d,o=self.shared_wait_pay();oid=o['id'];fare=o['fare'];balance=self.state(d['token'])['balance']
  def setup_admin(svc):return AdminConsole(svc,'independent-fixture-admin').login({'username':'admin','password':'independent-fixture-admin'})['session']
  session=self.mutate_fixture(setup_admin);admin=self.start('admin')
  payment={'action':'order.pay','payload':{'id':oid,'expectedFare':fare,'fareVersion':0},'requestId':'race-pay'}
  adjustment={'action':'order.adjustFare','payload':{'id':oid,'expectedStage':'WAIT_PAY','expectedFare':fare,'fareVersion':0,'amount':fare+100,'reason':'并发调价测试','confirmed':True},'requestId':'race-adjust'}
  def send(job):
   server,path,body,headers=job
   req=urllib.request.Request(f'http://127.0.0.1:{server.server_port}'+path,data=json.dumps(body).encode(),headers={'Content-Type':'application/json',**headers})
   try:
    with urllib.request.urlopen(req,timeout=20) as response:return response.status
   except urllib.error.HTTPError as error:
    status=error.code;error.close();return status
  jobs=[(self.driver,'/api/passenger/action',payment,{'X-Demo-Access':ACCESS,'Authorization':'Bearer '+p['token']}),(admin,'/api/local/admin/v2/action',adjustment,{'X-Admin-Token':ADMIN,'X-Admin-Session':session})]
  self.remote.barrier=threading.Barrier(2)
  try:
   with concurrent.futures.ThreadPoolExecutor(2) as pool:statuses=list(pool.map(send,jobs))
  finally:self.remote.barrier=None
  self.assertEqual(sorted(statuses),[200,503])
  current=self.get_order(p['token'],oid)
  if current['stage']=='DONE':
   self.assertEqual(current['fare'],fare);self.assertEqual(send(jobs[1]),409)
  else:
   self.assertEqual(current['stage'],'WAIT_PAY');self.assertEqual(current['fare'],fare+100);self.assertEqual(send(jobs[0]),409)
   self.pa(p['token'],'order.pay',{'id':oid,'expectedFare':fare+100,'fareVersion':1})
  final=self.get_order(p['token'],oid);st=self.state(d['token'])
  self.assertEqual(st['balance'],balance+final['fare']);self.assertEqual(sum(e['title']=='行程收入 '+oid for e in st['entries']),1)

if __name__=='__main__':unittest.main(verbosity=2)
