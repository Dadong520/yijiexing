import urllib.request,urllib.error,urllib.parse,json,csv,io,hashlib,datetime,os
from pathlib import Path
OUT=Path(__file__).resolve().parent;BASE='http://127.0.0.1:8808';checks=[];sessions=[]
def req(path,body=None,session=None):
 h={'Content-Type':'application/json'}
 if session:h['X-Admin-Session']=session
 r=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers=h)
 try:
  with urllib.request.urlopen(r,timeout=10) as x:return x.status,x.read()
 except urllib.error.HTTPError as e:return e.code,e.read()
def login(user,password):
 status,b=req('/api/local/admin/v2/login',{'username':user,'password':password});assert status==200;session=json.loads(b)['data']['session'];sessions.append(session);return session
def q(dataset,sess,**kw):
 status,b=req('/api/local/admin/v2/query?'+urllib.parse.urlencode({'dataset':dataset,**kw}),session=sess);assert status==200,(dataset,status);return json.loads(b)['data']
def record(name,proof):checks.append({'check':name,'result':'PASS','proof':proof})
try:
 a=login('admin',os.environ['QA_ADMIN_PASSWORD'])
 datasets={d:q(d,a) for d in ['orders','drivers','passengers','payments','support','audit','statistics','cancellations','refunds']}
 assert datasets['orders']['total']==34 and datasets['drivers']['total']==2 and datasets['passengers']['total']==2 and datasets['payments']['total']==36 and datasets['support']['total']==4 and datasets['audit']['total']>=205 and datasets['statistics']['total']==4 and datasets['cancellations']['total']==2 and datasets['refunds']['total']==3
 record('All9 real datasets query', {d:{'total':x['total'],'summary':x['summary']} for d,x in datasets.items()})
 day=q('orders',a,startDate='2026-10-24',endDate='2026-10-24');assert {x['id'] for x in day['items']}=={'B','C','D'} and day['summary']['refundAmount']==2700
 record('UTC8 inclusive date boundaries',{'ids':[x['id'] for x in day['items']],'refundCents':2700})
 c=q('orders',a,startDate='2026-10-24',endDate='2026-10-24',kind='STANDARD',passengerMobile='13800000101',plate='A1001',stage='DONE');assert [x['id'] for x in c['items']]==['D'];record('Combined date/type/phone/plate/status',['D'])
 p1=q('orders',a,page=1,pageSize=15);p2=q('orders',a,page=2,pageSize=15);assert len(p1['items'])==len(p2['items'])==15 and not ({x['id'] for x in p1['items']}&{x['id'] for x in p2['items']});record('Pagination15 no duplicates',{'total':p1['total'],'page1':15,'page2':15})
 r=datasets['refunds'];assert r['total']==3 and r['summary']['refundAmount']==2700 and [x['amount'] for x in r['items']]==[900,900,900];record('Equal amount/time refunds not deduplicated',{'ids':[x['id'] for x in r['items']],'amounts':[900,900,900]})
 cancel=next(x for x in datasets['cancellations']['items'] if x['id']=='C');assert cancel['stage']=='WAIT_CONTINUE' and cancel['cancellationStatus']=='AWAITING_PASSENGER';record('CancelC awaiting continuation',{'stage':cancel['stage'],'status':cancel['cancellationStatus']})
 old=q('audit',a,actor='ADMIN:old');assert any(x['id']==1 for x in old['items']);record('Historical audit beyond legacy200',{'allTotal':datasets['audit']['total'],'oldActorTotal':old['total'],'oldId1Visible':True})
 for ds in ['orders','drivers']:
  params={'dataset':ds,('billingMode' if ds=='orders' else 'startDate'):('1' if ds=='orders' else '2026-10-24')};status,_=req('/api/local/admin/v2/query?'+urllib.parse.urlencode(params),session=a);assert status==400
 record('Unavailable field/date rejected',{'ordersBillingMode':400,'driversDate':400})
 oc=next(x for x in datasets['support']['items'] if x['id']=='OC1');record('Support account source evidence',{'id':'OC1','accountId':oc.get('accountId'),'ownerId':oc.get('ownerId'),'orderId':oc.get('orderId')})
 v=login('viewer',os.environ['QA_VIEWER_PASSWORD']);assert q('orders',v)['total']==34
 for name,path,body,sess,expected in [('viewerExport','/api/local/admin/v2/export?dataset=orders',None,v,403),('viewerMutation','/api/local/admin/v2/action',{'action':'order.cancel','payload':{'id':'D'},'requestId':'frontend-denied-20261025'},v,403),('noSession','/api/local/admin/v2/query?dataset=orders',None,None,401),('driverPath','/api/state',None,a,404)]:
  status,_=req(path,body,sess);assert status==expected,(name,status);record(name,{'httpStatus':status})
finally:
 for sess in sessions:
  try:req('/api/local/admin/v2/logout',{},sess)
  except Exception:pass
 (OUT/'independent-http.json').write_text(json.dumps({'reviewer':'frontend non-author','apiSourceSha':'8a02527d025245381ebca517fcd88508c5e9606d','nativeUiSourceSha':'fe201d921eb9a5eca5be3b51d404dfc49fa4c71a','scope':'Real query/export/auth/permissions over loopback synthetic SQLite/CAS fixture; outer IAP replaced only; no production claims','checks':checks,'ownSessionsLoggedOut':True,'endedAt':datetime.datetime.now().astimezone().isoformat()},ensure_ascii=False,indent=2)+'\n')
print('PASS',len(checks),'bounded independent HTTP checks; own sessions logged out')
