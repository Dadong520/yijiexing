"""Actual shared CloudHandler HTTP/CAS integration. No patching of server routes.
Requires shared owner to integrate admin_console before this suite can pass.
"""
import json,os,pathlib,sys,unittest,urllib.request,urllib.error,uuid
from unittest.mock import patch
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'yijiexing-cloud-20261023/cloud-service/tests'))
import test_cloud
ADMIN=test_cloud.ADMIN

BASE='/api/local/admin/v2/'
PASSWORD='http-integration-admin-password'
class AdminHTTP(unittest.TestCase):
 start=test_cloud.CloudIntegration.start
 def setUp(self):
  self.env=patch.dict(os.environ,{'ADMIN_BOOTSTRAP_PASSWORD':PASSWORD});self.env.start()
  test_cloud.CloudIntegration.setUp(self);self.admin=self.start('admin')
 def tearDown(self):
  try:test_cloud.CloudIntegration.tearDown(self)
  finally:self.env.stop()
 def call(self,path,data=None,session=None,expected=200,server=None,admin_token=ADMIN):
  headers={'Content-Type':'application/json','X-Admin-Token':admin_token}
  if session:headers['X-Admin-Session']=session
  req=urllib.request.Request(f'http://127.0.0.1:{(server or self.admin).server_port}{BASE}{path}',data=None if data is None else json.dumps(data).encode(),headers=headers)
  try:
   with urllib.request.urlopen(req,timeout=30) as r:status,raw=r.status,r.read()
  except urllib.error.HTTPError as e:
   status,raw=e.code,e.read();e.close()
  self.assertEqual(status,expected,raw.decode()[:2000]);body=json.loads(raw);return body.get('data',body)
 def login(self,server=None,username='admin',password=PASSWORD,expected=200):return self.call('login',{'username':username,'password':password},server=server,expected=expected)
 def act(self,session,action,payload,rid=None,expected=200):return self.call('action',{'action':action,'payload':payload,'requestId':rid or str(uuid.uuid4())},session=session,expected=expected)
 def test_public_driver_and_bad_outer_token(self):
  self.call('state',server=self.driver,expected=404)
  self.call('login',{'username':'admin','password':PASSWORD},server=self.driver,expected=404)
  self.assertEqual(self.remote.writes,0)
  self.call('state',admin_token='wrong',expected=401)
 def test_bootstrap_persists_and_missing_session_denied(self):
  session=self.login()['session'];self.assertGreater(self.remote.writes,0)
  self.call('state',expected=401)
  replacement=self.start('admin');state=self.call('state',session=session,server=replacement)
  self.assertEqual(state['user']['role'],'SUPER_ADMIN');self.assertEqual(len(state['users']),1)
  self.assertNotIn('password_hash',json.dumps(state));self.assertNotIn(PASSWORD,json.dumps(state))
 def test_viewer_server_rejects_mutation_and_logout_invalidates(self):
  session=self.login()['session']
  self.act(session,'user.save',{'username':'viewer','password':'http-viewer-password','role':'VIEWER','enabled':True})
  viewer=self.login(username='viewer',password='http-viewer-password')['session']
  self.assertEqual(self.call('state',session=viewer)['users'],[])
  self.act(viewer,'order.cancel',{'id':'unknown','expectedStage':'AVAILABLE','reason':'权限测试','confirmed':True},expected=403)
  self.call('export?dataset=orders',session=viewer,expected=403)
  self.call('logout',{},session=viewer);self.call('state',session=viewer,expected=401)
 def test_snapshot_failure_withholds_mutation_success_and_retry_recovers(self):
  session=self.login()['session'];payload={'username':'recover','password':'recover-password-long','role':'VIEWER','enabled':True}
  self.remote.fail_write=True
  self.act(session,'user.save',payload,'durable-once',expected=503)
  self.remote.fail_write=False
  state=self.call('state',session=session);self.assertNotIn('recover',[u['username'] for u in state['users']])
  self.act(session,'user.save',payload,'durable-once')
  self.act(session,'user.save',payload,'durable-once')
  self.assertEqual(sum(u['username']=='recover' for u in self.call('state',session=session)['users']),1)
 def test_uncertain_upload_retry_idempotent(self):
  session=self.login()['session'];payload={'username':'uncertain','password':'uncertain-password','role':'VIEWER','enabled':True}
  self.remote.uncertain_write=True
  self.act(session,'user.save',payload,'uncertain-once',expected=503)
  self.act(session,'user.save',payload,'uncertain-once')
  state=self.call('state',session=session)
  self.assertEqual(sum(u['username']=='uncertain' for u in state['users']),1)
  self.assertEqual(sum(a['action']=='user.save' and 'uncertain' in a['after_value'] for a in state['audit']),1)
 def test_login_limit_survives_new_instances(self):
  self.login()
  for i in range(5):
   instance=self.admin if i%2==0 else self.start('admin')
   self.login(server=instance,password='incorrect-password',expected=401)
  self.login(server=self.start('admin'),expected=429)
  svc=self.admin.coordinator.begin()
  self.assertEqual(svc.db.execute("SELECT failures FROM console_login_attempts WHERE username='admin'").fetchone()[0],5)
  self.assertEqual(svc.db.execute("SELECT count(*) FROM audits WHERE action='console.login.failed'").fetchone()[0],5)
 def test_certificate_preview_requires_session_and_driver_ownership(self):
  from common import SAMPLE_IMAGE
  session=self.login()['session'];svc=self.admin.coordinator.begin()
  code=svc.code('13800000001')['verificationCode'];driver=svc.login({'mobile':'13800000001','code':code})['profile']['driver']['id']
  fid=svc.save_image(driver,SAMPLE_IMAGE);profile=json.loads(svc.account(driver)['profile']);profile['certificates'][1]['fileId']=fid;svc.save_profile(driver,profile);svc.db.commit();self.admin.coordinator.finish()
  path='certificate-file?driverId='+driver+'&type=license'
  self.call(path,expected=401)
  preview=self.call(path,session=session);self.assertEqual(preview['imageBase64'],SAMPLE_IMAGE)
  self.call(path+'&side=back',session=session,expected=404)
  svc=self.admin.coordinator.begin();svc.db.execute('UPDATE files SET driver=? WHERE id=?',('unrelated-driver',fid));svc.db.commit();self.admin.coordinator.finish()
  self.call(path,session=session,expected=404)
 def test_failed_login_snapshot_failure_is_503(self):
  self.login();self.remote.fail_write=True
  self.login(password='incorrect-password',expected=503)
  self.remote.fail_write=False
  self.assertIn('session',self.login())

if __name__=='__main__':unittest.main(verbosity=2)
