"""Coordinator independent contract checks. Real local HTTP/CAS, synthetic locations only.
Does not import the distance author's fixtures or bypass the production distance check.
"""
import sys,json,time,math,uuid,unittest,pathlib,datetime
API=pathlib.Path('/Users/dongge/.codex/tmp/yijiexing-trip-api-20261025/apps/cloud-service')
sys.path[:0]=[str(API),str(API/'tests')]
from test_public_experience import PublicExperienceHTTP,ADMIN

class IndependentDistance(PublicExperienceHTTP):
    def action(self,who,name,payload,rid=None,status=200):
        prefix='/api/action' if who=='d' else '/api/passenger/action'
        return self.request(prefix,{'action':name,'payload':payload,'requestId':rid or str(uuid.uuid4())},token=self.d['token'] if who=='d' else self.p['token'],status=status)
    def order(self):
        return next(o for o in self.request('/api/passenger/state',token=self.p['token'])['orders'] if o['id']==self.oid)
    def fresh(self,meters=0):
        # Northwards along the same longitude: independent spherical construction.
        lat=30.6687+meters/(6371000*math.pi/180)
        point={'latitude':lat,'longitude':111.3226,'accuracy':5,'time':int(time.time()*1000),'coordinateSystem':'WGS84'}
        self.action('d','location.report',point)
        self.action('p','location.report',{**point,'id':self.oid,'latitude':30.6687})
    def begin_order(self):
        self.d=self.login();self.p=self.login('passenger')
        self.action('d','driver.online',{'online':True})
        self.oid=self.action('p','order.create',{'kind':'STANDARD','origin':'独立测试上车点','destination':'独立测试目的地','originLat':30.6687,'originLng':111.3226,'destinationLat':30.7,'destinationLng':111.35})['result']['order']['id']
        self.action('d','order.accept',{'id':self.oid})
        self.action('d','order.advance',{'id':self.oid,'expectedStage':'PICKUP'})
        self.assertEqual(self.order()['stage'],'ARRIVED')
    def pickup(self,**kw):
        return self.action('d','order.advance',{'id':self.oid,'expectedStage':'ARRIVED','phoneTail':'0102'},**kw)
    def decide(self,consent,approve=True,**kw):
        return self.action('p','order.distanceConsent',{'id':self.oid,'consentId':consent,'approve':approve},**kw)
    def test_missing_gps_pending_then_same_consent_cannot_double_advance_or_credit(self):
        self.begin_order();balance=self.request('/api/state',token=self.d['token'])['state']['balance']
        self.pickup(rid='pickup-identical');o=self.order();self.assertEqual(o['stage'],'ARRIVED');c=o['distanceConsent'];self.assertEqual(c['status'],'PENDING');self.assertIsNone(c['distanceMeters'])
        self.pickup(rid='pickup-identical');self.pickup();self.assertEqual(self.order()['distanceConsent']['id'],c['id'])
        other=self.request('/api/passenger/auth/code',{'mobile':'13800000101'})['verificationCode'];ot=self.request('/api/passenger/auth/login',{'mobile':'13800000101','code':other})['token']
        self.request('/api/passenger/action',{'action':'order.distanceConsent','payload':{'id':self.oid,'consentId':c['id'],'approve':True},'requestId':'attacker'},token=ot,status=404)
        self.decide(c['id']);self.assertEqual(self.order()['stage'],'TRIP');self.decide(c['id']);self.assertEqual(self.order()['stage'],'TRIP');self.decide(c['id'],False,status=409)
        self.pickup(status=409)
        self.action('d','order.advance',{'id':self.oid,'expectedStage':'TRIP'});o=self.order();self.assertEqual(o['stage'],'TRIP');end=o['distanceConsent'];self.assertEqual(end['type'],'DROPOFF')
        self.decide(end['id']);self.decide(end['id']);self.assertEqual(self.order()['stage'],'WAIT_PAY');self.assertEqual(self.request('/api/state',token=self.d['token'])['state']['balance'],balance)
        self.action('p','order.create',{'kind':'STANDARD','origin':'不得重复下单','destination':'测试终点','originLat':30.6687,'originLng':111.3226,'destinationLat':30.7,'destinationLng':111.35},status=409)
        b=self.request('/api/passenger/action',{'action':'order.create','payload':{'kind':'STANDARD','origin':'其他乘客上车点','destination':'其他乘客终点','originLat':30.6687,'originLng':111.3226,'destinationLat':30.7,'destinationLng':111.35},'requestId':'other-order'},token=ot)['result']['order']
        self.action('d','order.accept',{'id':b['id']});self.assertTrue(any(x['id']==b['id'] and x['stage']=='PICKUP' for x in self.request('/api/state',token=self.d['token'])['state']['orders']))
        self.action('p','order.pay',{'id':self.oid,'outcome':'SUCCESS'},rid='pay-once');self.action('p','order.pay',{'id':self.oid,'outcome':'SUCCESS'},rid='pay-once');self.action('p','order.pay',{'id':self.oid,'outcome':'SUCCESS'})
        o=self.order();s=self.request('/api/state',token=self.d['token'])['state'];self.assertEqual(o['stage'],'DONE');self.assertEqual(s['balance'],balance+o['fare']);self.assertEqual(sum(x.get('title')=='行程收入 '+self.oid for x in s['entries']),1)
    def test_below_and_above_300_use_server_coordinates(self):
        self.begin_order();self.fresh(299);self.pickup();self.assertEqual(self.order()['stage'],'TRIP')
        # Current driver is far from the destination, so arrival must remain pending.
        self.action('d','order.advance',{'id':self.oid,'expectedStage':'TRIP'});o=self.order();self.assertEqual(o['stage'],'TRIP');self.assertGreater(o['distanceConsent']['distanceMeters'],300)
    def test_301_rejection_preserves_stage_and_bad_tail_cannot_create_request(self):
        self.begin_order();self.fresh(301)
        self.action('d','order.advance',{'id':self.oid,'expectedStage':'ARRIVED','phoneTail':'9999'},status=400);self.assertFalse(self.order().get('distanceConsent'))
        self.pickup();o=self.order();self.assertEqual(o['stage'],'ARRIVED');self.assertGreater(o['distanceConsent']['distanceMeters'],300);cid=o['distanceConsent']['id']
        self.decide(cid,False);self.assertEqual(self.order()['stage'],'ARRIVED');self.decide(cid,True,status=409)
    def test_stale_location_not_accepted_and_future_or_invalid_rejected(self):
        self.begin_order();self.fresh(0)
        self.action('d','location.report',{'latitude':30.6687,'longitude':111.3226,'accuracy':5,'time':int((time.time()-121)*1000),'coordinateSystem':'WGS84'})
        self.pickup();self.assertEqual(self.order()['stage'],'ARRIVED');self.assertEqual(self.order()['distanceConsent']['status'],'PENDING')
        self.action('d','location.report',{'latitude':30.6687,'longitude':111.3226,'accuracy':5,'time':int((time.time()+120)*1000),'coordinateSystem':'WGS84'},status=400)
        self.action('p','location.report',{'id':self.oid,'latitude':91,'longitude':111.3226,'accuracy':5,'time':int(time.time()*1000),'coordinateSystem':'WGS84'},status=400)
    def test_cancel_invalidates_pending_consent(self):
        self.begin_order();self.pickup();c=self.order()['distanceConsent']['id'];self.action('p','order.cancel',{'id':self.oid,'reason':'独立取消测试'});self.decide(c,status=409);self.assertEqual(self.order()['stage'],'CANCELLED')

if __name__=='__main__':
    names=[name for name in IndependentDistance.__dict__ if name.startswith('test_')]
    result=unittest.TextTestRunner(verbosity=2).run(unittest.TestSuite(IndependentDistance(n) for n in names))
    print(json.dumps({'coordinatorIndependentScenarios':len(names),'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'localOnly':True,'authorDistanceFixturesUsed':False}))
    raise SystemExit(not result.wasSuccessful())
