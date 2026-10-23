"""Existing explicit certificate dates constrain only new transport contracts."""
import datetime as dt
import json
from common import now
from policy_helpers import PolicyCase

class CertificateDateTests(PolicyCase):
    def set_certificate(self,d,**fields):
        def update(svc):
            driver=d['profile']['driver']['id'];profile=json.loads(svc.account(driver)['profile'])
            profile['certificates'][0].update(fields);svc.save_profile(driver,profile)
        self.mutate_fixture(update)

    def test_expired_or_future_dates_block_private_shared_and_negotiation(self):
        p,d=self.setup_pair();self.enable_negotiation();oid=self.create(p['token'])['id'];today=now().date()
        for fields,reason in [({'validFrom':(today-dt.timedelta(days=30)).isoformat(),'validUntil':(today-dt.timedelta(days=1)).isoformat()},'CERTIFICATE_EXPIRED'),
                              ({'validFrom':(today+dt.timedelta(days=1)).isoformat(),'validUntil':(today+dt.timedelta(days=30)).isoformat()},'CERTIFICATE_NOT_EFFECTIVE')]:
            self.set_certificate(d,**fields)
            for order_id in ('YC1001',oid):
                result=self.da(d['token'],'order.accept',{'id':order_id},expected=403);self.assertEqual(result['details']['reason'],reason)
            result=self.da(d['token'],'order.negotiate',{'origin':'现场起点','destination':'现场终点','amount':2800},expected=403);self.assertEqual(result['details']['reason'],reason)
        self.assertEqual(self.get_order(p['token'],oid)['stage'],'AVAILABLE')

    def test_inclusive_today_and_legacy_missing_or_invalid_dates_remain_compatible(self):
        p,d=self.setup_pair();today=now().date().isoformat()
        for start,end in [(today,today),(None,None),('', 'LONG_TERM'),('2026-99-99','not-a-date'),('2026-10-24T00:00:00+08:00','2026/10/24')]:
            self.set_certificate(d,validFrom=start,validUntil=end)
            oid=self.create(p['token'])['id'];self.da(d['token'],'order.accept',{'id':oid});self.pa(p['token'],'order.cancel',{'id':oid,'reason':'完成本地测试'})

    def test_bid_selection_and_negotiation_claim_recheck_newly_expired_dates(self):
        p,d=self.setup_pair();self.enable_negotiation();today=now().date();oid=self.create(p['token'],'BID')['id']
        self.da(d['token'],'order.bid',{'id':oid,'amount':3000});self.set_certificate(d,validUntil=(today-dt.timedelta(days=1)).isoformat())
        self.pa(p['token'],'order.selectDriver',{'id':oid,'driverId':d['profile']['driver']['id']},expected=403)
        self.pa(p['token'],'order.cancel',{'id':oid,'reason':'结束竞价测试'})
        self.set_certificate(d,validUntil=today.isoformat());o=self.negotiate(d);offer=self.da(d['token'],'order.pairOffer',{'id':o['id']})['result']
        self.set_certificate(d,validUntil=(today-dt.timedelta(days=1)).isoformat())
        self.pa(p['token'],'negotiation.claim',{'pairCode':offer['pairCode']},expected=403)
        self.set_certificate(d,validUntil=today.isoformat());self.pa(p['token'],'negotiation.claim',{'pairCode':offer['pairCode']})

    def test_expiry_does_not_block_existing_trip_late_payment_or_offline(self):
        p,d=self.setup_pair();oid=self.create(p['token'])['id'];self.da(d['token'],'order.accept',{'id':oid})
        self.set_certificate(d,validUntil=(now().date()-dt.timedelta(days=1)).isoformat())
        self.finish(d['token'],oid);self.assertEqual(self.get_order(p['token'],oid)['stage'],'WAIT_PAY')
        self.da(d['token'],'driver.online',{'online':False});self.pa(p['token'],'order.pay',{'id':oid})
        self.assertEqual(self.get_order(p['token'],oid)['stage'],'DONE')
        self.assertEqual(sum(e['title']=='行程收入 '+oid for e in self.state(d['token'])['entries']),1)
