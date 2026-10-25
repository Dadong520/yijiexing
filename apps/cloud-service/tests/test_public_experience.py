"""Public experience auth boundaries: real HTTP + SQLite + in-memory snapshot CAS.

Only loopback requests and temporary data. No production secrets or GCP calls.
"""
import json
import pathlib
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from cloud_server import make_cloud_server
from test_cloud import MemoryCAS

ADMIN = 'local-http-fixture-admin-token-32-characters-minimum'
LEGACY = 'obsolete-local-http-fixture-access-code'


class PublicExperienceHTTP(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.remote = MemoryCAS()
        self.servers = []
        self.public = self.start('driver')
        self.admin = self.start('admin')

    def tearDown(self):
        for server, thread in self.servers:
            server.shutdown()
            thread.join(timeout=5)
            server.service_close()
        self.tmp.cleanup()

    def start(self, role):
        # No access_code supplied: this must be a supported public-client setup.
        path = pathlib.Path(self.tmp.name) / str(len(self.servers)) / 'state.sqlite'
        server = make_cloud_server(self.remote, path, 0, role=role,
                                   admin_token=ADMIN if role == 'admin' else '')
        thread = threading.Thread(target=server.serve_forever,
                                  kwargs={'poll_interval': 0.01}, daemon=True)
        thread.start()
        self.servers.append((server, thread))
        return server

    def request(self, path, data=None, *, status=200, token=None, legacy=None,
                internal=None, server=None):
        headers = {'Content-Type': 'application/json'}
        if token is not None:
            headers['Authorization'] = 'Bearer ' + token
        if legacy is not None:
            headers['X-Demo-Access'] = legacy
        if internal is not None:
            headers['X-Admin-Token'] = internal
        server = server or self.public
        request = urllib.request.Request(
            f'http://127.0.0.1:{server.server_port}{path}',
            data=None if data is None else json.dumps(data).encode(), headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                actual, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            with error:
                actual, raw = error.code, error.read()
        self.assertEqual(actual, status, raw.decode()[:1000])
        result = json.loads(raw)
        return result.get('data', result)

    def login(self, role='driver', legacy=None):
        prefix, mobile = ('/api', '13800000003') if role == 'driver' else ('/api/passenger', '13800000102')
        code = self.request(prefix + '/auth/code', {'mobile': mobile}, legacy=legacy)['verificationCode']
        result = self.request(prefix + '/auth/login', {'mobile': mobile, 'code': code}, legacy=legacy)
        return result

    def test_no_code_login_and_correct_role_state(self):
        driver = self.login()
        passenger = self.login('passenger')
        d = self.request('/api/state', token=driver['token'])
        p = self.request('/api/passenger/state', token=passenger['token'])
        self.assertEqual(d['profile']['driver']['id'], driver['profile']['driver']['id'])
        self.assertEqual(p['passenger']['id'], passenger['passenger']['id'])
        self.assertEqual(p['passenger']['mobile'], '13800000102')
        self.assertEqual(self.public.access_code, '')
        self.assertEqual(self.public.admin_token, '')

    def test_legacy_header_valid_wrong_or_absent_does_not_gate_existing_session(self):
        driver = self.login(legacy=LEGACY)
        passenger = self.login('passenger', legacy='wrong')
        for value in (None, '', 'wrong', LEGACY):
            self.request('/api/state', token=driver['token'], legacy=value)
            self.request('/api/passenger/state', token=passenger['token'], legacy=value)

    def test_no_token_and_wrong_role_rejected_for_state_and_action(self):
        driver = self.login()
        passenger = self.login('passenger')
        for prefix in ('/api', '/api/passenger'):
            self.request(prefix + '/state', status=401)
            self.request(prefix + '/action', {'action': 'profile.update', 'payload': {},
                         'requestId': 'no-token'}, status=401)
        self.request('/api/state', token=passenger['token'], status=401)
        self.request('/api/passenger/state', token=driver['token'], status=401)
        self.request('/api/action', {'action': 'driver.online', 'payload': {'online': True},
                     'requestId': 'wrong-role-driver'}, token=passenger['token'], status=401)
        self.request('/api/passenger/action', {'action': 'profile.update', 'payload': {},
                     'requestId': 'wrong-role-passenger'}, token=driver['token'], status=401)

    def test_non_allowlisted_and_cross_role_mobile_rejected(self):
        for prefix, mobiles in [('/api', ('19999999999', '13800000102')),
                                ('/api/passenger', ('19999999999', '13800000003'))]:
            for mobile in mobiles:
                self.request(prefix + '/auth/code', {'mobile': mobile}, status=403)
                self.request(prefix + '/auth/login', {'mobile': mobile, 'code': '123456'}, status=403)

    def test_public_tokens_cannot_reach_admin_routes_or_erase_accounts(self):
        driver = self.login()
        passenger = self.login('passenger')
        before = self.request('/api/state', token=driver['token'])
        for token in (None, driver['token'], passenger['token']):
            for path in ('/api/local/admin/state', '/api/local/admin/v2/state'):
                self.request(path, token=token, internal=ADMIN, status=404)
            for path in ('/api/local/admin/action', '/api/local/admin/v2/action', '/api/local/admin/shutdown'):
                self.request(path, {'action': 'local.reset', 'payload': {}},
                             token=token, internal=ADMIN, status=404)
        error = self.request('/api/action', {'action': 'local.reset', 'payload': {},
                             'requestId': 'public-reset-denied'}, token=driver['token'], status=403)
        self.assertEqual(error['details']['reason'], 'DEMO_OPERATION_DISABLED')
        self.request('/api/action', {'action': 'config.publish', 'payload': {'systemEnabled': False},
                     'requestId': 'no-admin-through-driver-action'}, token=driver['token'], status=404)
        after = self.request('/api/state', token=driver['token'])
        self.assertEqual(after['state']['orders'], before['state']['orders'])
        self.assertEqual(after['state']['balance'], before['state']['balance'])

    def test_internal_admin_secret_and_app_session_protection_unchanged(self):
        self.login()
        for internal in (None, 'wrong'):
            self.request('/api/local/admin/state', server=self.admin, internal=internal, status=401)
        state = self.request('/api/local/admin/state', server=self.admin, internal=ADMIN)
        self.assertIn('configs', state)
        # A valid outer secret alone does not authorize the v2 application session.
        self.request('/api/local/admin/v2/state', server=self.admin, internal=ADMIN, status=401)
        self.request('/api/auth/code', {'mobile': '13800000003'}, server=self.admin, status=404)
        self.request('/api/passenger/auth/code', {'mobile': '13800000102'}, server=self.admin, status=404)
        self.request('/api/local/admin/shutdown', {}, server=self.admin, internal=ADMIN, status=404)

    def test_ordinary_authenticated_action_and_request_id_replay_still_work(self):
        driver = self.login()
        payload = {'action': 'driver.online', 'payload': {'online': True}, 'requestId': 'same-id-after-public-login'}
        a = self.request('/api/action', payload, token=driver['token'])
        b = self.request('/api/action', payload, token=driver['token'])
        self.assertTrue(a['state']['online'])
        self.assertEqual(a['result'], b['result'])
        conflicting = {**payload, 'payload': {'online': False}}
        self.request('/api/action', conflicting, token=driver['token'], status=409)

    def test_tokens_remain_valid_after_public_server_recreation(self):
        driver = self.login()
        passenger = self.login('passenger')
        replacement = self.start('driver')
        self.request('/api/state', token=driver['token'], server=replacement)
        self.request('/api/passenger/state', token=passenger['token'], server=replacement)


if __name__ == '__main__':
    unittest.main()
