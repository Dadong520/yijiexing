"""Local-only v8 policy fixtures; use the existing HTTP/SQLite/MemoryCAS harness."""
import json
import unittest
from test_cloud import CloudIntegration
from test_passenger import PassengerIntegration

class PolicyCase(unittest.TestCase):
    setUp=CloudIntegration.setUp
    tearDown=CloudIntegration.tearDown
    start=CloudIntegration.start
    request=CloudIntegration.request
    login=CloudIntegration.login
    passenger_login=PassengerIntegration.passenger_login
    pa=PassengerIntegration.pa
    da=PassengerIntegration.da
    create=PassengerIntegration.create
    finish=PassengerIntegration.finish
    get_order=PassengerIntegration.get_order
    setup_pair=PassengerIntegration.setup_pair
    assert_driver_compatible=PassengerIntegration.assert_driver_compatible

    def mutate_fixture(self, callback):
        with self.driver.request_lock:
            svc=self.driver.coordinator.begin()
            try:
                result=callback(svc);svc.db.commit();self.driver.coordinator.finish();return result
            except Exception:
                svc.db.rollback();raise

    def state(self,token):return self.request('/api/state',token=token)['state']
    def projected(self,token,oid):return next(o for o in self.state(token)['orders'] if o['id']==oid)
    def shared_wait_pay(self):
        p,d=self.setup_pair();o=self.create(p['token']);self.da(d['token'],'order.accept',{'id':o['id']});self.finish(d['token'],o['id'])
        return p,d,self.get_order(p['token'],o['id'])
    def private_wait_pay(self,d):
        self.da(d['token'],'order.accept',{'id':'YC1001'});self.finish(d['token'],'YC1001','6758')
        return self.projected(d['token'],'YC1001')
    def enable_negotiation(self):
        def update(svc):
            row=svc.db.execute("SELECT published FROM configs WHERE scope='city' AND target='420500'").fetchone();cfg=json.loads(row['published'])
            for level in ('TRIAL','NORMAL','VIP'):cfg['levels'][level].setdefault('params',{})['offlineNegotiation']=True
            svc.db.execute("UPDATE configs SET published=? WHERE scope='city' AND target='420500'",(json.dumps(cfg),))
        self.mutate_fixture(update)
    def negotiate(self,d):
        return self.da(d['token'],'order.negotiate',{'origin':'现场测试起点','destination':'现场测试终点','amount':2800})['result']['order']
