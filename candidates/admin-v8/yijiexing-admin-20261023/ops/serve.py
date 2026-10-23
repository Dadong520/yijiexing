#!/usr/bin/env python3
"""Loopback console gateway; credentials remain in the local process."""
import argparse
import json
import pathlib
import shutil
import ssl
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = pathlib.Path(__file__).resolve().parents[1]
CLOUD = ROOT.parent / 'yijiexing-cloud-20261023'
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

class Gateway(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, body, mime='application/json; charset=utf-8'):
        if not isinstance(body, bytes):
            body = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'no-referrer')
        self.send_header('Content-Security-Policy', "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.handle_request()

    def do_POST(self):
        self.handle_request()

    def handle_request(self):
        allowed = {f'127.0.0.1:{self.server.server_port}', f'localhost:{self.server.server_port}'}
        if self.headers.get('Host') not in allowed:
            return self.reply(403, {'message': '仅允许本机访问'})
        origin = self.headers.get('Origin')
        if origin and origin not in {'http://' + value for value in allowed}:
            return self.reply(403, {'message': '拒绝跨站访问'})
        if self.headers.get('Sec-Fetch-Site') == 'cross-site':
            return self.reply(403, {'message': '拒绝跨站访问'})
        path = urllib.parse.urlsplit(self.path).path
        if path == '/api/local/gateway/status' and self.command == 'GET':
            return self.reply(200, {'service': 'yijiexing-local-admin-gateway', 'ready': True})
        if path.startswith('/api/local/admin/v2/'):
            if self.command == 'POST' and 'application/json' not in self.headers.get('Content-Type', ''):
                return self.reply(415, {'message': '请求必须使用 JSON'})
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 <= length <= 1048576:
                    return self.reply(413, {'message': '请求过大'})
                data = self.rfile.read(length) if self.command == 'POST' else None
                with self.server.token_lock:
                    if time.time() >= self.server.token_expiry:
                        result = subprocess.run([self.server.gcloud, 'auth', 'print-identity-token'], capture_output=True, text=True, timeout=25, check=True)
                        self.server.identity = result.stdout.strip()
                        self.server.token_expiry = time.time() + 2400
                headers = {'Authorization': 'Bearer ' + self.server.identity, 'X-Admin-Token': self.server.admin_token, 'Content-Type': 'application/json'}
                if self.headers.get('X-Admin-Session'):
                    headers['X-Admin-Session'] = self.headers['X-Admin-Session']
                request = urllib.request.Request(self.server.upstream + self.path, data=data, headers=headers, method=self.command)
                try:
                    response = self.server.opener.open(request, timeout=60)
                except urllib.error.HTTPError as error:
                    response = error
                with response:
                    payload = response.read(8 * 1024 * 1024)
                    mime = response.headers.get('Content-Type', 'application/json')
                    if response.code == 403 and 'application/json' not in mime:
                        return self.reply(403, {'message': 'Google Cloud IAM 拒绝访问，请使用已获授权的本机云账号'})
                    return self.reply(response.code, payload, mime)
            except Exception:
                return self.reply(503, {'message': '云后台暂时无法连接，请检查本机 Google Cloud 登录状态及服务是否暂停后重试'})
        if self.command != 'GET':
            return self.reply(404, {'message': '接口不存在'})
        name = {'/': 'admin.html', '/admin': 'admin.html', '/admin.html': 'admin.html', '/static/admin.css': 'admin.css', '/static/admin.js': 'admin.js', '/index.html': 'admin.html', '/admin.js': 'admin.js', '/admin.css': 'admin.css', '/app.js': 'app.js', '/styles.css': 'styles.css'}.get(path)
        if not name or not (ROOT / 'web' / name).is_file():
            return self.reply(404, {'message': '页面不存在'})
        mime = 'text/html' if name.endswith('.html') else ('text/javascript' if name.endswith('.js') else 'text/css')
        self.reply(200, (ROOT / 'web' / name).read_bytes(), mime + '; charset=utf-8')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=8790)
    args = parser.parse_args()
    config = json.loads((CLOUD / 'ops/deployment.json').read_text())
    if config['project'] != 'yijiexing-driver-demo-1023':
        raise SystemExit('拒绝非独立体验环境')
    upstream = config['urls']['admin']
    parsed = urllib.parse.urlsplit(upstream)
    if parsed.scheme != 'https' or not parsed.hostname.startswith('driver-admin-') or not parsed.hostname.endswith('.run.app') or parsed.path or parsed.query:
        raise SystemExit('管理服务地址不在允许范围')
    server = ThreadingHTTPServer(('127.0.0.1', args.port), Gateway)
    server.upstream = upstream
    server.admin_token = (CLOUD / '.private/admin-token.txt').read_text().strip()
    server.token_lock = threading.Lock()
    server.token_expiry = 0
    server.gcloud = shutil.which('gcloud') or next((str(p) for p in [pathlib.Path('/opt/homebrew/bin/gcloud'), pathlib.Path.home() / 'google-cloud-sdk/bin/gcloud', pathlib.Path('/usr/local/bin/gcloud')] if p.is_file()), 'gcloud')
    server.opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile='/etc/ssl/cert.pem')))
    print(f'后台访问地址：http://127.0.0.1:{args.port}；按 Ctrl+C 关闭本机入口。', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == '__main__':
    main()
