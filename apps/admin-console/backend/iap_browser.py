"""Admin-only browser entry point over the unchanged v8 business service.

Default IAP mode requires a verified assertion and an allowed Google user.
Explicit server configuration ADMIN_AUTH_MODE=PASSWORD omits that outer check.
Both modes retain console password/session/RBAC and all browser route/CSRF guards.
"""
import json
import os
import threading
import time
import urllib.request
from urllib.parse import urlsplit

import cloud_server
from common import ApiError, require
from google.auth import jwt

CERT_URL = 'https://www.gstatic.com/iap/verify/public_key'
_certs = None
_expires = 0
_cert_lock = threading.Lock()


def auth_mode():
    mode = os.environ.get('ADMIN_AUTH_MODE', 'IAP')
    require(mode in ('IAP', 'PASSWORD'), '后台登录模式配置无效', 503)
    return mode


def certificates():
    global _certs, _expires
    with _cert_lock:
        if _certs is None or time.monotonic() >= _expires:
            try:
                with urllib.request.urlopen(CERT_URL, timeout=5) as response:
                    raw = response.read(131073)
                if len(raw) > 131072:
                    raise ValueError('oversize certificate response')
                value = json.loads(raw)
                if not isinstance(value, dict) or not value:
                    raise ValueError('invalid certificate response')
            except Exception:
                raise ApiError(503, 'Google 登录验证暂时不可用，请稍后重试') from None
            _certs, _expires = value, time.monotonic() + 300
        return _certs


def verify_identity(assertion, audience, allowed_emails):
    require(bool(assertion) and len(assertion) <= 16384, '请先通过 Google 账号登录后台', 401)
    try:
        header = jwt.decode_header(assertion)
        require(header.get('alg') == 'ES256', 'Google 登录签名类型无效', 403)
    except Exception:
        raise ApiError(403, 'Google 登录签名格式无效') from None
    certs = certificates()
    try:
        claims = jwt.decode(assertion, certs=certs, audience=audience, clock_skew_in_seconds=30)
        valid = (claims.get('iss') == 'https://cloud.google.com/iap'
                 and bool(claims.get('sub'))
                 and claims.get('email', '').lower() in allowed_emails
                 and isinstance(claims.get('iat'), (int, float))
                 and isinstance(claims.get('exp'), (int, float))
                 and claims['exp'] - claims['iat'] <= 660
                 and claims['iat'] <= time.time() + 30)
    except Exception:
        valid = False
    require(valid, 'Google 登录身份无效或未获授权，请重新登录', 403)
    return claims['email']


class BrowserHandler(cloud_server.CloudHandler):
    def guard(self):
        super().guard()
        require(self.server.role == 'admin', '网页入口只提供管理服务', 404)
        path = urlsplit(self.path).path
        require(path in ('/', '/admin', '/admin/', '/static/admin.js', '/static/admin.css')
                or path.startswith('/api/local/admin/v2/'), '管理接口不存在', 404)
        if auth_mode() == 'IAP':
            audience = os.environ.get('IAP_EXPECTED_AUDIENCE', '')
            allowed = frozenset(v.strip().lower() for v in os.environ.get('IAP_ALLOWED_EMAILS', '').split(',') if v.strip())
            require(bool(audience) and bool(allowed), '后台网页登录尚未配置', 503)
            verify_identity(self.headers.get('X-Goog-IAP-JWT-Assertion', ''), audience, allowed)
        if self.command == 'POST':
            origin = self.headers.get('Origin', '')
            require(origin in self.server.origin_allowlist, '拒绝非本站操作，请刷新后台重试', 403)
            require(self.headers.get('Sec-Fetch-Site', 'same-origin') in ('same-origin', 'none'), '拒绝跨站操作', 403)
        # Remove all caller-supplied values before attaching the server-held secret.
        # This header never appears in browser assets, API responses or logs.
        if path.startswith('/api/local/admin/v2/'):
            del self.headers['X-Admin-Token']
            self.headers['X-Admin-Token'] = self.server.admin_token


def main():
    if os.environ.get('CLOUD_ROLE') != 'admin':
        raise RuntimeError('IAP browser entry point requires CLOUD_ROLE=admin')
    auth_mode()  # Fail closed at startup, including invalid/empty explicit modes.
    cloud_server.CloudHandler = BrowserHandler
    cloud_server.main()


if __name__ == '__main__':
    main()
