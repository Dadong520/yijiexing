"""Admin v2 real HTTP + SQLite + snapshot CAS tests, entirely local fixtures.
No production credentials, Google Cloud, or original test environment are used.
"""
import json
import os
import pathlib
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from cloud_server import make_cloud_server
from test_cloud import MemoryCAS

ACCESS = 'admin-http-test-only-driver-access-32-characters'
OUTER = 'admin-http-test-only-outer-token-48-characters-long'
PASSWORD = 'LocalTestOnly!Admin123'
PREFIX = '/api/local/admin/v2/'


class AdminHttpIntegration(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {'ADMIN_BOOTSTRAP_PASSWORD': PASSWORD})
        self.env.start()
        self.tmp = tempfile.TemporaryDirectory()
        self.remote = MemoryCAS()
        self.servers = []
        self.admin = self.start('admin')
        self.driver = self.start('driver')
        self.rid = 0

    def tearDown(self):
        for server, thread in self.servers:
            server.shutdown(); thread.join(timeout=5); server.service_close()
        self.tmp.cleanup(); self.env.stop()

    def start(self, role):
        path = pathlib.Path(self.tmp.name) / str(len(self.servers)) / 'demo.sqlite'
        server = make_cloud_server(self.remote, path, 0, role,
                                  ACCESS if role == 'driver' else '',
                                  OUTER if role == 'admin' else '')
        thread = threading.Thread(target=server.serve_forever, kwargs={'poll_interval': 0.01}, daemon=True)
        thread.start(); self.servers.append((server, thread))
        return server

    def request(self, endpoint, payload=None, expected=200, session=None,
                outer=OUTER, server=None, actor='http-test'):
        headers = {'Content-Type': 'application/json', 'X-Demo-Access': ACCESS,
                   'X-Local-Actor': actor}
        if outer is not None:headers['X-Admin-Token'] = outer
        if session:headers['X-Admin-Session'] = session
        request = urllib.request.Request(
            f'http://127.0.0.1:{(server or self.admin).server_port}{PREFIX}{endpoint}',
            data=None if payload is None else json.dumps(payload).encode(), headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                status, body, response_headers = response.status, response.read(), response.headers
        except urllib.error.HTTPError as exc:
            status, body, response_headers = exc.code, exc.read(), exc.headers
            exc.close()
        self.assertEqual(expected, status, body.decode(errors='replace')[:2000])
        if response_headers.get_content_type() == 'application/json':
            result = json.loads(body)
            return result.get('data', result)
        return {'body': body, 'mime': response_headers.get_content_type(), 'headers': response_headers}

    def login(self, username='admin', password=PASSWORD, server=None, expected=200):
        return self.request('login', {'username': username, 'password': password}, server=server, expected=expected)

    def action(self, session, action, payload, expected=200, server=None, request_id=None, actor='http-test'):
        self.rid += 1
        return self.request('action', {'action': action, 'payload': payload,
                            'requestId': request_id or f'test-admin-request-{self.rid}'},
                            session=session, server=server, expected=expected, actor=actor)

    def test_public_driver_rejects_every_admin_v2_endpoint_before_storage(self):
        for endpoint, payload in [('login', {'username':'admin','password':PASSWORD}), ('state',None),
                                  ('action',{}), ('logout',{}), ('export?dataset=orders',None)]:
            self.request(endpoint, payload, server=self.driver, expected=404)
        self.assertEqual(0, self.remote.reads)
        self.assertEqual(0, self.remote.writes)

    def test_outer_token_and_session_are_independent_required_boundaries(self):
        self.request('login', {'username':'admin','password':PASSWORD}, outer='wrong', expected=401)
        self.request('state', outer=None, expected=401)
        login = self.login(); session = login['session']
        self.request('state', session=session, outer='wrong', expected=401)
        self.request('state', expected=401)
        self.request('state', session='fabricated-session', expected=401)
        state = self.request('state', session=session)
        self.assertEqual('SUPER_ADMIN', state['user']['role'])
        serialized = json.dumps(state)
        for secret in (PASSWORD, OUTER, session):self.assertNotIn(secret, serialized)
        self.request('logout', {}, session=session)
        self.request('state', session=session, expected=401)

    def test_each_role_denies_out_of_role_actions_even_with_spoofed_actor(self):
        admin_session = self.login()['session']
        denied = {
            'VIEWER': ['order.cancel', 'config.draft', 'user.save', 'order.adjustFare'],
            'OPERATOR': ['order.adjustFare', 'order.refund', 'user.save', 'config.draft'],
            'FINANCE': ['support.reply', 'certificate.audit', 'user.save', 'config.draft'],
            'CONFIG_ADMIN': ['order.cancel', 'order.refund', 'user.save', 'support.reply'],
        }
        for role, actions in denied.items():
            username = 'role_' + role.lower()
            self.action(admin_session, 'user.save', {'username': username, 'password': PASSWORD,
                        'role': role, 'enabled': True})
            session = self.login(username)['session']
            state = self.request('state', session=session)
            self.assertEqual(role, state['user']['role'])
            self.assertEqual([], state['users'])
            for action in actions:
                self.action(session, action, {}, expected=403, actor='ADMIN:admin')
            if role == 'VIEWER':
                self.request('export?dataset=orders', session=session, expected=403)
        # Existing sessions are invalidated when the account is disabled.
        viewer = self.login('role_viewer')['session']
        self.action(admin_session, 'user.save', {'username':'role_viewer','role':'VIEWER','enabled':False})
        self.request('state', session=viewer, expected=401)

    def test_export_is_csv_and_audit_survives_cold_reload_cas(self):
        session = self.login()['session']; before = self.remote.writes
        result = self.request('export?dataset=orders', session=session)
        self.assertEqual('text/csv', result['mime'])
        self.assertTrue(result['body'].startswith(b'\xef\xbb\xbf'))
        self.assertIn('passengerId', result['body'].decode('utf-8-sig'))
        self.assertEqual('nosniff', result['headers'].get('X-Content-Type-Options'))
        self.assertGreater(self.remote.writes, before)
        replacement = self.start('admin')
        state = self.request('state', session=session, server=replacement)
        exports = [r for r in state['audit'] if r['action'] == 'console.export']
        self.assertEqual(1, len(exports))

    def test_csv_is_not_acknowledged_when_export_audit_cannot_persist(self):
        session = self.login()['session']; self.remote.fail_write = True
        error = self.request('export?dataset=orders', session=session, expected=503)
        self.assertEqual('DEMO_STORAGE_UNAVAILABLE', error['details']['reason'])
        self.remote.fail_write = False
        replacement = self.start('admin')
        state = self.request('state', session=session, server=replacement)
        self.assertFalse(any(r['action'] == 'console.export' for r in state['audit']))

    def test_login_failures_and_rate_limit_survive_cold_restart(self):
        self.login()  # Persist bootstrap before intentionally failing authentication.
        self.login(password='incorrect-test-password', expected=401)
        replacement = self.start('admin')
        for _ in range(4):self.login(password='incorrect-test-password', server=replacement, expected=401)
        error = self.login(server=self.start('admin'), expected=429)
        self.assertEqual(429, error['code'])
        persisted = self.servers[-1][0].coordinator.service.db.execute(
            'SELECT failures FROM console_login_attempts WHERE username=?', ('admin',)).fetchone()
        self.assertEqual(5, persisted['failures'])

    def test_cas_failure_never_confirms_login_action_or_security_counter(self):
        session = self.login()['session']
        self.remote.fail_write = True
        self.login(expected=503)
        self.remote.fail_write = False
        self.assertEqual('admin', self.request('state', session=session)['user']['username'])
        before = self.remote.writes
        self.remote.fail_write = True
        self.action(session, 'user.save', {'username':'uncommitted','password':PASSWORD,
                    'role':'VIEWER','enabled':True}, expected=503)
        self.remote.fail_write = False
        replacement = self.start('admin')
        users = self.request('state', session=session, server=replacement)['users']
        self.assertNotIn('uncommitted', [u['username'] for u in users])
        self.assertEqual(before, self.remote.writes)
        self.remote.fail_write = True
        self.login(password='incorrect-test-password', server=replacement, expected=503)
        self.remote.fail_write = False
        fresh = self.start('admin')
        self.request('state', session=session, server=fresh)
        count = fresh.coordinator.service.db.execute('SELECT count(*) FROM console_login_attempts').fetchone()[0]
        self.assertEqual(0, count)

if __name__ == '__main__':unittest.main()
