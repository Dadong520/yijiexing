"""修改意见3.0: points rules (乘客：完成订单/支付金额；司机：日间/夜间订单，固定或阶梯),
referral (拉新) and the driver service score (服务分). Non-monetary; points only.
"""
from common import *

MAX_REWARDED_INVITES = 100
DAY_START, DAY_END = 5 * 60 + 30, 23 * 60   # 日间 5:30-23:00（下单时间）
LEVEL_NAMES = ['卓越司机', '优秀司机', '标准司机', '待提升司机', '风险司机']
DEFAULT_SCORE_RULE = {
    'initial': 100, 'max': None, 'min': None,
    'levels': [{'name': '卓越司机', 'min': 110, 'max': None}, {'name': '优秀司机', 'min': 100, 'max': 110},
               {'name': '标准司机', 'min': 90, 'max': 100}, {'name': '待提升司机', 'min': 80, 'max': 90},
               {'name': '风险司机', 'min': None, 'max': 80}],
    'acceptSlots': {'enabled': False, 'items': []}, 'onlineAdd': {'enabled': False, 'items': []},
    'ratingAdd': {'enabled': False, 'points': 0}, 'onlineDeduct': {'enabled': False, 'items': []},
    'ratingDeduct': {'enabled': False, 'items': {}},
    'complaintDeduct': {'enabled': False, 'cycleDays': None, 'items': []},
    'version': 0, 'enabledAt': None,
}


def is_day_order(created):
    t = parse_time(created).astimezone(TZ)
    m = t.hour * 60 + t.minute
    return DAY_START <= m < DAY_END


def tier_points(tiers, value):
    best = 0
    for t in sorted(tiers, key=lambda t: t['from']):
        if value >= t['from']:
            best = t['points']
    return best


class GrowthMixin:
    def init_growth(self):
        self.db.execute('CREATE TABLE IF NOT EXISTS invite_codes(code TEXT PRIMARY KEY,role TEXT,owner TEXT,UNIQUE(role,owner))')
        self.db.execute('''CREATE TABLE IF NOT EXISTS referrals(id TEXT PRIMARY KEY,inviter_role TEXT,inviter TEXT,
            invitee_role TEXT,invitee TEXT,created TEXT,status TEXT,reward INTEGER,rewarded_at TEXT,trigger TEXT,
            UNIQUE(invitee_role,invitee))''')
        self.db.execute('CREATE TABLE IF NOT EXISTS driver_service_scores(driver TEXT PRIMARY KEY,score INTEGER,evaluated_through TEXT)')
        self.db.execute('''CREATE TABLE IF NOT EXISTS service_score_entries(id TEXT PRIMARY KEY,driver TEXT,delta INTEGER,
            score_after INTEGER,reason TEXT,ref TEXT,created TEXT)''')

    # ---- points ---------------------------------------------------------
    def add_points(self, role, owner, delta, title):
        if not delta:
            return
        entry = {'id': uid('PT'), 'title': title, 'delta': delta, 'date': stamp()}
        if role == 'PASSENGER':
            data = self._passenger_extra_store(owner); pts = data['points']
        else:
            profile = json.loads(self.account(owner)['profile']); pts = profile['points']
        pts['balance'] += delta
        if delta > 0:
            pts['total'] += delta
        pts['entries'].insert(0, entry); del pts['entries'][200:]
        if role == 'PASSENGER':
            self._save_passenger_extras(owner, data)
        else:
            self.save_profile(owner, profile)

    def points_rules(self, audience):
        row = self.points_rule(audience)
        if row.get('rules'):
            return row['enabled'], row['rules']
        trigger = 'ORDER' if audience == 'PASSENGER' else 'ANY'
        return row['enabled'], [{'trigger': trigger, 'mode': 'FIXED', 'points': row.get('perOrder', 1), 'tiers': []}]

    def month_completed(self, role, owner, predicate=lambda o: True):
        start = now().astimezone(TZ).replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        column = 'passenger' if role == 'PASSENGER' else 'driver'
        n = 0
        for r in self.db.execute(f'SELECT payload FROM passenger_orders WHERE {column}=?', (owner,)):
            o = json.loads(r['payload'])
            if o.get('stage') == 'DONE' and o.get('completedAt') and parse_time(o['completedAt']) >= start and predicate(o):
                n += 1
        return n

    def order_points(self, audience, o, owner):
        enabled, rules = self.points_rules(audience)
        if not enabled:
            return 0, []
        total, lines = 0, []
        day = is_day_order(o['createdAt'])
        for rule in rules:
            trig = rule['trigger']
            if trig == 'AMOUNT':
                yuan = o.get('fare', 0) // 100
                # Both ORDER and AMOUNT tiers use this month's completed-order index.
                # For AMOUNT the selected tier is a per-whole-yuan multiplier.
                count = self.month_completed(audience, owner, lambda x: x['id'] != o['id']) + 1
                multiplier = rule['points'] if rule['mode'] == 'FIXED' else tier_points(rule['tiers'], count)
                pts = multiplier * yuan
                label = '支付金额积分'
            elif trig in ('ORDER', 'ANY') or (trig == 'DAY' and day) or (trig == 'NIGHT' and not day):
                # This order is not saved as DONE yet: it is the (count+1)-th of the month.
                if trig in ('DAY', 'NIGHT'):
                    count = self.month_completed(audience, owner, lambda x: x['id'] != o['id'] and is_day_order(x['createdAt']) == day) + 1
                else:
                    count = self.month_completed(audience, owner, lambda x: x['id'] != o['id']) + 1
                pts = rule['points'] if rule['mode'] == 'FIXED' else tier_points(rule['tiers'], count)
                label = {'ORDER': '完成订单积分', 'ANY': '完成订单积分', 'DAY': '日间订单积分', 'NIGHT': '夜间订单积分'}[trig]
            else:
                continue
            if pts > 0:
                total += pts; lines.append(label)
        return total, lines

    def award_passenger_points(self, o):
        pid = o.get('passengerId')
        if not pid or o.get('passengerPointsAwarded') is not None:
            return
        if not self.db.execute('SELECT 1 FROM passengers WHERE id=?', (pid,)).fetchone():
            return
        pts, lines = self.order_points('PASSENGER', o, pid)
        self.add_points('PASSENGER', pid, pts, '、'.join(lines) + ' ' + o['id'] if lines else '')
        o['passengerPointsAwarded'] = pts
        self.referral_first_order('PASSENGER', pid)

    def award_driver_points(self, driver, o):
        pts, lines = self.order_points('DRIVER', o, driver) if o.get('shared') else (self.driver_points_per_order(), ['完成订单积分'])
        self.add_points('DRIVER', driver, pts, '、'.join(lines) + ' ' + o['id'] if lines else '')
        if o.get('shared'):
            self.referral_first_order('DRIVER', driver)

    # ---- referral -------------------------------------------------------
    def invite_code(self, role, owner):
        row = self.db.execute('SELECT code FROM invite_codes WHERE role=? AND owner=?', (role, owner)).fetchone()
        if row:
            return row['code']
        while True:
            code = ('P' if role == 'PASSENGER' else 'D') + secrets.token_hex(3).upper()
            if not self.db.execute('SELECT 1 FROM invite_codes WHERE code=?', (code,)).fetchone():
                break
        self.db.execute('INSERT INTO invite_codes VALUES (?,?,?)', (code, role, owner))
        return code

    def referral_rule(self, role):
        return self.operations.get('referralRule', role) or {'role': role, 'enabled': False, 'trigger': 'REGISTER',
            'tiered': False, 'points': 0, 'tiers': [], 'cumulative': False, 'bonuses': [], 'version': 0}

    def register_referral(self, role, owner, code):
        if code is None or (isinstance(code, str) and not code.strip()):
            return None
        require(isinstance(code, str) and re.fullmatch(r'[PD][0-9A-F]{6}', code.strip().upper()) is not None, '邀请码格式无效')
        row = self.db.execute('SELECT * FROM invite_codes WHERE code=?', (code.strip().upper(),)).fetchone()
        require(row is not None, '邀请码不存在', 404)
        require(not (row['role'] == role and row['owner'] == owner), '不能填写自己的邀请码')
        rid = uid('RF')
        self.db.execute('INSERT OR IGNORE INTO referrals VALUES (?,?,?,?,?,?,?,?,?,?)',
                        (rid, row['role'], row['owner'], role, owner, stamp(), 'PENDING', 0, None, None))
        if self.referral_rule(row['role'])['trigger'] == 'REGISTER':
            self.reward_referral(rid, 'REGISTER')
        return rid

    def referral_first_order(self, role, owner):
        row = self.db.execute("SELECT id,inviter_role FROM referrals WHERE invitee_role=? AND invitee=? AND status='PENDING'", (role, owner)).fetchone()
        if row and self.referral_rule(row['inviter_role'])['trigger'] == 'FIRST_ORDER':
            self.reward_referral(row['id'], 'FIRST_ORDER')

    def reward_referral(self, rid, trigger):
        r = self.db.execute('SELECT * FROM referrals WHERE id=?', (rid,)).fetchone()
        if not r or r['status'] != 'PENDING':
            return
        rule = self.referral_rule(r['inviter_role'])
        if not rule['enabled']:
            self.db.execute("UPDATE referrals SET status='NO_RULE',trigger=? WHERE id=?", (trigger, rid))
            return
        n = self.db.execute("SELECT count(*) FROM referrals WHERE inviter_role=? AND inviter=? AND status='REWARDED'",
                            (r['inviter_role'], r['inviter'])).fetchone()[0] + 1
        if n > MAX_REWARDED_INVITES:
            self.db.execute("UPDATE referrals SET status='LIMIT_REACHED',trigger=? WHERE id=?", (trigger, rid))
            return
        pts = tier_points(rule['tiers'], n) if rule['tiered'] else rule['points']
        bonus = sum(b['points'] for b in rule['bonuses'] if b['count'] == n) if rule['cumulative'] else 0
        self.add_points(r['inviter_role'], r['inviter'], pts, f'邀请奖励（第{n}位）')
        if bonus:
            self.add_points(r['inviter_role'], r['inviter'], bonus, f'累计邀请{n}人奖励')
        self.db.execute("UPDATE referrals SET status='REWARDED',reward=?,rewarded_at=?,trigger=? WHERE id=?",
                        (pts + bonus, stamp(), trigger, rid))

    def referral_records(self, role=None, owner=None):
        sql, args = 'SELECT * FROM referrals', ()
        if role:
            sql += ' WHERE inviter_role=? AND inviter=?'; args = (role, owner)
        rows = []
        for r in self.db.execute(sql + ' ORDER BY created DESC', args):
            table = 'passengers' if r['invitee_role'] == 'PASSENGER' else 'accounts'
            invitee = self.db.execute(f'SELECT mobile FROM {table} WHERE id=?', (r['invitee'],)).fetchone()
            mobile = invitee['mobile'] if invitee else ''
            rows.append({**dict(r), 'mobileMasked': mobile[:3] + '****' + mobile[-4:] if mobile else '',
                         'phoneTail': mobile[-4:], 'completed': r['status'] == 'REWARDED', 'createdAt': r['created']})
        return rows

    def referral_view(self, role, owner):
        records = self.referral_records(role, owner)
        rule = self.referral_rule(role)
        rewarded = [r for r in records if r['status'] == 'REWARDED']
        return {'code': self.invite_code(role, owner), 'inviteCode': self.invite_code(role, owner),
                'invited': len(records), 'count': len(records), 'completed': len(rewarded),
                'awarded': sum(r['reward'] for r in rewarded), 'reward': sum(r['reward'] for r in rewarded),
                'records': [{'mobileMasked': r['mobileMasked'], 'phoneTail': r['phoneTail'], 'createdAt': r['created'],
                             'status': r['status'], 'reward': r['reward'], 'completed': r['completed']} for r in records],
                'rule': {k: rule[k] for k in ('enabled', 'trigger', 'tiered', 'points', 'tiers', 'cumulative', 'bonuses')},
                'maxRewarded': MAX_REWARDED_INVITES}

    # ---- service score --------------------------------------------------
    def score_rule(self):
        return self.operations.get('serviceScoreRule', 'RULE') or clone(DEFAULT_SCORE_RULE)

    def score_level(self, score, rule=None):
        for lv in (rule or self.score_rule())['levels']:
            if (lv['min'] is None or score >= lv['min']) and (lv['max'] is None or score < lv['max']):
                return lv['name']
        return '未分级'

    def driver_score(self, driver):
        row = self.db.execute('SELECT score FROM driver_service_scores WHERE driver=?', (driver,)).fetchone()
        if row:
            return row['score']
        score = self.score_rule()['initial']
        yesterday = (now().date() - dt.timedelta(days=1)).isoformat()
        self.db.execute('INSERT INTO driver_service_scores VALUES (?,?,?)', (driver, score, yesterday))
        return score

    def change_score(self, driver, delta, reason, ref=''):
        if not delta:
            return
        rule = self.score_rule()
        score = self.driver_score(driver) + delta
        if rule.get('max') is not None:
            score = min(score, rule['max'])
        if rule.get('min') is not None:
            score = max(score, rule['min'])
        self.db.execute('UPDATE driver_service_scores SET score=? WHERE driver=?', (score, driver))
        self.db.execute('INSERT INTO service_score_entries VALUES (?,?,?,?,?,?,?)', (uid('SS'), driver, delta, score, reason, ref, stamp()))

    def score_order_events(self, o):
        """Called from save_shared: acceptance, manual rating and new complaints, each once."""
        driver = o.get('driverId')
        if not driver:
            return
        rule = self.score_rule()
        if o.get('acceptedAt') and o.get('scoreAcceptRef') != o['acceptedAt']:
            o['scoreAcceptRef'] = o['acceptedAt']
            if rule['acceptSlots']['enabled']:
                t = parse_time(o['acceptedAt']).astimezone(TZ); m = t.hour * 60 + t.minute
                for slot in rule['acceptSlots']['items']:
                    s, e = slot['start'], slot['end']
                    sm, em = int(s[:2]) * 60 + int(s[3:]), int(e[:2]) * 60 + int(e[3:])
                    if (sm <= m < em) if sm < em else (m >= sm or m < em):
                        self.change_score(driver, slot['points'], f'接单加分 {s}-{e}', o['id']); break
        ev = o.get('evaluation')
        if ev and not o.get('scoreRatingDone') and ev.get('source', 'MANUAL') == 'MANUAL':
            o['scoreRatingDone'] = True
            rating = ev['rating']
            if rating == 5 and rule['ratingAdd']['enabled']:
                self.change_score(driver, rule['ratingAdd']['points'], '乘客评分5分', o['id'])
            if rule['ratingDeduct']['enabled']:
                pts = rule['ratingDeduct']['items'].get(f'{float(rating):g}', 0)
                self.change_score(driver, -pts, f'乘客评分{rating}分', o['id'])
        complaints = o.get('complaints') or []
        counted = o.get('scoreComplaintsCounted', 0)
        if len(complaints) > counted:
            o['scoreComplaintsCounted'] = len(complaints)
            cfg = rule['complaintDeduct']
            if cfg['enabled']:
                for _ in range(len(complaints) - counted):
                    n = self.driver_complaint_count(driver, cfg.get('cycleDays'), rule.get('enabledAt'), o)
                    for item in cfg['items']:
                        if n >= item['min'] and (item.get('max') is None or n <= item['max']):
                            self.change_score(driver, -item['points'], f'周期内第{n}次投诉', o['id']); break

    def driver_complaint_count(self, driver, cycle_days, enabled_at, current_order=None):
        start = parse_time(enabled_at) if enabled_at else None
        if start and cycle_days:
            periods = int((now() - start).total_seconds() // (cycle_days * 86400))
            start = start + dt.timedelta(days=periods * cycle_days)
        n = 0
        for r in self.db.execute('SELECT payload FROM passenger_orders WHERE driver=?', (driver,)):
            order = json.loads(r['payload'])
            if current_order and order['id'] == current_order['id']:
                order = current_order
            for c in order.get('complaints') or []:
                if start is None or parse_time(c['createdAt']) >= start:
                    n += 1
        return n

    def evaluate_daily_scores(self):
        rule = self.score_rule()
        if not (rule['onlineAdd']['enabled'] or rule['onlineDeduct']['enabled']):
            return
        yesterday = now().date() - dt.timedelta(days=1)
        for r in self.db.execute('SELECT id FROM accounts').fetchall():
            self.driver_score(r['id'])
            done = dt.date.fromisoformat(self.db.execute('SELECT evaluated_through FROM driver_service_scores WHERE driver=?', (r['id'],)).fetchone()[0])
            day = done + dt.timedelta(days=1)
            while day <= yesterday:
                since = dt.datetime.combine(day, dt.time(), TZ)
                minutes = self.online_seconds_between(r['id'], since, since + dt.timedelta(days=1)) / 60
                for key, sign, label in (('onlineAdd', 1, '在线时长加分'), ('onlineDeduct', -1, '在线时长扣分')):
                    if rule[key]['enabled']:
                        for item in rule[key]['items']:
                            if item['min'] <= minutes < item['max'] or (item['max'] == 1440 and minutes >= 1440):
                                self.change_score(r['id'], sign * item['points'], f'{label}（{day.isoformat()} 在线{int(minutes)}分钟）', day.isoformat()); break
                self.db.execute('UPDATE driver_service_scores SET evaluated_through=? WHERE driver=?', (day.isoformat(), r['id']))
                day += dt.timedelta(days=1)

    def online_seconds_between(self, driver, start, end):
        total = 0
        for row in self.db.execute('SELECT started,ended FROM driver_online_log WHERE driver=?', (driver,)):
            s = parse_time(row['started']); e = parse_time(row['ended']) if row['ended'] else now()
            total += max(0, (min(e, end) - max(s, start)).total_seconds())
        return total

    def service_score_view(self, driver):
        rule = self.score_rule()
        score = self.driver_score(driver)
        entries = [{'title': r['reason'], 'delta': r['delta'], 'scoreAfter': r['score_after'], 'date': r['created']}
                   for r in self.db.execute('SELECT * FROM service_score_entries WHERE driver=? ORDER BY created DESC LIMIT 100', (driver,))]
        return {'value': score, 'score': score, 'level': self.score_level(score, rule), 'entries': entries, 'records': entries,
                'levels': rule['levels'], 'notice': '服务分仅用于展示司机服务等级，不影响派单和接单。'}
