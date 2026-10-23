"""Optional driver stage preconditions protect delayed confirmation dialogs."""
from policy_helpers import PolicyCase

class AdvanceStageTests(PolicyCase):
    def test_private_stale_arrived_does_not_end_trip_and_original_retry_is_safe(self):
        p,d=self.setup_pair();dt=d['token'];oid='YC1001'
        self.da(dt,'order.accept',{'id':oid})
        self.da(dt,'order.advance',{'id':oid,'expectedStage':'PICKUP'})
        start={'id':oid,'expectedStage':'ARRIVED','phoneTail':'6758'}
        self.da(dt,'order.advance',start,rid='start-private-once')
        before=self.projected(dt,oid)
        self.da(dt,'order.advance',start,expected=409)
        self.da(dt,'order.advance',start,rid='start-private-once')
        self.assertEqual(self.projected(dt,oid),before);self.assertEqual(before['stage'],'TRIP')
        self.da(dt,'order.advance',{'id':oid,'expectedStage':'TRIP'})
        b=self.create(p['token']);self.da(dt,'order.accept',{'id':b['id']});new=self.get_order(p['token'],b['id'])
        self.da(dt,'order.advance',start,expected=409)
        self.da(dt,'order.advance',start,rid='start-private-once')
        self.assertEqual(self.get_order(p['token'],b['id']),new)
        self.assertEqual(self.projected(dt,oid)['stage'],'WAIT_PAY')

    def test_shared_all_stage_preconditions_replay_and_cross_order_unchanged(self):
        p,d=self.setup_pair();dt=d['token'];oid=self.create(p['token'],'RESERVED')['id'];self.da(dt,'order.accept',{'id':oid})
        for stage,next_stage in [('RESERVED','PICKUP'),('PICKUP','ARRIVED'),('ARRIVED','TRIP')]:
            payload={'id':oid,'expectedStage':stage,'phoneTail':'0101'};rid='shared-'+stage
            self.da(dt,'order.advance',payload,rid=rid)
            before=self.get_order(p['token'],oid);self.assertEqual(before['stage'],next_stage)
            self.da(dt,'order.advance',payload,expected=409)
            self.da(dt,'order.advance',payload,rid=rid)
            self.assertEqual(self.get_order(p['token'],oid),before)
        self.da(dt,'order.advance',{'id':oid,'expectedStage':'TRIP'})
        p2=self.passenger_login('13800000102');b=self.create(p2['token']);self.da(dt,'order.accept',{'id':b['id']});before=self.get_order(p2['token'],b['id'])
        self.da(dt,'order.advance',{'id':oid,'expectedStage':'ARRIVED','phoneTail':'0101'},expected=409)
        self.da(dt,'order.advance',{'id':oid,'expectedStage':'ARRIVED','phoneTail':'0101'},rid='shared-ARRIVED')
        self.assertEqual(self.get_order(p2['token'],b['id']),before)
        self.assertEqual(self.get_order(p['token'],oid)['stage'],'WAIT_PAY')

    def test_legacy_missing_stage_still_supported_and_invalid_stage_cannot_mutate(self):
        p,d=self.setup_pair();dt=d['token'];oid=self.create(p['token'])['id'];self.da(dt,'order.accept',{'id':oid})
        for stage in (None,True,'', 'TRIP'):
            self.da(dt,'order.advance',{'id':oid,'expectedStage':stage},expected=409)
        self.assertEqual(self.get_order(p['token'],oid)['stage'],'PICKUP')
        self.da(dt,'order.advance',{'id':oid});self.assertEqual(self.get_order(p['token'],oid)['stage'],'ARRIVED')
