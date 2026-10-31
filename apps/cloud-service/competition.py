"""Five-second shared-order contention, inside the existing lock/snapshot CAS.
No background timer. Positions and identities of contenders never enter public DTOs.
"""
import math
from common import *
from trip_distance import distance_meters

SCHEMA = '''
CREATE TABLE IF NOT EXISTS order_competitions(
 order_id TEXT PRIMARY KEY, round INTEGER NOT NULL, status TEXT NOT NULL,
 started TEXT, deadline TEXT, binding TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS order_contenders(
 order_id TEXT NOT NULL, round INTEGER NOT NULL, driver TEXT NOT NULL,
 entered TEXT NOT NULL, distance REAL NOT NULL, location TEXT NOT NULL,
 outcome TEXT NOT NULL, PRIMARY KEY(order_id,round,driver));
CREATE INDEX IF NOT EXISTS contenders_driver ON order_contenders(driver,entered);
'''


def instant(t=None):
    return (t or now()).isoformat(timespec='microseconds')


class CompetitionMixin:
    def init_competitions(self):
        self.db.executescript(SCHEMA)

    def competition_time(self):
        return getattr(self, '_competition_request_time', None) or now()

    @staticmethod
    def competition_enabled(o):
        return bool(o and o.get('shared') and o.get('kind') in ('STANDARD', 'RESERVED'))

    @staticmethod
    def competition_binding(o):
        return dumps({k: o.get(k) for k in ('origin', 'originLat', 'originLng', 'destination',
            'destinationLat', 'destinationLng', 'waypoints', 'passengerId', 'rider', 'city')})

    def competition_row(self, o):
        return self.db.execute('SELECT * FROM order_competitions WHERE order_id=?', (o['id'],)).fetchone()

    def competition_view(self, driver, o):
        row = self.competition_row(o)
        rnd = row['round'] if row else 1
        status = row['status'] if row else ('READY' if o['stage'] == 'AVAILABLE' and not o.get('driverId') else 'ASSIGNED' if o.get('driverId') else 'CLOSED')
        outcome = self.db.execute('SELECT outcome FROM order_contenders WHERE order_id=? AND round=? AND driver=?',
            (o['id'], rnd, driver)).fetchone() if driver else None
        deadline = row['deadline'] if row else None
        return {'round': rnd, 'status': status, 'deadline': deadline, 'serverNow': instant(self.competition_time()), 'serverTime': instant(self.competition_time()),
                'selfStatus': outcome['outcome'] if outcome else 'NONE',
                'remainingMs': max(0, math.ceil((parse_time(deadline)-self.competition_time()).total_seconds()*1000)) if deadline and status == 'OPEN' else 0,
                'myOutcome': outcome['outcome'] if outcome else 'NONE'}

    def competition_results(self, driver):
        return [{'orderId': row['order_id'], 'round': row['round'], 'outcome': row['outcome']}
            for row in self.db.execute('SELECT order_id,round,outcome FROM order_contenders WHERE driver=? ORDER BY entered DESC,order_id DESC LIMIT 20', (driver,))]

    def competition_result(self, driver, o, replayed_round=None):
        view = self.competition_view(driver, o)
        result = {'orderId': o['id'], 'competition': view, 'pending': view['myOutcome'] == 'PENDING'}
        if replayed_round is not None and view['round'] != replayed_round:
            result.update(pending=False, roundChanged=True, replayedRound=replayed_round)
        return result

    def require_competition_round(self, o, p):
        row = self.competition_row(o)
        rnd = row['round'] if row else 1
        requested = p.get('competitionRound', 1)
        require(type(requested) is int and requested == rnd and ('competitionRound' in p or rnd == 1),
            '竞抢轮次已变化，请刷新后重新操作', 409, {'reason': 'COMPETITION_ROUND_STALE', 'round': rnd})
        return rnd

    def competition_eligibility(self, driver, o, meters):
        a = self.account(driver)
        profile = json.loads(a['profile'])
        require(profile['driver']['city'] == o['city'] and self.passenger_driver_allowed(o['passengerId'], driver), '订单不存在', 404)
        cfg, _, _ = self.effective(o['city'])
        require(cfg['systemEnabled'], '此区域暂停接单', 403)
        self.operations.require_feature(o['city'], 'reservation' if o['kind'] == 'RESERVED' else 'instant')
        st = self.sync_shared_orders(driver)
        # Real fixed contention distance, not the historic synthetic hall distance.
        eligible = clone(o)
        eligible['distance'] = str(meters / 1000)
        self.new_accept(driver, st, eligible, 'reservation' if o['kind'] == 'RESERVED' else 'instant')
        if o['kind'] != 'RESERVED':
            self.assert_no_trip_conflict(driver_id=driver, passenger_id=o['passengerId'], exclude_order_id=o['id'])
        return st

    def join_competition(self, driver, o, p):
        require(set(p) <= {'id', 'competitionRound'}, '竞抢只接受订单和轮次参数')
        rnd = self.require_competition_round(o, p)
        profile=json.loads(self.account(driver)['profile'])
        require(profile['driver']['city']==o['city'] and self.passenger_driver_allowed(o['passengerId'],driver),'订单不存在',404)
        require(o['stage'] == 'AVAILABLE' and not o.get('driverId'), '本轮已结束或订单已获派', 409, {'reason': 'COMPETITION_CLOSED'})
        row = self.competition_row(o)
        previous = self.db.execute('SELECT outcome FROM order_contenders WHERE order_id=? AND round=? AND driver=?', (o['id'], rnd, driver)).fetchone()
        if previous:
            require(previous['outcome'] == 'PENDING', '你已退出本轮，请等待新轮次', 409, {'reason': 'COMPETITION_WITHDRAWN'})
            return self.competition_result(driver, o)  # No new time or position snapshot.
        a = self.account(driver)
        location = json.loads(a['location']) if a['location'] else None
        usable, _ = self.usable_trip_location(location)
        require(usable, '请先提供新鲜、准确的手机定位后再竞抢', 409, {'reason': 'COMPETITION_LOCATION_REQUIRED'})
        self.validate_point(o.get('originLat'), o.get('originLng'))
        require(o.get('coordinateSystem') == 'WGS84' and not o.get('coordinatesSynthetic'), '订单上车点坐标不可用于竞抢', 409)
        meters = round(distance_meters(location, {'latitude': o['originLat'], 'longitude': o['originLng']}), 6)
        self.competition_eligibility(driver, o, meters)
        entered = self.competition_time()
        if row and row['status'] == 'OPEN':
            require(entered < parse_time(row['deadline']), '本轮竞抢已截止', 409, {'reason': 'COMPETITION_CLOSED'})
        else:
            self.db.execute('INSERT OR REPLACE INTO order_competitions VALUES (?,?,?,?,?,?)',
                (o['id'], rnd, 'OPEN', instant(entered), instant(entered+dt.timedelta(seconds=5)), self.competition_binding(o)))
        self.db.execute('INSERT INTO order_contenders VALUES (?,?,?,?,?,?,?)',
            (o['id'], rnd, driver, instant(entered), meters, dumps(location), 'PENDING'))
        self.audit('DRIVER:'+driver, 'competition.join', None, {'orderId': o['id'], 'round': rnd})
        return self.competition_result(driver, o)

    def finish_competition_round(self, o, row, outcome, reopen=False):
        self.db.execute("UPDATE order_contenders SET outcome=? WHERE order_id=? AND round=? AND outcome='PENDING'", (outcome, o['id'], row['round']))
        self.db.execute('UPDATE order_competitions SET round=?,status=?,started=NULL,deadline=NULL,binding=? WHERE order_id=?',
            (row['round']+1 if reopen else row['round'], 'READY' if reopen else 'CLOSED', self.competition_binding(o), o['id']))

    def settle_competitions(self):
        if getattr(self, '_settling_competitions', False):
            return
        self._settling_competitions = True
        try:
            rows = self.db.execute("SELECT * FROM order_competitions WHERE status IN ('OPEN','ASSIGNED') ORDER BY deadline,order_id").fetchall()
            for row in rows:
                o = self.shared_order(row['order_id'])
                if not o:
                    self.db.execute("UPDATE order_contenders SET outcome='CANCELLED' WHERE order_id=? AND round=? AND outcome='PENDING'", (row['order_id'], row['round']))
                    self.db.execute("UPDATE order_competitions SET status='CLOSED' WHERE order_id=?", (row['order_id'],))
                    continue
                if row['status'] == 'ASSIGNED':
                    if o.get('driverId') and o['stage'] in ('RESERVED','PICKUP','ARRIVED','TRIP'):
                        continue
                    if o['stage'] in ('CANCELLED','WAIT_CONTINUE'):
                        self.db.execute("UPDATE order_contenders SET outcome='CANCELLED' WHERE order_id=? AND round=? AND outcome='WON'", (o['id'],row['round']))
                    self.finish_competition_round(o,row,'CANCELLED')
                    continue
                if o['stage'] != 'AVAILABLE' or o.get('driverId') or not self.competition_enabled(o):
                    self.finish_competition_round(o, row, 'CANCELLED')
                    continue
                if row['binding'] != self.competition_binding(o):
                    self.finish_competition_round(o, row, 'SUPERSEDED', reopen=True)
                    continue
                if self.competition_time() < parse_time(row['deadline']):
                    continue
                winner = None
                for candidate in self.db.execute("SELECT * FROM order_contenders WHERE order_id=? AND round=? AND outcome='PENDING' ORDER BY distance,entered,driver", (o['id'], row['round'])).fetchall():
                    try:
                        st = self.competition_eligibility(candidate['driver'], o, candidate['distance'])
                    except ApiError:
                        self.db.execute("UPDATE order_contenders SET outcome='INELIGIBLE' WHERE order_id=? AND round=? AND driver=?", (o['id'], row['round'], candidate['driver']))
                        continue
                    winner = candidate['driver']
                    o['driverId'] = winner
                    o['stage'] = 'RESERVED' if o['kind'] == 'RESERVED' else 'PICKUP'
                    o['cancelledBy'] = None
                    o['cancelReason'] = None
                    st['todayAccepted'] += 1
                    self.save_state(winner, st)
                    self.save_shared(o, 'COMPETITION_ASSIGNED')
                    self.db.execute("UPDATE order_competitions SET status='ASSIGNED' WHERE order_id=?", (o['id'],))
                    self.db.execute("UPDATE order_contenders SET outcome=CASE WHEN driver=? THEN 'WON' ELSE 'LOST' END WHERE order_id=? AND round=? AND outcome='PENDING'", (winner, o['id'], row['round']))
                    break
                if winner is None:
                    self.finish_competition_round(o, row, 'LOST', reopen=True)
        finally:
            self._settling_competitions = False

    def withdraw_driver_competitions(self, driver):
        # Explicit offline/logout retires pending entries; re-login or an old
        # request retry cannot silently resurrect them in the same round.
        self.db.execute("UPDATE order_contenders SET outcome='WITHDRAWN' WHERE driver=? AND outcome='PENDING'", (driver,))

    def withdraw_competition(self, driver, o, p):
        rnd = self.require_competition_round(o, p)
        candidate = self.db.execute('SELECT outcome FROM order_contenders WHERE order_id=? AND round=? AND driver=?', (o['id'], rnd, driver)).fetchone()
        require(o['stage'] == 'AVAILABLE' and candidate is not None and candidate['outcome'] == 'PENDING', '没有可退出的本轮竞抢', 409)
        textfield(p.get('reason', '退出竞抢'), '取消原因', 2, 200)
        self.db.execute("UPDATE order_contenders SET outcome='WITHDRAWN' WHERE order_id=? AND round=? AND driver=?", (o['id'], rnd, driver))
        return self.competition_result(driver, o)

    def reopen_competition(self, driver, o, p):
        rnd = self.require_competition_round(o, p)
        require(o['stage'] in ('RESERVED', 'PICKUP', 'ARRIVED'), '当前订单不能取消', 409)
        require(not o.get('paid'), '此订单已付款，请通过原退款处理流程操作，暂不自动重开', 409)
        reason = textfield(p.get('reason', '司机取消'), '取消原因', 2, 200)
        # No fare/payment/refund/penalty mutation. Contact remains the immutable rider.
        o.update(stage='AVAILABLE', driverId=None, cancelReason=reason, cancelledBy='DRIVER', arrivedWaypoints=[])
        self.save_shared(o, 'COMPETITION_REOPENED')
        self.db.execute("UPDATE order_contenders SET outcome='CANCELLED' WHERE order_id=? AND round=? AND driver=?", (o['id'], rnd, driver))
        self.db.execute('INSERT OR REPLACE INTO order_competitions VALUES (?,?,?,?,?,?)',
            (o['id'], rnd+1, 'READY', None, None, self.competition_binding(o)))
        return self.competition_result(driver, o)
