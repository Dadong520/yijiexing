"""修改意见3.0: driver blocklist by passenger account, same-city rankings, online
duration, threshold heat maps and the points mall.

Everything joins the existing request transaction and SQLite snapshot. Points are
a non-monetary demo ledger: no payment, settlement, coupon or shipping provider is
called and nothing here changes a fare.
"""
from collections import defaultdict
from common import *

HEAT_CELL = 0.005          # roughly 500 m grid cells
HEAT_WINDOW_MINUTES = 60
PERIODS = {'day': '今日', 'week': '本周', 'month': '本月', 'year': '本年'}
METRICS = {'orders': ('day', 'month', 'year'), 'online': ('day', 'week', 'month', 'year')}
MALL_AUDIENCES = ('PASSENGER', 'DRIVER')


def period_start(period, moment=None):
    t = (moment or now()).astimezone(TZ)
    day = t.replace(hour=0, minute=0, second=0, microsecond=0)
    if period == 'week':
        return day - dt.timedelta(days=day.weekday())
    if period == 'month':
        return day.replace(day=1)
    if period == 'year':
        return day.replace(month=1, day=1)
    return day


def safe_time(value):
    try:
        return parse_time(value)
    except ApiError:
        return None


def duration_label(seconds):
    minutes = int(seconds // 60)
    return f'{minutes // 60}小时{minutes % 60}分钟' if minutes >= 60 else f'{minutes}分钟'


class EngagementMixin:
    def init_engagement(self):
        self.db.execute('''CREATE TABLE IF NOT EXISTS driver_blocked_passengers(
            driver TEXT,passenger TEXT,blacklist_id TEXT,created TEXT,PRIMARY KEY(driver,passenger))''')
        self.db.execute('''CREATE TABLE IF NOT EXISTS driver_online_log(
            id INTEGER PRIMARY KEY AUTOINCREMENT,driver TEXT NOT NULL,started TEXT NOT NULL,ended TEXT)''')
        self.db.execute('CREATE INDEX IF NOT EXISTS driver_online_log_driver ON driver_online_log(driver,ended)')

    # ---- driver blocklist -------------------------------------------------
    def block_passenger(self, driver, profile, o, reason):
        """Block the booking passenger account behind a shared order. The driver
        only ever sees the 4-digit tail; the passenger ID stays server-side."""
        tail = o.get('phoneTail') if isinstance(o.get('phoneTail'), str) else ''
        pid = o.get('passengerId')
        entry = None
        if pid:
            row = self.db.execute('SELECT blacklist_id FROM driver_blocked_passengers WHERE driver=? AND passenger=?', (driver, pid)).fetchone()
            entry = next((b for b in profile['blacklist'] if row and b['id'] == row['blacklist_id']), None)
        if entry is None:
            entry = next((b for b in profile['blacklist'] if tail and b['phoneTail'] == tail), None)
        if entry is None:
            entry = {'id': uid('BL'), 'phoneTail': tail or '----', 'reason': reason, 'createdAt': stamp(),
                     'sourceOrderId': o.get('id'), 'scope': 'ACCOUNT' if pid else 'TAIL'}
            profile['blacklist'].append(entry)
        if pid:
            self.db.execute('INSERT OR REPLACE INTO driver_blocked_passengers VALUES (?,?,?,?)', (driver, pid, entry['id'], stamp()))
        return entry

    def unblock_passenger_entry(self, driver, blacklist_id):
        self.db.execute('DELETE FROM driver_blocked_passengers WHERE driver=? AND blacklist_id=?', (driver, blacklist_id))

    def driver_passenger_allowed(self, driver, o, blacklist=None):
        pid = o.get('passengerId')
        if pid and self.db.execute('SELECT 1 FROM driver_blocked_passengers WHERE driver=? AND passenger=?', (driver, pid)).fetchone():
            return False
        if blacklist is None:
            blacklist = json.loads(self.account(driver)['profile']).get('blacklist', [])
        tails = {b.get('phoneTail') for b in blacklist}
        if o.get('phoneTail') in tails:
            return False
        if pid:
            row = self.db.execute('SELECT mobile FROM passengers WHERE id=?', (pid,)).fetchone()
            if row and row['mobile'][-4:] in tails:
                return False
        return True

    # ---- online duration --------------------------------------------------
    def track_online(self, driver, online):
        open_row = self.db.execute('SELECT id FROM driver_online_log WHERE driver=? AND ended IS NULL', (driver,)).fetchone()
        if online and not open_row:
            self.db.execute('INSERT INTO driver_online_log(driver,started,ended) VALUES (?,?,NULL)', (driver, stamp()))
        elif not online and open_row:
            self.db.execute('UPDATE driver_online_log SET ended=? WHERE driver=? AND ended IS NULL', (stamp(), driver))

    def online_seconds(self, since, drivers=None):
        current = now()
        totals = defaultdict(float)
        for row in self.db.execute('SELECT driver,started,ended FROM driver_online_log'):
            if drivers is not None and row['driver'] not in drivers:
                continue
            start, end = safe_time(row['started']), safe_time(row['ended']) if row['ended'] else current
            if start is None or end is None:
                continue
            overlap = (min(end, current) - max(start, since)).total_seconds()
            if overlap > 0:
                totals[row['driver']] += overlap
        return totals

    # ---- rankings ---------------------------------------------------------
    def driver_ranking(self, driver, metric='orders', period='day'):
        require(metric in METRICS, '排行类型无效')
        require(period in METRICS[metric], '此排行不支持该统计周期')
        me = json.loads(self.account(driver)['profile'])['driver']
        city = me['city']
        since = period_start(period)
        peers = {}
        for row in self.db.execute('SELECT id,profile FROM accounts'):
            d = json.loads(row['profile'])['driver']
            if d.get('city') == city:
                peers[row['id']] = d
        values = defaultdict(float)
        if metric == 'orders':
            for row in self.db.execute('SELECT payload FROM passenger_orders WHERE driver IS NOT NULL'):
                o = json.loads(row['payload'])
                done = safe_time(o.get('completedAt') or (o.get('updatedAt') if o.get('stage') == 'DONE' else None))
                if o.get('stage') == 'DONE' and o.get('driverId') in peers and done and done >= since:
                    values[o['driverId']] += 1
        else:
            values = self.online_seconds(since, peers)
        ordered = sorted(peers, key=lambda k: (-values.get(k, 0), k))
        entries, my_rank = [], None
        for index, key in enumerate(ordered, 1):
            value = values.get(key, 0)
            if key == driver:
                my_rank = index if value > 0 else None
            if value <= 0 or index > 50:
                continue
            name = str(peers[key].get('name') or '司机')
            plate = str(peers[key].get('plate') or '')
            label = f'{int(value)} 单' if metric == 'orders' else duration_label(value)
            entries.append({'rank': index, 'name': name[:1] + '师傅' if key != driver else name, 'plateTail': plate[-3:],
                            'value': int(value), 'valueLabel': label, 'isMe': key == driver,
                            # Legacy client field names.
                            'completed': int(value), 'orders': int(value), 'title': name[:1] + '师傅', 'subtitle': label})
        mine = values.get(driver, 0)
        return {'metric': metric, 'period': PERIODS[period] + ('接单排行' if metric == 'orders' else '在线时长排行'),
                'periodKey': period, 'periods': list(METRICS[metric]), 'city': city, 'cityName': me.get('cityName'),
                'scope': '仅统计同一服务区域的司机', 'since': stamp(since), 'myRank': my_rank,
                'myValue': int(mine), 'myValueLabel': f'{int(mine)} 单' if metric == 'orders' else duration_label(mine),
                'completed': int(mine) if metric == 'orders' else None, 'entries': entries, 'peerCount': len(peers),
                'definition': '接单数按乘客真实下单且已完成的订单统计，不含司机私有演示单。' if metric == 'orders'
                              else '在线时长按上线至下线的累计时间统计，仍在线时计算到当前时刻。'}

    # ---- heat map ---------------------------------------------------------
    def heatmap_thresholds(self, city):
        row = self.operations.get('heatmap', city)
        return row or {'city': city, 'passenger': None, 'driver': None, 'version': 0}

    @staticmethod
    def heat_level(count, limits):
        if not limits:
            return 0
        return 3 if count >= limits['high'] else 2 if count >= limits['mid'] else 1 if count >= limits['low'] else 0

    def driver_heatmap(self, driver, kind='passenger'):
        require(kind in ('passenger', 'driver'), '热力图类型无效')
        city = json.loads(self.account(driver)['profile'])['driver']['city']
        config = self.heatmap_thresholds(city)
        limits = config.get(kind)
        any_configured = bool(config.get('passenger') or config.get('driver'))
        cells = defaultdict(int)
        if limits:
            if kind == 'passenger':
                since = now() - dt.timedelta(minutes=HEAT_WINDOW_MINUTES)
                for row in self.db.execute('SELECT payload FROM passenger_orders'):
                    o = json.loads(row['payload'])
                    created = safe_time(o.get('createdAt'))
                    if o.get('city') == city and created and created >= since and type(o.get('originLat')) in (int, float):
                        cells[(round(o['originLat'] / HEAT_CELL), round(o['originLng'] / HEAT_CELL))] += 1
            else:
                for row in self.db.execute('SELECT state,profile,location FROM accounts WHERE location IS NOT NULL'):
                    if not json.loads(row['state']).get('online') or json.loads(row['profile'])['driver'].get('city') != city:
                        continue
                    location = json.loads(row['location']) if row['location'] else None
                    usable, _ = self.usable_trip_location(location)
                    if usable:
                        cells[(round(location['latitude'] / HEAT_CELL), round(location['longitude'] / HEAT_CELL))] += 1
        points = []
        for (la, ln), count in sorted(cells.items()):
            level = self.heat_level(count, limits)
            if level:
                lat, lng, weight = round(la * HEAT_CELL, 6), round(ln * HEAT_CELL, 6), (.4, .7, 1.0)[level - 1]
                points.append({'lat': lat, 'lng': lng, 'latitude': lat, 'longitude': lng, 'weight': weight,
                               'intensity': weight, 'count': count, 'level': level})
        notice = ('当前区域未配置热力阈值，显示普通地图。' if not any_configured else
                  '此类型未配置阈值，不渲染热力。' if not limits else
                  ('客流热力按近60分钟乘客下单起点生成，每60分钟更新。' if kind == 'passenger'
                   else '司机热力按当前在线司机的实时定位生成。'))
        return {'type': kind, 'city': city, 'render': bool(limits), 'thresholdsConfigured': any_configured,
                'thresholds': limits, 'points': points, 'source': 'orders' if kind == 'passenger' else 'drivers',
                'windowMinutes': HEAT_WINDOW_MINUTES if kind == 'passenger' else 0,
                'refreshSeconds': 3600 if kind == 'passenger' else 30, 'updatedAt': stamp(), 'notice': notice}

    # ---- points & mall ----------------------------------------------------
    def points_rule(self, audience):
        row = self.operations.get('pointsRule', audience)
        return row or {'audience': audience, 'perOrder': 1, 'enabled': True, 'version': 0}

    def passenger_points(self, data):
        points = data.setdefault('points', {'balance': 0, 'total': 0, 'entries': []})
        return points

    def driver_points_per_order(self):
        rule = self.points_rule('DRIVER')
        return rule['perOrder'] if rule['enabled'] else 0

    def mall_products(self, audience):
        rows = [p for p in self.operations.rows('mallProduct') if p['enabled'] and p['audience'] == audience]
        return [{k: p[k] for k in ('id', 'name', 'description', 'points', 'stock', 'type', 'redeemed')} for p in rows]

    def mall_redemptions(self, audience, owner):
        rows = [r for r in self.operations.rows('mallRedemption') if r['audience'] == audience and r['ownerId'] == owner]
        return sorted(rows, key=lambda r: r['createdAt'], reverse=True)[:50]

    def mall_view(self, audience, owner):
        return {'products': self.mall_products(audience), 'redemptions': self.mall_redemptions(audience, owner),
                'rule': {k: self.points_rule(audience)[k] for k in ('perOrder', 'enabled')},
                'notice': '积分为平台体验积分，不可提现或转让；实物商品由运营人员线下发货。'}

    def mall_redeem(self, audience, owner, points, p):
        """Mutates the supplied points ledger; the caller persists it."""
        require(set(p) <= {'productId', 'receiverName', 'receiverMobile', 'receiverAddress'}, '兑换参数不支持')
        product = self.operations.get('mallProduct', self.operations.identifier(p.get('productId')))
        require(product is not None and product['enabled'] and product['audience'] == audience, '商品不存在或已下架', 404)
        require(product['stock'] > 0, '商品已兑完', 409)
        require(points['balance'] >= product['points'], '积分不足', 409)
        receiver = None
        if product['type'] == 'PHYSICAL':
            mobile = p.get('receiverMobile')
            require(isinstance(mobile, str) and bool(re.fullmatch(r'1\d{10}', mobile)), '收货手机号须为11位数字')
            receiver = {'name': textfield(p.get('receiverName'), '收货人', 1, 30), 'mobile': mobile,
                        'address': textfield(p.get('receiverAddress'), '收货地址', 5, 200)}
        product.update(stock=product['stock'] - 1, redeemed=product.get('redeemed', 0) + 1)
        self.operations.write('mallProduct', product['id'], product)
        record = {'id': uid('RD'), 'productId': product['id'], 'productName': product['name'], 'type': product['type'],
                  'audience': audience, 'ownerId': owner, 'points': product['points'], 'receiver': receiver,
                  'status': 'PENDING_SHIPMENT' if product['type'] == 'PHYSICAL' else 'COMPLETED',
                  'createdAt': stamp(), 'shippedAt': None, 'shipNote': '', 'version': 1}
        self.operations.write('mallRedemption', record['id'], record)
        points['balance'] -= product['points']
        points['entries'].insert(0, {'id': uid('PT'), 'title': '兑换 ' + product['name'], 'delta': -product['points'], 'date': stamp()})
        return {'redemption': clone(record), 'balance': points['balance']}

    def points_accounts(self):
        rows = []
        for r in self.db.execute('SELECT id,mobile,profile FROM accounts ORDER BY id'):
            profile = json.loads(r['profile']); pts = profile.get('points', {})
            rows.append({'audience': 'DRIVER', 'ownerId': r['id'], 'name': profile['driver'].get('name'), 'mobile': r['mobile'],
                         'balance': pts.get('balance', 0), 'total': pts.get('total', 0), 'entries': pts.get('entries', [])[:20]})
        self.db.execute('CREATE TABLE IF NOT EXISTS passenger_extras(pid TEXT PRIMARY KEY,payload TEXT NOT NULL)')
        extras = {r['pid']: json.loads(r['payload']) for r in self.db.execute('SELECT pid,payload FROM passenger_extras')}
        for r in self.db.execute('SELECT id,mobile,profile FROM passengers ORDER BY id'):
            pts = extras.get(r['id'], {}).get('points', {})
            rows.append({'audience': 'PASSENGER', 'ownerId': r['id'], 'name': json.loads(r['profile']).get('name'), 'mobile': r['mobile'],
                         'balance': pts.get('balance', 0), 'total': pts.get('total', 0), 'entries': pts.get('entries', [])[:20]})
        return rows
