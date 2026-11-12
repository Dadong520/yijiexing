"""修改意见3.0 admin operations: tariff, service fee, coupons and pool, new-user
coupons, points rules, referral rules, service score rules and invoice handling.

Each action validates its whole payload, checks expectedVersion where it edits a
versioned record, and returns the stored value. The caller audits and records
idempotency (AdminConsole.action).
"""
from common import *
from pricing import DEFAULT_TARIFF, TARIFF_INT_FIELDS, clock_minutes
from growth import LEVEL_NAMES

EXTRA_ACTIONS = frozenset({
    'ops.tariff.save', 'ops.serviceFee.save', 'ops.couponTemplate.save', 'ops.couponPool.recharge',
    'ops.newUserCoupon.save', 'ops.referralRule.save', 'ops.serviceScoreRule.save',
    'ops.invoice.redFlush', 'ops.invoice.reissue', 'ops.invoiceReissue.decide', 'ops.invoiceIssuer.save',
    'ops.techInvoice.issue'})
TARIFF_LIMITS = {'startPrice': (0, 100000), 'startMeters': (0, 100000), 'unitPrice': (1, 10000), 'unitMeters': (50, 10000),
                 'returnAfterMeters': (0, 1000000), 'returnRate': (0, 300), 'nightRate': (0, 300), 'freeWaitMinutes': (0, 120),
                 'waitPrice': (0, 10000), 'lowSpeedKmh': (0, 60), 'emptyFee': (0, 100000), 'freeCancelMinutes': (0, 120)}
RATING_KEYS = [f'{x / 2:g}' for x in range(1, 10)]  # 0.5 ... 4.5（满分5分不扣分）


def integer(value, label, low, high=None):
    require(type(value) is int and value >= low and (high is None or value <= high),
            f'{label}须为{low}{"至" + str(high) if high is not None else "以上"}的整数')
    return value


def boolean(value, label):
    require(type(value) is bool, label + '须为布尔值')
    return value


def ranges(items, label, low=0, high=None, open_max=False):
    require(isinstance(items, list) and len(items) <= 5, label + '最多5个范围')
    out = []
    for item in items:
        require(isinstance(item, dict), label + '格式无效')
        lo = integer(item.get('min'), label + '最小值', low, high)
        hi = item.get('max')
        if hi is None:
            require(open_max, label + '须填写最大值')
        else:
            integer(hi, label + '最大值', low, high); require(hi > lo, label + '最大值须大于最小值')
        out.append({'min': lo, 'max': hi, 'points': integer(item.get('points'), label + '分值', 0, 100000)})
    out.sort(key=lambda r: r['min'])
    for a, b in zip(out, out[1:]):
        require(a['max'] is not None and (b['min'] > a['max'] if open_max else b['min'] >= a['max']), label + '范围不能重叠')
    return out


class OpsExtras:
    def __init__(self, ops):
        self.ops, self.svc = ops, ops.svc

    def versioned(self, kind, oid, p):
        before = self.ops.get(kind, oid)
        self.ops.expected(before, p)
        return before, (before['version'] + 1 if before else 1)

    def handle(self, action, p, actor):
        require(isinstance(p, dict), '操作参数须为对象')
        fn = getattr(self, action.replace('ops.', '').replace('.', '_'))
        return fn(p, actor)

    # ---- pricing --------------------------------------------------------
    def tariff_save(self, p, actor):
        self.ops.fields(p, set(DEFAULT_TARIFF) | {'city', 'expectedVersion'}, set(DEFAULT_TARIFF) | {'city', 'expectedVersion'})
        city = self.svc.city(self.ops.identifier(p['city']))['id']
        before, version = self.versioned('tariff', city, p)
        value = {'city': city, 'version': version, 'updatedAt': stamp(), 'updatedBy': actor}
        for k in TARIFF_INT_FIELDS:
            value[k] = integer(p[k], k, *TARIFF_LIMITS[k])
        for k in ('nightStart', 'nightEnd'):
            clock_minutes(p[k]); value[k] = p[k]
        self.ops.write('tariff', city, value)
        return 'tariff', before, value

    def serviceFee_save(self, p, actor):
        self.ops.fields(p, {'city', 'expectedVersion', 'amount', 'enabled'}, {'city', 'expectedVersion', 'amount', 'enabled'})
        city = self.svc.city(self.ops.identifier(p['city']))['id']
        before, version = self.versioned('serviceFee', city, p)
        value = {'city': city, 'amount': integer(p['amount'], '区域服务费（分）', 0, 100000),
                 'enabled': boolean(p['enabled'], '启用状态'), 'version': version, 'updatedAt': stamp()}
        self.ops.write('serviceFee', city, value)
        return 'serviceFee', before, value

    # ---- coupons --------------------------------------------------------
    def couponTemplate_save(self, p, actor):
        allowed = {'id', 'expectedVersion', 'name', 'description', 'type', 'threshold', 'amount', 'validity', 'validDays',
                   'validStartAt', 'validEndAt', 'totalQuantity', 'perUserLimit', 'claimable', 'claimStartAt', 'claimEndAt', 'enabled'}
        self.ops.fields(p, allowed, {'name', 'type', 'amount', 'validity', 'totalQuantity', 'perUserLimit', 'claimable', 'enabled'})
        oid = self.ops.identifier(p['id']) if 'id' in p else uid('CT')
        before, version = self.versioned('couponTemplate', oid, p)
        if 'id' in p:
            require(before is not None, '优惠券不存在', 404)
        require(p['type'] in ('FULL_REDUCTION', 'DIRECT'), '优惠类型须为满减券或立减券')
        amount = integer(p['amount'], '优惠金额（分）', 1, 1000000)
        threshold = integer(p.get('threshold', 0), '使用门槛（分）', 0, 10000000)
        if p['type'] == 'FULL_REDUCTION':
            require(threshold > amount, '满减券门槛须大于优惠金额')
        else:
            threshold = 0
        value = {'id': oid, 'name': textfield(p['name'], '优惠券名称', 1, 40), 'description': textfield(p.get('description') or '', '使用说明', 0, 300),
                 'type': p['type'], 'threshold': threshold, 'amount': amount, 'validity': p['validity'],
                 'totalQuantity': integer(p['totalQuantity'], '发放总量', 1, 1000000), 'perUserLimit': integer(p['perUserLimit'], '每人限领', 1, 100),
                 'claimable': boolean(p['claimable'], '领券中心展示'), 'enabled': boolean(p['enabled'], '启用状态'),
                 'issued': before['issued'] if before else 0, 'version': version, 'updatedAt': stamp(),
                 'createdAt': before['createdAt'] if before else stamp()}
        require(value['totalQuantity'] >= value['issued'], '发放总量不能小于已发放数量', 409)
        if p['validity'] == 'DAYS':
            value['validDays'] = integer(p.get('validDays'), '领取后有效天数', 1, 365)
        else:
            require(p['validity'] == 'RANGE', '有效期类型无效')
            start, end = parse_time(p.get('validStartAt')), parse_time(p.get('validEndAt'))
            require(end > start, '有效期结束须晚于开始')
            value.update(validStartAt=stamp(start.astimezone(TZ)), validEndAt=stamp(end.astimezone(TZ)))
        for k in ('claimStartAt', 'claimEndAt'):
            if p.get(k):
                value[k] = stamp(parse_time(p[k]).astimezone(TZ))
        value['cost'] = value['amount'] * value['totalQuantity']
        self.ops.write('couponTemplate', oid, value)
        return 'couponTemplate', before, value

    def couponPool_recharge(self, p, actor):
        self.ops.fields(p, {'amount', 'reference', 'note'}, {'amount', 'reference'})
        amount = integer(p['amount'], '充值金额（分）', 1, 1000000000)
        ref = textfield(p['reference'], '扫呗入账单号', 2, 64)
        require(not any(e.get('reference') == ref for e in self.ops.rows('couponPoolEntry')), '该入账单号已登记', 409)
        before = self.svc.coupon_pool()
        pool, _ = self.svc.pool_change('RECHARGE', amount, reference=ref, note=textfield(p.get('note') or '', '备注', 0, 200),
                                       operator=actor, manual=True)
        return 'couponPool', before, pool

    def newUserCoupon_save(self, p, actor):
        self.ops.fields(p, {'expectedVersion', 'enabled', 'templateIds'}, {'expectedVersion', 'enabled', 'templateIds'})
        before, version = self.versioned('newUserCoupon', 'CONFIG', p)
        ids = p['templateIds']
        require(isinstance(ids, list) and all(isinstance(x, str) for x in ids) and len(ids) <= 10 and len(set(ids)) == len(ids), '关联优惠券最多10张且不能重复')
        for tid in ids:
            t = self.ops.get('couponTemplate', self.ops.identifier(tid)); require(t is not None, '关联优惠券不存在', 404)
        value = {'id': 'CONFIG', 'enabled': boolean(p['enabled'], '启用状态'), 'templateIds': ids, 'version': version, 'updatedAt': stamp()}
        self.ops.write('newUserCoupon', 'CONFIG', value)
        return 'newUserCoupon', before, value

    # ---- growth ---------------------------------------------------------
    def tiers(self, items, label):
        require(isinstance(items, list) and 1 <= len(items) <= 5, label + '须为1至5档')
        require(all(isinstance(t, dict) for t in items), label + '格式无效')
        out = [{'from': integer(t.get('from'), label + '起始值', 1), 'points': integer(t.get('points'), label + '积分', 0, 1000000)} for t in items]
        require(len({t['from'] for t in out}) == len(out), label + '起始值不能重复')
        return sorted(out, key=lambda t: t['from'])

    def referralRule_save(self, p, actor):
        keys = {'role', 'expectedVersion', 'enabled', 'trigger', 'tiered', 'points', 'tiers', 'cumulative', 'bonuses'}
        self.ops.fields(p, keys, keys)
        require(p['role'] in ('PASSENGER', 'DRIVER'), '适用对象无效')
        require(p['trigger'] in ('REGISTER', 'FIRST_ORDER'), '触发条件须为注册或首单')
        before, version = self.versioned('referralRule', p['role'], p)
        value = {'role': p['role'], 'enabled': boolean(p['enabled'], '启用状态'), 'trigger': p['trigger'],
                 'tiered': boolean(p['tiered'], '是否阶梯'), 'cumulative': boolean(p['cumulative'], '累计奖励'),
                 'points': integer(p['points'], '每邀请积分', 0, 1000000) if not p['tiered'] else 0,
                 'tiers': self.tiers(p['tiers'], '阶梯') if p['tiered'] else [],
                 'bonuses': [], 'version': version, 'updatedAt': stamp()}
        if p['cumulative']:
            require(all(isinstance(b, dict) for b in p['bonuses']) if isinstance(p['bonuses'], list) else False, '累计奖励格式无效')
            require(isinstance(p['bonuses'], list) and 1 <= len(p['bonuses']) <= 5, '累计奖励须为1至5档')
            value['bonuses'] = sorted([{'count': integer(b.get('count'), '累计人数', 1, 100), 'points': integer(b.get('points'), '奖励积分', 1, 1000000)}
                                       for b in p['bonuses']], key=lambda b: b['count'])
        require(len({b['count'] for b in value['bonuses']}) == len(value['bonuses']), '累计奖励人数不能重复')
        self.ops.write('referralRule', p['role'], value)
        return 'referralRule', before, value

    def points_rules(self, audience, rules):
        triggers = ('ORDER', 'AMOUNT') if audience == 'PASSENGER' else ('DAY', 'NIGHT')
        require(isinstance(rules, list) and 1 <= len(rules) <= len(triggers), '积分规则数量无效')
        out = []
        for r in rules:
            require(isinstance(r, dict) and r.get('trigger') in triggers and r.get('mode') in ('FIXED', 'TIERED'), '积分规则触发条件或计算方式无效')
            out.append({'trigger': r['trigger'], 'mode': r['mode'],
                        'points': integer(r.get('points', 0), '固定积分', 0, 100000) if r['mode'] == 'FIXED' else 0,
                        'tiers': self.tiers(r.get('tiers'), '阶梯') if r['mode'] == 'TIERED' else []})
        require(len({r['trigger'] for r in out}) == len(out), '同一触发条件只能配置一条规则')
        return out

    # ---- service score --------------------------------------------------
    def serviceScoreRule_save(self, p, actor):
        keys = {'expectedVersion', 'initial', 'max', 'min', 'levels', 'acceptSlots', 'onlineAdd', 'ratingAdd',
                'onlineDeduct', 'ratingDeduct', 'complaintDeduct'}
        self.ops.fields(p, keys, keys)
        before, version = self.versioned('serviceScoreRule', 'RULE', p)
        initial = integer(p['initial'], '初始服务分', -100000, 100000)
        hi, lo = p['max'], p['min']
        if hi is not None:
            require(type(hi) is int and hi > initial, '服务分上限须为整数且大于初始服务分')
        if lo is not None:
            require(type(lo) is int and lo < initial, '服务分下限须为整数且小于初始服务分')
        levels = p['levels']
        require(isinstance(levels, list) and len(levels) == len(LEVEL_NAMES) and all(isinstance(l, dict) for l in levels) and [l.get('name') for l in levels] == LEVEL_NAMES, '等级须为卓越、优秀、标准、待提升、风险五级')
        for i, lv in enumerate(levels):
            require((lv.get('max') is None) == (i == 0) and (lv.get('min') is None) == (i == 4), '等级范围：卓越无上限、风险无下限，其余须填写')
            for k in ('min', 'max'):
                require(lv.get(k) is None or type(lv[k]) is int, '服务分范围须为整数')
            if i:
                require(lv['max'] == levels[i - 1]['min'], '相邻等级范围须首尾相接')
            if lv.get('min') is not None and lv.get('max') is not None:
                require(lv['max'] > lv['min'], '等级范围最大值须大于最小值')
        slots = p['acceptSlots']
        require(isinstance(slots, dict) and isinstance(slots.get('items'), list) and len(slots['items']) <= 5, '接单时段最多5个')
        boolean(slots.get('enabled'), '接单加分启用')
        items = []
        for s in slots['items']:
            require(isinstance(s, dict), '接单时段格式无效')
            a, b = clock_minutes(s.get('start')), clock_minutes(s.get('end'))
            require(a != b, '时段起止不能相同')
            items.append({'start': s['start'], 'end': s['end'], 'points': integer(s.get('points'), '接单加分', 0, 1000)})
        if slots['enabled']:
            require(items, '启用后须配置接单时段')
            covered = [0] * 1440
            for s in items:
                a, b = clock_minutes(s['start']), clock_minutes(s['end'])
                for m in (range(a, b) if a < b else list(range(a, 1440)) + list(range(0, b))):
                    covered[m] += 1
            require(all(c == 1 for c in covered), '全部接单时段须恰好覆盖24小时且不重叠')
        def block(key, label):
            v = p[key]
            require(isinstance(v, dict), label + '格式无效')
            enabled = boolean(v.get('enabled'), label + '启用')
            out = ranges(v.get('items', []), label, 0, 1440)
            require(not enabled or out, '启用后须配置' + label)
            return {'enabled': enabled, 'items': out}
        rating_add = p['ratingAdd']; require(isinstance(rating_add, dict), '评分加分格式无效')
        rating_deduct = p['ratingDeduct']; require(isinstance(rating_deduct, dict) and isinstance(rating_deduct.get('items'), dict), '评分扣分格式无效')
        require(set(rating_deduct['items']) <= set(RATING_KEYS), '评分档位须为0.5至4.5分')
        complaint = p['complaintDeduct']; require(isinstance(complaint, dict), '投诉扣分格式无效')
        cycle = complaint.get('cycleDays')
        require(cycle is None or (type(cycle) is int and 1 <= cycle <= 3650), '更新周期须为正整数天')
        c_enabled = boolean(complaint.get('enabled'), '投诉扣分启用')
        c_items = ranges(complaint.get('items', []), '投诉次数范围', 0, None, open_max=True)
        require(not c_enabled or c_items, '启用后须配置投诉次数范围')
        require(isinstance(p['onlineAdd'], dict) and isinstance(p['onlineDeduct'], dict), '在线时长规则格式无效')
        enabled_any = slots['enabled'] or p['onlineAdd'].get('enabled') or p['onlineDeduct'].get('enabled') or rating_add.get('enabled') or rating_deduct.get('enabled') or c_enabled
        value = {'initial': initial, 'max': hi, 'min': lo, 'levels': [{'name': l['name'], 'min': l.get('min'), 'max': l.get('max')} for l in levels],
                 'acceptSlots': {'enabled': slots['enabled'], 'items': items}, 'onlineAdd': block('onlineAdd', '在线时长加分'),
                 'onlineDeduct': block('onlineDeduct', '在线时长扣分'),
                 'ratingAdd': {'enabled': boolean(rating_add.get('enabled'), '评分加分启用'), 'points': integer(rating_add.get('points', 0), '五分加分', 0, 1000)},
                 'ratingDeduct': {'enabled': boolean(rating_deduct.get('enabled'), '评分扣分启用'),
                                  'items': {k: integer(v, '评分扣分', 0, 1000) for k, v in rating_deduct['items'].items()}},
                 'complaintDeduct': {'enabled': c_enabled, 'cycleDays': cycle, 'items': c_items},
                 'version': version, 'updatedAt': stamp(),
                 'enabledAt': (before or {}).get('enabledAt') or (stamp() if enabled_any else None)}
        self.ops.write('serviceScoreRule', 'RULE', value)
        return 'serviceScoreRule', before, value

    # ---- invoices -------------------------------------------------------
    def invoice_redFlush(self, p, actor):
        self.ops.fields(p, {'passengerId', 'invoiceId', 'reason'}, {'passengerId', 'invoiceId', 'reason'})
        data, inv = self.svc.find_passenger_invoice(p['passengerId'], p['invoiceId'])
        before = clone(inv)
        self.svc.red_flush(data, inv, textfield(p['reason'], '冲红原因', 2, 200), actor)
        self.svc._save_passenger_extras(p['passengerId'], data)
        return 'invoice', before, inv

    def invoice_reissue(self, p, actor):
        self.ops.fields(p, {'passengerId', 'invoiceId', 'reason', 'type', 'title', 'taxNumber', 'email'},
                        {'passengerId', 'invoiceId', 'reason', 'title'})
        data, inv = self.svc.find_passenger_invoice(p['passengerId'], p['invoiceId'])
        before = clone(inv)
        fields = self.svc.invoice_title_fields(p)
        row = self.svc.reissue_invoice(p['passengerId'], data, inv, fields, textfield(p['reason'], '重开原因', 2, 200), actor)
        return 'invoice', before, row

    def invoiceReissue_decide(self, p, actor):
        self.ops.fields(p, {'id', 'expectedVersion', 'approve', 'reason'}, {'id', 'expectedVersion', 'approve', 'reason'})
        record = self.ops.get('invoiceReissue', self.ops.identifier(p['id']))
        require(record is not None, '重开申请不存在', 404)
        self.ops.expected(record, p)
        require(record['status'] == 'PENDING', '该申请已处理', 409)
        before = clone(record)
        reason = textfield(p['reason'], '处理意见', 2, 200)
        data, inv = self.svc.find_passenger_invoice(record['passengerId'], record['invoiceId'])
        if boolean(p['approve'], '审核结果'):
            row = self.svc.reissue_invoice(record['passengerId'], data, inv, record['newTitle'], record['reason'], actor)
            record.update(status='APPROVED', newInvoiceId=row['id'])
            data, inv = self.svc.find_passenger_invoice(record['passengerId'], record['invoiceId'])
        else:
            record['status'] = 'REJECTED'
        inv['reissueStatus'] = record['status']
        self.svc._save_passenger_extras(record['passengerId'], data)
        record.update(decidedAt=stamp(), decidedBy=actor, decision=reason, version=record['version'] + 1)
        self.ops.write('invoiceReissue', record['id'], record)
        return 'invoiceReissue', before, record

    def invoiceIssuer_save(self, p, actor):
        self.ops.fields(p, {'expectedVersion', 'name', 'taxNumber', 'taxRate'}, {'expectedVersion', 'name', 'taxNumber', 'taxRate'})
        before, version = self.versioned('invoiceIssuer', 'ISSUER', p)
        tax = p['taxNumber'].strip().upper() if isinstance(p['taxNumber'], str) else ''
        require(re.fullmatch(r'[0-9A-Z]{15}|[0-9A-Z]{18}|[0-9A-Z]{20}', tax) is not None, '税号须为15、18或20位字母数字')
        value = {'id': 'ISSUER', 'name': textfield(p['name'], '开票方名称', 2, 100), 'taxNumber': tax,
                 'taxRate': integer(p['taxRate'], '税率（%）', 0, 20), 'version': version, 'updatedAt': stamp()}
        self.ops.write('invoiceIssuer', 'ISSUER', value)
        return 'invoiceIssuer', before, value

    def techInvoice_issue(self, p, actor):
        result = self.svc.issue_tech_invoice(p, actor)
        return 'techInvoice', None, result['techInvoice']
