"""Cloud-only entry point. It imports no local data or original Spring config."""
import hmac
import json
import os
import pathlib
import signal
import sys
import threading
from http.server import ThreadingHTTPServer
from urllib.parse import urlparse

from common import ApiError, require, stamp
from domain import Service
from passenger import PASSENGER_MOBILES
from distribution import APKUnavailable, GCSFixedAPK, INSTALL_HTML, PASSENGER_INSTALL_HTML, MAX_APK_BYTES, UNAVAILABLE_HTML
from server import Handler, ROOT
from snapshot import GCSObject, SnapshotConflict, SnapshotCoordinator, SnapshotUnavailable


class CloudService(Service):
    def __init__(self, db_path, allowed_mobiles, allowed_passenger_mobiles=PASSENGER_MOBILES):
        self.allowed_mobiles = frozenset(allowed_mobiles)
        self.allowed_passenger_mobiles = frozenset(allowed_passenger_mobiles)
        super().__init__(db_path)

    def mobile(self, mobile):
        value = super().mobile(mobile)
        require(value in self.allowed_mobiles, '此手机号未获演示访问授权', 403,
                {'reason': 'DEMO_MOBILE_NOT_ALLOWED'})
        return value

    def authenticate(self, token):
        driver = super().authenticate(token)
        self.mobile(self.account(driver)['mobile'])
        return driver

    def code(self, mobile, kind='LOGIN'):
        data = super().code(mobile, kind)
        data['message'] = '独立云演示验证码，没有发送真实短信'
        return data

    def action(self, driver, action, payload, request_id=None, sandbox_actor=False):
        require(action != 'local.reset', '云演示不开放清空账户操作', 403,
                {'reason': 'DEMO_OPERATION_DISABLED'})
        return super().action(driver, action, payload, request_id, sandbox_actor)

    def data(self, driver):
        data = super().data(driver)
        data['environment'] = {
            'name': 'Google Cloud 独立演示沙箱', 'sandbox': True,
            'originalSpringBackend': False, 'persistence': 'PRIVATE_GCS_SQLITE_CAS',
            'network': '独立云演示环境，不连接原测试环境',
            'scheduledJobs': '请求到达时补执行到期任务；无人使用时不常驻运行',
        }
        return data


class CloudServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, addr, coordinator, role, access_code='', admin_token='',
                 enabled=True, origin_allowlist=(), apk_source=None, passenger_apk_source=None):
        if role not in ('driver', 'admin'):
            raise ValueError('CLOUD_ROLE must be driver or admin')
        if role == 'driver' and len(access_code) < 24:
            raise ValueError('driver access secret must contain at least 24 characters')
        if role == 'admin' and len(admin_token) < 32:
            raise ValueError('admin token must contain at least 32 characters')
        super().__init__(addr, CloudHandler)
        self.coordinator = coordinator
        self.service = None
        self.role = role
        self.access_code = access_code if role == 'driver' else ''
        self.admin_token = admin_token if role == 'admin' else ''
        self.enabled = enabled
        self.origin_allowlist = frozenset(origin_allowlist)
        self.request_lock = threading.RLock()
        self.stop_event = threading.Event()
        self.apk_source = apk_source if role == 'driver' else None
        self.passenger_apk_source = passenger_apk_source if role == 'driver' else None

    def service_close(self):
        self.stop_event.set()
        self.server_close()
        with self.request_lock:
            self.coordinator.close()


class CloudHandler(Handler):
    server_version = 'YijiexingDemo/0.3'

    def do_HEAD(self):
        self.dispatch()

    def distribution(self, path):
        if self.server.role != 'driver' or self.command not in ('GET', 'HEAD') or path not in ('/download', '/download.apk', '/download-passenger', '/download-passenger.apk'):
            return Handler.send(self, 404, {'code': 404, 'message': '下载入口不存在'})
        if urlparse(self.path).query:
            return Handler.send(self, 400, {'code': 400, 'message': '下载入口不接受文件路径或地址参数'})
        passenger = path.startswith('/download-passenger')
        if path in ('/download', '/download-passenger'):
            body = (PASSENGER_INSTALL_HTML if passenger else INSTALL_HTML).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('Referrer-Policy', 'no-referrer')
            self.send_header('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'")
            self.end_headers()
            if self.command != 'HEAD':
                self.wfile.write(body)
            return
        try:
            source = self.server.passenger_apk_source if passenger else self.server.apk_source
            if source is None:
                raise APKUnavailable('no distribution object configured')
            stream = source.prepare(head=self.command == 'HEAD')
            if not 0 < stream.size <= MAX_APK_BYTES:
                raise APKUnavailable('APK outside size limit')
        except Exception as exc:
            sys.stderr.write(f'[{stamp()}] APK unavailable: {type(exc).__name__}\n')
            if self.command == 'HEAD':
                self.send_response(503); self.send_header('Content-Length', '0'); self.end_headers()
                return
            return Handler.send(self, 503, UNAVAILABLE_HTML, 'text/html; charset=utf-8')
        # Cloud Run buffers bounded HTTP/1 responses and caps them at 32 MiB.
        # Advertise true HTTP/1.1 chunk framing only for this APK route. Other
        # business responses keep their existing protocol and Content-Length.
        self.protocol_version = 'HTTP/1.1'
        self.close_connection = True
        self.send_response(200)
        self.send_header('Content-Type', 'application/vnd.android.package-archive')
        self.send_header('Content-Disposition', 'attachment; filename="yijiexing-passenger.apk"' if passenger else 'attachment; filename="yijiexing-cloud.apk"')
        if self.command == 'HEAD':
            self.send_header('Content-Length', str(stream.size))
        else:
            self.send_header('Transfer-Encoding', 'chunked')
        self.send_header('Connection', 'close')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        if self.command == 'HEAD':
            return
        sent = 0
        try:
            for chunk in stream.chunks:
                if not isinstance(chunk, bytes) or not chunk or sent + len(chunk) > stream.size:
                    raise APKUnavailable('invalid distribution length')
                self.wfile.write(f'{len(chunk):X}\r\n'.encode('ascii'))
                self.wfile.write(chunk)
                self.wfile.write(b'\r\n')
                self.wfile.flush()
                sent += len(chunk)
            if sent != stream.size:
                raise APKUnavailable('incomplete distribution stream')
            # The complete terminator is sent only after exact source length is
            # verified. An error closes an unterminated stream so a partial APK
            # cannot be mistaken for a successfully completed download.
            self.wfile.write(b'0\r\n\r\n')
            self.wfile.flush()
        except Exception as exc:
            self.close_connection = True
            sys.stderr.write(f'[{stamp()}] APK stream interrupted: {type(exc).__name__}\n')
        return

    def send(self, status, data, mime='application/json; charset=utf-8'):
        # The original handler commits SQLite before calling send(). Withhold all
        # bytes until GCS has conditionally persisted every change, including
        # business errors that increment OTP attempt counters.
        self.pending_response = (status, data, mime)

    def guard(self):
        path = urlparse(self.path).path
        if self.server.role == 'driver':
            if path in ('/', '/admin', '/admin/') or path.startswith('/static/') or path.startswith('/api/local/admin/'):
                raise ApiError(404, '接口不存在')
            require(self.server.enabled, '演示服务已暂停，请联系管理员开启', 503,
                    {'reason': 'DEMO_PAUSED'})
            supplied = self.headers.get('X-Demo-Access', '')
            require(bool(supplied), '请输入演示访问码', 403,
                    {'reason': 'DEMO_ACCESS_REQUIRED'})
            require(hmac.compare_digest(supplied.encode(), self.server.access_code.encode()),
                    '演示访问码无效', 403, {'reason': 'DEMO_ACCESS_INVALID'})
        else:
            require(path in ('/', '/admin', '/admin/', '/static/admin.js', '/static/admin.css')
                    or path.startswith('/api/local/admin/'), '管理服务不开放司机接口', 404)
            require(path != '/api/local/admin/shutdown', '云端不开放关闭接口', 404)
            # Cloud Run IAM is the outer boundary. The separate admin secret is
            # required again for all data APIs by the original handler.
        origin = self.headers.get('Origin')
        if origin:
            # Native clients have no Origin; admin gcloud proxy is loopback only.
            from urllib.parse import urlsplit
            parsed = urlsplit(origin)
            loopback = parsed.scheme == 'http' and parsed.hostname in ('127.0.0.1', 'localhost')
            require(loopback or origin in self.server.origin_allowlist, '不允许此网页来源访问演示服务', 403)

    def dispatch(self):
        self.pending_response = None
        path = urlparse(self.path).path
        if path.startswith('/download'):
            return self.distribution(path)
        if self.command == 'GET' and path == '/health':
            # A startup probe does not create synthetic accounts or touch GCS.
            return Handler.send(self, 200, {'code': 0, 'data': {
                'status': 'ok', 'environment': 'CLOUD_ISOLATED_SANDBOX',
                'role': self.server.role, 'originalSpringBackend': False,
            }})
        try:
            self.guard()
        except ApiError as exc:
            return Handler.send(self, exc.status, {'code': exc.code, 'message': exc.message, 'details': exc.details})
        # Static admin UI contains no token, account data, or database details.
        # Avoid GCS reads merely to serve IAM-protected assets.
        if self.server.role == 'admin' and path in ('/', '/admin', '/admin/', '/static/admin.js', '/static/admin.css') and self.command == 'GET':
            filename = 'static/admin.html' if path in ('/', '/admin', '/admin/') else path[1:]
            mime = 'text/html; charset=utf-8' if filename.endswith('.html') else ('text/javascript; charset=utf-8' if filename.endswith('.js') else 'text/css; charset=utf-8')
            return Handler.send(self, 200, (ROOT / filename).read_text(), mime)
        with self.server.request_lock:
            try:
                self.server.service = self.server.coordinator.begin()
                super().dispatch()
                self.server.coordinator.finish()
            except (SnapshotConflict, SnapshotUnavailable) as exc:
                # Never acknowledge a local commit that lacks a GCS CAS commit.
                self.server.coordinator.invalidate()
                self.server.service = None
                conflict = isinstance(exc, SnapshotConflict)
                self.pending_response = (503, {'code': 503,
                    'message': '演示数据正在同步，请稍后重试' if conflict else '演示持久化暂不可用，请稍后重试',
                    'details': {'reason': 'DEMO_STORAGE_CONFLICT' if conflict else 'DEMO_STORAGE_UNAVAILABLE', 'retryable': True}}, 'application/json; charset=utf-8')
                sys.stderr.write(f'[{stamp()}] cloud persistence unavailable: {type(exc).__name__}\n')
            except Exception as exc:
                self.server.coordinator.invalidate()
                self.server.service = None
                self.pending_response = (503, {'code': 503, 'message': '演示服务暂不可用，请稍后重试',
                    'details': {'reason': 'DEMO_STORAGE_UNAVAILABLE', 'retryable': True}}, 'application/json; charset=utf-8')
                sys.stderr.write(f'[{stamp()}] cloud request unavailable: {type(exc).__name__}\n')
            status, data, mime = self.pending_response or (500, {'code': 500, 'message': '演示服务未产生响应'}, 'application/json; charset=utf-8')
            return Handler.send(self, status, data, mime)


def make_cloud_server(remote, db_path, port=8080, role='driver', access_code='', admin_token='',
                      allowed_mobiles=('13800000001', '13800000002', '13800000003'),
                      enabled=True, host='127.0.0.1', max_bytes=32 * 1024 * 1024,
                      origin_allowlist=(), apk_source=None, allowed_passenger_mobiles=PASSENGER_MOBILES, passenger_apk_source=None):
    coordinator = SnapshotCoordinator(remote, db_path,
        lambda path: CloudService(path, allowed_mobiles, allowed_passenger_mobiles), max_bytes)
    return CloudServer((host, port), coordinator, role, access_code, admin_token, enabled, origin_allowlist, apk_source, passenger_apk_source)


def main():
    role = os.environ.get('CLOUD_ROLE', '')
    bucket = os.environ['SNAPSHOT_BUCKET']
    object_name = os.environ.get('SNAPSHOT_OBJECT', 'demo/state.sqlite')
    allowed = tuple(v.strip() for v in os.environ.get('DEMO_ALLOWED_MOBILES', '13800000001,13800000002,13800000003').split(',') if v.strip())
    require(bool(allowed), '演示手机号白名单不能为空', 500)
    access_code = os.environ.get('DEMO_ACCESS_CODE', '') if role == 'driver' else ''
    admin_token = os.environ.get('DEMO_ADMIN_TOKEN', '') if role == 'admin' else ''
    if role == 'driver' and os.environ.get('DEMO_ADMIN_TOKEN'):
        raise RuntimeError('driver role must not receive admin secret')
    remote = GCSObject(bucket, object_name)
    server = make_cloud_server(remote, '/tmp/yijiexing/state.sqlite',
        port=int(os.environ.get('PORT', '8080')), role=role, access_code=access_code,
        admin_token=admin_token, allowed_mobiles=allowed,
        allowed_passenger_mobiles=tuple(filter(None, os.environ.get('DEMO_PASSENGER_MOBILES', ','.join(PASSENGER_MOBILES)).split(','))),
        enabled=os.environ.get('DEMO_ENABLED', 'true').lower() == 'true', host='0.0.0.0',
        max_bytes=int(os.environ.get('MAX_SNAPSHOT_BYTES', str(32 * 1024 * 1024))),
        origin_allowlist=tuple(filter(None, os.environ.get('DEMO_ALLOWED_ORIGINS', '').split(','))),
        apk_source=GCSFixedAPK(remote.bucket) if role == 'driver' else None,
        passenger_apk_source=GCSFixedAPK(remote.bucket, passenger=True) if role == 'driver' else None)
    def stop(_signum, _frame):
        server.stop_event.set()
        threading.Thread(target=server.shutdown, daemon=True).start()
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    print(f'Independent cloud demo listening on PORT; role={role}; no original backend is contacted.', flush=True)
    try:
        server.serve_forever(poll_interval=0.25)
    finally:
        server.service_close()


if __name__ == '__main__':
    main()
