"""Real HTTP + signed ES256 assertions; no cloud accounts or live orders."""
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from unittest.mock import patch

sys.path.insert(0, os.environ.get('YIJ_BACKEND_SOURCE', '/private/tmp/yijiexing-passenger-v8/cloud-service'))
sys.path.insert(0, str(Path(__file__).parent))
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from google.auth import crypt, jwt
import cloud_server
import iap_browser
from common import ApiError
from tests.test_cloud import MemoryCAS

AUD = '/projects/491829048305/locations/us-west1/services/driver-admin'
EMAIL = 'test-admin@example.invalid'
ADMIN = 'test-only-admin-secret-which-never-goes-to-the-browser'


class BrowserTests(unittest.TestCase):
    def setUp(self):
        key = ec.generate_private_key(ec.SECP256R1())
        private = key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
        public = key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)
        self.signer = crypt.ES256Signer.from_string(private, key_id='test-key')
        self.cert_patch = patch.object(iap_browser, 'certificates', return_value={'test-key': public})
        self.cert_patch.start()
        self.env = patch.dict(os.environ, {'ADMIN_AUTH_MODE':'IAP', 'IAP_EXPECTED_AUDIENCE': AUD, 'IAP_ALLOWED_EMAILS': EMAIL, 'ADMIN_BOOTSTRAP_PASSWORD': 'test-password-only-2026'})
        self.env.start()
        self.temp = tempfile.TemporaryDirectory()
        with patch.object(cloud_server, 'CloudHandler', iap_browser.BrowserHandler):
            self.remote = MemoryCAS()
            self.server = cloud_server.make_cloud_server(self.remote, Path(self.temp.name)/'state.sqlite', 0, role='admin', admin_token=ADMIN, origin_allowlist=('https://admin.example.invalid',))
        self.thread = threading.Thread(target=self.server.serve_forever, kwargs={'poll_interval': .01}, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown(); self.thread.join(); self.server.service_close()
        self.temp.cleanup(); self.env.stop(); self.cert_patch.stop()

    def token(self, **updates):
        now = int(time.time())
        payload = dict(iss='https://cloud.google.com/iap', aud=AUD, sub='test-user', email=EMAIL, iat=now, exp=now+600)
        payload.update(updates)
        return jwt.encode(self.signer, payload).decode()

    def request(self, path, body=None, assertion=True, **extra):
        headers = {'Content-Type':'application/json'}
        if assertion: headers['X-Goog-IAP-JWT-Assertion'] = self.token() if assertion is True else assertion
        if body is not None: headers['Origin'] = 'https://admin.example.invalid'
        headers.update(extra)
        req = urllib.request.Request(f'http://127.0.0.1:{self.server.server_port}'+path, data=json.dumps(body).encode() if body is not None else None, headers=headers)
        try: response = urllib.request.urlopen(req, timeout=5)
        except urllib.error.HTTPError as error: response = error
        with response:
            raw=response.read()
            return response.status, json.loads(raw) if response.headers.get('Content-Type','').startswith('application/json') else raw.decode()

    def test_missing_assertion_rejected_even_with_outer_secret(self):
        status,_=self.request('/api/local/admin/v2/state',assertion=False,**{'X-Admin-Token':ADMIN})
        self.assertEqual(status,401);self.assertEqual(self.remote.writes,0)

    def test_unset_mode_is_strict_iap_and_client_mode_header_cannot_select_password(self):
        os.environ.pop('ADMIN_AUTH_MODE')
        status,_=self.request('/api/local/admin/v2/state',assertion=False,**{'X-Admin-Auth-Mode':'PASSWORD','ADMIN_AUTH_MODE':'PASSWORD','X-Admin-Token':ADMIN})
        self.assertEqual(401,status);self.assertEqual(0,self.remote.writes)

    def test_signed_assertion_does_not_bypass_console_login(self):
        status,_=self.request('/api/local/admin/v2/state');self.assertEqual(status,401)

    def test_console_login_state_logout_and_no_secret_disclosure(self):
        status,value=self.request('/api/local/admin/v2/login',{'username':'admin','password':'test-password-only-2026'},**{'X-Admin-Token':'forged'})
        self.assertEqual(status,200,value)
        session=value['data']['session']
        status,state=self.request('/api/local/admin/v2/state',**{'X-Admin-Session':session})
        self.assertEqual(status,200,state);self.assertNotIn(ADMIN,json.dumps(state))
        status,_=self.request('/api/local/admin/v2/logout',{},**{'X-Admin-Session':session});self.assertEqual(status,200)
        status,_=self.request('/api/local/admin/v2/state',**{'X-Admin-Session':session});self.assertEqual(status,401)

    def test_wrong_audience_email_issuer_expired_and_tampered_rejected(self):
        for changes in ({'aud':'wrong'},{'email':'other@example.invalid'},{'iss':'https://evil.invalid'},{'iat':int(time.time())-900,'exp':int(time.time())-300},{'iat':int(time.time())+100},{'exp':int(time.time())+2000}):
            with self.subTest(changes=changes):
                status,_=self.request('/api/local/admin/v2/state',assertion=self.token(**changes));self.assertEqual(status,403)
        token=self.token();head,body,sig=token.split('.');tampered=head+'.'+body+'.'+('A' if sig[0]!='A' else 'B')+sig[1:]
        status,_=self.request('/api/local/admin/v2/state',assertion=tampered);self.assertEqual(status,403)
        self.assertEqual(self.remote.writes,0)

    def test_cross_origin_and_missing_origin_operations_rejected(self):
        for headers in ({'Origin':'https://evil.invalid'},{'Origin':''},{'Sec-Fetch-Site':'cross-site'}):
            status,_=self.request('/api/local/admin/v2/login',{'username':'admin','password':'test-password-only-2026'},**headers)
            self.assertEqual(status,403)
        self.assertEqual(self.remote.writes,0)

    def test_legacy_admin_mutations_and_shutdown_unavailable(self):
        for path in ('/api/local/admin/action','/api/local/admin/shutdown','/api/auth/login'):
            status,_=self.request(path,{});self.assertEqual(status,404)

    def test_signed_query_is_session_protected_and_filtered_export_matches(self):
        status,_=self.request('/api/local/admin/v2/query?dataset=orders')
        self.assertEqual(status,401)
        _,login=self.request('/api/local/admin/v2/login',{'username':'admin','password':'test-password-only-2026'})
        headers={'X-Admin-Session':login['data']['session']}
        status,value=self.request('/api/local/admin/v2/query?dataset=orders&orderId=IAP-NO-MATCH',**headers)
        self.assertEqual(status,200,value);self.assertEqual(value['data']['total'],0)
        self.assertIn('schema',value['data'])
        status,csv_text=self.request('/api/local/admin/v2/export?dataset=orders&orderId=IAP-NO-MATCH',**headers)
        self.assertEqual(status,200);self.assertEqual(len(csv_text.strip().splitlines()),1)
        status,_=self.request('/api/local/admin/v2/query?dataset=orders&assigned=true',**headers)
        self.assertEqual(status,400)
        status,_=self.request('/api/local/admin/v2/query?dataset=orders',assertion=False,**headers)
        self.assertEqual(status,401)

    def test_certificate_outage_is_retryable_without_mutation(self):
        with patch.object(iap_browser,'certificates',side_effect=ApiError(503,'验证暂不可用')):
            status,_=self.request('/api/local/admin/v2/state');self.assertEqual(status,503)
        self.assertEqual(self.remote.writes,0)

    def test_browser_viewer_cannot_manage_accounts_or_adjust_fares(self):
        _, login = self.request('/api/local/admin/v2/login', {'username':'admin','password':'test-password-only-2026'})
        admin_headers = {'X-Admin-Session':login['data']['session']}
        status, value = self.request('/api/local/admin/v2/action', {'action':'user.save','payload':{'username':'reader','password':'test-reader-password','enabled':True,'role':'VIEWER'},'requestId':'browser-create-reader-001'}, **admin_headers)
        self.assertEqual(status,200,value)
        status, login = self.request('/api/local/admin/v2/login', {'username':'reader','password':'test-reader-password'})
        self.assertEqual(status,200,login)
        headers = {'X-Admin-Session':login['data']['session']}
        status,state = self.request('/api/local/admin/v2/state',**headers)
        self.assertEqual(status,200,state)
        self.assertEqual(state['data'].get('users'), [])
        for action in ('user.save','order.adjustFare'):
            status,_=self.request('/api/local/admin/v2/action',{'action':action,'payload':{},'requestId':'forbidden-'+action},**headers)
            self.assertEqual(status,403)


class PasswordBrowserTests(unittest.TestCase):
    # Reuse only the HTTP fixture, not the IAP test methods. Both suites run.
    def setUp(self):
        BrowserTests.setUp(self)
        os.environ['ADMIN_AUTH_MODE']='PASSWORD'
        os.environ.pop('IAP_EXPECTED_AUDIENCE',None)
        os.environ.pop('IAP_ALLOWED_EMAILS',None)
    tearDown=BrowserTests.tearDown
    token=BrowserTests.token
    def request(self,path,body=None,assertion=False,**extra):
        return BrowserTests.request(self,path,body,assertion=assertion,**extra)
    def login(self,username='admin',password='test-password-only-2026'):
        status,value=self.request('/api/local/admin/v2/login',{'username':username,'password':password})
        self.assertEqual(200,status,value)
        return value['data']['session']

    def test_anonymous_page_assets_reachable_without_iap_or_secret(self):
        with patch.object(iap_browser,'verify_identity',side_effect=AssertionError('PASSWORD must not verify IAP')):
            for path in ('/','/admin','/admin/','/static/admin.js','/static/admin.css'):
                status,value=self.request(path)
                self.assertEqual(200,status,path);self.assertNotIn(ADMIN,value)
        self.assertEqual(0,self.remote.reads);self.assertEqual(0,self.remote.writes)

    def test_all_data_endpoints_require_session_even_with_forged_headers(self):
        for forged in ({},{'X-Admin-Token':ADMIN},{'X-Admin-Session':'fake-session'},
                       {'X-Admin-Token':ADMIN,'X-Admin-Session':'fake-session','X-Goog-IAP-JWT-Assertion':'fake'}):
            for path,body in [('/api/local/admin/v2/state',None),('/api/local/admin/v2/query?dataset=orders',None),
                              ('/api/local/admin/v2/export?dataset=orders',None),
                              ('/api/local/admin/v2/action',{'action':'order.cancel','payload':{},'requestId':'unauthorized'}),
                              ('/api/local/admin/v2/logout',{})]:
                with self.subTest(path=path,headers=list(forged)):
                    status,_=self.request(path,body,**forged);self.assertEqual(401,status)

    def test_password_login_state_query_export_logout_and_no_secret_disclosure(self):
        session=self.login();headers={'X-Admin-Session':session,'X-Admin-Token':'caller-forged'}
        for path in ('/api/local/admin/v2/state','/api/local/admin/v2/query?dataset=orders','/api/local/admin/v2/export?dataset=orders'):
            status,value=self.request(path,**headers);self.assertEqual(200,status,value)
            self.assertNotIn(ADMIN,json.dumps(value));self.assertNotIn('test-password-only-2026',json.dumps(value))
        status,_=self.request('/api/local/admin/v2/logout',{},**headers);self.assertEqual(200,status)
        status,_=self.request('/api/local/admin/v2/state',**headers);self.assertEqual(401,status)

    def test_wrong_password_attempts_persist_and_lock_even_correct_password(self):
        for _ in range(5):
            status,_=self.request('/api/local/admin/v2/login',{'username':'admin','password':'incorrect'})
            self.assertEqual(401,status)
        for password in ('incorrect','test-password-only-2026'):
            status,_=self.request('/api/local/admin/v2/login',{'username':'admin','password':password})
            self.assertEqual(429,status)
        self.assertGreater(self.remote.writes,0)

    def test_viewer_cannot_write_or_export_and_action_is_audited(self):
        session=self.login();headers={'X-Admin-Session':session}
        status,value=self.request('/api/local/admin/v2/action',{'action':'user.save','payload':{'username':'reader','password':'test-reader-password','enabled':True,'role':'VIEWER'},'requestId':'password-create-reader'},**headers)
        self.assertEqual(200,status,value)
        viewer=self.login('reader','test-reader-password');headers={'X-Admin-Session':viewer}
        status,state=self.request('/api/local/admin/v2/state',**headers)
        self.assertEqual(200,status);self.assertEqual([],state['data']['users'])
        self.assertTrue(any(r['action']=='user.save' for r in state['data']['audit']))
        for action in ('user.save','order.adjustFare','order.cancel','config.draft'):
            status,_=self.request('/api/local/admin/v2/action',{'action':action,'payload':{},'requestId':'denied-'+action},**headers)
            self.assertEqual(403,status)
        status,_=self.request('/api/local/admin/v2/export?dataset=orders',**headers);self.assertEqual(403,status)

    def test_origin_and_fetch_site_guards_apply_after_login_too(self):
        session=self.login()
        before=self.remote.writes
        for forged in ({'Origin':'https://evil.invalid'},{'Origin':''},{'Sec-Fetch-Site':'cross-site'},{'Sec-Fetch-Site':'same-site'}):
            for path,body in [('/api/local/admin/v2/login',{'username':'admin','password':'test-password-only-2026'}),
                              ('/api/local/admin/v2/logout',{}),('/api/local/admin/v2/action',{'action':'order.cancel'})]:
                status,_=self.request(path,body,**{'X-Admin-Session':session,**forged});self.assertEqual(403,status)
        self.assertEqual(before,self.remote.writes)

    def test_legacy_shutdown_driver_and_unknown_api_not_exposed(self):
        for path in ('/api/local/admin/action','/api/local/admin/state','/api/local/admin/shutdown','/api/local/admin/export',
                     '/api/auth/login','/api/state','/api/action','/api/passenger/state','/static/unknown.js'):
            status,_=self.request(path,{});self.assertEqual(404,status,path)

    def test_unknown_mode_fails_closed_even_with_valid_console_session(self):
        session=self.login()
        for mode in ('','password','UNKNOWN',' PASSWORD '):
            with self.subTest(mode=mode):
                os.environ['ADMIN_AUTH_MODE']=mode
                for path in ('/','/api/local/admin/v2/state'):
                    status,_=self.request(path,**{'X-Admin-Session':session});self.assertEqual(503,status)
                with patch.dict(os.environ,{'CLOUD_ROLE':'admin'}), patch.object(cloud_server,'main') as main:
                    with self.assertRaises(ApiError):iap_browser.main()
                    main.assert_not_called()


if __name__=='__main__': unittest.main()
