#!/usr/bin/env python3
"""Start the local-only gateway independently of the invoking terminal."""
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
URL = 'http://127.0.0.1:8790'
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def ready():
    try:
        with OPENER.open(URL + '/api/local/gateway/status', timeout=1) as response:
            value = json.load(response)
        return value.get('service') == 'yijiexing-local-admin-gateway' and value.get('ready') is True
    except (OSError, ValueError, urllib.error.URLError):
        return False


def main():
    if ready():
        print('后台已运行：' + URL)
        return 0
    private = ROOT / '.private'
    private.mkdir(exist_ok=True, mode=0o700)
    log_path = private / 'gateway.log'
    fd = os.open(log_path, os.O_CREAT | os.O_WRONLY | os.O_APPEND, 0o600)
    os.chmod(log_path, 0o600)
    with os.fdopen(fd, 'ab') as log:
        process = subprocess.Popen(
            [sys.executable, str(ROOT / 'ops' / 'serve.py')],
            cwd=ROOT, stdin=subprocess.DEVNULL, stdout=log, stderr=log,
            start_new_session=True, close_fds=True,
        )
    for _ in range(50):
        if ready():
            pid_path = private / 'gateway.pid'
            pid_path.write_text(str(process.pid))
            os.chmod(pid_path, 0o600)
            print('后台已启动：' + URL + '；关闭启动窗口后仍可访问。')
            return 0
        if process.poll() is not None:
            break
        time.sleep(0.1)
    print('后台未能启动，请查看本机 .private/gateway.log（可能有其他程序占用8790端口）。', file=sys.stderr)
    return 1


if __name__ == '__main__':
    raise SystemExit(main())
