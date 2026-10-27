"""Local HTTP/SQLite/snapshot-CAS author tests; no live accounts or money."""
import concurrent.futures,json,unittest,urllib.error,urllib.request
from unittest.mock import patch
import test_admin_http as fixture
from admin_console import AdminConsole,ROLES
from common import dumps,stamp
from seed import initial_profile,initial_state

class OperationsDraftTests(unittest.TestCase):
    setUp=fixture.AdminHttpIntegration.setUp
    tearDown=fixture.AdminHttpIntegration.tearDown
    start=fixture.AdminHttpIntegration.start
    request=fixture.AdminHttpIntegration.request
    login=fixture.AdminHttpIntegration.login
    action=fixture.AdminHttpIntegration.action
    def draft(self,**extra):return {'module':'regional_service_fees','name':'服务费草稿','scope':'CITY','target':'420500','description':'不启用扣费','parameters':[{'name':'候选比例','value':'3','unit':'%'}],**extra}
    def mutate(self,fn):
        with self.admin.request_lock:
            svc=self.admin.coordinator.begin()
            try:r=fn(svc);svc.db.commit();self.admin.coordinator.finish();return r
            except Exception:svc.db.rollback();raise
    def business(self):return self.mutate(lambda svc:{t:[tuple(r) for r in svc.db.execute(f'SELECT * FROM {t} ORDER BY rowid')] for t in ('accounts','passengers','passenger_orders','configs','scheduled','approvals')})
    def seed_links(self):
        def seed(svc):
            profile=initial_profile('13800000001');profile['driver']['id']='OPS-D1'
            svc.db.execute('INSERT INTO accounts VALUES (?,?,?,?,?,?,?,?)',('OPS-D1','13800000001',dumps(initial_state()),dumps(profile),None,stamp(),2,None))
            p={'id':'OPS-P1','name':'本地乘客','mobile':'13800000101','city':'420500','cityName':'宜昌','favorites':[],'feedback':[]}
            svc.db.execute('INSERT INTO passengers VALUES (?,?,?)',(p['id'],p['mobile'],dumps(p)))
            o=svc.passenger_create_order(p['id'],{'origin':'本地起点','destination':'本地终点','originLat':30.6687,'originLng':111.3226,'destinationLat':30.6645,'destinationLng':111.3694})['order'];v=svc.shared_order(o['id']);v.update(driverId='OPS-D1',stage='PICKUP');svc.save_shared(v);return v['id']
        return self.mutate(seed)
    def test_catalog_is_32_and_initial_lists_are_empty(self):
        s=self.login()['session'];v=self.request('state',session=s)['operationDrafts']
        self.assertEqual(len(v['catalog']),32);self.assertEqual(len({x['id'] for x in v['catalog']}),32);self.assertEqual(v['drafts'],[]);self.assertEqual(v['records'],[]);self.assertTrue(v['draftOnly'])
        for x in v['catalog']:self.assertEqual(set(x),{'id','category','name','notice'});self.assertIn('仅保存',x['notice'])
    def test_draft_lifecycle_versions_persistence_and_no_business_change(self):
        s=self.login()['session'];before=self.business();a=self.action(s,'ops.draft.save',self.draft())['result']['draft'];self.assertEqual(a['version'],1)
        p=self.draft(id=a['id'],expectedVersion=1,name='第二稿');b=self.action(s,'ops.draft.save',p)['result']['draft'];self.assertEqual(b['version'],2);self.action(s,'ops.draft.save',p,expected=409)
        self.action(s,'ops.draft.archive',{'id':a['id'],'expectedVersion':True},expected=400)
        c=self.action(s,'ops.draft.archive',{'id':a['id'],'expectedVersion':2})['result']['draft'];self.assertEqual(c['version'],3);self.assertEqual(c['status'],'ARCHIVED')
        self.action(s,'ops.draft.save',self.draft(id=a['id'],expectedVersion=3),expected=409)
        fresh=self.start('admin');self.assertEqual(self.request('state',session=s,server=fresh)['operationDrafts']['drafts'],[c]);self.assertEqual(self.business(),before)
    def test_idempotent_write_one_row_one_audit_and_fingerprint(self):
        s=self.login()['session'];p=self.draft();a=self.action(s,'ops.draft.save',p,request_id='ops-once')['result'];self.assertEqual(a,self.action(s,'ops.draft.save',p,request_id='ops-once')['result'])
        self.action(s,'ops.draft.save',{**p,'name':'另一个'},request_id='ops-once',expected=409);v=self.request('state',session=s);self.assertEqual(len(v['operationDrafts']['drafts']),1);self.assertEqual(sum(x['action']=='ops.draft.save' for x in v['audit']),1)
    def test_manual_record_links_query_and_archive_without_ledger_change(self):
        s=self.login()['session'];oid=self.seed_links();before=self.business();p={'module':'order_splits','title':'人工说明','description':'不代表分账到账','orderId':oid,'driverId':'OPS-D1','passengerId':'OPS-P1','occurredAt':'2026-10-27T08:00:00+08:00'}
        a=self.action(s,'ops.record.add',p,request_id='record-once')['result']['record'];self.assertEqual(a['source'],'MANUAL');self.assertEqual(a,self.action(s,'ops.record.add',p,request_id='record-once')['result']['record'])
        self.action(s,'ops.record.update',{'id':a['id'],'description':'抹除'},expected=403)
        for field,value in [('orderId','missing'),('driverId','missing'),('occurredAt','not-a-time')]:self.action(s,'ops.record.add',{**p,field:value},expected=400 if field=='occurredAt' else 404)
        q=self.request('query?dataset=ops.records&module=order_splits&driverId=OPS-D1&pageSize=1',session=s);self.assertEqual(q['items'],[a]);self.assertEqual(q['total'],1)
        b=self.action(s,'ops.record.archive',{'id':a['id'],'expectedVersion':1})['result']['record'];self.assertEqual(b['status'],'ARCHIVED');self.assertEqual(b['description'],a['description']);self.assertEqual(self.business(),before)
        self.assertTrue(any(x['action']=='ops.record.archive' for x in self.request('state',session=s)['audit']))
    def test_invalid_fields_types_scopes_and_no_execution_routes(self):
        s=self.login()['session'];bad=[self.draft(enabled=True),self.draft(amount=100),self.draft(module='__import__'),self.draft(scope=[]),self.draft(target='missing'),self.draft(parameters={}),self.draft(parameters=[{'name':'x','value':10}]),self.draft(parameters=[{'name':'x','value':'1','compute':True}]),self.draft(parameters=[{'name':'x','value':'1'},{'name':'X','value':'2'}]),self.draft(description='x'*2001),self.draft(expectedVersion=True)]
        for p in bad:self.action(s,'ops.draft.save',p,expected=400)
        self.action(s,'ops.draft.publish',{},expected=403);self.assertEqual(self.request('state',session=s)['operationDrafts']['drafts'],[])
    def test_rbac_no_anonymous_or_public_driver_access(self):
        self.request('action',{'action':'ops.draft.save','payload':self.draft(),'requestId':'anonymous'},expected=401);s=self.login()['session']
        for role in ('VIEWER','OPERATOR','FINANCE','CONFIG_ADMIN'):
            username='ops_'+role.lower();self.action(s,'user.save',{'username':username,'password':fixture.PASSWORD,'role':role,'enabled':True});token=self.login(username)['session'];self.assertEqual(len(self.request('state',session=token)['operationDrafts']['catalog']),32);self.action(token,'ops.draft.save',self.draft(),expected=200 if role=='CONFIG_ADMIN' else 403)
        self.request('query?dataset=ops.records',server=self.driver,expected=404)
        def no_read(svc):
            c=AdminConsole(svc);u={'username':'limited','role':'LIMITED','permissions':['orders.read']}
            with patch.dict(ROLES,{'LIMITED':{'orders.read'}}):
                self.assertEqual(c.state(u)['operationDrafts']['catalog'],[])
                with self.assertRaises(Exception) as e:c.query(u,{'dataset':['ops.records']})
                self.assertEqual(e.exception.status,403)
        self.mutate(no_read)
    def test_cas_failure_and_lost_ack_retry_no_duplicate(self):
        s=self.login()['session'];self.remote.fail_write=True;self.action(s,'ops.draft.save',self.draft(),request_id='failed',expected=503);self.remote.fail_write=False;self.assertEqual(self.request('state',session=s)['operationDrafts']['drafts'],[])
        self.remote.uncertain_write=True;self.action(s,'ops.draft.save',self.draft(),request_id='lost',expected=503);a=self.action(s,'ops.draft.save',self.draft(),request_id='lost')['result']['draft'];self.assertEqual(self.request('state',session=s)['operationDrafts']['drafts'],[a])
    def test_concurrent_updates_allow_one_version_writer(self):
        s=self.login()['session'];a=self.action(s,'ops.draft.save',self.draft())['result']['draft']
        def send(i):
            body={'action':'ops.draft.save','payload':self.draft(id=a['id'],expectedVersion=1,name='候选'+str(i)),'requestId':'race-'+str(i)};req=urllib.request.Request(f'http://127.0.0.1:{self.admin.server_port}{fixture.PREFIX}action',data=json.dumps(body).encode(),headers={'Content-Type':'application/json','X-Admin-Token':fixture.OUTER,'X-Admin-Session':s})
            try:
                with urllib.request.urlopen(req,timeout=20) as r:return r.status
            except urllib.error.HTTPError as e:e.read();status=e.code;e.close();return status
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:r=list(pool.map(send,[1,2]))
        self.assertEqual(sorted(r),[200,409]);self.assertEqual(self.request('state',session=s)['operationDrafts']['drafts'][0]['version'],2)
    def test_query_filter_pagination_and_bad_queries(self):
        s=self.login()['session']
        for i in range(3):self.action(s,'ops.draft.save',self.draft(name='草稿'+str(i)))
        q=self.request('query?dataset=ops.drafts&page=2&pageSize=2',session=s);self.assertEqual(q['total'],3);self.assertEqual(len(q['items']),1)
        for q in ('dataset=ops.drafts&module=unknown','dataset=ops.records&pageSize=1000','dataset=ops.drafts&page=1&page=2','dataset=ops.drafts&enabled=true','dataset=ops.drafts&driverId=D1'):self.request('query?'+q,session=s,expected=400)
    def test_driver_and_passenger_state_do_not_include_manual_admin_notes(self):
        s=self.login()['session'];self.action(s,'ops.record.add',{'module':'phone_booking','title':'手工记录','description':'PRIVATE-ADMIN-NOTE-MARKER'})
        def public(path,body=None,token=None):
            h={'Content-Type':'application/json','X-Demo-Access':fixture.ACCESS}
            if token:h['Authorization']='Bearer '+token
            req=urllib.request.Request(f'http://127.0.0.1:{self.driver.server_port}{path}',data=json.dumps(body).encode() if body else None,headers=h)
            with urllib.request.urlopen(req,timeout=20) as r:return json.loads(r.read())['data']
        for prefix,mobile in [('/api','13800000001'),('/api/passenger','13800000101')]:
            code=public(prefix+'/auth/code',{'mobile':mobile})['verificationCode'];logged=public(prefix+'/auth/login',{'mobile':mobile,'code':code});raw=json.dumps(public(prefix+'/state',token=logged['token']));self.assertNotIn('operationDrafts',raw);self.assertNotIn('PRIVATE-ADMIN-NOTE-MARKER',raw)
