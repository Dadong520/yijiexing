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
        self.env = patch.dict(os.environ, {'IAP_EXPECTED_AUDIENCE': AUD, 'IAP_ALLOWED_EMAILS': EMAIL, 'ADMIN_BOOTSTRAP_PASSWORD': 'test-password-only-2026'})
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


if __name__=='__main__': unittest.main()
