"""Coordinator non-author HTTP review, synthetic memory-only persistence."""
import os,sys,json,threading,tempfile,shutil,hashlib,urllib.request,urllib.error
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[3]
BACKEND=Path('/Users/dongge/.codex/tmp/yijiexing-trip-api-20261025/apps/cloud-service')
sys.path[:0]=[str(BACKEND),str(ROOT/'apps/admin-console/backend')]
import cloud_server,server,iap_browser
from tests.test_cloud import MemoryCAS
P='review-only-password-2026'; admin_secret='review-only-server-secret-at-least-32-characters'; checks=[]
with tempfile.TemporaryDirectory() as folder,patch.dict(os.environ,{'ADMIN_AUTH_MODE':'PASSWORD','ADMIN_BOOTSTRAP_PASSWORD':P}):
 folder=Path(folder);shutil.copytree(ROOT/'apps/admin-console/web',folder/'static')
 with patch.object(server,'ROOT',folder),patch.object(cloud_server,'ROOT',folder),patch.object(cloud_server,'CloudHandler',iap_browser.BrowserHandler):
  remote=MemoryCAS();srv=cloud_server.make_cloud_server(remote,folder/'state.sqlite',0,role='admin',admin_token=admin_secret,origin_allowlist=('https://review.invalid',))
  thread=threading.Thread(target=srv.serve_forever,kwargs={'poll_interval':.01},daemon=True);thread.start()
  opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
  def req(path,body=None,headers=None):
   h={'Content-Type':'application/json'}
   if body is not None:h['Origin']='https://review.invalid'
   h.update(headers or {})
   request=urllib.request.Request(f'http://127.0.0.1:{srv.server_port}'+path,data=None if body is None else json.dumps(body).encode(),headers=h)
   try:r=opener.open(request,timeout=5)
   except urllib.error.HTTPError as e:r=e
   with r:
    raw=r.read();return r.status,json.loads(raw) if r.headers.get('Content-Type','').startswith('application/json') else raw.decode()
  try:
   status,html=req('/');assert status==200 and '使用后台管理账号和密码登录' in html and 'Google 账号' not in html
   status,js=req('/static/admin.js');assert status==200 and hashlib.sha256(js.encode()).hexdigest()==hashlib.sha256((ROOT/'apps/admin-console/web/admin.js').read_bytes()).hexdigest();checks.append('anonymous real login assets, no Google step')
   for path in ['state','query?dataset=orders','export?dataset=orders']:
    status,_=req('/api/local/admin/v2/'+path);assert status==401,(path,status)
   status,_=req('/api/local/admin/v2/action',{'action':'user.save','payload':{},'requestId':'anonymous'});assert status==401
   for headers in [{'X-Admin-Token':admin_secret},{'X-Goog-IAP-JWT-Assertion':'forged','X-Admin-Session':'fake','Cookie':'session=admin'},{'X-Admin-Role':'SUPER_ADMIN'}]:
    status,_=req('/api/local/admin/v2/state',headers=headers);assert status==401
   for path in ['/api/local/admin/state','/api/local/admin/action','/api/local/admin/shutdown','/api/auth/login']:
    status,_=req(path);assert status==404
   checks.append('state/query/export/action protected; forged privileges and legacy routes blocked')
   status,_=req('/api/local/admin/v2/login',{'username':'admin','password':P},{'Origin':'https://other.invalid'});assert status==403
   status,_=req('/api/local/admin/v2/login',{'username':'admin','password':P},{'Sec-Fetch-Site':'cross-site'});assert status==403;checks.append('same-origin protection retained')
   status,result=req('/api/local/admin/v2/login',{'username':'admin','password':P});assert status==200
   token=result['data']['session'];h={'X-Admin-Session':token};status,data=req('/api/local/admin/v2/state',headers=h);assert status==200 and admin_secret not in json.dumps(data) and P not in json.dumps(data)
   status,_=req('/api/local/admin/v2/action',{'action':'user.save','payload':{'username':'review-reader','password':'review-reader-password','role':'VIEWER','enabled':True},'requestId':'review-reader-create'},h);assert status==200
   status,result=req('/api/local/admin/v2/login',{'username':'review-reader','password':'review-reader-password'});assert status==200
   vh={'X-Admin-Session':result['data']['session']};status,_=req('/api/local/admin/v2/action',{'action':'user.save','payload':{},'requestId':'reader-forbidden'},vh);assert status==403
   status,_=req('/api/local/admin/v2/logout',{},h);assert status==200;status,_=req('/api/local/admin/v2/state',headers=h);assert status==401;checks.append('password login, server secret undisclosed, viewer cannot write, logout revokes session')
   os.environ['ADMIN_AUTH_MODE']='INVALID';status,_=req('/');assert status==503
   os.environ.pop('ADMIN_AUTH_MODE');status,_=req('/',headers={'X-Admin-Auth-Mode':'PASSWORD'});assert status in (401,503);checks.append('unknown mode fails closed; default IAP cannot be disabled by caller headers')
  finally:srv.shutdown();thread.join();srv.service_close()
files=['apps/admin-console/backend/iap_browser.py','apps/admin-console/web/admin.html','apps/admin-console/web/admin.js']
out={'status':'PASS','reviewer':'coordinator (non-author server auth mode)','cases':checks,'memoryOnlyFixtures':True,'realCloudWrites':False,'files':{f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest() for f in files}}
(Path(__file__).parent/'independent-http.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps({'status':'PASS','cases':len(checks)}))
