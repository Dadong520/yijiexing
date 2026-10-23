#!/usr/bin/env python3
"""Start or reuse this exact offline preview, on loopback only."""
from pathlib import Path
import argparse, functools, hashlib, http.server, json, os, socket, subprocess, sys, time, urllib.request
ROOT=Path(__file__).resolve().parent
HOST='127.0.0.1'
PORT=8788
URL=f'http://{HOST}:{PORT}/'
IDENTITY=hashlib.sha256(str(ROOT).encode()).hexdigest()
def read_health():
    try:
        with urllib.request.urlopen(URL+'__preview_health__',timeout=0.8) as response:
            return json.load(response)
    except Exception:
        return None
def occupied():
    with socket.socket() as sock:
        return sock.connect_ex((HOST,PORT))==0
def is_ours(health):
    return isinstance(health,dict) and health.get('identity')==IDENTITY and health.get('root')==str(ROOT) and health.get('service')=='yijiexing-offline-visual-preview'
def serve():
    class Handler(http.server.SimpleHTTPRequestHandler):
        def do_GET(self):
            if self.path=='/__preview_health__':
                body=json.dumps({'service':'yijiexing-offline-visual-preview','identity':IDENTITY,'root':str(ROOT),'pid':os.getpid(),'readonly':True}).encode()
                self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(body)
                return
            super().do_GET()
    handler=functools.partial(Handler,directory=str(ROOT))
    server=http.server.ThreadingHTTPServer((HOST,PORT),handler)
    server.serve_forever()
def start(open_browser=False):
    health=read_health()
    if is_ours(health):
        subprocess.run([sys.executable,str(ROOT/'build.py')],check=True,cwd=ROOT)
        print('已复用本目录的离线视觉预览：'+URL)
    else:
        if occupied():
            raise SystemExit('无法启动：127.0.0.1:8788 已被其他服务占用，或无法确认属于本预览目录。未停止或替换任何服务。')
        subprocess.run([sys.executable,str(ROOT/'build.py')],check=True,cwd=ROOT)
        with (ROOT/'preview-server.log').open('ab') as log:
            child=subprocess.Popen([sys.executable,str(Path(__file__).resolve()),'--serve'],cwd=ROOT,stdin=subprocess.DEVNULL,stdout=log,stderr=log,start_new_session=True,close_fds=True)
        for _ in range(30):
            if is_ours(read_health()):
                break
            if child.poll() is not None:
                raise SystemExit('预览服务未启动，请查看 preview-server.log；没有停止其他服务。')
            time.sleep(0.1)
        else:
            raise SystemExit('未确认预览服务已就绪，请查看 preview-server.log。')
        print('离线视觉预览已独立启动：'+URL)
    if open_browser:
        subprocess.run(['open',URL],check=False)
    print('仅监听本机127.0.0.1；浏览器只读预览，非微信运行；没有设置开机常驻。')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--serve',action='store_true');parser.add_argument('--open',action='store_true');args=parser.parse_args()
    serve() if args.serve else start(args.open)
