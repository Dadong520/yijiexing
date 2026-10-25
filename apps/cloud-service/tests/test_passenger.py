"""HTTP/CAS shared-order regressions; never contacts any external environment."""
import concurrent.futures
import datetime as dt
import uuid
import unittest
import test_cloud
from test_cloud import ACCESS, ADMIN
from common import now, stamp


class PassengerIntegration(unittest.TestCase):
    setUp=test_cloud.CloudIntegration.setUp
    tearDown=test_cloud.CloudIntegration.tearDown
    start=test_cloud.CloudIntegration.start
    request=test_cloud.CloudIntegration.request
    login=test_cloud.CloudIntegration.login
    def passenger_login(self,mobile='13800000101',server=None):
        code=self.request('/api/passenger/auth/code',{'mobile':mobile},server=server)['verificationCode']
        return self.request('/api/passenger/auth/login',{'mobile':mobile,'code':code},server=server)
    def pa(self,token,action,payload=None,rid=None,**kw):
        return self.request('/api/passenger/action',{'action':action,'payload':payload or {},'requestId':rid or str(uuid.uuid4())},token=token,**kw)
    def assert_driver_compatible(self,reply):
        import math
        stages={'AVAILABLE','BID_SENT','PREPAY','RESERVED','PICKUP','ARRIVED','TRIP','WAIT_PAY','DONE','CANCELLED'}
        kinds={'STANDARD','RESERVED','PHONE','BID','NEGOTIATED'}
        orders=reply.get('state',{}).get('orders',[])[:]
        if 'order' in reply.get('result',{}):orders.append(reply['result']['order'])
        for order in orders:
            self.assertIn(order['stage'],stages);self.assertIn(order['kind'],kinds)
            for field in ('id','origin','destination','distance','mileage','time','remark','phoneTail'):self.assertIsInstance(order[field],str)
            self.assertIs(type(order['fare']),int)
            for field in ('originLat','originLng','destinationLat','destinationLng'):self.assertIsInstance(order[field],(int,float));self.assertTrue(math.isfinite(order[field]))
        return reply
    def da(self,token,action,payload=None,**kw):
        return self.assert_driver_compatible(self.request('/api/action',{'action':action,'payload':payload or {},'requestId':kw.pop('rid',str(uuid.uuid4()))},token=token,**kw))
    def create(self,token,kind='STANDARD',**overrides):
        p={'kind':kind,'origin':'万达广场','destination':'宜昌东站','originLat':30.6687,'originLng':111.3226,'destinationLat':30.6645,'destinationLng':111.3694}
        if kind=='RESERVED':p['scheduledAt']=stamp(now()+dt.timedelta(hours=1))
        p.update(overrides);return self.pa(token,'order.create',p)['result']['order']
    def get_order(self,token,oid):
        return next(o for o in self.request('/api/passenger/state',token=token)['orders'] if o['id']==oid)
    def setup_pair(self):
        passenger=self.passenger_login();driver=self.login();self.da(driver['token'],'driver.online',{'online':True})
        return passenger,driver
    def finish(self,dtoken,oid,tail='0101',reserved=False):
        if reserved:self.da(dtoken,'order.advance',{'id':oid})
        self.da(dtoken,'order.advance',{'id':oid})
        self.da(dtoken,'order.advance',{'id':oid,'phoneTail':tail})
        self.da(dtoken,'order.advance',{'id':oid})
    def test_standard_shared_full_flow_payment_once_and_restart(self):
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];o=self.create(pt);oid=o['id']
        self.assertIn(oid,[x['id'] for x in self.request('/api/state',token=dtok)['state']['orders']])
        self.da(dtok,'order.accept',{'id':oid});self.assertEqual(self.get_order(pt,oid)['driver']['id'],d['profile']['driver']['id'])
        self.da(dtok,'order.advance',{'id':oid});self.da(dtok,'order.advance',{'id':oid,'phoneTail':'9999'},expected=400)
        self.assertEqual(self.get_order(pt,oid)['stage'],'ARRIVED')
        self.da(dtok,'order.advance',{'id':oid,'phoneTail':'0101'});self.pa(pt,'order.cancel',{'id':oid,'reason':'测试取消'},expected=409)
        self.da(dtok,'order.advance',{'id':oid});self.assertEqual(self.get_order(pt,oid)['stage'],'WAIT_PAY')
        self.pa(pt,'order.pay',{'id':oid,'outcome':'FAILED'});self.assertEqual(self.get_order(pt,oid)['stage'],'WAIT_PAY')
        self.pa(pt,'order.pay',{'id':oid,'outcome':'PENDING'});self.pa(pt,'order.pay',{'id':oid,'outcome':'SUCCESS'},rid='payment-once')
        before=self.request('/api/state',token=dtok)['state'];self.pa(pt,'order.pay',{'id':oid,'outcome':'SUCCESS'},rid='payment-once');self.pa(pt,'order.pay',{'id':oid,'outcome':'SUCCESS'})
        after=self.request('/api/state',token=dtok)['state'];self.assertEqual(after['balance'],before['balance']);self.assertEqual(after['todayCompleted'],3)
        self.assertEqual(sum(x.get('title','')=='行程收入 '+oid for x in after['entries']),1)
        self.pa(pt,'order.evaluate',{'id':oid,'rating':5,'content':'行程顺利'});self.pa(pt,'order.evaluate',{'id':oid,'rating':1},expected=409)
        self.pa(pt,'order.complaint',{'id':oid,'reason':'服务问题','content':'独立测试投诉内容'})
        reboot=self.start();restored=self.request('/api/passenger/state',token=pt,server=reboot)['orders'][0]
        self.assertEqual(restored['stage'],'DONE');self.assertEqual(restored['evaluation']['rating'],5);self.assertEqual(len(restored['complaints']),1)
    def test_bid_multiple_drivers_selection_and_prepaid_finish(self):
        p,d=self.setup_pair();pt=p['token'];d2=self.login('13800000002');self.da(d2['token'],'driver.online',{'online':True});o=self.create(pt,'BID');oid=o['id']
        self.da(d['token'],'order.bid',{'id':oid,'amount':2500});self.da(d2['token'],'order.bid',{'id':oid,'amount':2200})
        current=self.get_order(pt,oid);self.assertEqual(len(current['bids']),2);self.assertEqual(current['stage'],'AVAILABLE')
        self.pa(pt,'order.selectDriver',{'id':oid,'driverId':d2['profile']['driver']['id']});self.da(d['token'],'order.advance',{'id':oid},expected=404)
        self.assertEqual(self.get_order(pt,oid)['fare'],2200);self.pa(pt,'order.pay',{'id':oid,'outcome':'SUCCESS'})
        self.finish(d2['token'],oid);self.assertEqual(self.get_order(pt,oid)['stage'],'DONE')
        self.assertEqual(self.request('/api/state',token=d2['token'])['state']['todayCompleted'],3)
    def test_roles_ownership_sandbox_and_idempotency(self):
        p,d=self.setup_pair();pt=p['token'];other=self.passenger_login('13800000102')['token'];o=self.create(pt);oid=o['id']
        self.request('/api/state',token=pt,expected=401);self.request('/api/passenger/state',token=d['token'],expected=401)
        for action,payload in [('order.cancel',{'id':oid,'reason':'恶意取消'}),('order.pay',{'id':oid}),('order.evaluate',{'id':oid,'rating':5})]:self.pa(other,action,payload,expected=404)
        self.request('/api/passenger/orders/'+oid+'/route',token=other,expected=404)
        self.request('/api/passenger/auth/code',{'mobile':'13800000001'},expected=403)
        self.request('/api/auth/code',{'mobile':'13800000101'},expected=403)
        self.pa(pt,'profile.update',{'name':'新的昵称'},rid='same-id');self.pa(pt,'profile.update',{'name':'另个昵称'},rid='same-id',expected=409)
        self.da(d['token'],'order.accept',{'id':oid});self.da(d['token'],'order.pay',{'id':oid},expected=403)
        admin=self.start(role='admin');self.request('/api/local/admin/action',{'action':'sandbox.event','payload':{'driverId':d['profile']['driver']['id'],'orderId':oid,'event':'PASSENGER_PAY_SUCCESS'}},server=admin,admin=True,expected=403)
    def test_racing_accept_and_cas_no_dual_assignment(self):
        p,d=self.setup_pair();d2=self.login('13800000002');self.da(d2['token'],'driver.online',{'online':True});oid=self.create(p['token'])['id']
        # Competing requests at one service serialize under the same service lock.
        def accept(token):
            import json,urllib.request,urllib.error
            data=json.dumps({'action':'order.accept','payload':{'id':oid},'requestId':str(uuid.uuid4())}).encode()
            req=urllib.request.Request(f'http://127.0.0.1:{self.driver.server_port}/api/action',data=data,headers={'Content-Type':'application/json','X-Demo-Access':ACCESS,'Authorization':'Bearer '+token})
            try:return urllib.request.urlopen(req).status
            except urllib.error.HTTPError as e:return e.code
        with concurrent.futures.ThreadPoolExecutor(2) as pool:statuses=list(pool.map(accept,[d['token'],d2['token']]))
        self.assertEqual(sorted(statuses),[200,404]);self.assertIn(self.get_order(p['token'],oid)['driver']['id'],[d['profile']['driver']['id'],d2['profile']['driver']['id']])
    def test_cancel_refund_reserved_waypoints_and_external_markers(self):
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];oid=self.create(pt,'BID')['id'];self.da(dtok,'order.bid',{'id':oid,'amount':1000});self.pa(pt,'order.selectDriver',{'id':oid,'driverId':d['profile']['driver']['id']});self.pa(pt,'order.pay',{'id':oid})
        self.pa(pt,'order.cancel',{'id':oid,'reason':'计划有变'});self.assertEqual(self.get_order(pt,oid)['paymentStatus'],'REFUNDED')
        oid=self.create(pt,'RESERVED',waypoints=[{'name':'宜昌中途点','latitude':30.666,'longitude':111.34}])['id'];self.da(dtok,'order.accept',{'id':oid})
        for _ in range(2):self.da(dtok,'order.advance',{'id':oid})
        self.da(dtok,'order.advance',{'id':oid,'phoneTail':'0101'});self.da(dtok,'order.advance',{'id':oid},expected=409)
        self.da(dtok,'order.waypoint',{'id':oid,'index':0});self.da(dtok,'order.advance',{'id':oid});self.pa(pt,'order.pay',{'id':oid})
        self.assertEqual(self.request('/api/passenger/orders/'+oid+'/route',token=pt)['source'],'synthetic')
        self.assertTrue(self.request('/api/passenger/orders/'+oid+'/fees',token=pt)['sandbox'])
    def test_profile_favorites_feedback_and_block_duplicate_order(self):
        p=self.passenger_login();pt=p['token'];o=self.create(pt)
        payload={'kind':'STANDARD','origin':'测试起点','destination':'测试终点','originLat':30,'originLng':111,'destinationLat':30.1,'destinationLng':111.1}
        self.pa(pt,'order.create',payload,expected=409);self.pa(pt,'profile.update',{'city':'420100'},expected=409)
        f=self.pa(pt,'favorite.save',{'label':'家','address':'宜昌测试地址','latitude':30.66,'longitude':111.3})['result']['favorite']
        self.pa(pt,'feedback.submit',{'content':'测试反馈内容已经提交'});self.pa(pt,'favorite.remove',{'id':f['id']})
        self.pa(pt,'order.cancel',{'id':o['id'],'reason':'测试完成'});state=self.pa(pt,'profile.update',{'city':'420100','name':'新乘客昵称'})
        self.assertEqual(state['passenger']['city'],'420100');self.assertEqual(state['favorites'],[]);self.assertEqual(len(state['feedback']),1)
    def test_passenger_uncertain_commit_create_retries_once(self):
        pt=self.passenger_login()['token'];payload={'kind':'STANDARD','origin':'测试起点','destination':'测试终点','originLat':30,'originLng':111,'destinationLat':30.1,'destinationLng':111.1}
        self.remote.uncertain_write=True
        self.pa(pt,'order.create',payload,rid='uncertain-create',expected=503)
        result=self.pa(pt,'order.create',payload,rid='uncertain-create');self.assertEqual(len(result['orders']),1)
        self.assertEqual(result['result']['order']['id'],result['orders'][0]['id'])
    def test_passenger_create_validates_points_and_reservation(self):
        pt=self.passenger_login()['token'];base={'kind':'STANDARD','origin':'起点测试','destination':'终点测试','originLat':30,'originLng':111,'destinationLat':30.1,'destinationLng':111.1}
        for changes in ({'originLat':float('nan')},{'originLng':181},{'kind':'PHONE'},{'kind':'RESERVED','scheduledAt':stamp(now())},{'destinationLat':30,'destinationLng':111}):
            self.pa(pt,'order.create',{**base,**changes},expected=400)
        self.assertEqual(self.request('/api/passenger/state',token=pt)['orders'],[])
    def test_driver_bid_withdraw_reopens_and_order_update_locks_after_bid(self):
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];oid=self.create(pt,'BID')['id']
        self.pa(pt,'order.update',{'id':oid,'destination':'宜昌测试新目的地','destinationLat':30.67,'destinationLng':111.38})
        self.da(dtok,'order.bid',{'id':oid,'amount':2000})
        self.pa(pt,'order.update',{'id':oid,'destination':'不允许改目的地','destinationLat':30.68,'destinationLng':111.39},expected=409)
        self.pa(pt,'order.update',{'id':oid,'remark':'请到北门接我'})
        self.da(dtok,'order.cancel',{'id':oid,'reason':'撤回报价'});self.assertEqual(self.get_order(pt,oid)['bids'],[])
        self.da(dtok,'order.bid',{'id':oid,'amount':1900});self.assertEqual(self.get_order(pt,oid)['bids'][0]['amount'],1900)
    def test_cross_instance_race_cas_never_assigns_twice(self):
        p,d=self.setup_pair();d2=self.login('13800000002');self.da(d2['token'],'driver.online',{'online':True});oid=self.create(p['token'])['id'];other=self.start()
        # Ensure both independent instances load the same revision before mutating.
        import threading,json,urllib.request,urllib.error
        self.remote.barrier=threading.Barrier(2)
        def accept(args):
            token,server=args;req=urllib.request.Request(f'http://127.0.0.1:{server.server_port}/api/action',data=json.dumps({'action':'order.accept','payload':{'id':oid},'requestId':str(uuid.uuid4())}).encode(),headers={'Content-Type':'application/json','X-Demo-Access':ACCESS,'Authorization':'Bearer '+token})
            try:return urllib.request.urlopen(req,timeout=15).status
            except urllib.error.HTTPError as e:return e.code
        try:
            with concurrent.futures.ThreadPoolExecutor(2) as pool:statuses=list(pool.map(accept,[(d['token'],self.driver),(d2['token'],other)]))
        finally:self.remote.barrier=None
        self.assertEqual(sorted(statuses),[200,503]);order=self.get_order(p['token'],oid)
        self.assertIn(order['driver']['id'],[d['profile']['driver']['id'],d2['profile']['driver']['id']])
        owners=[]
        for token in (d['token'],d2['token']):
            owned=[x for x in self.request('/api/state',token=token)['state']['orders'] if x['id']==oid and x['stage']=='PICKUP'];owners.extend(owned)
        self.assertEqual(len(owners),1)
    def test_no_destination_pickup_requires_completion_and_confirms_price(self):
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];o=self.pa(pt,'order.create',{'kind':'STANDARD','origin':'宜昌东站','originLat':30.6687,'originLng':111.3226,'destinationPending':True})['result']['order'];oid=o['id']
        self.assertEqual(o['fare'],0);self.assertTrue(o['destinationPending']);self.da(dtok,'order.accept',{'id':oid});self.da(dtok,'order.advance',{'id':oid})
        self.da(dtok,'order.advance',{'id':oid,'phoneTail':'0101'},expected=409)
        changed=self.pa(pt,'order.update',{'id':oid,'destination':'万达广场','destinationLat':30.6645,'destinationLng':111.3694})['result']['order'];self.assertGreater(changed['fare'],0)
        self.da(dtok,'order.advance',{'id':oid,'phoneTail':'0101'});self.assertTrue(self.get_order(pt,oid)['fareConfirmed'])
        self.pa(pt,'order.update',{'id':oid,'destination':'不允许再更改','destinationLat':30.664,'destinationLng':111.36},expected=409)
    def test_driver_cancel_same_order_continue_and_rider_snapshot(self):
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];r=self.pa(pt,'rider.save',{'name':'测试乘车人','mobile':'13800000999'})['result']['rider'];o=self.create(pt,riderId=r['id']);oid=o['id']
        self.assertEqual(o['phoneTail'],'0999');self.pa(pt,'rider.remove',{'id':r['id']});self.da(dtok,'order.accept',{'id':oid});self.da(dtok,'order.cancel',{'id':oid,'reason':'车辆出现故障'})
        self.assertEqual(self.get_order(pt,oid)['stage'],'WAIT_CONTINUE');result=self.pa(pt,'order.continue',{'id':oid,'type':1});self.assertEqual(len(result['orders']),1);self.assertEqual(result['orders'][0]['id'],oid)
        self.da(dtok,'order.accept',{'id':oid});self.finish(dtok,oid,'0999');self.pa(pt,'order.pay',{'id':oid})
        title=self.pa(pt,'invoice.title.save',{'type':'PERSONAL','title':'测试乘客'})['result']['invoiceTitle'];receipt=self.pa(pt,'invoice.apply',{'orderId':oid,'titleId':title['id']})['result']['invoice']
        self.assertTrue(receipt['simulated']);self.assertEqual(receipt['status'],'SIMULATED_ISSUED');self.pa(pt,'invoice.apply',{'orderId':oid,'titleId':title['id']});self.assertEqual(len(self.request('/api/passenger/state',token=pt)['invoices']),1)
    def test_block_driver_and_complaint_attachment_integrated(self):
        from common import SAMPLE_IMAGE
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];oid=self.create(pt)['id'];self.da(dtok,'order.accept',{'id':oid});self.finish(dtok,oid);self.pa(pt,'order.pay',{'id':oid})
        o=self.pa(pt,'order.complaint',{'id':oid,'reason':'测试投诉','content':'投诉内容用于独立测试'})['result']['order'];cid=o['complaints'][0]['id']
        result=self.pa(pt,'order.complaint.attach',{'id':oid,'complaintId':cid,'imagesBase64':[SAMPLE_IMAGE]});self.assertEqual(len(result['result']['attachments']),1)
        path=result['result']['attachments'][0]['path']+'?encoding=base64';image=self.request(path,token=pt);self.assertEqual(image['imageBase64'],SAMPLE_IMAGE)
        other=self.passenger_login('13800000102')['token'];self.request(path,token=other,expected=404);self.request(path,token=dtok,expected=401)
        self.pa(pt,'order.blockDriver',{'id':oid,'reason':'测试屏蔽'});nextoid=self.create(pt)['id'];self.assertNotIn(nextoid,[o['id'] for o in self.request('/api/state',token=dtok)['state']['orders']]);self.da(dtok,'order.accept',{'id':nextoid},expected=404)
        self.pa(pt,'order.unblockDriver',{'driverId':d['profile']['driver']['id']});self.da(dtok,'order.accept',{'id':nextoid})
    def test_bid_expiration_propagates_without_sandbox(self):
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];oid=self.create(pt,'BID')['id'];self.da(dtok,'order.bid',{'id':oid,'amount':1800})
        # Time-travel only in the local test fixture, then persist via the same CAS.
        with self.driver.request_lock:
            svc=self.driver.coordinator.begin();o=svc.shared_order(oid);o['expiresAt']=stamp(now()-dt.timedelta(seconds=1));svc.save_shared(o);svc.db.commit();self.driver.coordinator.finish()
        self.assertEqual(self.get_order(pt,oid)['stage'],'CANCELLED');self.assertNotIn(oid,[o['id'] for o in self.request('/api/state',token=dtok)['state']['orders']]);self.pa(pt,'order.selectDriver',{'id':oid,'driverId':d['profile']['driver']['id']},expected=409)
    def test_select_driver_revalidates_live_driver_blacklist(self):
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];oid=self.create(pt,'BID')['id'];self.da(dtok,'order.bid',{'id':oid,'amount':1800})
        self.da(dtok,'blacklist.add',{'phoneTail':'0101','reason':'测试报价后资质重新校验'})
        self.pa(pt,'order.selectDriver',{'id':oid,'driverId':d['profile']['driver']['id']},expected=403)
        self.assertEqual(self.get_order(pt,oid)['stage'],'AVAILABLE')

    def test_driver_complaint_preserves_alias_block_attachments_and_result(self):
        from common import SAMPLE_IMAGE
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];oid=self.create(pt)['id'];self.da(dtok,'order.accept',{'id':oid});self.finish(dtok,oid);self.pa(pt,'order.pay',{'id':oid})
        payload={'id':oid,'reason':'服务争议','content':'司机投诉用户并屏蔽测试','blockUser':True,'imagesBase64':[SAMPLE_IMAGE]}
        reply=self.da(dtok,'complaint.submit',payload,rid='driver-complaint-once');complaint=reply['result']['complaint']
        self.assertEqual(complaint['orderId'],oid);self.assertEqual(len(complaint['attachmentFileIds']),1)
        self.assertEqual(len(reply['profile']['complaints']),1);self.assertEqual(len(reply['profile']['blacklist']),1)
        self.assertEqual(reply['profile']['blacklist'][0]['phoneTail'],'0101')
        self.assertEqual(self.da(dtok,'order.complaint',payload,rid='driver-complaint-once')['result']['complaint']['id'],complaint['id'])
        replay=self.da(dtok,'order.complaint',payload);self.assertEqual(len(replay['profile']['blacklist']),1)
        blocked=self.create(pt)['id'];self.da(dtok,'order.accept',{'id':blocked},expected=403)
    def test_driver_wait_continue_projection_stays_legacy_compatible(self):
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];oid=self.create(pt)['id'];self.da(dtok,'order.accept',{'id':oid})
        reply=self.da(dtok,'order.cancel',{'id':oid,'reason':'司机取消测试'})
        self.assertEqual(reply['result']['order']['stage'],'CANCELLED');self.assertEqual(self.get_order(pt,oid)['stage'],'WAIT_CONTINUE')
        driverstate=self.assert_driver_compatible(self.request('/api/state',token=dtok));self.assertEqual(next(o for o in driverstate['state']['orders'] if o['id']==oid)['stage'],'CANCELLED')
        complaint=self.da(dtok,'order.complaint',{'id':oid,'reason':'取消争议','content':'司机取消之后也可投诉','blockUser':False})
        self.assertIn('complaint',complaint['result']);self.assertEqual(complaint['profile']['blacklist'],[])
    def test_driver_bid_projection_retains_bid_timestamp(self):
        p,d=self.setup_pair();oid=self.create(p['token'],'BID')['id'];reply=self.da(d['token'],'order.bid',{'id':oid,'amount':2100})
        self.assertEqual(reply['result']['order']['stage'],'BID_SENT');self.assertEqual(reply['result']['order']['fare'],2100);self.assertTrue(reply['result']['order']['bidAt'])

    def projected_shared(self,token,oid):
        reply=self.assert_driver_compatible(self.request('/api/state',token=token))
        return next((o for o in reply['state']['orders'] if o['id']==oid),None)
    def assert_projection_stages(self,order,canonical,participation):
        self.assertIsNotNone(order);self.assertEqual(order['canonicalStage'],canonical)
        self.assertEqual(order['driverParticipationStage'],participation);self.assertEqual(order['stage'],participation)
    def test_optional_projection_stages_follow_standard_trip_without_mutating_canonical(self):
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];oid=self.create(pt)['id']
        self.assert_projection_stages(self.projected_shared(dtok,oid),'AVAILABLE','AVAILABLE')
        self.da(dtok,'order.accept',{'id':oid});self.assert_projection_stages(self.projected_shared(dtok,oid),'PICKUP','PICKUP')
        self.da(dtok,'order.advance',{'id':oid});self.assert_projection_stages(self.projected_shared(dtok,oid),'ARRIVED','ARRIVED')
        self.da(dtok,'order.advance',{'id':oid,'phoneTail':'0101'});self.assert_projection_stages(self.projected_shared(dtok,oid),'TRIP','TRIP')
        self.da(dtok,'order.advance',{'id':oid});self.assert_projection_stages(self.projected_shared(dtok,oid),'WAIT_PAY','WAIT_PAY')
        self.pa(pt,'order.pay',{'id':oid});self.assert_projection_stages(self.projected_shared(dtok,oid),'DONE','DONE')
        canonical=self.get_order(pt,oid);self.assertNotIn('canonicalStage',canonical);self.assertNotIn('driverParticipationStage',canonical)
        private=next(o for o in self.request('/api/state',token=dtok)['state']['orders'] if not o.get('shared'))
        self.assertNotIn('canonicalStage',private);self.assertNotIn('driverParticipationStage',private)
    def test_optional_projection_stages_bid_selection_and_loser_visibility(self):
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];d2=self.login('13800000002');t2=d2['token'];self.da(t2,'driver.online',{'online':True});oid=self.create(pt,'BID')['id']
        self.da(dtok,'order.bid',{'id':oid,'amount':2100});self.assert_projection_stages(self.projected_shared(dtok,oid),'AVAILABLE','BID_SENT');self.assert_projection_stages(self.projected_shared(t2,oid),'AVAILABLE','AVAILABLE')
        self.da(t2,'order.bid',{'id':oid,'amount':2300});self.assert_projection_stages(self.projected_shared(t2,oid),'AVAILABLE','BID_SENT')
        self.pa(pt,'order.selectDriver',{'id':oid,'driverId':d['profile']['driver']['id']})
        self.assert_projection_stages(self.projected_shared(dtok,oid),'PREPAY','PREPAY');self.assertIsNone(self.projected_shared(t2,oid))
        self.assertEqual(self.get_order(pt,oid)['stage'],'PREPAY')
        self.pa(pt,'order.cancel',{'id':oid,'reason':'乘客取消已选单'});self.assert_projection_stages(self.projected_shared(dtok,oid),'CANCELLED','CANCELLED');self.assertIsNone(self.projected_shared(t2,oid))
    def test_optional_projection_distinguishes_continuation_from_final_cancellation(self):
        p,d=self.setup_pair();pt,dtok=p['token'],d['token'];oid=self.create(pt)['id'];self.da(dtok,'order.accept',{'id':oid})
        reply=self.da(dtok,'order.cancel',{'id':oid,'reason':'司机退出等待续叫'})
        self.assert_projection_stages(reply['result']['order'],'WAIT_CONTINUE','CANCELLED')
        self.assert_projection_stages(self.projected_shared(dtok,oid),'WAIT_CONTINUE','CANCELLED')
        self.assertEqual(self.get_order(pt,oid)['stage'],'WAIT_CONTINUE')
        self.pa(pt,'order.continue',{'id':oid,'type':2});self.assert_projection_stages(self.projected_shared(dtok,oid),'CANCELLED','CANCELLED')
        other=self.create(pt)['id'];self.pa(pt,'order.cancel',{'id':other,'reason':'取消未分配订单'});self.assertIsNone(self.projected_shared(dtok,other))
