"""Isolated real HTTP/SQLite/MemoryCAS contention tests; never real cloud/GPS."""
import concurrent.futures
import datetime as dt
import json
import threading
import uuid
from unittest.mock import patch
from policy_helpers import PolicyCase
from common import now, stamp


class OrderCompetition(PolicyCase):
    def setUp(self):
        super().setUp()
        self.time=now()
        self.clock=patch('competition.now',lambda:self.time)
        self.clock.start()
        self.p,self.d=self.setup_pair()
        self.d2=self.login('13800000002')
        self.da(self.d2['token'],'driver.online',{'online':True})
        self.locate(self.d['token'],30.6697)
        self.locate(self.d2['token'],30.6688)
        self.oid=self.create(self.p['token'])['id']

    def tearDown(self):
        self.clock.stop()
        super().tearDown()

    def locate(self,token,lat):
        return self.raw(token,'location.report',dict(latitude=lat,longitude=111.3226,accuracy=5,time=stamp(),coordinateSystem='WGS84'))

    def raw(self,token,action,payload,rid=None,expected=200,server=None):
        return self.request('/api/action',{'action':action,'payload':payload,'requestId':rid or str(uuid.uuid4())},token=token,expected=expected,server=server)

    def join(self,driver=None,oid=None,rnd=1,rid=None,expected=200,server=None):
        return self.raw((driver or self.d)['token'],'order.accept',dict(id=oid or self.oid,competitionRound=rnd),rid,expected,server)

    def advance_clock(self,seconds=5):
        self.time+=dt.timedelta(seconds=seconds)

    def canonical(self,oid=None,server=None):
        rows=self.request('/api/passenger/state',token=self.p['token'],server=server)['orders']
        return next(x for x in rows if x['id']==(oid or self.oid))

    def test_first_valid_fixed_deadline_single_and_nearest_multiple(self):
        first=self.join(rid='first')
        comp=first['result']['competition'];self.assertEqual(comp['myOutcome'],'PENDING')
        self.assertEqual(comp['status'],'OPEN');self.assertTrue(first['result']['pending'])
        self.assertEqual(self.canonical()['stage'],'AVAILABLE')
        self.advance_clock(1)
        self.join(self.d2)
        self.assertEqual(self.join(rid='first')['result']['competition']['deadline'],comp['deadline'])
        self.advance_clock(4)
        order=self.canonical();self.assertEqual(order['driver']['id'],self.d2['profile']['driver']['id'])
        self.assertEqual(order['stage'],'PICKUP')
        loser=self.request('/api/state',token=self.d['token'])
        self.assertNotIn(self.oid,[x['id'] for x in loser['state']['orders']])
        self.assertEqual(next(x['outcome'] for x in loser['competitionResults'] if x['orderId']==self.oid),'LOST')
        self.join(expected=409)

    def test_fractional_time_is_exact_five_seconds(self):
        self.time=self.time.replace(microsecond=987654)
        comp=self.join()['result']['competition']
        start=dt.datetime.fromisoformat(comp['serverTime']);end=dt.datetime.fromisoformat(comp['deadline'])
        self.assertEqual((end-start).total_seconds(),5)
        self.advance_clock(4.999999);self.assertEqual(self.canonical()['stage'],'AVAILABLE')
        self.advance_clock(.000001);self.assertEqual(self.canonical()['stage'],'PICKUP')

    def test_missing_bad_location_or_eligibility_never_starts_window(self):
        def clear(svc):svc.db.execute('UPDATE accounts SET location=NULL WHERE id=?',(self.d['profile']['driver']['id'],))
        self.mutate_fixture(clear);self.join(expected=409)
        self.assertEqual(self.mutate_fixture(lambda svc:svc.db.execute('SELECT count(*) FROM order_competitions').fetchone()[0]),0)
        self.locate(self.d['token'],30.6697)
        self.raw(self.d['token'],'driver.online',{'online':False})
        self.join(expected=409)
        self.raw(self.d['token'],'driver.online',{'online':True})
        self.join()

    def test_position_frozen_duplicate_does_not_refresh_rank(self):
        self.join(rid='original')
        deadline=self.join()['result']['competition']['deadline']
        self.locate(self.d['token'],30.6687) # now closer, but its original contender position stays.
        self.advance_clock(1)
        self.join(self.d2)
        self.assertEqual(self.join()['result']['competition']['deadline'],deadline)
        self.advance_clock(4)
        self.assertEqual(self.canonical()['driver']['id'],self.d2['profile']['driver']['id'])
        self.join(rid='original') # Safe idempotent result, never a second grant.

    def test_ties_use_entered_time_then_driver_id(self):
        self.locate(self.d2['token'],30.6697)
        # Same distance, d2 earlier wins even if its id sorts later.
        self.join(self.d2);self.advance_clock(.000001);self.join()
        self.advance_clock(5);self.assertEqual(self.canonical()['driver']['id'],self.d2['profile']['driver']['id'])

    def test_restart_keeps_deadline_and_get_settles(self):
        deadline=self.join()['result']['competition']['deadline']
        reboot=self.start()
        current=next(o for o in self.request('/api/state',token=self.d['token'],server=reboot)['state']['orders'] if o['id']==self.oid)
        self.assertEqual(current['competition']['deadline'],deadline)
        self.advance_clock(5)
        self.assertEqual(self.canonical(server=reboot)['stage'],'PICKUP')

    def test_late_accept_409_does_not_rollback_due_assignment(self):
        self.join()
        self.advance_clock(5)
        self.join(self.d2,expected=409) # Original Handler rollback used to erase prior scheduled writes.
        reboot=self.start()
        self.assertEqual(self.canonical(server=reboot)['driver']['id'],self.d['profile']['driver']['id'])
        self.assertEqual(self.canonical(server=reboot)['stage'],'PICKUP')

    def test_withdraw_offline_and_no_valid_candidate_reopen_without_timer(self):
        self.join();self.join(self.d2)
        self.raw(self.d['token'],'order.cancel',dict(id=self.oid,competitionRound=1,reason='退出本轮'))
        self.join(expected=409) # withdrawn cannot be resurrected by a delayed duplicate/new key.
        self.raw(self.d2['token'],'driver.online',dict(online=False))
        self.advance_clock(5)
        order=self.canonical();self.assertEqual(order['stage'],'AVAILABLE')
        view=next(o for o in self.request('/api/state',token=self.d['token'])['state']['orders'] if o['id']==self.oid)['competition']
        self.assertEqual((view['round'],view['status'],view['deadline']),(2,'READY',None))
        self.join(rnd=1,expected=409)
        self.join(rnd=2)

    def test_winner_cancel_reopens_same_order_round_and_old_requests_cannot_cross(self):
        self.join(rid='first-join');self.advance_clock(5);before=self.canonical()
        self.raw(self.d['token'],'order.cancel',dict(id=self.oid,competitionRound=1,reason='上车前取消'),rid='cancel-once')
        after=self.canonical();self.assertEqual((after['id'],after['stage'],after['driver']), (self.oid,'AVAILABLE',None))
        self.assertEqual(after['fare'],before['fare'])
        self.join(rid='first-join') # idempotent old request does not join round2.
        self.assertEqual(self.mutate_fixture(lambda svc:svc.db.execute('SELECT count(*) FROM order_contenders WHERE round=2').fetchone()[0]),0)
        self.join(expected=409)
        self.raw(self.d['token'],'order.accept',dict(id=self.oid),expected=409)
        self.join(self.d2,rnd=2);self.advance_clock(5)
        self.assertEqual(self.canonical()['driver']['id'],self.d2['profile']['driver']['id'])
        self.raw(self.d['token'],'order.cancel',dict(id=self.oid,competitionRound=1,reason='上车前取消'),rid='cancel-once')
        self.assertEqual(self.canonical()['stage'],'PICKUP')

    def test_passenger_cancel_and_route_change_invalidate_candidates(self):
        self.join()
        self.pa(self.p['token'],'order.update',dict(id=self.oid,destination='变更目的地',destinationLat=30.67,destinationLng=111.38))
        self.join(expected=409)
        self.join(rnd=2)
        self.pa(self.p['token'],'order.cancel',dict(id=self.oid,reason='乘客取消'))
        self.advance_clock(5);self.assertEqual(self.canonical()['stage'],'CANCELLED')
        self.join(rnd=2,expected=409)

    def test_grant_rechecks_online_certificate_membership_and_real_range(self):
        self.join();self.join(self.d2)
        self.raw(self.d2['token'],'driver.online',dict(online=False))
        self.advance_clock(5)
        self.assertEqual(self.canonical()['driver']['id'],self.d['profile']['driver']['id'])

    def test_grant_rechecks_certificate_after_join(self):
        self.join();self.join(self.d2)
        def expire(svc):
            a=svc.account(self.d2['profile']['driver']['id']);profile=json.loads(a['profile'])
            profile['certificates'][0]['validUntil']='2000-01-01'
            svc.save_profile(a['id'],profile)
        self.mutate_fixture(expire);self.advance_clock(5)
        self.assertEqual(self.canonical()['driver']['id'],self.d['profile']['driver']['id'])

    def test_grant_rechecks_membership_after_join(self):
        self.join();self.join(self.d2)
        def end_membership(svc):
            driver=self.d2['profile']['driver']['id']
            svc.db.execute('DELETE FROM subscriptions WHERE driver=?',(driver,))
            svc.db.execute('UPDATE accounts SET trial_completed=999 WHERE id=?',(driver,))
        self.mutate_fixture(end_membership);self.advance_clock(5)
        self.assertEqual(self.canonical()['driver']['id'],self.d['profile']['driver']['id'])

    def test_grant_rechecks_accept_preference_after_join(self):
        self.join();self.join(self.d2)
        def disable(svc):
            driver=self.d2['profile']['driver']['id'];st=json.loads(svc.account(driver)['state'])
            st['instant']=False;svc.save_state(driver,st)
        self.mutate_fixture(disable);self.advance_clock(5)
        self.assertEqual(self.canonical()['driver']['id'],self.d['profile']['driver']['id'])

    def test_zero_accuracy_stale_and_far_reported_positions_cannot_join(self):
        self.raw(self.d['token'],'location.report',dict(latitude=30.6687,longitude=111.3226,accuracy=0,time=stamp(),coordinateSystem='WGS84'))
        self.join(expected=409)
        self.raw(self.d['token'],'location.report',dict(latitude=30.6687,longitude=111.3226,accuracy=5,time=stamp(now()-dt.timedelta(seconds=121)),coordinateSystem='WGS84'))
        self.join(expected=409)
        self.locate(self.d['token'],31.)
        self.join(expected=403) # Actual distance violates membership/preference range, not synthetic1km.

    def test_reserved_waiting_does_not_occupy_instant_but_departure_still_single(self):
        self.pa(self.p['token'],'order.cancel',dict(id=self.oid,reason='重设预约场景'))
        self.oid=self.create(self.p['token'],'RESERVED')['id']
        self.join();self.advance_clock(5)
        self.assertEqual(self.canonical()['stage'],'RESERVED')
        p2=self.passenger_login('13800000102');second=self.create(p2['token'])['id']
        self.join(oid=second);self.advance_clock(5)
        self.request('/api/state',token=self.d['token'])
        self.raw(self.d['token'],'order.advance',dict(id=self.oid,expectedStage='RESERVED'),expected=409)
        self.raw(self.d['token'],'order.cancel',dict(id=second,competitionRound=1,reason='结束即时单'))
        self.raw(self.d['token'],'order.advance',dict(id=self.oid,expectedStage='RESERVED'))
        self.assertEqual(self.canonical()['stage'],'PICKUP')

    def test_bid_choice_private_demo_and_boarded_cancel_not_redefined(self):
        self.pa(self.p['token'],'order.cancel',dict(id=self.oid,reason='竞价场景'))
        bid=self.create(self.p['token'],'BID')['id']
        reply=self.raw(self.d['token'],'order.bid',dict(id=bid,amount=1600))
        self.assertNotIn('competition',reply['result']['order'])
        self.pa(self.p['token'],'order.selectDriver',dict(id=bid,driverId=self.d['profile']['driver']['id']))
        self.assertEqual(self.canonical(oid=bid)['stage'],'PREPAY')
        self.pa(self.p['token'],'order.cancel',dict(id=bid,reason='结束竞价'))
        private=self.raw(self.d['token'],'order.accept',dict(id='YC1001'))['result']['order']
        self.assertEqual(private['stage'],'PICKUP');self.assertNotIn('competition',private)

    def test_equal_distance_and_entered_time_uses_driver_id(self):
        self.locate(self.d2['token'],30.6697)
        self.join(self.d2);self.join()
        self.advance_clock(5)
        expected=min(self.d['profile']['driver']['id'],self.d2['profile']['driver']['id'])
        self.assertEqual(self.canonical()['driver']['id'],expected)

    def test_driver_cannot_cancel_after_boarding(self):
        self.join();self.advance_clock(5);self.canonical()
        def trip(svc):
            o=svc.shared_order(self.oid);o['stage']='TRIP';svc.save_shared(o)
        self.mutate_fixture(trip)
        self.raw(self.d['token'],'order.cancel',dict(id=self.oid,competitionRound=1,reason='行程中取消'),expected=409)
        self.assertEqual(self.canonical()['stage'],'TRIP')

    def test_two_windows_do_not_award_one_driver_two_executing_orders(self):
        p2=self.passenger_login('13800000102')
        oid2=self.create(p2['token'])['id']
        for oid in [self.oid,oid2]:self.join(oid=oid);self.join(self.d2,oid=oid)
        self.advance_clock(5)
        self.request('/api/state',token=self.d['token'])
        assigned=self.mutate_fixture(lambda svc:[json.loads(r['payload']) for r in svc.db.execute('SELECT payload FROM passenger_orders')])
        self.assertEqual(len(set(o['driverId'] for o in assigned)),2)
        self.assertTrue(all(o['stage']=='PICKUP' for o in assigned))

    def test_round_and_candidate_privacy_no_contacts_for_contender(self):
        self.join();reply=self.request('/api/state',token=self.d['token'])
        view=next(x for x in reply['state']['orders'] if x['id']==self.oid)['competition']
        self.assertEqual(set(view),{'round','status','deadline','serverNow','serverTime','selfStatus','myOutcome','remainingMs'})
        serialized=json.dumps(view);self.assertNotIn(self.d2['profile']['driver']['id'],serialized)
        self.assertNotIn('latitude',serialized)
        self.request('/api/driver/passenger-contact?orderId='+self.oid,token=self.d['token'],expected=404)

    def test_logout_before_deadline_retires_candidate_and_no_ghost_assignment(self):
        self.join();self.join(self.d2)
        self.request('/api/auth/logout',{},token=self.d2['token'])
        self.advance_clock(5)
        self.assertEqual(self.canonical()['driver']['id'],self.d['profile']['driver']['id'])
        outcome=self.mutate_fixture(lambda svc:svc.db.execute('SELECT outcome FROM order_contenders WHERE driver=?',(self.d2['profile']['driver']['id'],)).fetchone()[0])
        self.assertEqual(outcome,'WITHDRAWN')

    def test_two_services_cas_concurrent_settlement_unique_winner(self):
        self.join();other=self.start();self.join(self.d2,server=other)
        self.advance_clock(5)
        self.remote.barrier=threading.Barrier(2)
        def read(server):
            # Either one writer wins or the stale one is rejected, never acknowledged differently.
            req=__import__('urllib.request',fromlist=['Request']).Request(
                f'http://127.0.0.1:{server.server_port}/api/state',headers={'Authorization':'Bearer '+self.d['token']})
            try:
                with __import__('urllib.request',fromlist=['urlopen']).urlopen(req,timeout=15) as response:return response.status
            except __import__('urllib.error',fromlist=['HTTPError']).HTTPError as exc:return exc.code
        try:
            with concurrent.futures.ThreadPoolExecutor(2) as pool:statuses=list(pool.map(read,[self.driver,other]))
        finally:self.remote.barrier=None
        self.assertEqual(sorted(statuses),[200,503])
        self.assertEqual(self.canonical()['driver']['id'],self.d2['profile']['driver']['id'])
