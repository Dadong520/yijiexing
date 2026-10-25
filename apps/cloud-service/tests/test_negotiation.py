"""Opt-in pairing regressions using isolated HTTP, SQLite and in-memory cloud CAS."""
import concurrent.futures
import datetime as dt
import hashlib
import json
import re
import threading
import unittest
import urllib.error
import urllib.request
import uuid
import test_cloud
import test_passenger
from common import now, stamp


class NegotiationIntegration(unittest.TestCase):
    setUp=test_cloud.CloudIntegration.setUp
    tearDown=test_cloud.CloudIntegration.tearDown
    start=test_cloud.CloudIntegration.start
    request=test_cloud.CloudIntegration.request
    login=test_cloud.CloudIntegration.login
    pa=test_passenger.PassengerIntegration.pa
    da=test_passenger.PassengerIntegration.da
    passenger_login=test_passenger.PassengerIntegration.passenger_login
    get_order=test_passenger.PassengerIntegration.get_order
    finish=test_passenger.PassengerIntegration.finish
    assert_driver_compatible=test_passenger.PassengerIntegration.assert_driver_compatible

    def offer(self,mobile='13800000001',amount=2800):
        d=self.login(mobile);token=d['token'];self.da(token,'profile.update',{'city':'420100'});self.da(token,'driver.online',{'online':True})
        oid=self.da(token,'order.negotiate',{'origin':'武汉起点','destination':'武汉终点','originLat':30.59,'originLng':114.30,'destinationLat':30.60,'destinationLng':114.32,'amount':amount})['result']['order']['id']
        return d,oid
    def pair(self,mobile='13800000001'):
        p=self.passenger_login();d,oid=self.offer(mobile);offer=self.da(d['token'],'order.pairOffer',{'id':oid})['result']
        order=self.pa(p['token'],'negotiation.claim',{'pairCode':offer['pairCode']})['result']['order'];return p,d,order,offer

    def test_pair_claim_real_shared_trip_and_single_credit(self):
        p,d,o,offer=self.pair();pt,dtok=p['token'],d['token'];oid=o['id']
        self.assertEqual(o['kind'],'NEGOTIATED');self.assertEqual(o['stage'],'PREPAY');self.assertEqual(o['quoteVersion'],1)
        self.assertTrue(o['shared']);self.assertFalse(o['synthetic']);self.assertEqual(o['distanceEnforcement'],'PASSENGER_CONSENT_REQUIRED');self.assertNotIn('distanceEnforcementNotice',o);self.assertEqual(len(offer['pairCode']),32);self.assertTrue(offer['pairUri'].startswith('yijiexing://negotiation?code='))
        state=self.request('/api/state',token=dtok)['state'];self.assertEqual(sum(x['id']==oid for x in state['orders']),1)
        pay={'id':oid,'quoteVersion':1,'amount':o['fare'],'outcome':'SUCCESS'}
        result=self.pa(pt,'order.pay',pay,rid='neg-pay');self.assertEqual(result['result']['order']['stage'],'PICKUP')
        self.finish(dtok,oid);self.assertEqual(self.get_order(pt,oid)['stage'],'DONE')
        before=self.request('/api/state',token=dtok)['state'];self.pa(pt,'order.pay',pay,rid='neg-pay');self.pa(pt,'order.pay',pay)
        after=self.request('/api/state',token=dtok)['state'];self.assertEqual(after['balance'],before['balance']);self.assertEqual(after['todayCompleted'],3)
        self.pa(pt,'order.evaluate',{'id':oid,'rating':5,'content':'现场议价体验完成'});self.pa(pt,'order.complaint',{'id':oid,'reason':'费用问题','content':'独立配对行程测试投诉'})
        reboot=self.start();restored=self.request('/api/passenger/state',token=pt,server=reboot)['orders'][0];self.assertEqual(restored['stage'],'DONE')

    def test_dispute_requote_requires_explicit_current_amount_version(self):
        p,d,o,_=self.pair();pt,dtok=p['token'],d['token'];oid=o['id'];pay={'id':oid,'quoteVersion':1,'amount':2800,'outcome':'SUCCESS'}
        self.pa(pt,'order.pay',{'id':oid},expected=409);self.pa(pt,'order.pay',{**pay,'amount':1},expected=409)
        self.pa(pt,'order.pay',{**pay,'outcome':'PENDING'})
        disputed=self.pa(pt,'negotiation.dispute',{'id':oid,'quoteVersion':1,'content':'希望调整现场报价'})['result']['order'];self.assertEqual(disputed['negotiationStatus'],'DISPUTED')
        self.pa(pt,'order.pay',pay,expected=409)
        quote={'id':oid,'quoteVersion':1,'amount':2500};requoted=self.da(dtok,'order.requote',quote,rid='requote-once')['result']['order'];self.assertEqual(requoted['quoteVersion'],2);self.assertEqual(requoted['paymentStatus'],'UNPAID')
        self.assertEqual(self.da(dtok,'order.requote',quote,rid='requote-once')['result']['order']['quoteVersion'],2)
        self.da(dtok,'order.requote',quote,expected=409);self.pa(pt,'order.pay',pay,expected=409)
        for outcome in ('FAILED','PENDING','CANCEL'):
            self.pa(pt,'order.pay',{'id':oid,'quoteVersion':2,'amount':2500,'outcome':outcome});self.assertEqual(self.get_order(pt,oid)['stage'],'PREPAY')
        self.pa(pt,'order.pay',{'id':oid,'quoteVersion':2,'amount':2500,'outcome':'SUCCESS'});self.da(dtok,'order.requote',{'id':oid,'quoteVersion':2,'amount':2400},expected=409)
        self.assertEqual(self.get_order(pt,oid)['fare'],2500)

    def test_pair_code_rotation_expiry_claim_and_ownership(self):
        d,oid=self.offer();dtok=d['token'];pt=self.passenger_login()['token'];other=self.passenger_login('13800000102')['token']
        first=self.da(dtok,'order.pairOffer',{'id':oid},rid='offer-first')['result'];self.assertEqual(first,self.da(dtok,'order.pairOffer',{'id':oid},rid='offer-first')['result'])
        second=self.da(dtok,'order.pairOffer',{'id':oid})['result'];self.assertNotEqual(first['pairCode'],second['pairCode'])
        self.pa(pt,'negotiation.claim',{'pairCode':first['pairCode']},expected=409);self.pa(pt,'negotiation.claim',{'pairCode':oid},expected=400)
        claimed=self.pa(pt,'negotiation.claim',{'pairCode':second['pairCode']})['result']['order'];self.assertEqual(claimed['id'],oid)
        self.assertTrue(self.pa(pt,'negotiation.claim',{'pairCode':second['pairCode']})['result']['idempotent'])
        self.pa(other,'negotiation.claim',{'pairCode':second['pairCode']},expected=409);self.pa(other,'negotiation.dispute',{'id':oid,'quoteVersion':1,'content':'恶意修改报价'},expected=404)
        stranger=self.login('13800000002');self.da(stranger['token'],'order.requote',{'id':oid,'quoteVersion':1,'amount':1000},expected=404)
        self.da(dtok,'order.pairOffer',{'id':oid},expected=409)

    def test_expired_or_cancelled_private_offer_cannot_be_claimed(self):
        d,oid=self.offer();pt=self.passenger_login()['token'];offer=self.da(d['token'],'order.pairOffer',{'id':oid})['result']
        with self.driver.request_lock:
            svc=self.driver.coordinator.begin();svc.db.execute('UPDATE negotiation_offers SET expires=? WHERE code_hash=?',(stamp(now()-dt.timedelta(seconds=1)),hashlib.sha256(offer['pairCode'].encode()).hexdigest()));svc.db.commit();self.driver.coordinator.finish()
        self.pa(pt,'negotiation.claim',{'pairCode':offer['pairCode']},expected=409)
        fresh=self.da(d['token'],'order.pairOffer',{'id':oid})['result'];self.da(d['token'],'order.cancel',{'id':oid,'reason':'现场不再乘车'})
        self.pa(pt,'negotiation.claim',{'pairCode':fresh['pairCode']},expected=409)

    def test_old_sandbox_behavior_unchanged_until_pair_offer(self):
        d,oid=self.offer();admin=self.start(role='admin');event={'action':'sandbox.event','payload':{'driverId':d['profile']['driver']['id'],'orderId':oid,'event':'PASSENGER_PAY_SUCCESS'}}
        self.request('/api/local/admin/action',event,server=admin,admin=True);self.assertEqual(next(x for x in self.request('/api/state',token=d['token'])['state']['orders'] if x['id']==oid)['stage'],'TRIP')
        self.da(d['token'],'order.advance',{'id':oid});d2,oid2=self.offer('13800000002');self.da(d2['token'],'order.pairOffer',{'id':oid2})
        event['payload'].update(driverId=d2['profile']['driver']['id'],orderId=oid2)
        self.request('/api/local/admin/action',event,server=admin,admin=True,expected=403)

    def test_paired_driver_cancel_is_final_and_does_not_enter_marketplace(self):
        p,d,o,_=self.pair();oid=o['id'];reply=self.da(d['token'],'order.cancel',{'id':oid,'reason':'现场协商取消'})
        self.assertEqual(reply['result']['order']['stage'],'CANCELLED');self.assertEqual(self.get_order(p['token'],oid)['stage'],'CANCELLED')
        self.pa(p['token'],'order.continue',{'id':oid,'type':1},expected=409)
        d2=self.login('13800000002');self.assertNotIn(oid,[x['id'] for x in d2['state']['orders']])

    def test_two_passengers_compete_for_single_offer(self):
        d,oid=self.offer();offer=self.da(d['token'],'order.pairOffer',{'id':oid})['result'];p1=self.passenger_login();p2=self.passenger_login('13800000102')
        def claim(token):
            req=urllib.request.Request(f'http://127.0.0.1:{self.driver.server_port}/api/passenger/action',data=json.dumps({'action':'negotiation.claim','payload':{'pairCode':offer['pairCode']},'requestId':str(uuid.uuid4())}).encode(),headers={'Content-Type':'application/json','X-Demo-Access':test_cloud.ACCESS,'Authorization':'Bearer '+token})
            try:return urllib.request.urlopen(req).status
            except urllib.error.HTTPError as exc:return exc.code
        with concurrent.futures.ThreadPoolExecutor(2) as pool:statuses=list(pool.map(claim,[p1['token'],p2['token']]))
        self.assertEqual(sorted(statuses),[200,409]);count=sum(len(self.request('/api/passenger/state',token=p['token'])['orders']) for p in (p1,p2));self.assertEqual(count,1)

    def test_uncertain_claim_commit_is_recoverable_without_duplicate_private_order(self):
        d,oid=self.offer();offer=self.da(d['token'],'order.pairOffer',{'id':oid})['result'];pt=self.passenger_login()['token'];self.remote.uncertain_write=True
        self.pa(pt,'negotiation.claim',{'pairCode':offer['pairCode']},rid='uncertain-claim',expected=503)
        result=self.pa(pt,'negotiation.claim',{'pairCode':offer['pairCode']},rid='uncertain-claim');self.assertEqual(len(result['orders']),1)
        self.assertEqual(sum(o['id']==oid for o in self.request('/api/state',token=d['token'])['state']['orders']),1)
    def test_cross_instance_claim_cas_has_only_one_owner(self):
        d,oid=self.offer();offer=self.da(d['token'],'order.pairOffer',{'id':oid})['result'];p1=self.passenger_login();p2=self.passenger_login('13800000102');other=self.start()
        self.remote.barrier=threading.Barrier(2)
        def claim(args):
            token,server=args
            req=urllib.request.Request(f'http://127.0.0.1:{server.server_port}/api/passenger/action',data=json.dumps({'action':'negotiation.claim','payload':{'pairCode':offer['pairCode']},'requestId':'competing-claim'}).encode(),headers={'Content-Type':'application/json','X-Demo-Access':test_cloud.ACCESS,'Authorization':'Bearer '+token})
            try:return urllib.request.urlopen(req,timeout=15).status
            except urllib.error.HTTPError as exc:return exc.code
        try:
            with concurrent.futures.ThreadPoolExecutor(2) as pool:statuses=list(pool.map(claim,[(p1['token'],self.driver),(p2['token'],other)]))
        finally:self.remote.barrier=None
        self.assertEqual(sorted(statuses),[200,503]);loser=(p1,p2)[statuses.index(503)];self.pa(loser['token'],'negotiation.claim',{'pairCode':offer['pairCode']},rid='competing-claim',expected=409)
        count=sum(len(self.request('/api/passenger/state',token=p['token'])['orders']) for p in (p1,p2));self.assertEqual(count,1)

    def test_payment_and_requote_race_preserves_agreed_price(self):
        p,d,o,_=self.pair();oid=o['id']
        operations=[(p['token'],'/api/passenger/action','order.pay',{'id':oid,'quoteVersion':1,'amount':2800,'outcome':'SUCCESS'}),(d['token'],'/api/action','order.requote',{'id':oid,'quoteVersion':1,'amount':2500})]
        def send(args):
            token,path,action,payload=args
            req=urllib.request.Request(f'http://127.0.0.1:{self.driver.server_port}{path}',data=json.dumps({'action':action,'payload':payload,'requestId':str(uuid.uuid4())}).encode(),headers={'Content-Type':'application/json','X-Demo-Access':test_cloud.ACCESS,'Authorization':'Bearer '+token})
            try:return urllib.request.urlopen(req).status
            except urllib.error.HTTPError as exc:return exc.code
        with concurrent.futures.ThreadPoolExecutor(2) as pool:statuses=list(pool.map(send,operations))
        self.assertEqual(sorted(statuses),[200,409]);order=self.get_order(p['token'],oid)
        if statuses[0]==200:self.assertEqual((order['stage'],order['fare'],order['quoteVersion']),('PICKUP',2800,1))
        else:self.assertEqual((order['stage'],order['fare'],order['quoteVersion']),('PREPAY',2500,2));self.assertFalse(order['paid'])

    def test_pairing_requires_no_active_passenger_order_and_blocks_fake_code(self):
        p=self.passenger_login();pt=p['token'];d,oid=self.offer();offer=self.da(d['token'],'order.pairOffer',{'id':oid})['result']
        active=self.pa(pt,'order.create',{'origin':'测试起点','destination':'测试终点','originLat':30.59,'originLng':114.3,'destinationLat':30.60,'destinationLng':114.32})['result']['order']
        self.pa(pt,'negotiation.claim',{'pairCode':offer['pairCode']},expected=409);self.pa(pt,'negotiation.claim',{'pairCode':'a'*32},expected=404)
        self.pa(pt,'order.cancel',{'id':active['id'],'reason':'准备现场配对'})
        self.pa(pt,'negotiation.claim',{'pairCode':offer['pairCode']})

    def test_private_pairing_projection_recovers_after_restart_without_invitation(self):
        d,oid=self.offer();dtok=d['token'];private=next(o for o in self.request('/api/state',token=dtok)['state']['orders'] if o['id']==oid)
        self.assertNotIn('negotiationPairingRequired',private);self.assertNotIn('pairingExpiresAt',private)
        reply=self.da(dtok,'order.pairOffer',{'id':oid});offer=reply['result'];projected=next(o for o in reply['state']['orders'] if o['id']==oid)
        self.assertIs(projected['negotiationPairingRequired'],True);self.assertEqual(projected['pairingExpiresAt'],offer['expiresAt']);self.assertEqual(projected['stage'],'PREPAY')
        reboot=self.start();state=self.request('/api/state',token=dtok,server=reboot);encoded=json.dumps(state)
        self.assertNotIn(offer['pairCode'],encoded);self.assertNotIn(offer['pairUri'],encoded);self.assertNotIn('pairCode',encoded);self.assertNotIn('pairUri',encoded)
        restored=next(o for o in state['state']['orders'] if o['id']==oid);self.assertTrue(restored['negotiationPairingRequired'])
        with self.driver.request_lock:
            svc=self.driver.coordinator.begin();svc.db.execute('UPDATE negotiation_offers SET expires=? WHERE driver=? AND order_id=?',(stamp(now()-dt.timedelta(seconds=1)),d['profile']['driver']['id'],oid));svc.db.commit();self.driver.coordinator.finish()
        expired=next(o for o in self.request('/api/state',token=dtok)['state']['orders'] if o['id']==oid);self.assertTrue(expired['negotiationPairingRequired'])
        refreshed=self.da(dtok,'order.pairOffer',{'id':oid})['result'];pt=self.passenger_login()['token'];claimed=self.pa(pt,'negotiation.claim',{'pairCode':refreshed['pairCode']})['result']['order']
        self.assertEqual(claimed['negotiationStatus'],'OFFERED');self.assertNotIn('negotiationPairingRequired',claimed);self.assertNotIn('pairingExpiresAt',claimed)
        shared=next(o for o in self.request('/api/state',token=dtok)['state']['orders'] if o['id']==oid);self.assertNotIn('negotiationPairingRequired',shared);self.assertNotIn('pairingExpiresAt',shared)
        self.assertEqual(shared['canonicalStage'],'PREPAY');self.assertEqual(shared['driverParticipationStage'],'PREPAY')
    def test_cancelled_private_pairing_has_no_active_pairing_projection(self):
        d,oid=self.offer();dtok=d['token'];self.da(dtok,'order.pairOffer',{'id':oid});self.da(dtok,'order.cancel',{'id':oid,'reason':'配对前取消订单'})
        cancelled=next(o for o in self.request('/api/state',token=dtok)['state']['orders'] if o['id']==oid)
        self.assertEqual(cancelled['stage'],'CANCELLED');self.assertNotIn('negotiationPairingRequired',cancelled);self.assertNotIn('pairingExpiresAt',cancelled)
