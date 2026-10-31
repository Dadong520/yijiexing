"""Route-rule regressions with a private temporary SQLite; no cloud/provider calls."""
import datetime as dt
import math
import unittest
import test_trip_distance as fixture
from common import ApiError, now, stamp


class RoundTripNavigation(unittest.TestCase):
    setUp = fixture.TripDistanceRules.setUp
    tearDown = fixture.TripDistanceRules.tearDown
    pa = fixture.TripDistanceRules.pa
    da = fixture.TripDistanceRules.da

    def payload(self, **changes):
        value = dict(kind='STANDARD', origin='回环起点', destination='回环起点',
                     originLat=30., originLng=111., destinationLat=30., destinationLng=111.,
                     waypoints=[dict(name='北方途经点', latitude=30.1, longitude=111.)])
        value.update(changes)
        return value

    def test_create_all_kinds_full_legs_and_unchanged_formula(self):
        # Exact north/south arc, two legs (not zero endpoint distance).
        km = 6371 * math.pi / 180 * .1 * 2
        expected = max(800, int(round((8 + max(0, km * 1.3 - 2) * 2.4) * 100)))
        for kind in ('STANDARD', 'RESERVED', 'BID'):
            o = self.pa('order.create', self.payload(kind=kind, scheduledAt=stamp(now()+dt.timedelta(hours=1))))['order']
            self.assertEqual(o['estimatedFare'], expected)
            self.assertEqual(o['mileage'], f'{km*1.3:.1f}')
            self.assertEqual([x['sortNo'] for x in o['waypoints']], [1])
            self.pa('order.cancel', dict(id=o['id'], reason='结束隔离测试'))

    def test_missing_all_same_and_alias_cannot_fake_a_loop(self):
        for points in ([], [dict(name='起点别名', latitude=30., longitude=111.)]):
            for name in ('回环起点', '起点其他别名'):
                with self.assertRaises(ApiError):
                    self.pa('order.create', self.payload(destination=name, waypoints=points))
        self.assertEqual(self.svc.db.execute('SELECT count(*) FROM passenger_orders').fetchone()[0], 0)
        self.assertEqual(self.svc.db.execute('SELECT count(*) FROM passenger_order_contacts').fetchone()[0], 0)

    def test_two_stops_one_same_and_one_away_preserve_order(self):
        points = [dict(name='同点别名', latitude=30., longitude=111.),
                  dict(name='真正离开', latitude=30.1, longitude=111.)]
        o = self.pa('order.create', self.payload(waypoints=points))['order']
        self.assertEqual([x['name'] for x in o['waypoints']], ['同点别名', '真正离开'])
        with self.assertRaises(ApiError):
            self.svc.validate_route_endpoints('同点名称','同点名称',(30.,111.),(30.,111.),[])

    def test_bad_waypoints_are_rejected_before_shape_allowance(self):
        for value in (True, '30.1', None, float('nan'), float('inf'), 91.):
            with self.subTest(value=repr(value)), self.assertRaises(ApiError):
                self.pa('order.create', self.payload(waypoints=[dict(name='非法点',latitude=value,longitude=111.)]))
        for points in (['只有名称'], [dict(name='坏',latitude=30.1,longitude=111.)],
                       [dict(name='有效点',latitude=30.1,longitude=111.)]*3):
            with self.assertRaises(ApiError):self.pa('order.create', self.payload(waypoints=points))

    def test_epsilon_and_full_loop_300km_limit(self):
        # Coordinate tolerance matches old endpoint guard; no new arbitrary minimum fare length.
        with self.assertRaises(ApiError):
            self.pa('order.create', self.payload(waypoints=[dict(name='容差以内',latitude=30.+.5e-7,longitude=111.)]))
        o = self.pa('order.create', self.payload(waypoints=[dict(name='短但非零',latitude=30.+2e-7,longitude=111.)]))['order']
        self.assertEqual(o['fare'],800)
        self.pa('order.cancel', dict(id=o['id'],reason='结束隔离测试'))
        boundary = math.degrees(300. / (2*6371))
        inside = self.pa('order.create', self.payload(waypoints=[dict(name='边界内点',latitude=30.+boundary-1e-6,longitude=111.)]))['order']
        self.pa('order.cancel', dict(id=inside['id'],reason='结束隔离测试'))
        with self.assertRaises(ApiError):
            self.pa('order.create', self.payload(waypoints=[dict(name='边界外点',latitude=30.+boundary+1e-6,longitude=111.)]))

    def test_update_loop_uses_same_rule_and_fare_and_still_locks_after_accept(self):
        o = self.pa('order.create', self.payload(destination='普通终点',destinationLat=30.2))['order']
        update = dict(id=o['id'],destination='回环起点',destinationLat=30.,destinationLng=111.)
        current = self.pa('order.update',update)['order']
        km = 6371*math.pi/180*.1*2
        self.assertEqual(current['fare'],int(round((8+(km*1.3-2)*2.4)*100)))
        with self.assertRaises(ApiError):self.pa('order.update', update, pid='P2')
        self.da('order.accept',dict(id=o['id']))
        with self.assertRaises(ApiError):self.pa('order.update',update)

    def test_update_total_leg_limit_and_invalid_stop_do_not_change_saved_fare(self):
        # Initial O->north1.2->north1.3 is below300km; sending D south1.2
        # makes the sum of legs exceed300 even though the endpoints alone do not.
        o=self.pa('order.create',self.payload(destination='北部终点',destinationLat=31.3,
            waypoints=[dict(name='北部中途',latitude=31.2,longitude=111.)]))['order']
        before=self.svc.shared_order(o['id'])
        with self.assertRaises(ApiError):
            self.pa('order.update',dict(id=o['id'],destination='南部终点',destinationLat=28.8,destinationLng=111.))
        self.assertEqual(before,self.svc.shared_order(o['id']))
        # A legacy malformed stored stop must not be silently discarded on edit.
        legacy=dict(before);legacy['waypoints']=[dict(name='历史缺坐标')]
        self.svc.save_shared(legacy)
        with self.assertRaises(ApiError):
            self.pa('order.update',dict(id=o['id'],destination='回环起点',destinationLat=30.,destinationLng=111.))
        self.assertEqual(legacy,self.svc.shared_order(o['id']))

    def test_plain_update_rejects_return_to_start_without_stops(self):
        o = self.pa('order.create', self.payload(destination='普通终点',destinationLat=30.2,waypoints=[]))['order']
        with self.assertRaises(ApiError):
            self.pa('order.update',dict(id=o['id'],destination='另一别名',destinationLat=30.,destinationLng=111.))
        self.assertEqual(self.svc.shared_order(o['id'])['destination'], '普通终点')

    def test_create_and_update_idempotency_do_not_duplicate_contact_or_reprice(self):
        payload=self.payload()
        o=self.pa('order.create',payload,rid='loop-create')['order']
        self.assertEqual(self.pa('order.create',payload,rid='loop-create')['order']['id'],o['id'])
        with self.assertRaises(ApiError):self.pa('order.create',self.payload(destination='别名'),rid='loop-create')
        update=dict(id=o['id'],destination='起点别名',destinationLat=30.,destinationLng=111.)
        result=self.pa('order.update',update,rid='loop-update')
        self.assertEqual(result,self.pa('order.update',update,rid='loop-update'))
        self.assertEqual(self.svc.db.execute('SELECT count(*) FROM passenger_order_contacts').fetchone()[0],1)
