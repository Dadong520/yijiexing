"""Transport availability is independent of outstanding passenger payment."""
import concurrent.futures
import json
import threading
import urllib.request
import urllib.error
import uuid
from policy_helpers import PolicyCase
from test_cloud import ACCESS
from domain import ACTIVE

class WaitPayPolicyTests(PolicyCase):
    def test_private_wait_pay_allows_new_shared_accept_and_bid_selection(self):
        p,d=self.setup_pair();old=self.private_wait_pay(d)
        self.assertEqual(old['stage'],'WAIT_PAY');self.assertFalse(old['paid']);self.assertFalse(old['credited'])
        before=json.dumps(old,sort_keys=True)
        for kind in ('STANDARD','RESERVED','BID'):
            oid=self.create(p['token'],kind)['id']
            if kind=='BID':
                self.da(d['token'],'order.bid',{'id':oid,'amount':3600})
                self.pa(p['token'],'order.selectDriver',{'id':oid,'driverId':d['profile']['driver']['id']})
            else:self.da(d['token'],'order.accept',{'id':oid})
            self.assertEqual(json.dumps(self.projected(d['token'],old['id']),sort_keys=True),before)
            self.pa(p['token'],'order.cancel',{'id':oid,'reason':'结束本地测试'})

    def test_shared_debt_all_kinds_and_negotiation_blocked_until_paid(self):
        p,d,old=self.shared_wait_pay();self.enable_negotiation();neg=self.negotiate(d)
        offer=self.da(d['token'],'order.pairOffer',{'id':neg['id']})['result']
        for kind in ('STANDARD','RESERVED','BID'):
            result=self.pa(p['token'],'order.create',{'kind':kind},expected=409)
            self.assertEqual(result['details']['reason'],'PASSENGER_OUTSTANDING_PAYMENT')
        self.pa(p['token'],'negotiation.claim',{'pairCode':offer['pairCode']},expected=409)
        self.da(d['token'],'driver.online',{'online':False})
        again=self.passenger_login()['token'];self.pa(again,'order.create',{'kind':'STANDARD'},expected=409)
        self.assertEqual(self.get_order(again,old['id'])['stage'],'WAIT_PAY')
        self.da(d['token'],'driver.online',{'online':True})
        self.pa(again,'order.pay',{'id':old['id']})
        self.assertEqual(self.projected(d['token'],neg['id'])['stage'],'PREPAY')
        self.assertEqual(self.pa(again,'negotiation.claim',{'pairCode':offer['pairCode']})['result']['order']['id'],neg['id'])

    def test_shared_wait_pay_allows_private_then_multiple_shared_wait_pay(self):
        p,d,a=self.shared_wait_pay();dt=d['token']
        self.da(dt,'order.accept',{'id':'YC1001'});self.da(dt,'order.cancel',{'id':'YC1001','reason':'结束私有测试'})
        p2=self.passenger_login('13800000102');b=self.create(p2['token']);self.da(dt,'order.accept',{'id':b['id']});self.finish(dt,b['id'],'0102')
        p3=self.passenger_login('13800000103');c=self.create(p3['token']);self.da(dt,'order.accept',{'id':c['id']})
        state=self.state(dt);self.assertEqual(sum(o['stage']=='WAIT_PAY' for o in state['orders']),2)
        self.assertEqual([o['id'] for o in state['orders'] if o['stage'] in ACTIVE],[c['id']])
        self.assertEqual(self.get_order(p['token'],a['id'])['stage'],'WAIT_PAY')

    def test_late_payment_and_uncertain_retry_preserve_new_trip_and_credit_once(self):
        p,d,a=self.shared_wait_pay();p2=self.passenger_login('13800000102');b=self.create(p2['token']);dt=d['token'];self.da(dt,'order.accept',{'id':b['id']})
        self.da(dt,'order.advance',{'id':b['id']});self.da(dt,'order.advance',{'id':b['id'],'phoneTail':'0102'})
        original=self.get_order(p2['token'],b['id']);balance=self.state(dt)['balance']
        self.remote.uncertain_write=True
        payload={'id':a['id'],'outcome':'SUCCESS','expectedFare':a['fare'],'fareVersion':0}
        self.pa(p['token'],'order.pay',payload,rid='late-payment',expected=503)
        self.pa(p['token'],'order.pay',payload,rid='late-payment');self.pa(p['token'],'order.pay',payload)
        state=self.state(dt);self.assertEqual(self.get_order(p2['token'],b['id']),original)
        self.assertEqual(state['balance'],balance+a['fare']);self.assertEqual([o['id'] for o in state['orders'] if o['stage'] in ACTIVE],[b['id']])
        self.assertEqual(sum(e['title']=='行程收入 '+a['id'] for e in state['entries']),1)
        reboot=self.start();restored=self.request('/api/state',token=dt,server=reboot)['state'];self.assertEqual([o['id'] for o in restored['orders'] if o['stage'] in ACTIVE],[b['id']])

    def test_one_driver_cannot_concurrently_accept_two_new_orders_with_old_debt(self):
        p,d=self.setup_pair();self.private_wait_pay(d);p2=self.passenger_login('13800000102');ids=[self.create(p['token'])['id'],self.create(p2['token'])['id']]
        def accept(oid):
            req=urllib.request.Request(f'http://127.0.0.1:{self.driver.server_port}/api/action',data=json.dumps({'action':'order.accept','payload':{'id':oid},'requestId':str(uuid.uuid4())}).encode(),headers={'Content-Type':'application/json','X-Demo-Access':ACCESS,'Authorization':'Bearer '+d['token']})
            try:
                with urllib.request.urlopen(req,timeout=15) as response:return response.status
            except urllib.error.HTTPError as exc:return exc.code
        with concurrent.futures.ThreadPoolExecutor(2) as pool:statuses=list(pool.map(accept,ids))
        self.assertEqual(sorted(statuses),[200,409]);self.assertEqual(sum(o['stage'] in ACTIVE for o in self.state(d['token'])['orders']),1)

    def test_cross_instance_accept_conflict_with_private_debt_retains_one_assignment(self):
        p,d=self.setup_pair();self.private_wait_pay(d);p2=self.passenger_login('13800000102');ids=[self.create(p['token'])['id'],self.create(p2['token'])['id']];other=self.start()
        self.remote.barrier=threading.Barrier(2)
        def accept(pair):
            oid,server=pair;req=urllib.request.Request(f'http://127.0.0.1:{server.server_port}/api/action',data=json.dumps({'action':'order.accept','payload':{'id':oid},'requestId':str(uuid.uuid4())}).encode(),headers={'Content-Type':'application/json','X-Demo-Access':ACCESS,'Authorization':'Bearer '+d['token']})
            try:
                with urllib.request.urlopen(req,timeout=15) as response:return response.status
            except urllib.error.HTTPError as exc:return exc.code
        try:
            with concurrent.futures.ThreadPoolExecutor(2) as pool:statuses=list(pool.map(accept,zip(ids,[self.driver,other])))
        finally:self.remote.barrier=None
        self.assertEqual(sorted(statuses),[200,503]);self.assertEqual(sum(o['stage'] in ACTIVE for o in self.state(d['token'])['orders']),1)
        self.assertEqual(self.projected(d['token'],'YC1001')['stage'],'WAIT_PAY')

    def test_cross_instance_late_payment_and_accept_retry_preserve_both_orders(self):
        p,d,a=self.shared_wait_pay();p2=self.passenger_login('13800000102');b=self.create(p2['token']);other=self.start();balance=self.state(d['token'])['balance']
        calls=[(self.driver,'/api/passenger/action',p['token'],{'action':'order.pay','payload':{'id':a['id'],'expectedFare':a['fare'],'fareVersion':0},'requestId':'pay-cas'}),
               (other,'/api/action',d['token'],{'action':'order.accept','payload':{'id':b['id']},'requestId':'accept-cas'})]
        def invoke(call):
            server,path,token,payload=call
            req=urllib.request.Request(f'http://127.0.0.1:{server.server_port}'+path,data=json.dumps(payload).encode(),headers={'Content-Type':'application/json','X-Demo-Access':ACCESS,'Authorization':'Bearer '+token})
            try:
                with urllib.request.urlopen(req,timeout=15) as response:return response.status
            except urllib.error.HTTPError as exc:return exc.code
        self.remote.barrier=threading.Barrier(2)
        try:
            with concurrent.futures.ThreadPoolExecutor(2) as pool:statuses=list(pool.map(invoke,calls))
        finally:self.remote.barrier=None
        self.assertEqual(sorted(statuses),[200,503])
        # Retry both exact requests: the winning write replays; the loser reads the new snapshot.
        for call in calls:self.assertEqual(invoke(call),200)
        self.assertEqual(self.get_order(p['token'],a['id'])['stage'],'DONE')
        self.assertEqual(self.get_order(p2['token'],b['id'])['stage'],'PICKUP')
        state=self.state(d['token']);self.assertEqual([o['id'] for o in state['orders'] if o['stage'] in ACTIVE],[b['id']])
        self.assertEqual(state['balance'],balance+a['fare']);self.assertEqual(sum(e['title']=='行程收入 '+a['id'] for e in state['entries']),1)

    def test_negotiation_revalidates_certification_on_create_and_claim(self):
        p,d=self.setup_pair();self.enable_negotiation();did=d['profile']['driver']['id']
        def certified(value):
            def update(svc):
                profile=json.loads(svc.account(did)['profile']);profile['driver']['certificationStatus']=value;svc.save_profile(did,profile)
            self.mutate_fixture(update)
        certified('PENDING');self.da(d['token'],'order.negotiate',{'origin':'现场起点','destination':'现场终点','amount':2800},expected=403)
        certified('APPROVED');o=self.negotiate(d);offer=self.da(d['token'],'order.pairOffer',{'id':o['id']})['result'];certified('REJECTED')
        self.pa(p['token'],'negotiation.claim',{'pairCode':offer['pairCode']},expected=403)
        certified('APPROVED');self.da(d['token'],'driver.online',{'online':False});self.pa(p['token'],'negotiation.claim',{'pairCode':offer['pairCode']},expected=409)
        self.da(d['token'],'driver.online',{'online':True});self.assertEqual(self.pa(p['token'],'negotiation.claim',{'pairCode':offer['pairCode']})['result']['order']['stage'],'PREPAY')

    def test_missing_mutation_request_id_rejected_without_advancing_private_trip(self):
        p,d=self.setup_pair();dt=d['token'];self.da(dt,'order.accept',{'id':'YC1001'})
        for rid in (None,'',True):
            self.request('/api/action',{'action':'order.advance','payload':{'id':'YC1001'},'requestId':rid},token=dt,expected=400)
        self.assertEqual(self.projected(dt,'YC1001')['stage'],'PICKUP')
        self.da(dt,'order.advance',{'id':'YC1001'},rid='advance-once');self.da(dt,'order.advance',{'id':'YC1001'},rid='advance-once')
        self.assertEqual(self.projected(dt,'YC1001')['stage'],'ARRIVED')
