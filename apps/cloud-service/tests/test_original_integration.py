"""HTTP + real SQLite integration tests. No supplied backend service is contacted."""
import uuid, base64, datetime as dt, json, pathlib, sys, tempfile, threading, time, unittest, urllib.error, urllib.request
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
from server import make_server,start_scheduler
from common import SAMPLE_IMAGE,stamp,now

class LocalIntegration(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.path=pathlib.Path(self.temp.name)/'test.sqlite';self.server=make_server(self.path,0,'test-admin-only');self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start();start_scheduler(self.server);self.base=f'http://127.0.0.1:{self.server.server_port}';self.token=None;self.login()
    def tearDown(self):
        self.server.shutdown();self.thread.join();self.server.service_close();self.temp.cleanup()
    def request(self,path,data=None,admin=False,actor='editor',expected=200,token=None):
        headers={'Content-Type':'application/json'}
        if admin:headers.update({'X-Admin-Token':'test-admin-only','X-Local-Actor':actor})
        elif token or self.token:headers['Authorization']='Bearer '+(token or self.token)
        req=urllib.request.Request(self.base+path,data=None if data is None else json.dumps(data).encode(),headers=headers)
        try:r=urllib.request.urlopen(req,timeout=10);status=r.status;body=r.read()
        except urllib.error.HTTPError as e:status=e.code;body=e.read()
        self.assertEqual(status,expected,body.decode()[:2000]);obj=json.loads(body);return obj.get('data',obj)
    def login(self,mobile='13800000001'):
        code=self.request('/api/auth/code',{'mobile':mobile})['verificationCode'];d=self.request('/api/auth/login',{'mobile':mobile,'code':code});self.token=d['token'];self.driver=d['profile']['driver']['id'];return d
    def state(self):return self.request('/api/state')
    def action(self,action,p=None,expected=200,requestId=None):return self.request('/api/action',{'action':action,'payload':p or {},'requestId':requestId or str(uuid.uuid4())},expected=expected)
    def admin(self,action,p,actor='editor',expected=200):return self.request('/api/local/admin/action',{'action':action,'payload':p},True,actor,expected)
    def adminstate(self):return self.request('/api/local/admin/state',admin=True)
    def cfg(self,scope='city',target='420500'):return next(c for c in self.adminstate()['configs'] if c['scope']==scope and c['target']==target)
    def draft(self,patch,scope='city',target='420500',remove=None):return self.admin('config.draft',{'scope':scope,'target':target,'expectedRevision':self.cfg(scope,target)['revision'],'patch':patch,'removePaths':remove or []})
    def publish(self,scope='city',target='420500',extra=None):return self.admin('config.publish',{'scope':scope,'target':target,'expectedRevision':self.cfg(scope,target)['revision'],**(extra or {})})
    def cat(self,city='420500'):return self.request('/api/benefits/catalog?city='+city)
    def level(self,cat=None,level='NORMAL'):return next(l for l in (cat or self.cat())['levels'] if l['id']==level)
    def buy(self,level='NORMAL',city='420500',outcome='SUCCESS',renew=True,key=None):
        cat=self.cat(city);r=self.request('/api/benefits/orders',{'city':city,'level':level,'version':cat['version'],'agree':True,'autoRenew':renew,'paymentMethod':'WECHAT','idempotencyKey':key or 'buy-'+str(time.time_ns())});oid=r['result']['order']['id'];return self.request('/api/benefits/pay',{'orderId':oid,'outcome':outcome})
    def expire(self):return self.admin('trial.set',{'driverId':self.driver,'daysUsed':31,'ordersCompleted':2})
    def test_AC01_levels_and_baseline(self):
        self.assertEqual([l['id'] for l in self.cat()['levels']],['TRIAL','NORMAL','VIP']);c=self.cat('420100');self.assertEqual(self.level(c)['price'],25000);self.assertEqual(self.level(c,'VIP')['price'],36500)
    def test_AC02_draft_does_not_leak_or_change_other_level(self):
        before=self.cat();self.draft({'levels':{'VIP':{'rights':{'heatmap':False}}}},'national','CN');after=self.cat();self.assertEqual(before['version'],after['version']);self.assertEqual(self.level(after,'VIP')['rights'],self.level(before,'VIP')['rights']);self.assertEqual(self.level(after)['rights'],self.level(before)['rights'])
    def test_AC03_publish_entitlements_same_version(self):
        self.draft({'levels':{'VIP':{'rights':{'heatmap':False}}}},'national','CN');self.publish('national','CN');c=self.cat();l=self.level(c,'VIP');self.assertFalse(next(r for r in l['rights'] if r['key']=='heatmap')['enabled']);self.assertEqual(l['version'],c['version'])
    def test_AC04_map_types_validation(self):
        self.draft({'levels':{'NORMAL':{'params':{'mapQuota':{'type':'UNLIMITED'}}}}});self.publish();self.assertEqual(self.level()['mapQuota']['type'],'UNLIMITED')
        self.admin('config.draft',{'scope':'city','target':'420500','expectedRevision':self.cfg()['revision'],'patch':{'levels':{'NORMAL':{'params':{'mapQuota':{'type':'ORDERS','freeOrders':-1,'overagePrice':0}}}}}},expected=400)
    def test_AC05_city_region_and_custom_data(self):
        cs=self.adminstate()['cities'];self.assertEqual(len([c for c in cs if '昌' in c['name'] and c['region']=='CENTRAL']),1);self.assertEqual(len([c for c in cs if '不存在' in c['name'] and c['region']=='SOUTH']),0)
    def test_AC06_price_published_catalog_and_order(self):
        self.draft({'levels':{'NORMAL':{'price':22100}}});self.publish();self.assertEqual(self.level()['price'],22100);self.assertEqual(self.buy()['result']['order']['amount'],22100)
    def test_AC07_recovered_draft(self):
        self.draft({'levels':{'NORMAL':{'price':22300}}});self.assertEqual(self.cfg()['draft']['levels']['NORMAL']['price'],22300);self.assertEqual(self.level()['price'],19900)
    def test_AC08_per_field_clear_inherits(self):
        self.draft({},remove=['levels.NORMAL.params.nearbyKm']);self.publish();l=self.level();self.assertEqual(l['params']['nearbyKm'],3);self.assertEqual(l['price'],19900);self.assertEqual(l['mapQuota']['freeOrders'],200)
    def test_AC09_batch_dedup(self):
        pr=self.admin('batch.preview',{'cities':['420500','420500'],'regions':['CENTRAL'],'policy':'OVERWRITE_ALL','patch':{'levels':{'NORMAL':{'price':22000}}}})['result']['preview'];self.assertEqual(pr['uniqueCityCount'],2);self.assertEqual(pr['driverCount'],980)
    def test_AC10_batch_skip_and_results(self):
        p={'cities':['420500','420100'],'policy':'SKIP_EXISTING','patch':{'levels':{'NORMAL':{'price':22000}}}}
        pr=self.admin('batch.preview',p)['result']['preview'];p.update(expectedRevisions={c['target']:c['revision'] for c in pr['changes']},idempotencyKey='B1');b=self.admin('batch.execute',p)['result']['batch'];self.assertEqual([r['status'] for r in b['results']],['APPLIED','SKIPPED']);self.assertEqual(self.level()['price'],19900);self.assertEqual(self.level(self.cat('420100'))['price'],22000)
        self.assertTrue(self.admin('batch.execute',p)['result']['idempotent'])
    def test_AC11_batch_switch_only_preserves_prices(self):
        p={'cities':['420500'],'policy':'SWITCH_ONLY','patch':{'levels':{'NORMAL':{'saleEnabled':True}}}}
        pr=self.admin('batch.preview',p)['result']['preview'];p.update(expectedRevisions={c['target']:c['revision'] for c in pr['changes']},idempotencyKey='B2');self.admin('batch.execute',p);self.assertEqual(self.level()['price'],19900);self.assertEqual(self.level()['firstYearPrice'],16900)
    def close_sale(self):
        self.draft({'levels':{'NORMAL':{'saleEnabled':False}}});a=self.publish(extra={'confirmed':True,'reason':'本地停售测试','subscriptionPolicy':'KEEP_UNTIL_EXPIRY'})['result']['approval'];return a
    def test_AC12_two_stage_approval_and_rejection(self):
        a=self.close_sale();self.assertTrue(self.level()['saleEnabled']);self.admin('approval.decide',{'id':a['id'],'decision':'APPROVE'},actor='editor',expected=403);self.admin('approval.decide',{'id':a['id'],'decision':'APPROVE'},actor='approver-1');self.assertTrue(self.level()['saleEnabled']);self.admin('approval.decide',{'id':a['id'],'decision':'REJECT'},actor='approver-2');self.assertTrue(self.level()['saleEnabled'])
    def test_AC13_closure_preserves_paid_and_stops_renewal(self):
        s=self.buy()['result']['subscription'];a=self.close_sale();self.admin('approval.decide',{'id':a['id'],'decision':'APPROVE'},actor='approver-1');self.admin('approval.decide',{'id':a['id'],'decision':'APPROVE'},actor='approver-2');c=self.cat();self.assertFalse(self.level(c)['saleEnabled']);self.assertEqual(c['subscription']['endAt'],s['endAt']);self.assertFalse(c['subscription']['autoRenew']);self.assertTrue(c['canAccept']);self.request('/api/benefits/renewal',{'subscriptionId':s['id'],'enabled':True},expected=403)
    def test_AC14_server_scheduler_without_browser(self):
        old=self.level()['price'];self.draft({'levels':{'NORMAL':{'price':22900}}});self.publish(extra={'effectiveAt':stamp(now()+dt.timedelta(seconds=2))});self.assertEqual(self.level()['price'],old);time.sleep(2.2);self.assertEqual(self.level()['price'],22900)
    def test_AC15_trial_either_limit_preserves_active_trip_and_wallet(self):
        self.action('driver.online',{'online':True});self.action('order.accept',{'id':'YC1001'});self.expire();d=self.state();self.assertFalse(d['features']['canAcceptOrders']);self.assertEqual(d['benefits']['trial']['endReason'],'DAYS_LIMIT');self.action('order.advance',{'id':'YC1001'});self.action('order.advance',{'id':'YC1001','phoneTail':'6758'});self.action('order.advance',{'id':'YC1001'});self.assertGreater(self.state()['state']['balance'],0);self.action('driver.online',{'online':True},expected=403)
        self.admin('trial.set',{'driverId':self.driver,'daysUsed':0,'ordersCompleted':15});self.assertEqual(self.cat()['trial']['endReason'],'ORDER_LIMIT')
    def test_AC16_city_switch_reprice_and_closed_level(self):
        self.assertEqual(self.level()['price'],19900);self.assertEqual(self.level(self.cat('420100'))['price'],25000)
        a=self.close_sale();self.admin('approval.decide',{'id':a['id'],'decision':'APPROVE'},actor='ap1');self.admin('approval.decide',{'id':a['id'],'decision':'APPROVE'},actor='ap2');cat=self.cat();self.request('/api/benefits/orders',{'city':'420500','level':'NORMAL','version':cat['version'],'agree':True,'idempotencyKey':'closed'},expected=403)
    def test_AC17_explicit_agreement(self):
        self.request('/api/benefits/orders',{'city':'420500','level':'NORMAL','version':self.cat()['version'],'agree':False,'idempotencyKey':'no-agree'},expected=400)
    def test_AC18_cancel_failed_pending_retry(self):
        d=self.buy(outcome='FAILED');oid=d['result']['order']['id'];self.assertIsNone(self.cat()['subscription']);self.request('/api/benefits/pay',{'orderId':oid,'outcome':'CANCEL'});self.assertIsNone(self.cat()['subscription']);r=self.request('/api/benefits/pay',{'orderId':oid,'outcome':'PENDING'});self.assertEqual(r['result']['order']['status'],'PROCESSING');r=self.request('/api/benefits/pay',{'orderId':oid,'outcome':'SUCCESS','paymentMethod':'ALIPAY'});self.assertEqual(r['result']['order']['paymentMethod'],'ALIPAY')
    def test_AC19_payment_idempotency_and_server_price(self):
        d=self.buy();o=d['result']['order'];s=d['result']['subscription'];r=self.request('/api/benefits/pay',{'orderId':o['id'],'outcome':'SUCCESS'});self.assertEqual(r['result']['subscription']['id'],s['id']);self.assertEqual(len(self.request('/api/benefits/subscriptions')['history']),1);self.assertEqual(o['amount'],19900)
    def test_AC20_renewal_off_keeps_end(self):
        s=self.buy()['result']['subscription'];r=self.request('/api/benefits/renewal',{'subscriptionId':s['id'],'enabled':False})['result']['subscription'];self.assertEqual(r['endAt'],s['endAt']);self.assertIsNone(r['nextChargeAt'])
    def test_AC21_snapshots_city_browse_price_change(self):
        s=self.buy()['result']['subscription'];self.draft({'levels':{'NORMAL':{'price':23900}}});self.publish();self.cat('420100');sub=self.cat()['subscription'];self.assertEqual(sub['amount'],19900);self.assertEqual(sub['rightsSnapshot'],s['rightsSnapshot']);self.assertEqual(sub['city'],'420500')
    def test_AC22_restore_keeps_city_identity(self):
        self.admin('config.restore',{'scope':'city','target':'420500','expectedRevision':self.cfg()['revision']});self.assertEqual(self.level()['price'],19900);self.publish();c=self.cat();self.assertEqual(self.level(c)['price'],25000);self.assertEqual(c['city']['id'],'420500');self.assertEqual(c['city']['drivers'],368)
    def test_price_revalidated_before_pay(self):
        d=self.buy(outcome='PENDING');oid=d['result']['order']['id'];self.draft({'levels':{'NORMAL':{'price':22000}}});self.publish();self.request('/api/benefits/pay',{'orderId':oid,'outcome':'SUCCESS'},expected=409);self.assertIsNone(self.cat()['subscription'])
    def test_conflict_optimistic_revision(self):
        rev=self.cfg()['revision'];self.draft({'levels':{'NORMAL':{'price':22200}}});self.admin('config.draft',{'scope':'city','target':'420500','expectedRevision':rev,'patch':{}},expected=409)
    def test_new_accept_blocks_server_bypass(self):
        self.expire();self.action('order.accept',{'id':'YC1001'},expected=403 if self.state()['state']['online'] else 409);self.assertFalse(self.state()['features']['canHeatmap']);self.request('/api/heatmap',expected=403)
    def test_order_state_machine_and_external_passenger_event(self):
        self.action('driver.online',{'online':True});self.action('order.bid',{'id':'YC1005','amount':4500});self.action('order.selectBid',{'id':'YC1005'},expected=403);self.admin('sandbox.event',{'driverId':self.driver,'event':'BID_SELECTED','orderId':'YC1005'});self.action('order.pay',{'id':'YC1005'},expected=403);self.admin('sandbox.event',{'driverId':self.driver,'event':'PASSENGER_PAY_SUCCESS','orderId':'YC1005'});self.action('order.advance',{'id':'YC1005'});self.action('order.advance',{'id':'YC1005','phoneTail':'0000'},expected=400);self.action('order.advance',{'id':'YC1005','phoneTail':'6758'});before=self.state()['state']['balance'];self.action('order.advance',{'id':'YC1005'});self.assertEqual(self.state()['state']['balance'],before+4500);self.action('order.advance',{'id':'YC1005'},expected=409)
    def test_wallet_password_withdraw_reject_once(self):
        self.action('wallet.password.set',{'password':'123456'});d=self.action('wallet.withdraw',{'amount':1000,'password':'123456','bankCardId':'BANK-DEMO'},requestId='withdraw-once');wid=d['result']['withdrawal']['id'];bal=d['state']['balance'];self.action('wallet.withdraw',{'amount':1000,'password':'123456','bankCardId':'BANK-DEMO'},requestId='withdraw-once');self.assertEqual(self.state()['state']['balance'],bal);self.admin('wallet.withdrawal.audit',{'driverId':self.driver,'id':wid,'approved':False,'reason':'演练退回'});self.assertEqual(self.state()['state']['balance'],bal+1000);self.admin('wallet.withdrawal.audit',{'driverId':self.driver,'id':wid,'approved':False},expected=409)
        code=self.action('wallet.password.code')['result']['verificationCode'];self.action('wallet.password.reset',{'code':code,'password':'654321'});self.action('wallet.password.change',{'oldPassword':'654321','password':'123456'})
    def test_bank_files_certificate_audit_and_account_isolation(self):
        self.action('bank.add',{'bankName':'演示银行','number':'6222000000000000','holder':'演示甲'},expected=400);self.action('bank.add',{'bankName':'演示银行','number':'9000000000000002','holder':'演示甲'})
        r=self.action('certificate.submit',{'type':'insurance','validFrom':now().date().isoformat(),'validUntil':(now()+dt.timedelta(days=365)).date().isoformat(),'imageBase64':SAMPLE_IMAGE});fid=r['result']['fileId'];self.assertEqual(self.request('/api/files/'+fid+'?encoding=base64')['imageBase64'],SAMPLE_IMAGE);self.assertEqual(r['result']['certificate']['reviewStatus'],'PENDING');self.assertEqual(r['result']['certificate']['status'],'APPROVED');self.admin('certificate.audit',{'driverId':self.driver,'type':'insurance','approved':False,'reason':'图片不清晰','expectedVersion':r['result']['certificate']['version'],'submissionId':r['result']['certificate']['pending']['submissionId']});self.assertEqual(self.state()['profile']['certificates'][0]['reviewStatus'],'REJECTED');self.assertEqual(self.state()['profile']['certificates'][0]['status'],'APPROVED');self.login('13800000002');self.request('/api/files/'+fid+'?encoding=base64',expected=404)
    def test_feedback_complaint_reply_and_blacklist(self):
        d=self.action('feedback.submit',{'content':'本地测试反馈提交','imagesBase64':[SAMPLE_IMAGE,SAMPLE_IMAGE]});f=d['result']['feedback'];self.admin('feedback.reply',{'driverId':self.driver,'id':f['id'],'reply':'已收到本地反馈'});self.assertEqual(self.state()['profile']['feedback'][0]['status'],'REPLIED');self.action('order.complaint',{'id':'HISTORY01','reason':'服务问题','content':'这是本地投诉测试','blockUser':True});self.assertEqual(len(self.state()['profile']['blacklist']),1)
    def test_location_route_and_estimate(self):
        self.action('location.report',{'latitude':30.66,'longitude':111.36,'accuracy':8,'time':stamp()});self.assertEqual(self.request('/api/location/latest')['location']['latitude'],30.66);route=self.request('/api/orders/YC1001/route');self.assertEqual(route['source'],'synthetic');self.assertEqual(len(route['coordinates']),4);f=self.request('/api/orders/YC1001/estimate?amount=5500');self.assertEqual(f['income'],5500)
    def test_admin_token_logout_and_unsupported_methods(self):
        self.request('/api/local/admin/state',expected=401);self.request('/api/auth/logout',{});self.request('/api/state',expected=401)
    def test_invoice_idempotency_and_sqlite_persistence(self):
        d=self.buy();oid=d['result']['order']['id'];p={'orderId':oid,'type':'COMPANY','title':'本地演示公司','taxNumber':'91330000123456789A'};inv=self.request('/api/benefits/invoices',p)['result']['invoice'];inv2=self.request('/api/benefits/invoices',p)['result']['invoice'];self.assertEqual(inv['id'],inv2['id']);self.assertTrue(inv['sandbox']);self.assertGreater(self.path.stat().st_size,0)

    def test_on_disk_restart_preserves_token_order_and_subscription(self):
        d=self.buy();sid=d['result']['subscription']['id'];self.action('profile.update',{'name':'本机持久化司机'})
        self.server.shutdown();self.thread.join();self.server.service_close()
        self.server=make_server(self.path,0,'test-admin-only');self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start();start_scheduler(self.server);self.base=f'http://127.0.0.1:{self.server.server_port}'
        self.assertEqual(self.state()['profile']['driver']['name'],'本机持久化司机');self.assertEqual(self.cat()['subscription']['id'],sid)
    def test_no_advance_renewal_overwrites_active_snapshot(self):
        original=self.buy()['result']['subscription'];c=self.cat()
        self.request('/api/benefits/orders',{'city':'420500','level':'NORMAL','version':c['version'],'agree':True,'idempotencyKey':'premature'},expected=409)
        self.assertEqual(self.cat()['subscription']['rightsSnapshot'],original['rightsSnapshot'])
    def test_schedule_conflict_does_not_overwrite_newer_draft(self):
        self.draft({'levels':{'NORMAL':{'price':23000}}});self.publish(extra={'effectiveAt':stamp(now()+dt.timedelta(seconds=2))});self.draft({'levels':{'NORMAL':{'price':24000}}});time.sleep(2.2)
        self.assertEqual(self.level()['price'],19900);self.assertEqual(self.adminstate()['scheduled'][0]['status'],'FAILED');self.assertEqual(self.cfg()['draft']['levels']['NORMAL']['price'],24000)
    def test_duplicate_payment_concurrently_creates_one_subscription(self):
        from concurrent.futures import ThreadPoolExecutor
        o=self.buy(outcome='PENDING')['result']['order']
        with ThreadPoolExecutor(max_workers=5) as pool:
            results=list(pool.map(lambda _:self.request('/api/benefits/pay',{'orderId':o['id'],'outcome':'SUCCESS'}),range(5)))
        self.assertEqual(len({r['result']['subscription']['id'] for r in results}),1);self.assertEqual(len(self.request('/api/benefits/subscriptions')['history']),1)
    def test_batch_close_is_atomic_until_second_approval(self):
        p={'cities':['420500','420100'],'policy':'SWITCH_ONLY','patch':{'levels':{'VIP':{'saleEnabled':False}}},'confirmed':True,'reason':'整批关闭测试','subscriptionPolicy':'KEEP_UNTIL_EXPIRY'}
        preview=self.admin('batch.preview',p)['result']['preview'];p.update(expectedRevisions={c['target']:c['revision'] for c in preview['changes']},idempotencyKey='batch-close');b=self.admin('batch.execute',p)['result']['batch'];self.assertEqual(b['status'],'WAIT_APPROVAL')
        self.assertTrue(self.level(self.cat('420100'),'VIP')['saleEnabled']);self.admin('approval.decide',{'id':b['approvalId'],'decision':'APPROVE'},actor='app1');self.assertTrue(self.level(self.cat('420100'),'VIP')['saleEnabled']);self.admin('approval.decide',{'id':b['approvalId'],'decision':'APPROVE'},actor='app2')
        self.assertFalse(self.level(self.cat('420100'),'VIP')['saleEnabled']);self.assertFalse(self.level(self.cat('420500'),'VIP')['saleEnabled']);self.assertEqual(self.adminstate()['batches'][0]['status'],'APPLIED')
    def test_rank_not_leaked_in_state_after_trial_end(self):
        self.expire();self.assertTrue(self.state()['profile']['ranking']['restricted']);self.assertEqual(self.state()['profile']['ranking']['entries'],[]);self.request('/api/ranking',expected=403)
    def test_code_reuse_and_wrong_password_reset_does_not_expire_session(self):
        self.action('wallet.password.code');self.action('wallet.password.reset',{'code':'wrong','password':'123456'},expected=400);self.assertEqual(self.state()['state']['loggedIn'],True)
    def test_driver_cannot_access_admin_attachment_or_other_orders(self):
        o=self.buy()['result']['order'];self.request('/api/local/admin/files/anything?encoding=base64',expected=401);self.login('13800000002');self.request('/api/benefits/orders/'+o['id'],expected=404)

    def test_cli_start_stop_and_reopen_keeps_database(self):
        import socket,subprocess
        from urllib.request import urlopen,Request
        tmp=pathlib.Path(self.temp.name)/'launch';tmp.mkdir();db=tmp/'local.sqlite'
        sock=socket.socket();sock.bind(('127.0.0.1',0));port=sock.getsockname()[1];sock.close()
        script=pathlib.Path(__file__).resolve().parents[1]/'server.py'
        proc=subprocess.Popen([sys.executable,str(script),'--port',str(port),'--db',str(db)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:
            deadline=time.time()+8
            while time.time()<deadline:
                try:
                    h=json.load(urlopen(f'http://127.0.0.1:{port}/health',timeout=.2))
                    if h['code']==0:break
                except OSError:time.sleep(.05)
            else:self.fail('CLI local service did not start')
            self.assertTrue((tmp/'runtime.json').exists());self.assertTrue(db.exists());adm=(tmp/'admin-token.txt').read_text().strip()
            req=Request(f'http://127.0.0.1:{port}/api/local/admin/shutdown',data=b'{}',headers={'Content-Type':'application/json','X-Admin-Token':adm})
            self.assertTrue(json.load(urlopen(req))['data']['stopping']);self.assertEqual(proc.wait(timeout=5),0);self.assertFalse((tmp/'runtime.json').exists());self.assertTrue(db.exists())
        finally:
            if proc.poll() is None:proc.terminate();proc.wait(timeout=5)
    def test_national_identity_cannot_be_redefined(self):
        self.admin('config.draft',{'scope':'national','target':'CN','expectedRevision':self.cfg('national','CN')['revision'],'patch':{'levels':{'VIP':{'id':'NORMAL'}}}},expected=400)

    def test_range_enforced_from_server_owned_synthetic_distance(self):
        self.draft({'levels':{'TRIAL':{'params':{'nearbyKm':1.0}}}});self.publish();self.action('driver.online',{'online':True});r=self.action('order.accept',{'id':'YC1001'},expected=403);self.assertEqual(r['details']['allowedKm'],1.0)
        self.action('order.accept',{'id':'YC1002'});self.assertEqual(next(o for o in self.state()['state']['orders'] if o['id']=='YC1002')['stage'],'PICKUP')
    def test_global_all_area_right_bypasses_level_nearby_but_keeps_preference(self):
        self.draft({'levels':{'TRIAL':{'rights':{'allArea':True}}}},'national','CN');self.publish('national','CN');self.draft({'levels':{'TRIAL':{'params':{'nearbyKm':0.2}}}});self.publish();self.action('driver.online',{'online':True});self.action('settings.update',{'range':1});self.action('order.accept',{'id':'YC1001'},expected=403);self.action('settings.update',{'range':5});self.action('order.accept',{'id':'YC1001'})
    def test_disabled_instant_keeps_reservation_online_capability(self):
        self.draft({'levels':{'TRIAL':{'rights':{'instant':False}}}},'national','CN');self.publish('national','CN');self.assertTrue(self.state()['features']['canAcceptOrders']);self.action('driver.online',{'online':True});self.action('order.accept',{'id':'YC1001'},expected=403);self.action('order.accept',{'id':'YC1003'})
    def test_expired_trial_preserves_withdraw_and_certificate_submission(self):
        self.expire();self.action('wallet.password.set',{'password':'123456'});self.action('wallet.withdraw',{'amount':100,'password':'123456','bankCardId':'BANK-DEMO'});r=self.action('certificate.submit',{'type':'insurance','validFrom':now().date().isoformat(),'validUntil':(now()+dt.timedelta(days=365)).date().isoformat(),'imageBase64':SAMPLE_IMAGE});self.assertEqual(r['result']['certificate']['reviewStatus'],'PENDING');self.assertEqual(r['result']['certificate']['status'],'APPROVED')

    def test_voice_floating_and_required_location_separated(self):
        d=self.state();self.assertFalse(d['state']['floating']);self.assertTrue(d['features']['canVoice']);self.assertTrue(d['features']['canFloating']);self.action('settings.update',{'floating':True})
        self.expire();d=self.state();self.assertFalse(d['features']['canFloating']);self.assertFalse(d['features']['canVoice']);self.action('settings.update',{'floating':True},expected=403);self.action('settings.update',{'voice':True},expected=403);self.action('settings.update',{'floating':False,'voice':False});self.action('location.report',{'latitude':30.66,'longitude':111.36,'accuracy':10})
    def test_city_can_disable_order_voice_without_changing_global_right(self):
        self.draft({'levels':{'TRIAL':{'params':{'voiceEnabled':False}}}});self.publish();self.assertFalse(self.state()['features']['canVoice']);self.assertTrue(self.cfg('national','CN')['published']['levels']['TRIAL']['rights']['voice'])

    def enable_negotiation(self):
        self.draft({'levels':{l:{'params':{'offlineNegotiation':True}} for l in ('TRIAL','NORMAL','VIP')}});self.publish();self.action('driver.online',{'online':True})
    def test_negotiate_persists_user_selected_coordinates_and_route(self):
        self.enable_negotiation();p={'origin':'地图选择起点','destination':'地图选择终点','amount':5000,'originLat':30.71,'originLng':111.31,'destinationLat':30.75,'destinationLng':111.39};r=self.action('order.negotiate',p);o=r['result']['order'];self.assertEqual(o['coordinateSource'],'USER_SELECTED')
        persisted=next(v for v in self.state()['state']['orders'] if v['id']==o['id'])
        for k in ('originLat','originLng','destinationLat','destinationLng'):self.assertEqual(persisted[k],p[k])
        route=self.request('/api/orders/'+o['id']+'/route');self.assertEqual(route['coordinates'][0],[111.31,30.71]);self.assertEqual(route['coordinates'][-1],[111.39,30.75])
    def test_negotiate_rejects_duplicate_place_and_invalid_or_missing_coordinates(self):
        self.enable_negotiation();base={'origin':'同一地图点','destination':'同一地图点','amount':5000};self.action('order.negotiate',base,expected=400)
        base.update(destination='不同终点',originLat=30.7);self.action('order.negotiate',base,expected=400)
        base.update(originLng=111.3,destinationLat=30.7,destinationLng=111.3);self.action('order.negotiate',base,expected=400)
        base.update(destinationLat=91);self.action('order.negotiate',base,expected=400)
        base.update(destinationLat=30.8,originLat='30.7');self.action('order.negotiate',base,expected=400)
    def test_negotiate_legacy_without_coordinates_is_explicitly_synthetic(self):
        self.enable_negotiation();o=self.action('order.negotiate',{'origin':'本地旧起点','destination':'本地旧终点','amount':5000})['result']['order'];self.assertTrue(o['coordinatesSynthetic']);self.assertEqual(o['coordinateSource'],'SYNTHETIC_DEFAULT')

    def test_scheduled_sale_closure_explicitly_rejected_without_silent_early_effect(self):
        self.draft({'levels':{'NORMAL':{'saleEnabled':False}}})
        self.admin('config.publish',{'scope':'city','target':'420500','expectedRevision':self.cfg()['revision'],'effectiveAt':stamp(now()+dt.timedelta(days=1)),'confirmed':True,'reason':'指定停售待支持','subscriptionPolicy':'KEEP_UNTIL_EXPIRY'},expected=400)
        self.assertTrue(self.level()['saleEnabled']);self.assertEqual(self.adminstate()['approvals'],[])
    def test_close_review_tasks_only_include_affected_city(self):
        s1=self.buy()['result']['subscription'];a=self.close_sale();self.admin('approval.decide',{'id':a['id'],'decision':'APPROVE'},actor='ap1');self.admin('approval.decide',{'id':a['id'],'decision':'APPROVE'},actor='ap2')
        s2=self.buy(city='420100')['result']['subscription'];self.draft({'levels':{'NORMAL':{'saleEnabled':False}}},target='420100')
        a=self.publish(target='420100',extra={'confirmed':True,'reason':'另一城市退款待确认','subscriptionPolicy':'REFUND_REVIEW'})['result']['approval'];self.admin('approval.decide',{'id':a['id'],'decision':'APPROVE'},actor='ap1');result=self.admin('approval.decide',{'id':a['id'],'decision':'APPROVE'},actor='ap2')['result']['approval'];self.assertEqual([t['subscriptionId'] for t in result['manualTasks']],[s2['id']]);self.assertNotEqual(s1['id'],s2['id'])

    def test_new_order_waypoints_clean_fixture_and_completion_guard(self):
        self.action('driver.online',{'online':True});self.action('order.accept',{'id':'YC1001'});self.action('order.advance',{'id':'YC1001'});self.action('order.advance',{'id':'YC1001','phoneTail':'6758'});self.action('order.advance',{'id':'YC1001'});self.admin('sandbox.event',{'driverId':self.driver,'event':'PASSENGER_PAY_SUCCESS','orderId':'YC1001'})
        original=next(o for o in self.state()['state']['orders'] if o['id']=='YC1001');self.assertEqual(original['stage'],'DONE');self.assertIn('completedAt',original)
        d=self.admin('sandbox.event',{'driverId':self.driver,'event':'NEW_ORDER','kind':'STANDARD','waypoints':['合成途经点一',{'name':'合成途经点二','latitude':30.67,'longitude':111.34}]});o=d['result']['order'];self.assertEqual(o['arrivedWaypoints'],[]);self.assertEqual([v['name'] for v in o['waypoints']],['合成途经点一','合成途经点二']);self.assertEqual([v['sortNo'] for v in o['waypoints']],[1,2]);self.assertEqual(o['stage'],'AVAILABLE');self.assertFalse(o['paid']);self.assertFalse(o['credited'])
        for field in ('paymentStatus','completedAt','cancelReason','bidAt','paidAt'):self.assertNotIn(field,o)
        self.assertEqual(next(v for v in self.state()['state']['orders'] if v['id']=='YC1001'),original)
        self.action('order.accept',{'id':o['id']});self.action('order.advance',{'id':o['id']});self.action('order.advance',{'id':o['id'],'phoneTail':'6758'});self.action('order.advance',{'id':o['id']},expected=400)
        self.action('order.waypoint',{'id':o['id'],'index':0});self.action('order.waypoint',{'id':o['id'],'index':0});self.action('order.advance',{'id':o['id']},expected=400);self.action('order.waypoint',{'id':o['id'],'index':1});result=self.action('order.advance',{'id':o['id']})['result']['order'];self.assertEqual(result['stage'],'WAIT_PAY');self.assertEqual(result['arrivedWaypoints'],[0,1])
    def test_new_order_waypoints_validation_rejects_invalid_payload_without_insertion(self):
        count=len(self.state()['state']['orders'])
        for waypoints in (['甲地','乙地','丙地'],[{'name':'无效经度','latitude':30.7,'longitude':190}],[{'name':'缺少纬度','longitude':111.3}],[True],['']):
            self.admin('sandbox.event',{'driverId':self.driver,'event':'NEW_ORDER','waypoints':waypoints},expected=400)
        self.assertEqual(len(self.state()['state']['orders']),count)

if __name__=='__main__':unittest.main(verbosity=2)
