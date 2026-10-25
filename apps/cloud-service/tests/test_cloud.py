"""Cloud adapter tests: real HTTP/SQLite and a deterministic CAS object emulator.
No Google Cloud resources, original backend, real SMS or money are touched.
"""
import concurrent.futures
import datetime as dt
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
from common import now, stamp
from snapshot import ObjectVersion, SnapshotConflict, SnapshotUnavailable

ACCESS = 'test-demo-access-secret-only-32-characters'
ADMIN = 'test-cloud-admin-secret-only-48-characters-long'


class MemoryCAS:
    def __init__(self):
        self.lock = threading.Lock()
        self.generation = 0
        self.data = None
        self.writes = 0
        self.reads = 0
        self.fail_read = False
        self.fail_write = False
        self.uncertain_write = False
        self.barrier = None

    def read(self, known_generation=None):
        with self.lock:
            self.reads += 1
            if self.fail_read:
                raise SnapshotUnavailable('test read unavailable')
            result = ObjectVersion(self.generation, None if known_generation == self.generation else self.data)
            barrier = self.barrier
        if barrier:
            barrier.wait(timeout=10)
        return result

    def write(self, data, expected_generation):
        with self.lock:
            if self.fail_write:
                raise SnapshotUnavailable('test write unavailable')
            if self.generation != expected_generation:
                raise SnapshotConflict('test stale writer')
            self.generation += 1
            self.writes += 1
            self.data = bytes(data)
            if self.uncertain_write:
                self.uncertain_write = False
                raise SnapshotUnavailable('test response lost after remote commit')
            return self.generation


class CloudIntegration(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.remote = MemoryCAS()
        self.servers = []
        self.driver = self.start()
        self.token = None
        self.driver_id = None

    def tearDown(self):
        for server, thread in self.servers:
            server.shutdown()
            thread.join()
            server.service_close()
        self.tmp.cleanup()

    def start(self, role='driver', enabled=True, max_bytes=32*1024*1024):
        path = pathlib.Path(self.tmp.name) / str(len(self.servers)) / 'demo.sqlite'
        server = make_cloud_server(self.remote, path, 0, role, ACCESS if role == 'driver' else '',
                                  ADMIN if role == 'admin' else '', enabled=enabled, max_bytes=max_bytes)
        thread = threading.Thread(target=server.serve_forever, kwargs={'poll_interval': 0.01}, daemon=True)
        thread.start()
        self.servers.append((server, thread))
        return server

    def request(self, path, data=None, expected=200, server=None, access=ACCESS,
                token=None, admin=False, actor='editor', origin=None):
        server = server or self.driver
        headers = {'Content-Type': 'application/json'}
        if access is not None:
            headers['X-Demo-Access'] = access
        if token or self.token:
            headers['Authorization'] = 'Bearer ' + (token or self.token)
        if admin:
            headers['X-Admin-Token'] = ADMIN
            headers['X-Local-Actor'] = actor
        if origin:
            headers['Origin'] = origin
        request = urllib.request.Request(f'http://127.0.0.1:{server.server_port}{path}',
                  data=None if data is None else json.dumps(data).encode(), headers=headers)
        try:
            response = urllib.request.urlopen(request, timeout=20)
            status, body = response.status, response.read()
        except urllib.error.HTTPError as exc:
            status, body = exc.code, exc.read()
        self.assertEqual(status, expected, body.decode()[:2000])
        obj = json.loads(body)
        return obj.get('data', obj)

    def login(self, mobile='13800000001', server=None):
        code = self.request('/api/auth/code', {'mobile': mobile}, server=server)['verificationCode']
        result = self.request('/api/auth/login', {'mobile': mobile, 'code': code}, server=server)
        self.token = result['token']
        self.driver_id = result['profile']['driver']['id']
        return result

    def action(self, name, payload=None, **kw):
        return self.request('/api/action', {'action': name, 'payload': payload or {},
                            'requestId': kw.pop('request_id', 'request-' + name)}, **kw)

    def test_health_never_reads_storage_or_exposes_secrets(self):
        self.remote.fail_read = True
        health = self.request('/health', access=None)
        self.assertEqual(health['status'], 'ok')
        self.assertEqual(self.remote.reads, 0)
        self.assertNotIn(ACCESS, json.dumps(health))
        self.assertIsNone(self.driver.admin_token or None)

    def test_legacy_access_header_is_optional_and_ignored(self):
        for access in (None, '', 'wrong', ACCESS):
            result = self.request('/api/auth/code', {'mobile': '13800000001'}, access=access)
            self.assertIn('verificationCode', result)

    def test_paused_is_503_and_preserves_storage(self):
        server = self.start(enabled=False)
        result = self.request('/api/state', expected=503, server=server)
        self.assertEqual(result['details']['reason'], 'DEMO_PAUSED')
        self.assertEqual(self.remote.writes, 0)

    def test_mobile_allowlist(self):
        result = self.request('/api/auth/code', {'mobile': '19999999999'}, expected=403)
        self.assertEqual(result['details']['reason'], 'DEMO_MOBILE_NOT_ALLOWED')
        self.assertEqual(self.driver.coordinator.service.db.execute('SELECT COUNT(*) FROM accounts').fetchone()[0], 0)

    def test_public_driver_has_no_admin_or_shutdown_or_reset(self):
        for path in ['/admin', '/', '/api/local/admin/state', '/api/local/admin/shutdown', '/static/admin.js']:
            self.request(path, expected=404)
        self.assertEqual(self.remote.reads, 0)
        self.login()
        self.action('local.reset', expected=403)

    def test_admin_role_denies_driver_api_and_shutdown(self):
        admin = self.start('admin')
        self.request('/api/auth/code', {'mobile': '13800000001'}, server=admin, expected=404)
        self.request('/api/local/admin/shutdown', {}, server=admin, admin=True, expected=404)
        self.request('/api/local/admin/state', server=admin, expected=401)
        result = self.request('/api/local/admin/state', server=admin, admin=True)
        self.assertIn('configs', result)
        self.assertEqual(admin.access_code, '')

    def test_cold_start_persists_login_and_reloads_session(self):
        first = self.login()
        replacement = self.start()
        result = self.request('/api/state', server=replacement)
        self.assertEqual(result['profile']['driver']['id'], first['profile']['driver']['id'])
        self.assertEqual(result['environment']['persistence'], 'PRIVATE_GCS_SQLITE_CAS')

    def test_two_instances_share_latest_profile(self):
        self.login()
        replacement = self.start()
        self.action('profile.update', {'name': '第一云实例'})
        self.assertEqual(self.request('/api/state', server=replacement)['profile']['driver']['name'], '第一云实例')
        self.action('profile.update', {'name': '第二云实例'}, server=replacement, request_id='second')
        self.assertEqual(self.request('/api/state')['profile']['driver']['name'], '第二云实例')

    def test_failed_storage_write_does_not_ack_local_profile(self):
        self.login()
        original = self.request('/api/state')['profile']['driver']['name']
        self.remote.fail_write = True
        result = self.action('profile.update', {'name': '未被持久化'}, expected=503)
        self.assertEqual(result['details']['reason'], 'DEMO_STORAGE_UNAVAILABLE')
        self.remote.fail_write = False
        self.assertEqual(self.request('/api/state')['profile']['driver']['name'], original)

    def test_uncertain_upload_reloads_and_idempotent_retry_keeps_result(self):
        self.login()
        self.remote.uncertain_write = True
        self.action('blacklist.add', {'phoneTail': '8899', 'reason': '断线幂等'}, expected=503, request_id='uncertain')
        result = self.action('blacklist.add', {'phoneTail': '8899', 'reason': '断线幂等'}, request_id='uncertain')
        self.assertEqual(len(result['profile']['blacklist']), 1)

    def test_otp_failed_attempt_count_survives_error_and_restart(self):
        code = self.request('/api/auth/code', {'mobile': '13800000001'})['verificationCode']
        wrong = '000000' if code != '000000' else '111111'
        self.request('/api/auth/login', {'mobile': '13800000001', 'code': wrong}, expected=400)
        replacement = self.start()
        # begin() loads the authoritative, newly committed failed-attempt counter.
        self.request('/api/auth/login', {'mobile': '13800000001', 'code': wrong}, server=replacement, expected=400)
        attempts = replacement.coordinator.service.db.execute('SELECT attempts FROM codes').fetchone()[0]
        self.assertEqual(attempts, 2)

    def test_otp_error_returns_503_when_error_counter_cannot_persist(self):
        code = self.request('/api/auth/code', {'mobile': '13800000001'})['verificationCode']
        self.remote.fail_write = True
        wrong = '000000' if code != '000000' else '111111'
        self.request('/api/auth/login', {'mobile': '13800000001', 'code': wrong}, expected=503)

    def test_read_failure_never_falls_back_to_seed_or_local_success(self):
        self.login()
        self.remote.fail_read = True
        self.request('/api/state', expected=503)
        self.remote.fail_read = False
        self.assertEqual(self.request('/api/state')['profile']['driver']['id'], self.driver_id)

    def test_deleted_existing_snapshot_refuses_reseed(self):
        self.login()
        with self.remote.lock:
            self.remote.generation, self.remote.data = 0, None
        self.request('/api/state', expected=503)
        self.assertTrue(self.driver.coordinator.ever_existed)

    def test_static_admin_ui_is_secret_free_and_does_not_read_storage(self):
        admin = self.start('admin')
        response = urllib.request.urlopen(f'http://127.0.0.1:{admin.server_port}/admin').read().decode()
        self.assertNotIn(ADMIN, response)
        self.assertNotIn(ACCESS, response)
        self.assertEqual(self.remote.reads, 0)

    def test_cross_origin_browser_request_rejected(self):
        self.request('/api/auth/code', {'mobile': '13800000001'}, expected=403, origin='https://evil.invalid')
        self.assertEqual(self.remote.reads, 0)

    def test_read_only_state_does_not_upload_snapshot(self):
        self.login()
        before = self.remote.writes
        self.request('/api/state')
        self.request('/api/benefits/catalog')
        self.assertEqual(self.remote.writes, before)

    def test_database_size_limit_fails_before_success(self):
        server = self.start(max_bytes=128)
        self.request('/api/auth/code', {'mobile': '13800000001'}, server=server, expected=503)
        self.assertEqual(self.remote.writes, 0)

    def test_cas_conflict_never_overwrites_winner(self):
        self.login()
        other = self.start()
        self.request('/api/state', server=other)
        self.remote.barrier = threading.Barrier(2)
        def change(server, name):
            req = urllib.request.Request(f'http://127.0.0.1:{server.server_port}/api/action',
                data=json.dumps({'action': 'profile.update', 'payload': {'name': name}, 'requestId': name}).encode(),
                headers={'Content-Type': 'application/json', 'X-Demo-Access': ACCESS, 'Authorization': 'Bearer '+self.token})
            try:
                r = urllib.request.urlopen(req, timeout=20)
                return r.status, json.load(r)
            except urllib.error.HTTPError as exc:
                return exc.code, json.load(exc)
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            futures = [pool.submit(change, self.driver, '并发实例甲'), pool.submit(change, other, '并发实例乙')]
            results = [f.result() for f in futures]
        self.remote.barrier = None
        self.assertEqual(sorted(r[0] for r in results), [200, 503])
        winner = next(r[1]['data']['profile']['driver']['name'] for r in results if r[0] == 200)
        self.assertEqual(self.request('/api/state')['profile']['driver']['name'], winner)

    def test_concurrent_cold_start_creates_one_snapshot_not_blind_overwrite(self):
        other = self.start()
        self.remote.barrier = threading.Barrier(2)
        def code(server, mobile):
            req = urllib.request.Request(f'http://127.0.0.1:{server.server_port}/api/auth/code',
                data=json.dumps({'mobile': mobile}).encode(),
                headers={'Content-Type': 'application/json', 'X-Demo-Access': ACCESS})
            try:
                r=urllib.request.urlopen(req, timeout=20)
                return r.status
            except urllib.error.HTTPError as exc:
                return exc.code
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            futures = [pool.submit(code, self.driver, '13800000001'), pool.submit(code, other, '13800000002')]
            statuses = [f.result() for f in futures]
        self.remote.barrier = None
        self.assertEqual(sorted(statuses), [200, 503])
        self.assertEqual(self.remote.writes, 1)

    def test_due_schedule_runs_lazily_and_is_persisted_on_get(self):
        self.login()
        admin = self.start('admin')
        state = self.request('/api/local/admin/state', server=admin, admin=True)
        cfg = next(c for c in state['configs'] if c['scope']=='city' and c['target']=='420500')
        draft = self.request('/api/local/admin/action', {'action':'config.draft', 'payload': {
            'scope':'city','target':'420500','expectedRevision':cfg['revision'],
            'patch':{'levels':{'NORMAL':{'price':22200}}}}}, server=admin, admin=True)
        cfg = next(c for c in draft['admin']['configs'] if c['scope']=='city' and c['target']=='420500')
        self.request('/api/local/admin/action', {'action':'config.publish','payload': {
            'scope':'city','target':'420500','expectedRevision':cfg['revision'],
            'effectiveAt':stamp(now()+dt.timedelta(seconds=2))}}, server=admin, admin=True)
        # Move only the test fixture's scheduled time into the past; no scheduler
        # thread is started in the cloud service.
        svc = admin.coordinator.begin()
        row = svc.db.execute('SELECT id,payload FROM scheduled').fetchone()
        p=json.loads(row['payload']);p['effectiveAt']=stamp(now()-dt.timedelta(seconds=1))
        svc.db.execute('UPDATE scheduled SET payload=? WHERE id=?',(json.dumps(p),row['id']))
        admin.coordinator.finish()
        cat=self.request('/api/benefits/catalog')
        self.assertEqual(next(l for l in cat['levels'] if l['id']=='NORMAL')['price'],22200)
        replacement=self.start()
        cat=self.request('/api/benefits/catalog',server=replacement)
        self.assertEqual(next(l for l in cat['levels'] if l['id']=='NORMAL')['price'],22200)


if __name__ == '__main__':
    unittest.main()
