"""Local HTTP fixtures only; no snapshot contents or live credentials loaded."""
import contextlib, io, json, pathlib, sys
source = pathlib.Path('/Users/dongge/.codex/tmp/yijiexing-workbuddy-20261103')
sys.path.insert(0, str(source/'apps/cloud-service/tests'))
from test_passenger import PassengerIntegration
checks=[]
t=PassengerIntegration(); captured=io.StringIO()
with contextlib.redirect_stdout(captured),contextlib.redirect_stderr(captured):
 t.setUp()
 try:
  p,d=t.setup_pair(); pt,dt=p['token'],d['token']; order=t.create(pt); oid=order['id']
  t.da(dt,'order.accept',{'id':oid})
  po=t.get_order(pt,oid); do=next(o for o in t.request('/api/state',token=dt)['state']['orders'] if o['id']==oid)
  assert po['stage']==do['stage']=='PICKUP'
  assert po['fare']==do['fare']==order['fare'] and po['estimatedFare']==order['estimatedFare']
  checks.append('same order PICKUP and integer fare equal across authenticated passenger/driver HTTP state')
  t.pa(pt,'order.cancel',{'id':oid,'reason':'同单取消测试'})
  po=t.get_order(pt,oid); do=next(o for o in t.request('/api/state',token=dt)['state']['orders'] if o['id']==oid)
  assert po['stage']==do['stage']=='CANCELLED'
  checks.append('assigned STANDARD passenger cancellation projects CANCELLED to owning driver')
  order=t.create(pt); oid=order['id']; t.pa(pt,'order.cancel',{'id':oid,'reason':'未分配取消测试'})
  assert t.get_order(pt,oid)['stage']=='CANCELLED'
  assert not any(o['id']==oid for o in t.request('/api/state',token=dt)['state']['orders'])
  checks.append('unassigned cancellation removes same order from driver hall')
 finally:
  t.tearDown()
print(json.dumps({'status':'PASS','checks':checks,'environment':'localhost ephemeral HTTP + SQLite + MemoryCAS; no cloud access'},ensure_ascii=False,indent=2))
