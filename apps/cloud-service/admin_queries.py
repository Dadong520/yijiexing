"""Read-only admin projections over the existing transaction and persistent data.

No second database, domain transitions, inferred clearing or external services.
The query and CSV export deliberately share one validated selection path.
"""
from collections import defaultdict
from common import *
from seed import CITIES

DATASETS = {
    'orders': 'orders.read', 'drivers': 'drivers.read',
    'passengers': 'passengers.read', 'payments': 'payments.read',
    'support': 'support.read', 'audit': 'audit.read',
    'statistics': 'statistics.read', 'cancellations': 'orders.read',
    'refunds': 'payments.read',
}
ORDER_FILTERS = {
    'kind': '订单类型', 'fareSource': '费用来源', 'passengerMobile': '乘客账号电话',
    'driverId': '驾驶员账号', 'driverName': '驾驶员姓名', 'driverMobile': '驾驶员电话',
    'stage': '订单状态', 'cancellationType': '取消参与方', 'plate': '车辆号牌',
    'orderId': '订单编号', 'city': '行政区划', 'billingMode': '计费模式',
    'company': '所属公司', 'assigned': '是否指派',
}
FILTERS = {
    'orders': ORDER_FILTERS, 'statistics': ORDER_FILTERS, 'cancellations': ORDER_FILTERS,
    'drivers': {'driverId': '司机账号', 'mobile': '司机电话', 'city': '行政区划',
                'currentLevel': '司机等级', 'certificateStatus': '资质状态',
                'vehicleType': '车型', 'plate': '车牌'},
    'passengers': {'passengerId': '乘客账号', 'mobile': '账号电话', 'city': '行政区划'},
    'payments': {'recordType': '记录类型', 'paymentStatus': '订单支付状态',
                 'refundStatus': '订单退款状态', 'withdrawalStatus': '提现状态',
                 'orderId': '订单编号', 'accountId': '账号'},
    'support': {'source': '来源', 'status': '处理状态', 'orderId': '订单编号', 'accountId': '账号'},
    'audit': {'actor': '操作者', 'action': '操作'},
    'refunds': {'orderId': '订单编号', 'accountId': '乘客账号', 'driverId': '司机账号',
                'refundSource': '退款轮次来源'},
}
UNSUPPORTED = {
    'billingMode': '原后台线上/线下计费模式未在共享订单持久保存，费用来源不等于计费模式。',
    'company': '共享订单没有权威公司ID或归属；演示司机organization不作为公司维度。',
    'assigned': '未实现后台指派业务，driverId不等于是否指派。',
}
FIXED = {
    'kind': ['STANDARD', 'RESERVED', 'BID', 'NEGOTIATED'],
    'stage': ['AVAILABLE', 'PREPAY', 'RESERVED', 'PICKUP', 'ARRIVED', 'TRIP',
              'WAIT_PAY', 'DONE', 'CANCELLED', 'WAIT_CONTINUE'],
    'cancellationType': ['PASSENGER', 'DRIVER', 'ADMIN'],
    'recordType': ['ORDER', 'WITHDRAWAL'], 'refundStatus': ['NONE', 'HAS_REFUND'],
    'refundSource': ['HISTORY', 'CURRENT'],
    'paymentStatus': ['UNPAID', 'SUCCESS', 'FAILED', 'PROCESSING', 'CANCELLED', 'REFUNDED'],
    'withdrawalStatus': ['PENDING', 'SANDBOX_APPROVED', 'REJECTED'],
    'certificateStatus': ['APPROVED', 'PENDING', 'REJECTED'],
    'currentLevel': ['TRIAL', 'NORMAL', 'VIP'],
    'status': ['PENDING', 'REPLIED', 'RECORDED'],
    'source': ['ORDER_COMPLAINT', 'EVALUATION', 'PASSENGER_FEEDBACK',
               'DRIVER_FEEDBACK', 'DRIVER_COMPLAINT'],
}
ENUMS = {'kind', 'stage', 'cancellationType', 'recordType', 'refundStatus',
         'refundSource', 'source', 'paymentStatus', 'withdrawalStatus',
         'status', 'certificateStatus', 'currentLevel', 'fareSource', 'vehicleType'}
LABELS = {
    'kind': {'STANDARD': '即时用车', 'RESERVED': '预约用车', 'BID': '司机报价', 'NEGOTIATED': '议价用车'},
    'stage': {'AVAILABLE': '待接单', 'PREPAY': '待预付（模拟）', 'RESERVED': '预约待接驾',
              'PICKUP': '接驾中', 'ARRIVED': '司机已到达', 'TRIP': '行程中', 'WAIT_PAY': '待支付（模拟）',
              'DONE': '已完成', 'CANCELLED': '已取消', 'WAIT_CONTINUE': '司机取消·待乘客续叫'},
    'cancellationType': {'PASSENGER': '乘客取消', 'DRIVER': '司机取消', 'ADMIN': '后台取消'},
    'recordType': {'ORDER': '订单支付状态', 'WITHDRAWAL': '模拟提现申请'},
    'refundStatus': {'NONE': '无退款', 'HAS_REFUND': '存在模拟退款'},
    'refundSource': {'HISTORY': '续叫前历史轮次', 'CURRENT': '当前轮次'},
    'source': {'ORDER_COMPLAINT': '订单投诉', 'EVALUATION': '订单评价',
               'PASSENGER_FEEDBACK': '乘客反馈', 'DRIVER_FEEDBACK': '司机反馈', 'DRIVER_COMPLAINT': '司机投诉'},
    'paymentStatus': {'UNPAID': '未支付', 'SUCCESS': '模拟支付成功', 'FAILED': '模拟支付失败',
                      'PROCESSING': '模拟支付处理中', 'CANCELLED': '支付已取消', 'REFUNDED': '已模拟退款'},
    'withdrawalStatus': {'PENDING': '待审核', 'SANDBOX_APPROVED': '模拟审核通过·未银行打款', 'REJECTED': '已驳回'},
    'certificateStatus': {'APPROVED': '资质通过', 'PENDING': '资质待审核', 'REJECTED': '资质驳回'},
    'currentLevel': {'TRIAL': '体验权益', 'NORMAL': '普通权益', 'VIP': 'VIP权益'},
    'status': {'PENDING': '待处理', 'REPLIED': '已回复', 'RECORDED': '已记录'},
    'city': {c['id']: c['name'] for c in CITIES},
}
EXACT = ENUMS | {'driverId', 'passengerId', 'accountId', 'city'}
DATE_FIELDS = {'orders': 'createdAt', 'drivers': None, 'passengers': None,
               'payments': 'createdAt', 'support': 'createdAt', 'audit': 'created',
               'statistics': 'createdAt', 'cancellations': 'cancelledAt',
               'refunds': 'refundedAt'}
SEARCH_FIELDS = {
    'orders': ['id', 'origin', 'destination', 'passengerId', 'driverId', 'passengerName',
               'passengerMobile', 'driverName', 'driverMobile', 'plate', 'city', 'remark'],
    'drivers': ['id', 'name', 'mobile', 'city', 'plate', 'vehicleType'],
    'passengers': ['id', 'name', 'mobile', 'city'],
    'payments': ['id', 'orderId', 'accountId', 'driverId', 'paymentStatus', 'status'],
    'support': ['id', 'rowId', 'orderId', 'accountId', 'content', 'reason', 'reply'],
    'audit': ['id', 'actor', 'action'],
    'refunds': ['id', 'orderId', 'accountId', 'driverId'],
}
ORDER_DEFINITION = ('共享passenger_orders权威记录；排除司机私有演示订单。金额为整数分。'
    'paidFare仅为DONE且仍paid订单的fare，不是历史收款或清分收入；'
    'refundAmount为历史每轮退款与当前退款之和，等额同刻也不去重。')
LIMITS = ('所有支付、退款、提现为模拟；未接真实收单、银行打款或公司清分。'
          '不提供电话叫车、后台指派、超时/有责取消的虚构对应。')


def timestamp(value):
    """Legacy missing/bad dates remain unknown instead of becoming today's date."""
    if not value:
        return None
    try:
        return parse_time(value).timestamp()
    except (ApiError, ValueError, OverflowError, OSError):
        return None


def scalar_params(params):
    require(isinstance(params, dict), '查询参数格式错误')
    out = {}
    for key, value in params.items():
        if isinstance(value, list):
            require(len(value) == 1, '查询参数不可重复：' + str(key))
            value = value[0]
        require(isinstance(key, str) and isinstance(value, str), '查询参数须为字符串')
        require(len(value) <= 200, '查询参数过长：' + key)
        out[key] = value.strip()
    return out


def date_boundary(value, end=False):
    require(re.fullmatch(r'\d{4}-\d{2}-\d{2}', value) is not None, '日期须为YYYY-MM-DD')
    try:
        day = dt.date.fromisoformat(value)
        if end:
            day += dt.timedelta(days=1)
        return dt.datetime.combine(day, dt.time(), TZ).timestamp()
    except (ValueError, OverflowError):
        raise ApiError(400, '日期无效或超出范围')


def refund_rows(orders):
    rows = []
    for o in orders:
        history = o.get('refundHistory') or []
        for index, record in enumerate(history):
            rows.append({'id': o['id'] + ':HISTORY:' + str(index), 'orderId': o['id'],
                         'accountId': o.get('passengerId'), 'driverId': record.get('driverId'),
                         'amount': record.get('amount', 0), 'refundedAt': record.get('at'),
                         'refundSource': 'HISTORY', 'roundIndex': index,
                         'paymentMode': record.get('mode', 'SIMULATED')})
        if o.get('refundAmount'):
            rows.append({'id': o['id'] + ':CURRENT', 'orderId': o['id'],
                         'accountId': o.get('passengerId'), 'driverId': o.get('driverId'),
                         'amount': o['refundAmount'], 'refundedAt': o.get('refundedAt'),
                         'refundSource': 'CURRENT', 'roundIndex': len(history),
                         'paymentMode': o.get('refundMode', 'SIMULATED')})
    return rows


def order_summary(rows):
    return {'orderCount': len(rows), 'completedCount': sum(o.get('stage') == 'DONE' for o in rows),
            'cancelledCount': sum(o.get('stage') == 'CANCELLED' for o in rows),
            'activeCount': sum(o.get('stage') not in ('DONE', 'CANCELLED') for o in rows),
            'paidFare': sum(o.get('fare', 0) for o in rows if o.get('stage') == 'DONE' and o.get('paid')),
            'refundAmount': sum(r['amount'] for r in refund_rows(rows))}


class AdminQueries:
    def __init__(self, console, user):
        self.console, self.svc, self.db, self.user = console, console.svc, console.db, user

    def permit(self, permission):
        self.console.permit(self.user, permission)
        require(permission in self.user.get('permissions', []), '当前账号没有此查询权限', 403)

    def orders(self):
        orders = self.console.orders()
        passengers = {r['id']: dict(r) for r in self.db.execute('SELECT id,mobile,profile FROM passengers')}
        drivers = {r['id']: dict(r) for r in self.db.execute('SELECT id,mobile,profile FROM accounts')}
        for o in orders:
            p, d = passengers.get(o.get('passengerId')), drivers.get(o.get('driverId'))
            pp = json.loads(p['profile']) if p else {}
            dp = json.loads(d['profile']).get('driver', {}) if d else {}
            if 'passengers.read' in self.user['permissions']:
                o.update(passengerMobile=p['mobile'] if p else None, passengerName=pp.get('name'))
            if 'drivers.read' in self.user['permissions']:
                o.update(driverMobile=d['mobile'] if d else None, driverName=dp.get('name'),
                         plate=dp.get('plate'), vehicleType=dp.get('vehicleType'))
            events = [e for e in (o.get('events') or []) if e.get('type') in
                      ('PASSENGER_CANCEL', 'DRIVER_CANCEL', 'ADMIN_CANCEL') and timestamp(e.get('at')) is not None]
            o.update(orderId=o['id'], cancellationType=o.get('cancelledBy'),
                     cancelledAt=max(events, key=lambda e: timestamp(e['at']))['at'] if events else None)
        return orders

    def rows(self, dataset):
        if dataset in ('orders', 'statistics', 'cancellations', 'refunds', 'payments'):
            orders = self.orders()
            if dataset in ('orders', 'statistics'):
                return orders
            if dataset == 'cancellations':
                return [{**o, 'cancellationStatus': 'CLOSED' if o['stage'] == 'CANCELLED' else 'AWAITING_PASSENGER'}
                        for o in orders if o.get('stage') in ('CANCELLED', 'WAIT_CONTINUE')]
            if dataset == 'refunds':
                return refund_rows(orders)
            rows = []
            for o in orders:
                refunds = refund_rows([o])
                history = o.get('refundHistory') or []
                rows.append({**{k: o.get(k) for k in ('passengerId', 'driverId', 'fare', 'paid', 'paidAt', 'refundedAt', 'paymentStatus', 'paymentMode')},
                             'id': o['id'], 'orderId': o['id'], 'recordType': 'ORDER',
                             'accountId': o.get('passengerId'), 'createdAt': o.get('paidAt') or o.get('createdAt'),
                             'amount': o.get('fare', 0), 'status': o.get('paymentStatus'),
                             'refundAmount': sum(r['amount'] for r in refunds),
                             'currentRefundAmount': o.get('refundAmount', 0),
                             'historicalRefundAmount': sum(r.get('amount', 0) for r in history),
                             'refundRecords': [{**r, 'at': r['refundedAt'], 'source': r['refundSource'], 'mode': r['paymentMode']} for r in refunds], 'refundHistory': history,
                             'refundStatus': 'HAS_REFUND' if refunds else 'NONE'})
            for r in self.db.execute('SELECT id,profile FROM accounts ORDER BY id'):
                for w in json.loads(r['profile']).get('wallet', {}).get('withdrawals', []):
                    rows.append({**clone(w), 'id': 'WITHDRAWAL:' + r['id'] + ':' + w['id'],
                                 'withdrawalId': w['id'], 'recordType': 'WITHDRAWAL', 'accountId': r['id'],
                                 'driverId': r['id'], 'withdrawalStatus': w.get('status'),
                                 'paymentMode': 'SIMULATED'})
            return rows
        if dataset == 'audit':
            return [dict(r) for r in self.db.execute('SELECT * FROM audits ORDER BY id')]
        if dataset == 'passengers':
            return [{**json.loads(r['profile']), 'id': r['id'], 'mobile': r['mobile'], 'passengerId': r['id']}
                    for r in self.db.execute('SELECT id,mobile,profile FROM passengers ORDER BY id')]
        state = self.console.state(self.user)
        if dataset == 'drivers':
            return [{**d, 'driverId': d['id'], 'plate': d['profile'].get('plate'),
                     'vehicleType': d['profile'].get('vehicleType'),
                     'certificateStatus': d['profile'].get('certificationStatus')}
                    for d in state['drivers']]
        # Every existing support source retains its real persistent record ID.
        rows = []
        order_owners = {o['id']: o.get('passengerId') for o in self.console.orders()}
        for index, r in enumerate(state['support']):
            account = order_owners.get(r.get('orderId')) if r['source'] == 'ORDER_COMPLAINT' else r.get('ownerId')
            rows.append({**r, 'accountId': account,
                         'rowId': r['source'] + ':' + str(r.get('ownerId')) + ':' + str(r.get('id', index))})
        return rows

    def schema(self, dataset, rows):
        fields = {'search': '搜索', 'startDate': '开始日期', 'endDate': '结束日期', **FILTERS[dataset]}
        schema, options = [], {}
        for key, label in fields.items():
            reason = UNSUPPORTED.get(key)
            if key in ('startDate', 'endDate') and DATE_FIELDS[dataset] is None:
                reason = '旧账号表没有持久登记时间，无法提供注册日期筛选。'
            permission = 'passengers.read' if key in ('passengerMobile',) else 'drivers.read' if key in ('driverMobile', 'driverName', 'plate', 'vehicleType') and dataset in ('orders', 'statistics', 'cancellations') else None
            if permission and permission not in self.user['permissions']:
                reason = '缺少关联账号读取权限：' + permission
            schema.append({'name': key, 'label': label,
                           'type': 'date' if key in ('startDate', 'endDate') else 'select' if key in ENUMS or key == 'city' else 'text',
                           'availability': 'NOT_IMPLEMENTED' if reason else 'AVAILABLE',
                           **({'reason': reason} if reason else {})})
            if key in ENUMS or key == 'city':
                actual = {str(r[key]) for r in rows if r.get(key) is not None and isinstance(r[key], (str, int)) and str(r[key])}
                values = set(FIXED.get(key, [])) | actual
                options[key] = [{'value': v, 'label': LABELS.get(key, {}).get(v, v)} for v in sorted(values)] if not reason else []
        return schema, options

    def select(self, params, paginate=True):
        params = scalar_params(params)
        dataset = params.get('dataset') or 'orders'
        require(dataset in DATASETS, '不支持的查询数据集')
        self.permit(DATASETS[dataset])
        allowed = {'dataset', 'search', 'startDate', 'endDate', 'page', 'pageSize'} | set(FILTERS[dataset])
        require(not (set(params) - allowed), '不支持的查询参数：' + ','.join(sorted(set(params) - allowed)))
        def integer(key, default, maximum):
            raw = params.get(key) or str(default)
            require(re.fullmatch(r'[1-9]\d{0,6}', raw) is not None and int(raw) <= maximum, key + '超出允许范围')
            return int(raw)
        page, size = integer('page', 1, 1000000), integer('pageSize', 20, 100)
        require(len(params.get('search', '')) <= 100, '搜索最多100字')
        for key in ('passengerMobile', 'driverMobile', 'driverName', 'plate', 'vehicleType'):
            if params.get(key) and dataset in ('orders', 'statistics', 'cancellations'):
                self.permit('passengers.read' if key == 'passengerMobile' else 'drivers.read')
        for key in UNSUPPORTED:
            require(not params.get(key), UNSUPPORTED[key], details={'field': key, 'availability': 'NOT_IMPLEMENTED'})
        if DATE_FIELDS[dataset] is None:
            require(not (params.get('startDate') or params.get('endDate')), '该账号表无持久登记时间，日期筛选未实现', details={'availability': 'NOT_IMPLEMENTED'})
        start = date_boundary(params['startDate']) if params.get('startDate') else None
        end = date_boundary(params['endDate'], True) if params.get('endDate') else None
        require(start is None or end is None or start < end, '开始日期不得晚于结束日期')
        rows = self.rows(dataset)
        schema, options = self.schema(dataset, rows)
        for key in ENUMS & set(FILTERS[dataset]):
            if params.get(key):
                require(params[key] in {o['value'] for o in options[key]}, '筛选值无效：' + key)
        fields = SEARCH_FIELDS['orders' if dataset in ('statistics', 'cancellations') else dataset]
        selected, missing_date = [], 0
        date_field = DATE_FIELDS[dataset]
        for row in rows:
            if params.get('search') and not any(params['search'].casefold() in str(row.get(k) or '').casefold() for k in fields):
                continue
            matches = True
            for key in FILTERS[dataset]:
                value = params.get(key)
                if not value:
                    continue
                actual = row.get(key)
                if actual is None or (str(actual) != value if key in EXACT else value.casefold() not in str(actual).casefold()):
                    matches = False
                    break
            if not matches:
                continue
            when = timestamp(row.get(date_field)) if date_field else None
            if date_field and when is None:
                missing_date += 1
            if (start is not None or end is not None) and (when is None or (start is not None and when < start) or (end is not None and when >= end)):
                continue
            selected.append(row)
        if dataset in ('orders', 'cancellations', 'statistics'):
            summary = order_summary(selected)
        elif dataset == 'refunds':
            summary = {'refundCount': len(selected), 'refundAmount': sum(r.get('amount', 0) for r in selected)}
        elif dataset == 'payments':
            order = [r for r in selected if r['recordType'] == 'ORDER']
            withdrawals = [r for r in selected if r['recordType'] == 'WITHDRAWAL']
            summary = {'orderCount': len(order), 'withdrawalCount': len(withdrawals),
                       'orderFare': sum(r.get('fare', 0) for r in order),
                       'refundAmount': sum(r.get('refundAmount', 0) for r in order),
                       'withdrawalAmount': sum(r.get('amount', 0) for r in withdrawals)}
        else:
            summary = {}
        if dataset == 'statistics':
            groups = defaultdict(list)
            for o in selected:
                when = timestamp(o.get('createdAt'))
                day = dt.datetime.fromtimestamp(when, TZ).date().isoformat() if when is not None else 'UNKNOWN'
                groups[day].append(o)
            selected = [{'id': key, 'date': key, **order_summary(group)} for key, group in groups.items()]
            selected.sort(key=lambda r: r['date'], reverse=True)
            selected.sort(key=lambda r: r['date'] == 'UNKNOWN')
        else:
            selected.sort(key=lambda r: str(r.get('rowId', r.get('id', ''))))
            if date_field:
                selected.sort(key=lambda r: timestamp(r.get(date_field)) if timestamp(r.get(date_field)) is not None else float('-inf'), reverse=True)
        total = len(selected)
        summary.update(rowCount=total, missingDateCount=missing_date)
        definition = {'orders': ORDER_DEFINITION,
                      'statistics': ORDER_DEFINITION + '按UTC+8订单创建日归组；日期条件筛选订单创建日，并非退款发生日。',
                      'cancellations': ORDER_DEFINITION + '仅当前CANCELLED/WAIT_CONTINUE；后者待乘客续叫，不是已终结。最后取消事件时间缺失保持未知，不展示为历史全部取消次数。',
                      'refunds': '每笔共享订单的refundHistory各轮及当前refundAmount分别一行；不按金额/时间去重，按退款发生时间筛选。',
                      'payments': '订单支付状态一订单一行，时间为paidAt或createdAt；另包含持久司机提现记录。订单金额、退款、提现分开汇总，不作为历史收款或公司清分。',
                      'drivers': '实际accounts账号及持久profile，不使用城市预置司机数量；无登记日期。',
                      'passengers': '实际passengers账号及持久profile，不从掩码还原手机号；无登记日期。',
                      'audit': '全部持久audits记录，查询不受旧state最近200条限制。',
                      'support': '持久共享订单评价/投诉及乘客、司机反馈/投诉；保留来源和原记录ID。'}[dataset]
        return {'dataset': dataset, 'items': selected[(page-1)*size:page*size] if paginate else selected,
                'total': total, 'page': page, 'pageSize': size, 'schema': schema, 'options': options,
                'summary': summary, 'definition': definition + LIMITS,
                'dateField': date_field, 'timezone': 'UTC+08:00',
                'dateRange': {'startInclusive': stamp(dt.datetime.fromtimestamp(start, TZ)) if start is not None else None,
                              'endExclusive': stamp(dt.datetime.fromtimestamp(end, TZ)) if end is not None else None}}
