#!/usr/bin/env python3
"""Internal demo protocol base; cloud_server.py is the deployed cloud entry point."""
import argparse, base64, contextlib, csv, io, json, os, pathlib, secrets, signal, sys, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs,urlparse
from common import *
from domain import Service
from admin_console import AdminConsole, ConsoleLoginError
ROOT=pathlib.Path(__file__).resolve().parent

class LocalServer(ThreadingHTTPServer):
    daemon_threads=True
    def __init__(self,addr,service,admin_token):
        super().__init__(addr,Handler);self.service=service;self.admin_token=admin_token;self.stop_event=threading.Event()
    def service_close(self):
        self.stop_event.set();self.server_close()
        with self.service.lock:self.service.close()

class Handler(BaseHTTPRequestHandler):
    server_version='YijiexingLocal/0.2'
    def log_message(self,fmt,*args):
        # Never log request bodies, tokens, mobile numbers or file contents.
        sys.stderr.write(f'[{stamp()}] {self.command} {urlparse(self.path).path} {args[1] if len(args)>1 else ""}\n')
    def send(self,status,data,mime='application/json; charset=utf-8'):
        body=dumps(data).encode() if mime.startswith('application/json') else (data.encode() if isinstance(data,str) else data)
        self.send_response(status);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(body)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.send_header('Referrer-Policy','no-referrer');self.send_header('X-Frame-Options','DENY')
        if mime.startswith('text/html'):self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'")
        self.end_headers()
        try:self.wfile.write(body)
        except (BrokenPipeError,ConnectionResetError):pass
    def envelope(self,data):self.send(200,{'code':0,'data':data})
    def do_OPTIONS(self):self.send(405,{'code':405,'message':'跨域接口不开放'})
    def do_GET(self):self.dispatch()
    def do_POST(self):self.dispatch()
    def guard(self):
        host=self.headers.get('Host','').split(':')[0]
        require(host in ('127.0.0.1','localhost','10.0.2.2'),'仅云端演示主机名允许访问',403)
        origin=self.headers.get('Origin')
        if origin:require(origin in (f'http://127.0.0.1:{self.server.server_port}',f'http://localhost:{self.server.server_port}'),'不允许其他来源访问云端演示服务',403)
        require(self.client_address[0] in ('127.0.0.1','::1'),'服务只接受云端演示连接',403)
    def body(self):
        try:n=int(self.headers.get('Content-Length','0'))
        except ValueError:raise ApiError(400,'请求长度无效')
        require(0<n<=14*1024*1024,'请求体为空或超过14MB',413)
        require(self.headers.get('Content-Type','').startswith('application/json'),'仅接受JSON请求',415)
        try:p=json.loads(self.rfile.read(n))
        except Exception:raise ApiError(400,'JSON请求格式错误')
        require(isinstance(p,dict),'请求须为JSON对象');return p
    def dispatch(self):
        try:
            stop_after_reply=False
            self.guard();u=urlparse(self.path);path=u.path;query=parse_qs(u.query);p=self.body() if self.command=='POST' else {}
            if path in ('/','/admin','/admin/') and self.command=='GET':return self.send(200,(ROOT/'static/admin.html').read_text(),'text/html; charset=utf-8')
            if path in ('/static/admin.js','/static/admin.css') and self.command=='GET':return self.send(200,(ROOT/path[1:]).read_text(),'text/javascript; charset=utf-8' if path.endswith('.js') else 'text/css; charset=utf-8')
            with self.server.service.lock:
                svc=self.server.service
                try:
                    require(not self.server.stop_event.is_set(),'云端演示服务正在停止',503)
                    svc.run_scheduled()
                    if path=='/health' and self.command=='GET':data={'status':'ok','environment':'LOCAL_ISOLATED_SANDBOX','originalSpringBackend':False,'serverTime':stamp(),'storage':'SQLite'}
                    elif path=='/api/auth/code' and self.command=='POST':data=svc.code(p.get('mobile'))
                    elif path=='/api/auth/login' and self.command=='POST':data=svc.login(p)
                    elif path=='/api/passenger/auth/code' and self.command=='POST':data=svc.passenger_code(p.get('mobile'))
                    elif path=='/api/passenger/auth/login' and self.command=='POST':data=svc.passenger_login(p)
                    elif path.startswith('/api/passenger/'):
                        auth=self.headers.get('Authorization','');require(auth.startswith('Bearer '),'请先登录乘客账户',401);token=auth[7:];passenger=svc.passenger_authenticate(token)
                        if path=='/api/passenger/state' and self.command=='GET':data=svc.passenger_data(passenger)
                        elif path=='/api/passenger/auth/logout' and self.command=='POST':svc.db.execute('DELETE FROM passenger_sessions WHERE token=?',(token,));data={'loggedOut':True}
                        elif path=='/api/passenger/action' and self.command=='POST':
                            result=svc.passenger_action(passenger,p.get('action'),p.get('payload',{}),p.get('requestId'));data=svc.passenger_data(passenger);data['result']=result
                        elif re.fullmatch(r'/api/passenger/orders/[^/]+/(route|fees)',path) and self.command=='GET':
                            oid=path.split('/')[4];data=svc.passenger_route(passenger,oid) if path.endswith('/route') else svc.passenger_fees(passenger,oid)
                        elif re.fullmatch(r'/api/passenger/files/[^/]+',path) and self.command=='GET':
                            fid=path.split('/')[-1];mime,raw=svc.passenger_complaint_file(passenger,fid)
                            if query.get('encoding',[''])[0]=='base64':data={'fileId':fid,'mimeType':mime,'imageBase64':base64.b64encode(raw).decode()}
                            else:svc.db.commit();return self.send(200,raw,mime)
                        else:raise ApiError(404,'乘客接口不存在')
                    elif path.startswith('/api/local/admin/'):
                        token=self.headers.get('X-Admin-Token','');require(hmac.compare_digest(token,self.server.admin_token),'需要云端演示独立管理令牌',401)
                        actor=textfield(self.headers.get('X-Local-Actor','local-editor'),'云端演示操作身份',2,40)
                        if path.startswith('/api/local/admin/v2/'):
                            console=AdminConsole(svc);session=self.headers.get('X-Admin-Session','')
                            if path=='/api/local/admin/v2/login' and self.command=='POST':
                                try:data=console.login(p)
                                except ConsoleLoginError:svc.db.commit();raise
                            else:
                                user=console.authenticate(session)
                                if path=='/api/local/admin/v2/state' and self.command=='GET':data=console.state(user)
                                elif path=='/api/local/admin/v2/certificate-file' and self.command=='GET':data=console.certificate_file(user,query.get('driverId',[''])[0],query.get('type',[''])[0],query.get('side',['front'])[0])
                                elif path=='/api/local/admin/v2/action' and self.command=='POST':
                                    result=console.action(user,p.get('action'),p.get('payload',{}),p.get('requestId'));data={'result':result,'state':console.state(user)}
                                elif path=='/api/local/admin/v2/logout' and self.command=='POST':data=console.logout(session)
                                elif path=='/api/local/admin/v2/export' and self.command=='GET':
                                    data=console.export(user,query.get('dataset',['orders'])[0]);svc.db.commit();return self.send(200,data,'text/csv; charset=utf-8')
                                else:raise ApiError(404,'管理接口不存在')
                        elif path=='/api/local/admin/shutdown' and self.command=='POST':
                            data={'stopping':True,'environment':'LOCAL_ISOLATED_SANDBOX'};stop_after_reply=True
                        elif path=='/api/local/admin/state' and self.command=='GET':data=svc.admin_state()
                        elif path=='/api/local/admin/action' and self.command=='POST':data={'result':svc.admin_action(p.get('action'),p.get('payload',{}),actor),'admin':svc.admin_state()}
                        elif path.startswith('/api/local/admin/files/') and self.command=='GET':
                            f=svc.db.execute('SELECT * FROM files WHERE id=?',(path.split('/')[-1],)).fetchone();require(f is not None,'附件不存在',404)
                            data={'fileId':f['id'],'mimeType':f['mime'],'imageBase64':base64.b64encode(f['content']).decode()}
                        elif path=='/api/local/admin/export' and self.command=='GET':
                            rows=svc.admin_state()['cities'];buf=io.StringIO();w=csv.writer(buf);w.writerow(['city','cityName','region','normalPriceCents','vipPriceCents','source','version'])
                            for row in rows:w.writerow([row['id'],row['name'],row['regionName'],row['normalPrice'],row['vipPrice'],row['source'],row['version']])
                            svc.db.commit();return self.send(200,'\ufeff'+buf.getvalue(),'text/csv; charset=utf-8')
                        else:raise ApiError(404,'云端演示管理接口不存在')
                    else:
                        auth=self.headers.get('Authorization','');require(auth.startswith('Bearer '),'请先登录云端演示司机账户',401);token=auth[7:];driver=svc.authenticate(token)
                        if path=='/api/state' and self.command=='GET':data=svc.data(driver)
                        elif path=='/api/auth/logout' and self.command=='POST':svc.db.execute('DELETE FROM sessions WHERE token=?',(token,));data={'loggedOut':True}
                        elif path=='/api/action' and self.command=='POST':
                            if p.get('action')=='auth.logout':svc.db.execute('DELETE FROM sessions WHERE token=?',(token,));data={'loggedOut':True}
                            else:
                                result=svc.action(driver,p.get('action'),p.get('payload',{}),p.get('requestId'));data=svc.data(driver);data['result']=result
                        elif path=='/api/benefits/catalog' and self.command=='GET':data=svc.catalog(driver,query.get('city',[None])[0])
                        elif path=='/api/benefits/subscriptions' and self.command=='GET':data=svc.subscriptions(driver)
                        elif path.startswith('/api/benefits/orders/') and self.command=='GET':data={'order':svc.member_order(driver,path.split('/')[-1])}
                        elif path in ('/api/benefits/orders','/api/benefits/pay','/api/benefits/renewal','/api/benefits/invoices') and self.command=='POST':
                            result=svc.action(driver,{'/api/benefits/orders':'benefits.orders','/api/benefits/pay':'benefits.pay','/api/benefits/renewal':'benefits.renewal','/api/benefits/invoices':'benefits.invoices'}[path],p);data=svc.data(driver);data['result']=result
                        elif re.fullmatch(r'/api/orders/[^/]+/(route|fees|estimate)',path) and self.command=='GET':
                            oid=path.split('/')[3]
                            if path.endswith('/route'):data=svc.route(driver,oid)
                            else:
                                data=svc.fees(driver,oid)
                                if path.endswith('/estimate') and 'amount' in query:
                                    try:amount=int(query['amount'][0])
                                    except ValueError:raise ApiError(400,'报价须为整数分')
                                    cents(amount,'报价');data.update(fare=amount,income=amount)
                                data['fee']=0
                        elif path=='/api/location/latest' and self.command=='GET':
                            value=svc.account(driver)['location'];data={'location':json.loads(value) if value else None}
                        elif path=='/api/heatmap' and self.command=='GET':
                            svc.check_right(driver,'heatmap');data={'source':'synthetic','updatedAt':stamp(),'points':[{'lat':30.6645,'lng':111.3694,'weight':.9,'latitude':30.6645,'longitude':111.3694,'intensity':.9},{'lat':30.6687,'lng':111.3226,'weight':.7,'latitude':30.6687,'longitude':111.3226,'intensity':.7},{'lat':30.7066,'lng':111.2842,'weight':.6,'latitude':30.7066,'longitude':111.2842,'intensity':.6}],'notice':'云端演示合成订单热力，不代表实时供需'}
                        elif path=='/api/notifications' and self.command=='GET':
                            a=svc.account(driver);profile=json.loads(a['profile']);st=json.loads(a['state'])
                            items=list(profile['notices'])
                            for o in st['orders']:
                                if o['stage']!='AVAILABLE':items.append({'id':'ORDER-'+o['id']+'-'+o['stage'],'title':'订单 '+o['id'],'content':'当前状态：'+o['stage'],'createdAt':o.get('updatedAt',o['createdAt']),'orderId':o['id'],'type':'ORDER'})
                            for r in svc.db.execute('SELECT * FROM audits WHERE actor=? ORDER BY id DESC LIMIT 30',('DRIVER:'+driver,)):
                                items.append({'id':'AUDIT-'+str(r['id']),'title':'云端演示账户操作','content':r['action'],'createdAt':r['created'],'type':'ACCOUNT'})
                            data={'items':sorted(items,key=lambda i:i['createdAt'],reverse=True),'notifications':items,'sandbox':True}
                        elif path=='/api/ranking' and self.command=='GET':svc.check_right(driver,'ranking');data=json.loads(svc.account(driver)['profile'])['ranking']
                        elif path=='/api/local/sample-image-base64' and self.command=='GET':data={'imageBase64':base64.b64encode((ROOT/'static/sandbox-certificate-sample.png').read_bytes()).decode(),'sandbox':True}
                        elif path=='/api/local/sample-image' and self.command=='GET':svc.db.commit();return self.send(200,(ROOT/'static/sandbox-certificate-sample.png').read_bytes(),'image/png')
                        elif path.startswith('/api/files/') and self.command=='GET':
                            f=svc.db.execute('SELECT * FROM files WHERE id=? AND driver=?',(path.split('/')[-1],driver)).fetchone();require(f is not None,'文件不存在或不属于当前账户',404)
                            if query.get('encoding',[''])[0]=='base64':data={'fileId':f['id'],'mimeType':f['mime'],'imageBase64':base64.b64encode(f['content']).decode()}
                            else:svc.db.commit();return self.send(200,f['content'],f['mime'])
                        else:raise ApiError(404,'云端演示接口不存在')
                    svc.db.commit()
                except Exception:
                    svc.db.rollback();raise
            self.envelope(data)
            if stop_after_reply:threading.Thread(target=self.server.shutdown,daemon=True).start()
        except ApiError as e:self.send(e.status,{'code':e.code,'message':e.message,'details':e.details})
        except Exception as e:
            sys.stderr.write(f'[{stamp()}] local internal error: {type(e).__name__}: {e}\n')
            self.send(500,{'code':500,'message':'云端演示服务内部异常，请查看云端演示日志（未连接原测试环境）'})

def make_server(db_path,port=18765,admin_token=None):
    return LocalServer(('127.0.0.1',port),Service(db_path),admin_token or secrets.token_urlsafe(32))
def start_scheduler(server):
    def run():
        while not server.stop_event.wait(1):
            with server.service.lock:
                try:server.service.run_scheduled();server.service.db.commit()
                except Exception:server.service.db.rollback()
    thread=threading.Thread(target=run,name='local-publish-scheduler',daemon=True);thread.start();return thread

def main():
    parser=argparse.ArgumentParser(description='宜捷行云端演示隔离适配服务，禁止使用原后端配置')
    parser.add_argument('--port',type=int,default=18765);parser.add_argument('--db',type=pathlib.Path,default=ROOT/'data/local.sqlite');args=parser.parse_args()
    args.db.parent.mkdir(parents=True,exist_ok=True);os.chmod(args.db.parent,0o700)
    tokenfile=args.db.parent/'admin-token.txt'
    if tokenfile.exists():token=tokenfile.read_text().strip()
    else:
        token=secrets.token_urlsafe(32);fd=os.open(tokenfile,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'w') as out:out.write(token+'\n')
    server=make_server(args.db,args.port,token);os.chmod(args.db,0o600);start_scheduler(server)
    runtimefile=args.db.parent/'runtime.json';runtimefile.write_text(dumps({'pid':os.getpid(),'port':args.port,'servicePath':str(ROOT/'server.py'),'startedAt':stamp()}));os.chmod(runtimefile,0o600)
    print(f'宜捷行云端演示隔离服务已启动：http://127.0.0.1:{args.port}/admin\nSQLite: {args.db}\n管理令牌保存在: {tokenfile}\n合成演示数据独立保存在Google Cloud，不连接原测试环境；原 hsdd Spring 服务未启动。',flush=True)
    try:server.serve_forever(poll_interval=.5)
    except KeyboardInterrupt:pass
    finally:
        server.service_close()
        try:
            if json.loads(runtimefile.read_text()).get('pid')==os.getpid():runtimefile.unlink()
        except (FileNotFoundError,ValueError):pass
if __name__=='__main__':main()
