"""Published operations: real local HTTP, SQLite, restarts and snapshot CAS."""
import concurrent.futures
import datetime as dt
import json
import threading
import unittest
import urllib.error
import urllib.request
from unittest.mock import patch
import test_admin_http as fixture
import test_operations_drafts as drafts_fixture
from common import now, stamp


class LiveOperationsTests(unittest.TestCase):
    setUp = fixture.AdminHttpIntegration.setUp
    tearDown = fixture.AdminHttpIntegration.tearDown
    start = fixture.AdminHttpIntegration.start
    request = fixture.AdminHttpIntegration.request
    login = fixture.AdminHttpIntegration.login
    action = fixture.AdminHttpIntegration.action
    mutate = drafts_fixture.OperationsDraftTests.mutate

    def public(self, path, body=None, token=None, expected=200, server=None):
        headers = {'Content-Type': 'application/json'}
        if token: headers['Authorization'] = 'Bearer '+token
        req = urllib.request.Request(f'http://127.0.0.1:{(server or self.driver).server_port}'+path,
            data=json.dumps(body).encode() if body is not None else None, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=20) as r: status, raw = r.status, r.read()
        except urllib.error.HTTPError as e:
            status, raw = e.code, e.read(); e.close()
        self.assertIn(status, expected if isinstance(expected, tuple) else (expected,), raw.decode()[:1500])
        value = json.loads(raw)
        return value.get('data', value), status

    def user(self, mobile='13800000101', driver=False):
        prefix = '/api' if driver else '/api/passenger'
        code = self.public(prefix+'/auth/code', {'mobile':mobile})[0]['verificationCode']
        return self.public(prefix+'/auth/login', {'mobile':mobile,'code':code})[0]

    def pa(self, token, action, payload, rid=None, **kw):
        self.rid += 1
        return self.public('/api/passenger/action', {'action':action,'payload':payload,
            'requestId':rid or 'public-'+str(self.rid)}, token, **kw)[0]

    def da(self, token, action, payload, **kw):
        self.rid += 1
        return self.public('/api/action', {'action':action,'payload':payload,
            'requestId':'driver-'+str(self.rid)}, token, **kw)[0]

    def ps(self, token, client='MINIPROGRAM', **kw):
        return self.public('/api/passenger/state?client='+client, token=token, **kw)[0]

    def company(self, session, **kw):
        return self.action(session, 'ops.company.save', {'name':'测试出租车公司',
            'supportPhone':'0717-1234567','enabled':True,**kw})['result']['company']

    def announcement(self, session, publish=True, **kw):
        p = {'title':'测试公告','content':'纯文本消息 <script>不执行</script>',
            'scope':'NATIONAL','target':'CN','startAt':stamp(now()-dt.timedelta(hours=1)),
            'endAt':stamp(now()+dt.timedelta(hours=1)), **kw}
        a = self.action(session,'ops.announcement.save',p)['result']['announcement']
        if publish:
            a = self.action(session,'ops.announcement.publish',{'id':a['id'],'expectedVersion':a['version']})['result']['announcement']
        return a

    def features(self, session, features, scope='NATIONAL', target='CN', expected_version=0, publish=True):
        p = {'scope':scope,'target':target,'features':features,'expectedVersion':expected_version}
        v = self.action(session,'ops.passengerFeatures.save',p)['result']['passengerFeatureConfig']
        if publish:
            v = self.action(session,'ops.passengerFeatures.publish',{'scope':scope,'target':target,'expectedVersion':v['version']})['result']['passengerFeatureConfig']
        return v

    def order_payload(self, **kw):
        return {'kind':'STANDARD','origin':'合成测试起点','destination':'合成测试终点',
            'originLat':30.6687,'originLng':111.3226,'destinationLat':30.6645,'destinationLng':111.3694,**kw}

    def test_company_explicit_binding_public_selection_and_restart(self):
        s=self.login()['session'];p=self.user();other=self.user('13800000102');d=self.user('13800000001',True)
        self.assertEqual(self.ps(p['token'])['operations']['company']['status'],'UNCONFIGURED')
        c=self.company(s);self.pa(p['token'],'ops.company.select',{'companyId':c['id']})
        self.assertEqual(self.ps(other['token'])['operations']['company']['status'],'UNCONFIGURED')
        self.action(s,'ops.driverCompany.set',{'driverId':d['profile']['driver']['id'],'companyId':c['id'],'expectedVersion':0})
        self.assertEqual(self.public('/api/state',token=d['token'])[0]['operations']['company']['id'],c['id'])
        fresh=self.start('driver');self.assertEqual(self.ps(p['token'],server=fresh)['operations']['company']['id'],c['id'])
        self.company(s,id=c['id'],expectedVersion=1,enabled=False)
        self.assertEqual(self.ps(p['token'])['operations']['company']['status'],'DISABLED')
        self.assertIsNone(self.ps(p['token'])['operations']['company']['supportPhone'])
        self.pa(other['token'],'ops.company.select',{'companyId':c['id']},expected=404)
        self.pa(other['token'],'ops.driverCompany.set',{'driverId':d['profile']['driver']['id'],'companyId':c['id']},expected=404)

    def test_new_order_company_frozen_and_other_passenger_order_is_private(self):
        s=self.login()['session'];p=self.user();other=self.user('13800000102');d=self.user('13800000001',True)
        did=d['profile']['driver']['id'];a=self.company(s);b=self.company(s,name='第二家公司')
        self.action(s,'ops.driverCompany.set',{'driverId':did,'companyId':a['id'],'expectedVersion':0})
        self.da(d['token'],'driver.online',{'online':True})
        o=self.pa(p['token'],'order.create',self.order_payload())['result']['order']
        self.da(d['token'],'order.accept',{'id':o['id']})
        self.action(s,'ops.driverCompany.set',{'driverId':did,'companyId':b['id'],'expectedVersion':1})
        current=self.ps(p['token']);self.assertEqual(current['orders'][0]['company']['id'],a['id'])
        self.assertEqual(current['operations']['company']['id'],a['id'])
        self.public('/api/passenger/orders/'+o['id']+'/fees',token=other['token'],expected=404)
        self.assertNotIn(o['id'],json.dumps(self.ps(other['token'])))

    def test_admin_rbac_validation_versions_and_finance_unchanged(self):
        s=self.login()['session'];d=self.user('13800000001',True)
        before=self.public('/api/state',token=d['token'])[0]['state']
        c=self.company(s)
        self.action(s,'ops.company.save',{**c,'expectedVersion':0},expected=400)
        self.action(s,'ops.company.save',{'id':c['id'],'expectedVersion':0,'name':'冲突','supportPhone':None,'enabled':True},expected=409)
        for phone in ('tel:bad','---','1',123):
            self.action(s,'ops.company.save',{'name':'公司','supportPhone':phone,'enabled':True},expected=400)
        self.action(s,'ops.driverCompany.set',{'driverId':'missing','companyId':c['id'],'expectedVersion':0},expected=404)
        for role in ('VIEWER','FINANCE','OPERATOR','CONFIG_ADMIN'):
            name='live_'+role.lower();self.action(s,'user.save',{'username':name,'password':fixture.PASSWORD,'role':role,'enabled':True})
            rs=self.login(name)['session']
            self.action(rs,'ops.company.save',{'name':'权限测试','supportPhone':None,'enabled':True},expected=200 if role=='CONFIG_ADMIN' else 403)
        after=self.public('/api/state',token=d['token'])[0]['state']
        for key in ('balance','entries','todayIncome'):self.assertEqual(before[key],after[key])
        self.assertEqual(len(self.request('state',session=s)['operationDrafts']['catalog']),32)

    def test_announcement_read_is_non_consuming_and_once_per_user_version(self):
        s=self.login()['session'];p=self.user();p2=self.user('13800000102');a=self.announcement(s)
        version=a['published']['version'];payload={'id':a['id'],'version':version,'client':'MINIPROGRAM'}
        for _ in range(3):self.assertEqual(len(self.ps(p['token'])['operations']['announcements']),1)
        self.assertEqual(self.mutate(lambda x:x.db.execute('SELECT count(*) FROM live_announcement_acks').fetchone()[0]),0)
        self.assertTrue(self.pa(p['token'],'ops.announcement.ack',payload,rid='show-once')['result']['firstDisplay'])
        self.assertFalse(self.pa(p['token'],'ops.announcement.ack',payload,rid='show-once')['result']['firstDisplay'])
        self.assertFalse(self.pa(p['token'],'ops.announcement.ack',payload)['result']['firstDisplay'])
        self.assertTrue(self.pa(p2['token'],'ops.announcement.ack',payload)['result']['firstDisplay'])
        self.assertEqual(self.ps(p['token'])['operations']['announcements'],[])
        self.assertEqual(self.ps(p['token'],server=self.start('driver'))['operations']['announcements'],[])

    def test_announcement_audience_region_publish_edit_and_withdraw(self):
        s=self.login()['session'];p=self.user();a=self.announcement(s,scope='CITY',target='420500')
        self.assertEqual(self.ps(p['token'],'PASSENGER_ANDROID')['operations']['announcements'],[])
        content={**a['draft'],'id':a['id'],'expectedVersion':a['version'],'title':'新草稿'}
        draft=self.action(s,'ops.announcement.save',content)['result']['announcement']
        self.assertEqual(self.ps(p['token'])['operations']['announcements'][0]['title'],'测试公告')
        published=self.action(s,'ops.announcement.publish',{'id':a['id'],'expectedVersion':draft['version']})['result']['announcement']
        self.pa(p['token'],'ops.announcement.ack',{'id':a['id'],'version':a['published']['version'],'client':'MINIPROGRAM'},expected=409)
        self.pa(p['token'],'profile.update',{'city':'420100'})
        self.assertEqual(self.ps(p['token'])['operations']['announcements'],[])
        self.pa(p['token'],'ops.announcement.ack',{'id':a['id'],'version':published['version'],'client':'MINIPROGRAM'},expected=403)
        self.pa(p['token'],'profile.update',{'city':'420500'})
        self.action(s,'ops.announcement.withdraw',{'id':a['id'],'expectedVersion':published['version']})
        self.assertEqual(self.ps(p['token'])['operations']['announcements'],[])
        self.pa(p['token'],'ops.announcement.ack',{'id':a['id'],'version':published['version'],'client':'MINIPROGRAM'},expected=409)

    def test_announcement_start_inclusive_end_exclusive_and_bad_inputs(self):
        s=self.login()['session'];p=self.user();start=now()+dt.timedelta(minutes=1);end=start+dt.timedelta(minutes=1)
        a=self.announcement(s,startAt=stamp(start),endAt=stamp(end))
        self.assertEqual(self.ps(p['token'])['operations']['announcements'],[])
        with patch('live_operations.now',return_value=start):self.assertEqual(len(self.ps(p['token'])['operations']['announcements']),1)
        with patch('live_operations.now',return_value=end):
            self.assertEqual(self.ps(p['token'])['operations']['announcements'],[])
            self.pa(p['token'],'ops.announcement.ack',{'id':a['id'],'version':a['version'],'client':'MINIPROGRAM'},expected=409)
        for fields in ({'scope':'DISTRICT','target':'420502'},{'audiences':['DRIVER']},{'endAt':'2026-01-01'}, {'startAt':stamp(end),'endAt':stamp(start)}):
            body={**a['draft'],**fields};self.action(s,'ops.announcement.save',body,expected=400)
        self.ps(p['token'],'UNKNOWN',expected=400)
        self.pa(p['token'],'ops.announcement.ack',{'id':a['id'],'version':True,'client':'MINIPROGRAM'},expected=400)

    def test_two_client_ack_and_parallel_requests_have_one_first_display(self):
        s=self.login()['session'];p=self.user();a=self.announcement(s,audiences=['MINIPROGRAM','PASSENGER_ANDROID'])
        def send(client):return self.pa(p['token'],'ops.announcement.ack',{'id':a['id'],'version':a['version'],'client':client})['result']['firstDisplay']
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool: values=list(pool.map(send,['MINIPROGRAM','PASSENGER_ANDROID']))
        self.assertEqual(sorted(values),[False,True])

    def test_ack_failed_cas_and_lost_response_retry(self):
        s=self.login()['session'];p=self.user();a=self.announcement(s)
        body={'id':a['id'],'version':a['version'],'client':'MINIPROGRAM'}
        self.remote.fail_write=True;self.pa(p['token'],'ops.announcement.ack',body,rid='fail-ack',expected=503)
        self.remote.fail_write=False;self.assertEqual(len(self.ps(p['token'])['operations']['announcements']),1)
        self.remote.uncertain_write=True;self.pa(p['token'],'ops.announcement.ack',body,rid='lost-ack',expected=503)
        self.assertFalse(self.pa(p['token'],'ops.announcement.ack',body,rid='lost-ack')['result']['firstDisplay'])
        self.assertEqual(self.ps(p['token'])['operations']['announcements'],[])

    def test_two_instances_ack_cas_loser_retry_never_displays_again(self):
        s=self.login()['session'];p=self.user();a=self.announcement(s);other=self.start('driver')
        payload={'id':a['id'],'version':a['version'],'client':'MINIPROGRAM'}
        self.remote.barrier=threading.Barrier(2)
        def send(pair):
            i,server=pair
            return self.public('/api/passenger/action',{'action':'ops.announcement.ack',
                'payload':payload,'requestId':'two-instance-'+str(i)},p['token'],expected=(200,503),server=server)
        try:
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:replies=list(pool.map(send,[(0,self.driver),(1,other)]))
        finally:self.remote.barrier=None
        self.assertEqual(sorted(status for body,status in replies),[200,503])
        self.assertTrue(next(body['result']['firstDisplay'] for body,status in replies if status==200))
        loser=next(i for i,(body,status) in enumerate(replies) if status==503)
        self.assertFalse(self.pa(p['token'],'ops.announcement.ack',payload,rid='two-instance-'+str(loser))['result']['firstDisplay'])

    def test_review_file_selects_pending_and_keeps_active_attachment(self):
        s=self.login()['session'];d=self.user('13800000001',True)
        from common import SAMPLE_IMAGE
        body={'type':'insurance','expectedVersion':0,'validFrom':now().date().isoformat(),
            'validUntil':(now().date()+dt.timedelta(days=365)).isoformat(),'imageBase64':SAMPLE_IMAGE}
        cert=self.da(d['token'],'certificate.submit',body)['result']['certificate']
        driver=d['profile']['driver']['id'];endpoint='certificate-file?driverId='+driver+'&type=insurance'
        self.request(endpoint+'&source=ACTIVE',session=s,expected=404)
        result=self.request(endpoint,session=s)
        self.assertEqual(result['source'],'REVIEW');self.assertEqual(result['submissionId'],cert['pending']['submissionId'])
        self.action(s,'certificate.audit',{'driverId':driver,'type':'insurance','approved':True,
            'expectedVersion':cert['version'],'submissionId':cert['pending']['submissionId']})
        self.assertEqual(self.request(endpoint+'&source=ACTIVE',session=s)['fileId'],result['fileId'])

    def test_closed_feature_blocks_recall_but_keeps_decline_recall(self):
        s=self.login()['session'];p=self.user();d=self.user('13800000001',True)
        self.da(d['token'],'driver.online',{'online':True})
        o=self.pa(p['token'],'order.create',self.order_payload())['result']['order']
        self.da(d['token'],'order.accept',{'id':o['id']});self.da(d['token'],'order.cancel',{'id':o['id'],'reason':'演练司机取消'})
        self.features(s,{'instant':False})
        self.pa(p['token'],'order.continue',{'id':o['id'],'type':1},expected=403)
        self.assertEqual(self.pa(p['token'],'order.continue',{'id':o['id'],'type':2})['result']['order']['stage'],'CANCELLED')

    def test_feature_drafts_inheritance_publish_and_api_enforcement(self):
        s=self.login()['session'];p=self.user()
        row=self.features(s,{'instant':False},publish=False)
        self.assertTrue(self.ps(p['token'])['operations']['passengerFeatures']['features']['instant'])
        self.action(s,'ops.passengerFeatures.publish',{'scope':'NATIONAL','target':'CN','expectedVersion':row['version']})
        reply=self.pa(p['token'],'order.create',self.order_payload(),expected=403)
        self.assertEqual(reply['details']['reason'],'PASSENGER_FEATURE_DISABLED')
        self.features(s,{'instant':True,'bidding':False},'REGION','CENTRAL')
        value=self.ps(p['token'])['operations']['passengerFeatures'];self.assertTrue(value['features']['instant']);self.assertFalse(value['features']['bidding'])
        self.features(s,{'bidding':True},'CITY','420500')
        value=self.ps(p['token'])['operations']['passengerFeatures'];self.assertEqual(value['sources']['bidding']['scope'],'CITY')
        self.assertTrue(value['features']['bidding'])
        self.pa(p['token'],'order.create',self.order_payload(city='420502'),expected=400)
        self.assertEqual(self.ps(p['token'],server=self.start('driver'))['operations']['passengerFeatures'],value)

    def test_all_order_kind_gates_and_historical_cancel_survives_close(self):
        s=self.login()['session'];p=self.user();o=self.pa(p['token'],'order.create',self.order_payload())['result']['order']
        self.features(s,{'instant':False,'reservation':False,'bidding':False})
        self.assertEqual(self.ps(p['token'])['orders'][0]['id'],o['id'])
        self.pa(p['token'],'order.cancel',{'id':o['id'],'reason':'关闭后仍可取消'})
        for kind in ('STANDARD','RESERVED','BID'):
            self.pa(p['token'],'order.create',self.order_payload(kind=kind,scheduledAt=stamp(now()+dt.timedelta(hours=1))),expected=403)

    def test_invoice_new_apply_gate_but_existing_receipt_still_readable(self):
        s=self.login()['session'];p=self.user();o=self.pa(p['token'],'order.create',self.order_payload())['result']['order']
        def done(svc):
            v=svc.shared_order(o['id']);v.update(stage='DONE',paid=True);svc.save_shared(v)
        self.mutate(done)
        title=self.pa(p['token'],'invoice.title.save',{'type':'PERSONAL','title':'合成测试抬头'})['result']['invoiceTitle']
        row=self.features(s,{'invoice':False})
        body={'orderId':o['id'],'titleId':title['id']}
        self.pa(p['token'],'invoice.apply',body,expected=403)
        row=self.features(s,{'invoice':True},expected_version=row['version'])
        receipt=self.pa(p['token'],'invoice.apply',body)['result']['invoice']
        self.features(s,{'invoice':False},expected_version=row['version'])
        self.assertEqual(self.pa(p['token'],'invoice.apply',body)['result']['invoice'],receipt)

    def test_published_version_concurrency_and_cas_failure(self):
        s=self.login()['session'];a=self.company(s)
        def send(i):return self.action(s,'ops.company.save',{'id':a['id'],'expectedVersion':1,'name':'公司'+str(i),'supportPhone':None,'enabled':True},expected=200 if i==0 else 409)
        # Deterministic stale second writer and an actual separate-server CAS race below.
        send(0);send(1)
        other=self.start('admin');self.remote.barrier=threading.Barrier(2)
        def race(pair):
            i,server=pair
            body={'action':'ops.company.save','payload':{'name':'并发公司'+str(i),'supportPhone':None,'enabled':True},'requestId':'company-cas-'+str(i)}
            req=urllib.request.Request(f'http://127.0.0.1:{server.server_port}{fixture.PREFIX}action',data=json.dumps(body).encode(),headers={'Content-Type':'application/json','X-Admin-Token':fixture.OUTER,'X-Admin-Session':s})
            try:
                with urllib.request.urlopen(req,timeout=20) as r:r.read();return r.status
            except urllib.error.HTTPError as e:e.read();status=e.code;e.close();return status
        try:
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:statuses=list(pool.map(race,[(0,self.admin),(1,other)]))
        finally:self.remote.barrier=None
        self.assertEqual(sorted(statuses),[200,503])
        self.assertEqual(len(self.request('state',session=s)['operations']['companies']),2)

    def test_invoice_order_city_projection_survives_profile_city_switch_and_existing_receipt(self):
        s=self.login()['session'];p=self.user()
        def completed(city):
            order=self.pa(p['token'],'order.create',self.order_payload(city=city))['result']['order']
            def finish(svc):
                current=svc.shared_order(order['id']);current.update(stage='DONE',paid=True);svc.save_shared(current)
            self.mutate(finish)
            return order['id']
        a,b=completed('420500'),completed('420100')
        a_rules=self.features(s,{'invoice':False},'CITY','420500')
        b_rules=self.features(s,{'invoice':True},'CITY','420100')
        before=self.mutate(lambda svc:[tuple(r) for r in svc.db.execute('SELECT * FROM passenger_orders ORDER BY id')])
        state=self.ps(p['token']);orders={o['id']:o for o in state['orders']}
        self.assertFalse(state['operations']['passengerFeatures']['features']['invoice'])
        self.assertIs(orders[a]['invoiceApplicationEnabled'],False)
        self.assertIs(orders[b]['invoiceApplicationEnabled'],True)
        self.assertIn('CITY:420500:',orders[a]['invoiceApplicationRulesVersion'])
        self.assertIn('CITY:420100:',orders[b]['invoiceApplicationRulesVersion'])
        self.assertNotEqual(orders[a]['invoiceApplicationRulesVersion'],orders[b]['invoiceApplicationRulesVersion'])
        self.assertEqual(before,self.mutate(lambda svc:[tuple(r) for r in svc.db.execute('SELECT * FROM passenger_orders ORDER BY id')]))
        title=self.pa(p['token'],'invoice.title.save',{'type':'PERSONAL','title':'跨城合成抬头'})['result']['invoiceTitle']
        self.pa(p['token'],'invoice.apply',{'orderId':a,'titleId':title['id']},expected=403)
        receipt=self.pa(p['token'],'invoice.apply',{'orderId':b,'titleId':title['id']})['result']['invoice']
        self.pa(p['token'],'profile.update',{'city':'420100'})
        state=self.ps(p['token']);orders={o['id']:o for o in state['orders']}
        self.assertTrue(state['operations']['passengerFeatures']['features']['invoice'])
        self.assertFalse(orders[a]['invoiceApplicationEnabled']);self.assertTrue(orders[b]['invoiceApplicationEnabled'])
        self.features(s,{'invoice':True},'CITY','420500',a_rules['version'])
        self.features(s,{'invoice':False},'CITY','420100',b_rules['version'])
        state=self.ps(p['token']);orders={o['id']:o for o in state['orders']}
        self.assertFalse(state['operations']['passengerFeatures']['features']['invoice'])
        self.assertTrue(orders[a]['invoiceApplicationEnabled']);self.assertFalse(orders[b]['invoiceApplicationEnabled'])
        self.assertEqual(self.pa(p['token'],'invoice.apply',{'orderId':b,'titleId':title['id']})['result']['invoice'],receipt)
        self.assertEqual(self.pa(p['token'],'invoice.apply',{'orderId':a,'titleId':title['id']})['result']['invoice']['orderId'],a)
