"""修改意见3.0: coupon templates, passenger coupons, the coupon fund pool and the
new-user coupon activity. Recharges are manual simulated entries (扫呗 not wired).

Pool: 可用余额 available, 未核销 frozen, 已使用 used; total = sum of recharges.
Claim freezes face value; expiry unfreezes; a completed order deducts the
amount actually applied and returns any unused face value to available.
"""
from common import *

WARN_CENTS = 500000   # 可用余额小于5000元：不足预警
FUSE_CENTS = 50000    # 可用余额小于500元：熔断预警，停止领取
POOL_ID = 'POOL'


class CouponMixin:
    # ---- pool -----------------------------------------------------------
    def coupon_pool(self):
        return self.operations.get('couponPool', POOL_ID) or {'id': POOL_ID, 'total': 0, 'available': 0,
                                                               'frozen': 0, 'used': 0, 'version': 0}

    def pool_change(self, kind, amount, **ref):
        pool = self.coupon_pool()
        before = clone(pool)
        if kind == 'RECHARGE':
            pool['total'] += amount; pool['available'] += amount
        elif kind == 'FREEZE':
            pool['available'] -= amount; pool['frozen'] += amount
        elif kind == 'UNFREEZE':
            pool['frozen'] -= amount; pool['available'] += amount
        elif kind == 'DEDUCT':  # amount = applied; ref['face'] = frozen face value
            face = ref.get('face', amount)
            pool['frozen'] -= face; pool['used'] += amount; pool['available'] += face - amount
        require(min(pool['available'], pool['frozen'], pool['used']) >= 0 and pool['available'] + pool['frozen'] + pool['used'] == pool['total'], '营销资金池账务不平衡', 409)
        pool.update(version=pool['version'] + 1, updatedAt=stamp())
        self.operations.write('couponPool', POOL_ID, pool)
        entry = {'id': uid('CPE'), 'type': kind, 'amount': amount, 'createdAt': stamp(),
                 'availableAfter': pool['available'], 'frozenAfter': pool['frozen'], 'usedAfter': pool['used'],
                 'totalAfter': pool['total'], **ref}
        self.operations.write('couponPoolEntry', entry['id'], entry)
        self.check_pool_alerts(pool)
        return pool, before

    def check_pool_alerts(self, pool=None):
        pool = pool or self.coupon_pool()
        day = now().date().isoformat()
        for level, limit, text in (('WARNING', WARN_CENTS, '不足预警：营销资金池可用余额小于5000元'),
                                   ('FUSE', FUSE_CENTS, '熔断预警：营销资金池可用余额小于500元，已停止领券')):
            key = day + ':' + level
            if pool['available'] < limit and not self.operations.get('couponPoolAlert', key):
                # 单日仅触发一次；群消息推送未接入，仅在后台展示。
                self.operations.write('couponPoolAlert', key, {'id': key, 'level': level, 'message': text,
                    'available': pool['available'], 'createdAt': stamp(), 'pushed': False})

    def pool_fused(self):
        return self.coupon_pool()['available'] < FUSE_CENTS

    # ---- templates ------------------------------------------------------
    def coupon_templates(self, claimable_only=False):
        rows = [t for t in self.operations.rows('couponTemplate') if t['enabled']]
        current = now()
        if claimable_only:
            rows = [t for t in rows if t['claimable'] and (not t.get('claimStartAt') or parse_time(t['claimStartAt']) <= current)
                    and (not t.get('claimEndAt') or current < parse_time(t['claimEndAt']))]
        return rows

    def coupon_label(self, t):
        return (f"满{t['threshold'] / 100:g}减{t['amount'] / 100:g}" if t['type'] == 'FULL_REDUCTION' and t['threshold']
                else f"立减{t['amount'] / 100:g}元")

    def issue_coupon(self, pid, data, template_id, source):
        t = self.operations.get('couponTemplate', template_id)
        require(t is not None and t['enabled'], '优惠券不存在或已停用', 404)
        require(t['issued'] < t['totalQuantity'], '优惠券已领完', 409)
        mine = [c for c in data['coupons'] if c['templateId'] == t['id']]
        require(len(mine) < t['perUserLimit'], '已达到领取上限', 409)
        pool = self.coupon_pool()
        require(pool['available'] >= FUSE_CENTS and pool['available'] >= t['amount'], '当前营销余额不足，无法领取', 409,
                {'reason': 'COUPON_POOL_FUSED'})
        claimed = now()
        expires = (claimed + dt.timedelta(days=t['validDays'])) if t['validity'] == 'DAYS' else parse_time(t['validEndAt'])
        require(expires > claimed, '优惠券已过有效期', 409)
        coupon = {'id': uid('CPN'), 'templateId': t['id'], 'name': t['name'], 'label': self.coupon_label(t),
                  'type': t['type'], 'threshold': t['threshold'], 'amount': t['amount'], 'status': 'UNUSED',
                  'source': source, 'claimedAt': stamp(claimed), 'validFrom': t.get('validStartAt') or stamp(claimed),
                  'expiresAt': stamp(expires), 'orderId': None}
        data['coupons'].insert(0, coupon)
        t['issued'] += 1
        self.operations.write('couponTemplate', t['id'], t)
        self.pool_change('FREEZE', t['amount'], couponId=coupon['id'], passengerId=pid, templateId=t['id'])
        return coupon

    def issue_new_user_coupons(self, pid):
        config = self.operations.get('newUserCoupon', 'CONFIG')
        if not config or not config['enabled']:
            return []
        data = self._passenger_extra_store(pid)
        issued = []
        for tid in config['templateIds']:
            try:
                issued.append(self.issue_coupon(pid, data, tid, 'NEW_USER'))
            except ApiError:
                continue  # out of stock, disabled or pool fused: registration still succeeds
        self._save_passenger_extras(pid, data)
        return issued

    # ---- lifecycle ------------------------------------------------------
    def coupon_valid_now(self, c):
        current = now()
        return parse_time(c['validFrom']) <= current < parse_time(c['expiresAt'])

    def expire_coupons(self):
        self.db.execute('CREATE TABLE IF NOT EXISTS passenger_extras(pid TEXT PRIMARY KEY,payload TEXT NOT NULL)')
        current = now()
        for row in self.db.execute('SELECT pid,payload FROM passenger_extras').fetchall():
            data = json.loads(row['payload'])
            changed = False
            for c in data.get('coupons', []):
                if c['status'] == 'UNUSED' and parse_time(c['expiresAt']) <= current:
                    c.update(status='EXPIRED', expiredAt=stamp()); changed = True
                    self.pool_change('UNFREEZE', c['amount'], couponId=c['id'], passengerId=row['pid'])
            if changed:
                self.db.execute('UPDATE passenger_extras SET payload=? WHERE pid=?', (dumps(data), row['pid']))

    def lock_coupon(self, pid, o, coupon_id):
        data = self._passenger_extra_store(pid)
        c = next((x for x in data['coupons'] if x['id'] == coupon_id), None)
        require(c is not None, '优惠券不存在', 404)
        require(c['status'] == 'UNUSED' and self.coupon_valid_now(c), '优惠券不可用或已过期', 409)
        require(o['tripFare'] >= c['threshold'], f"运价费未满{c['threshold'] / 100:g}元，不能使用此券", 409)
        c.update(status='LOCKED', orderId=o['id'], lockedAt=stamp())
        self._save_passenger_extras(pid, data)
        o.update(couponId=c['id'], couponFaceValue=c['amount'], couponLabel=c['label'], couponThreshold=c['threshold'])

    def _order_coupon(self, o):
        if not o.get('couponId') or not o.get('passengerId'):
            return None, None
        data = self._passenger_extra_store(o['passengerId'])
        return data, next((x for x in data['coupons'] if x['id'] == o['couponId'] and x.get('orderId') == o['id']), None)

    def release_order_coupon(self, o):
        """下单后乘客/有责/无责/超时取消均退券；已过期则转为过期并解冻资金。"""
        data, c = self._order_coupon(o)
        if not c or c['status'] != 'LOCKED':
            return
        if parse_time(c['expiresAt']) <= now():
            c.update(status='EXPIRED', expiredAt=stamp(), orderId=None)
            self.pool_change('UNFREEZE', c['amount'], couponId=c['id'], passengerId=o['passengerId'], orderId=o['id'])
        else:
            c.update(status='UNUSED', orderId=None)
        c.pop('lockedAt', None)
        self._save_passenger_extras(o['passengerId'], data)
        o['couponReleased'] = True

    def clear_released_coupon(self, o):
        if not o.get('couponReleased'):
            return
        for key in ('couponId', 'couponFaceValue', 'couponLabel', 'couponThreshold'):
            o.pop(key, None)
        o['couponAmount'] = 0
        self.apply_order_price(o, o.get('estimatedTripFare', o.get('tripFare', o['fare'])))
        o['estimatedFare'] = o['fare']

    def consume_order_coupon(self, o):
        data, c = self._order_coupon(o)
        if not c or c['status'] != 'LOCKED':
            return
        c.update(status='USED', usedAt=stamp(), usedAmount=o.get('couponAmount', 0))
        self._save_passenger_extras(o['passengerId'], data)
        self.pool_change('DEDUCT', o.get('couponAmount', 0), face=c['amount'], couponId=c['id'],
                         passengerId=o['passengerId'], orderId=o['id'])

    def passenger_coupon_view(self, pid, data):
        claimed = {}
        for c in data.get('coupons', []):
            claimed[c['templateId']] = claimed.get(c['templateId'], 0) + 1
        center = [{'id': t['id'], 'name': t['name'], 'label': self.coupon_label(t), 'description': t.get('description', ''),
                   'type': t['type'], 'threshold': t['threshold'], 'amount': t['amount'],
                   'remaining': t['totalQuantity'] - t['issued'], 'claimedCount': claimed.get(t['id'], 0),
                   'perUserLimit': t['perUserLimit'], 'validity': t['validity'], 'validDays': t.get('validDays'),
                   'validEndAt': t.get('validEndAt')} for t in self.coupon_templates(True)]
        return {'coupons': data.get('coupons', []), 'couponCenter': center, 'couponPoolFused': self.pool_fused()}
