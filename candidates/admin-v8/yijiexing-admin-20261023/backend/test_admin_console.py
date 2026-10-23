import pathlib,sys,tempfile,unittest,uuid
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'yijiexing-cloud-20261023/cloud-service'))
from domain import Service
from common import ApiError,json
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from admin_console import AdminConsole

class ConsoleTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.s=Service(pathlib.Path(self.tmp.name)/'test.sqlite');self.c=AdminConsole(self.s,'test-admin-password-123');self.u=self.c.login({'username':'admin','password':'test-admin-password-123'})['user']
  code=self.s.passenger_code('13800000101')['verificationCode'];self.pid=self.s.passenger_login({'mobile':'13800000101','code':code})['passenger']['id']
  code=self.s.code('13800000001')['verificationCode'];d=self.s.login({'mobile':'13800000001','code':code});self.did=d['profile']['driver']['id'];self.s.action(self.did,'driver.online',{'online':True},str(uuid.uuid4()))
 def tearDown(self):self.s.close();self.tmp.cleanup()
 def action(self,a,p,rid=None):return self.c.action(self.u,a,p,rid or str(uuid.uuid4()))
 def pa(self,a,p):return self.s.passenger_action(self.pid,a,p,str(uuid.uuid4()))
 def da(self,a,p):return self.s.action(self.did,a,p,str(uuid.uuid4()))
 def create(self):return self.pa('order.create',{'origin':'万达广场','destination':'宜昌东站','originLat':30.6687,'originLng':111.3226,'destinationLat':30.6645,'destinationLng':111.3694})['order']['id']
 def test_new_bootstrap_normalizes_trailing_newline(self):
  from common import secret_check
  self.s.db.execute('DELETE FROM console_users')
  c=AdminConsole(self.s,'newline-bootstrap-secret\n')
  self.assertEqual(c.login({'username':'admin','password':'newline-bootstrap-secret'})['user']['username'],'admin')
  hashed=self.s.db.execute("SELECT password_hash FROM console_users WHERE username='admin'").fetchone()[0]
  self.assertTrue(secret_check('newline-bootstrap-secret',hashed));self.assertFalse(secret_check('newline-bootstrap-secret\n',hashed))
 def test_legacy_bootstrap_hash_repaired_only_when_raw_matches(self):
  from common import secret_hash,secret_check,stamp
  raw='legacy-bootstrap-secret\n'
  self.s.db.execute("UPDATE console_users SET password_hash=? WHERE username='admin'",(secret_hash(raw),))
  self.s.db.execute('INSERT INTO console_login_attempts VALUES (?,?,?)',('admin',5,stamp()))
  before=self.c.state(self.u)['statistics']
  c=AdminConsole(self.s,raw);self.assertIn('session',c.login({'username':'admin','password':raw.strip()}))
  self.assertEqual(c.state(self.u)['statistics'],before)
  self.assertEqual(self.s.db.execute("SELECT count(*) FROM audits WHERE action='console.bootstrap.normalize'").fetchone()[0],1)
  AdminConsole(self.s,raw)
  self.assertEqual(self.s.db.execute("SELECT count(*) FROM audits WHERE action='console.bootstrap.normalize'").fetchone()[0],1)
 def test_changed_admin_password_not_reset_by_bootstrap_repair(self):
  from common import secret_hash
  changed='changed-secret-password'
  original_hash=secret_hash(changed)
  self.s.db.execute("UPDATE console_users SET password_hash=? WHERE username='admin'",(original_hash,))
  c=AdminConsole(self.s,'original-bootstrap-secret\n')
  self.assertEqual(self.s.db.execute("SELECT password_hash FROM console_users WHERE username='admin'").fetchone()[0],original_hash)
  self.assertIn('session',c.login({'username':'admin','password':changed}))
  with self.assertRaises(ApiError):c.login({'username':'admin','password':'original-bootstrap-secret'})
 def test_login_rate_limit_and_audit(self):
  from admin_console import ConsoleLoginError
  for _ in range(5):
   with self.assertRaises(ConsoleLoginError) as e:self.c.login({'username':'admin','password':'wrong-password'})
   self.assertEqual(e.exception.status,401)
  with self.assertRaises(ConsoleLoginError) as e:self.c.login({'username':'admin','password':'test-admin-password-123'})
  self.assertEqual(e.exception.status,429)
  self.assertEqual(self.s.db.execute("SELECT count(*) FROM audits WHERE action='console.login.failed'").fetchone()[0],5)
 def test_canonical_and_cancel_idempotency(self):
  oid=self.create();self.assertEqual(len(self.c.state(self.u)['orders']),1);self.assertEqual(self.c.state(self.u)['statistics']['orderCount'],1)
  p={'id':oid,'expectedStage':'AVAILABLE','reason':'测试取消','confirmed':True};first=self.action('order.cancel',p,'cancel-once');self.assertEqual(first,self.action('order.cancel',p,'cancel-once'))
  self.assertEqual(self.s.passenger_data(self.pid)['orders'][0]['stage'],'CANCELLED')
  with self.assertRaises(ApiError) as e:self.action('order.cancel',{**p,'reason':'不同原因'},'cancel-once')
  self.assertEqual(e.exception.status,409)
 def test_full_flow_adjust_payment_refund_and_complaint(self):
  oid=self.create();self.da('order.accept',{'id':oid});self.da('order.advance',{'id':oid});self.da('order.advance',{'id':oid,'phoneTail':'0101'})
  with self.assertRaises(ApiError):self.action('order.cancel',{'id':oid,'expectedStage':'TRIP','reason':'拒绝行程中取消','confirmed':True})
  self.da('order.advance',{'id':oid});self.action('order.adjustFare',{'id':oid,'expectedStage':'WAIT_PAY','amount':1234,'reason':'模拟费用确认','confirmed':True})
  self.pa('order.pay',{'id':oid,'outcome':'FAILED'});self.pa('order.pay',{'id':oid,'outcome':'SUCCESS'})
  self.assertEqual(self.s.data(self.did)['state']['orders'][0]['fare'],1234)
  self.pa('order.evaluate',{'id':oid,'rating':5,'content':'完整流程通过'});self.pa('order.complaint',{'id':oid,'reason':'体验投诉','content':'测试处理回复同步'})
  cp=self.s.shared_order(oid)['complaints'][0];self.action('support.reply',{'source':'ORDER_COMPLAINT','ownerId':oid,'id':cp['id'],'reply':'已核查并处理'})
  self.assertEqual(self.s.passenger_data(self.pid)['orders'][0]['complaints'][0]['status'],'REPLIED')
  before=self.s.data(self.did)['state']['balance'];self.action('order.refund',{'id':oid,'expectedStage':'DONE','reason':'模拟退款验证','confirmed':True},'refund-once')
  self.assertEqual(self.s.data(self.did)['state']['balance'],before-1234);self.assertEqual(self.s.passenger_data(self.pid)['orders'][0]['refundAmount'],1234)
  with self.assertRaises(ApiError):self.action('order.refund',{'id':oid,'expectedStage':'DONE','reason':'重复退款测试','confirmed':True})
  self.assertEqual(self.c.state(self.u)['statistics']['paidFare'],0)
 def test_permissions_sessions_and_password_leak(self):
  self.action('user.save',{'username':'reader','password':'test-reader-password','enabled':True,'role':'VIEWER'})
  auth=self.c.login({'username':'reader','password':'test-reader-password'});reader=self.c.authenticate(auth['session'])
  with self.assertRaises(ApiError) as e:self.c.action(reader,'order.cancel',{},'reject')
  self.assertEqual(e.exception.status,403)
  with self.assertRaises(ApiError):self.c.export(reader,'orders')
  state=self.c.state(reader);self.assertEqual(state['users'],[]);self.assertNotIn('password_hash',json.dumps(state))
  self.action('user.save',{'username':'reader','enabled':False,'role':'VIEWER'})
  with self.assertRaises(ApiError):self.c.authenticate(auth['session'])
 def test_restart_and_rollback(self):
  oid=self.create();self.s.db.commit();p={'id':oid,'expectedStage':'AVAILABLE','reason':'回滚验证','confirmed':True}
  self.action('order.cancel',p);self.s.db.rollback();self.assertEqual(self.s.shared_order(oid)['stage'],'AVAILABLE')
  self.action('order.cancel',p);self.s.db.commit();path=pathlib.Path(self.tmp.name)/'test.sqlite';self.s.close();self.s=Service(path);self.c=AdminConsole(self.s)
  self.assertEqual(self.s.shared_order(oid)['stage'],'CANCELLED');self.assertTrue(self.c.login({'username':'admin','password':'test-admin-password-123'}))
 def test_certificate_file_checks_owner_and_current_reference(self):
  from common import SAMPLE_IMAGE
  with self.assertRaises(ApiError):self.c.certificate_file(self.u,self.did,'license')
  fid=self.s.save_image(self.did,SAMPLE_IMAGE);profile=json.loads(self.s.account(self.did)['profile']);profile['certificates'][1]['fileId']=fid;self.s.save_profile(self.did,profile)
  result=self.c.certificate_file(self.u,self.did,'license');self.assertEqual(result['imageBase64'],SAMPLE_IMAGE)
  self.s.db.execute('UPDATE files SET driver=? WHERE id=?',('unrelated-driver',fid))
  with self.assertRaises(ApiError) as e:self.c.certificate_file(self.u,self.did,'license')
  self.assertEqual(e.exception.status,404)
 def test_export_formula_escape(self):
  oid=self.create();o=self.s.shared_order(oid);o['origin']='=HYPERLINK("malicious")';self.s.save_shared(o)
  self.assertIn("'=HYPERLINK",self.c.export(self.u,'orders'))
 def test_admin_cancels_wait_continue_order(self):
  oid=self.create();o=self.s.shared_order(oid);o['stage']='WAIT_CONTINUE';self.s.save_shared(o,'DRIVER_CANCELLED_FOR_CONTINUATION')
  payload={'id':oid,'expectedStage':'WAIT_CONTINUE','confirmed':True,'reason':'乘客确认结束等待'}
  first=self.action('order.cancel',payload,'cancel-wait-continue')
  self.assertEqual(first['order']['stage'],'CANCELLED')
  self.assertEqual(self.s.passenger_data(self.pid)['orders'][0]['stage'],'CANCELLED')
  self.assertEqual(first,self.action('order.cancel',payload,'cancel-wait-continue'))
 def test_refunds_survive_continue_without_double_counting(self):
  from common import stamp
  oid=self.create();o=self.s.shared_order(oid)
  # Same amount/time in two rounds is legitimate and must not be deduplicated.
  refunded_at=stamp();o.update(stage='WAIT_CONTINUE',refundAmount=1200,refundedAt=refunded_at,refundMode='SIMULATED',paymentStatus='REFUNDED')
  o['refundHistory']=[{'amount':1200,'at':refunded_at,'driverId':self.did,'mode':'SIMULATED'}]
  self.s.save_shared(o)
  before=self.c.state(self.u);row=before['payments'][0]
  self.assertEqual(before['statistics']['refundAmount'],2400)
  self.assertEqual(row['refundAmount'],2400);self.assertEqual(row['currentRefundAmount'],1200);self.assertEqual(row['historicalRefundAmount'],1200);self.assertEqual(len(row['refundRecords']),2)
  self.pa('order.continue',{'id':oid,'type':1})
  after=self.c.state(self.u);row=after['payments'][0]
  self.assertEqual(after['statistics']['refundAmount'],2400);self.assertEqual(row['refundAmount'],2400)
  self.assertEqual(row['currentRefundAmount'],0);self.assertEqual(row['historicalRefundAmount'],2400);self.assertEqual(len(row['refundRecords']),2)
  self.assertNotIn('refundAmount',self.s.shared_order(oid))
  o=self.s.shared_order(oid);o.update(stage='CANCELLED',refundAmount=800,refundedAt=stamp(),refundMode='SIMULATED',paymentStatus='REFUNDED');self.s.save_shared(o)
  final=self.c.state(self.u);self.assertEqual(final['statistics']['refundAmount'],3200);self.assertEqual(final['payments'][0]['refundAmount'],3200);self.assertEqual(len(final['payments'][0]['refundRecords']),3)
  exported=self.c.export(self.u,'payments');self.assertIn('historicalRefundAmount',exported);self.assertIn('3200',exported)
 def test_state_conflict_and_confirmation(self):
  oid=self.create()
  with self.assertRaises(ApiError):self.action('order.cancel',{'id':oid,'expectedStage':'AVAILABLE','reason':'缺少确认'})
  with self.assertRaises(ApiError) as e:self.action('order.cancel',{'id':oid,'expectedStage':'DONE','reason':'过期状态','confirmed':True})
  self.assertEqual(e.exception.status,409);self.assertEqual(self.s.shared_order(oid)['stage'],'AVAILABLE')

if __name__=='__main__':unittest.main(verbosity=2)
