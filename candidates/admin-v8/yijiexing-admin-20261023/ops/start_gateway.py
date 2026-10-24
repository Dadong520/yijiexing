#!/usr/bin/env python3
"""Open the IAM/IAP-protected cloud console; no local credentials or server."""
import webbrowser

URL = 'https://driver-admin-491829048305.us-west1.run.app/'


def main():
    print('宜捷行后台：' + URL)
    print('使用 dadonggev587@gmail.com 通过 Google 登录，再输入后台管理账号和密码。')
    print('无需本机代理；其他设备可使用同一网址。首次访问可能需要等待冷启动。')
    return 0 if webbrowser.open(URL) else 1


if __name__ == '__main__':
    raise SystemExit(main())
