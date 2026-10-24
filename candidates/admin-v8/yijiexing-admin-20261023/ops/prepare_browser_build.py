"""Prepare a small admin-only overlay; do not alter frozen shared backend code."""
from pathlib import Path
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
BASE = 'us-west1-docker.pkg.dev/yijiexing-driver-demo-1023/demo/service@sha256:7d71539b9ad631aa1eaed5af8e34c4648c1fb5953cd78c3737605ea40d5d43ac'


def main():
    target = ROOT / '.private/browser-build'
    target.mkdir(parents=True, exist_ok=True, mode=0o700)
    (target / 'static').mkdir(exist_ok=True)
    shutil.copy2(ROOT / 'backend/iap_browser.py', target / 'iap_browser.py')
    for name in ('admin.html', 'admin.js', 'admin.css'):
        text = (ROOT / 'web' / name).read_text()
        text = text.replace('通过本机安全代理连接独立 Google Cloud 环境。', '已通过 Google 安全入口连接独立云环境，请登录管理账号。')
        text = text.replace('本机入口与云服务已启动', '网络连接和云服务正常')
        text = text.replace('本机入口和网络', '网络连接和云服务')
        text = text.replace('本机入口是否运行及网络连接', '网络连接和 Google 登录状态')
        (target / 'static' / name).write_text(text)
    (target / 'Dockerfile').write_text(f'''FROM {BASE}
USER root
RUN pip install --no-cache-dir google-auth==2.40.3 cryptography==46.0.3
COPY --chown=demo:demo iap_browser.py /app/iap_browser.py
COPY --chown=demo:demo static /app/static
USER 10001:10001
CMD ["python", "iap_browser.py"]
''')
    files = {str(p.relative_to(target)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(target.rglob('*')) if p.is_file()}
    manifest = {'baseImage': BASE, 'scope':'driver-admin only; unchanged v8 business modules and database schema', 'files':files}
    (ROOT / 'delivery/browser-overlay-source-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(target)


if __name__ == '__main__': main()
