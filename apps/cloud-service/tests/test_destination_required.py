"""BIZ06 blocks new no-destination trips; historical rows remain serviceable."""
import copy
from policy_helpers import PolicyCase
from common import now, stamp
import datetime as dt


class DestinationRequiredTests(PolicyCase):
    def payload(self, **extra):
        return {'kind':'STANDARD','origin':'万达广场','originLat':30.6687,'originLng':111.3226,
                'destination':'宜昌东站','destinationLat':30.6645,'destinationLng':111.3694,**extra}

    def snapshot(self):
        return self.mutate_fixture(lambda svc:{table:[tuple(r) for r in svc.db.execute(f'SELECT * FROM {table} ORDER BY rowid')]
            for table in ('passenger_orders','passenger_actions','actions','audits','accounts')})

    def test_true_is_rejected_for_all_kinds_without_order_or_idempotency_side_effects(self):
        p,d=self.setup_pair();before=self.snapshot()
        for kind in ('STANDARD','RESERVED','BID'):
            result=self.pa(p['token'],'order.create',self.payload(kind=kind,destinationPending=True),rid='reject-'+kind,expected=400)
            self.assertEqual(result['message'],'请先选择目的地后再叫车')
        self.assertEqual(self.snapshot(),before)
        # A rejected request has no cached result and does not reserve its id.
        created=self.pa(p['token'],'order.create',self.payload(),rid='reject-STANDARD')['result']['order']
        self.assertFalse(created['destinationPending']);self.assertGreater(created['fare'],0)

    def test_missing_invalid_and_same_destination_are_rejected(self):
        p,d=self.setup_pair();before=self.snapshot();missing=self.payload()
        for key in ('destination','destinationLat','destinationLng'):missing.pop(key)
        cases=[missing,self.payload(destination=''),self.payload(destination='   '),
            self.payload(destinationLat=None),self.payload(destinationLng=float('nan')),
            self.payload(destinationLat=True),self.payload(destination='万达广场'),
            self.payload(destinationLat=30.6687,destinationLng=111.3226),
            self.payload(destinationPending=1),self.payload(destinationPending='true')]
        for index,payload in enumerate(cases):
            with self.subTest(case=index):self.pa(p['token'],'order.create',payload,expected=400)
        self.assertEqual(self.snapshot(),before)

    def test_normal_false_and_default_create_standard_reserved_and_bid(self):
        p,d=self.setup_pair()
        for kind in ('STANDARD','RESERVED','BID'):
            for explicit in (False,True):
                payload=self.payload(kind=kind)
                if explicit:payload['destinationPending']=False
                if kind=='RESERVED':payload['scheduledAt']=stamp(now()+dt.timedelta(hours=1))
                order=self.pa(p['token'],'order.create',payload)['result']['order']
                self.assertEqual(order['kind'],kind);self.assertFalse(order['destinationPending']);self.assertGreater(order['fare'],0)
                self.pa(p['token'],'order.cancel',{'id':order['id'],'reason':'本地回归结束'})

    def test_historical_pending_row_can_complete_after_destination_update(self):
        p,d=self.setup_pair();pt,driver=p['token'],d['token']
        def historical(svc):
            valid=svc.passenger_create_order(p['passenger']['id'],self.payload())['order']
            order=svc.shared_order(valid['id'])
            order.update(destination='待补充目的地',destinationLat=order['originLat'],destinationLng=order['originLng'],
                         destinationPending=True,fare=0,estimatedFare=0,fareSource='PENDING_DESTINATION')
            svc.save_shared(order);return copy.deepcopy(order)
        old=self.mutate_fixture(historical);oid=old['id']
        self.assertTrue(self.get_order(pt,oid)['destinationPending'])
        self.da(driver,'order.accept',{'id':oid});self.da(driver,'order.advance',{'id':oid})
        self.da(driver,'order.advance',{'id':oid,'phoneTail':'0101'},expected=409)
        current=self.pa(pt,'order.update',{'id':oid,'destination':'宜昌东站','destinationLat':30.6645,'destinationLng':111.3694})['result']['order']
        self.assertFalse(current['destinationPending']);self.assertGreater(current['fare'],0)
        self.da(driver,'order.advance',{'id':oid,'phoneTail':'0101'});self.da(driver,'order.advance',{'id':oid})
        self.assertEqual(self.get_order(pt,oid)['stage'],'WAIT_PAY')
        self.pa(pt,'order.pay',{'id':oid,'outcome':'SUCCESS'})
        self.assertEqual(self.get_order(pt,oid)['stage'],'DONE')
