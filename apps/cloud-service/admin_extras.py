"""修改意见3.0 read-only admin projections: order progress and fee breakdown,
invoice records, driver wallet details and complaint attachments.

Derived from persisted records only. Amounts are integer cents of the simulated
ledger; no acquirer, bank or tax platform is contacted.
"""
from common import *

CHAIN = ['RESERVED', 'PICKUP', 'ARRIVED', 'TRIP', 'WAIT_PAY']
STEP_LABELS = [('created', '乘客下单'), ('paid', '乘客支付'), ('accepted', '司机接单'),
               ('arrived', '到达上车点'), ('boarded', '接到乘客'), ('delivered', '送达乘客')]
ACCEPT_EVENTS = {'COMPETITION_ASSIGNED', 'DRIVER_ACCEPT', 'PASSENGER_SELECTDRIVER', 'PASSENGER_NEGOTIATION_CLAIMED'}
RESET_EVENTS = {'COMPETITION_REOPENED', 'PASSENGER_CONTINUE'}


def order_timeline(svc, o):
    """下单-支付-接单-到达上车点-接到乘客-送达乘客 for the current service round."""
    times = {'created': o.get('createdAt')}
    inferred = False
    stage = None
    cancelled_at = None
    for e in o.get('events') or []:
        kind, at = e.get('type'), e.get('at')
        if kind in RESET_EVENTS:
            for key in ('accepted', 'arrived', 'boarded', 'delivered'):
                times.pop(key, None)
            stage = None
            continue
        if 'stage' in e:
            new = e['stage']
        elif kind in ACCEPT_EVENTS:
            new, inferred = ('RESERVED' if o.get('kind') == 'RESERVED' else 'PICKUP'), True
        elif kind == 'DRIVER_ADVANCE' and stage in CHAIN[:-1]:
            new, inferred = CHAIN[CHAIN.index(stage) + 1], True
        else:
            new = stage
        if kind and kind.endswith('_CANCEL'):
            cancelled_at = at
        if new != stage or kind in ACCEPT_EVENTS:
            if new in ('RESERVED', 'PICKUP', 'PREPAY') and o.get('driverId'):
                times.setdefault('accepted', at)
            elif new == 'ARRIVED':
                times.setdefault('arrived', at)
            elif new == 'TRIP':
                times.setdefault('boarded', at)
            elif new in ('WAIT_PAY', 'DONE') and stage == 'TRIP':
                times.setdefault('delivered', at)
        stage = new
    if o.get('paidAt'):
        times['paid'] = o['paidAt']
    if o.get('stage') == 'DONE' and 'delivered' not in times and o.get('completedAt'):
        times['delivered'] = o['completedAt']; inferred = True
    pickup_meters = None
    row = svc.db.execute("SELECT distance FROM order_contenders WHERE order_id=? AND driver=? AND outcome='WON' ORDER BY round DESC LIMIT 1",
                         (o['id'], o.get('driverId'))).fetchone() if o.get('driverId') else None
    if row:
        pickup_meters = row['distance']
    try:
        trip_km = float(o.get('mileage'))
    except (TypeError, ValueError):
        trip_km = None
    mileage = {'created': f'预估行程 {trip_km:.1f} 公里' if trip_km is not None else None,
               'accepted': f'接单时距上车点直线 {pickup_meters / 1000:.2f} 公里' if pickup_meters is not None else None,
               'delivered': f'计费里程（预估） {trip_km:.1f} 公里' if trip_km is not None else None}
    steps = [{'key': key, 'label': label, 'at': times.get(key), 'done': bool(times.get(key)),
              'mileage': mileage.get(key)} for key, label in STEP_LABELS]
    known = [s for s in steps if s['at']]
    return {'steps': steps, 'completedSteps': len(known), 'inferred': inferred,
            'cancelled': {'at': cancelled_at, 'by': o.get('cancelledBy'), 'reason': o.get('cancelReason')}
                         if o.get('stage') in ('CANCELLED', 'WAIT_CONTINUE') else None,
            'notice': '里程为下单预估与接单定位直线距离；未接入计价器或道路轨迹，分段实际行驶里程暂不可得。'
                      + ('部分时间由历史事件顺序推断。' if inferred else '')}


def fee_breakdown(o):
    pending = bool(o.get('destinationPending'))
    fare = o.get('fare') if type(o.get('fare')) is int else 0
    history = sum(r.get('amount', 0) for r in o.get('refundHistory') or [])
    refund = (o.get('refundAmount') or 0) + history
    lines = [{'key': 'estimatedFare', 'label': '下单预估运价', 'amount': o.get('estimatedFare')},
             {'key': 'fare', 'label': '运价费（当前订单费用）', 'amount': None if pending else fare},
             {'key': 'serviceFee', 'label': '区域服务费', 'amount': 0, 'note': '未配置'},
             {'key': 'coupon', 'label': '优惠券抵扣', 'amount': 0, 'note': '未使用'},
             {'key': 'payable', 'label': '乘客应付', 'amount': None if pending else fare},
             {'key': 'paid', 'label': '乘客实付（模拟）', 'amount': fare if o.get('paid') else 0},
             {'key': 'refund', 'label': '累计退款（模拟）', 'amount': refund},
             {'key': 'driverIncome', 'label': '司机入账（模拟）', 'amount': fare if o.get('credited') and not o.get('refundAmount') else 0}]
    return {'lines': lines, 'pending': pending, 'fareSource': o.get('fareSource'), 'paymentStatus': o.get('paymentStatus'),
            'adjustments': clone(o.get('adjustments') or []),
            'notice': '费用为体验估价/选定报价；服务费、优惠券、手续费与分账尚未接入，按0列示，不代表真实清分。'}


def invoice_rows(svc):
    rows = []
    svc.db.execute('CREATE TABLE IF NOT EXISTS passenger_extras(pid TEXT PRIMARY KEY,payload TEXT NOT NULL)')
    passengers = {r['id']: r for r in svc.db.execute('SELECT id,mobile,profile FROM passengers')}
    companies = {c['id']: c['name'] for c in svc.operations.rows('company')}
    order_company = {o['id']: o.get('companyId') for o in (json.loads(r['payload']) for r in svc.db.execute('SELECT payload FROM passenger_orders'))}
    for r in svc.db.execute('SELECT pid,payload FROM passenger_extras ORDER BY pid'):
        owner = passengers.get(r['pid'])
        for inv in json.loads(r['payload']).get('invoices', []):
            snap = inv.get('titleSnapshot') or {}
            rows.append({'id': inv['id'], 'role': 'PASSENGER', 'orderId': inv.get('orderId'), 'accountId': r['pid'],
                         'accountName': json.loads(owner['profile']).get('name') if owner else None,
                         'mobile': owner['mobile'] if owner else None, 'title': inv.get('title'),
                         'titleType': snap.get('type'), 'taxNumber': snap.get('taxNumber') or '', 'email': snap.get('email') or '',
                         'amount': inv.get('amount', 0), 'invoiceStatus': inv.get('status'), 'createdAt': inv.get('createdAt'),
                         'invoiceKind': '行程车费', 'source': '乘客端', 'issuedAt': inv.get('createdAt'),
                         'companyName': companies.get(order_company.get(inv.get('orderId'))), 'simulated': True})
    drivers = {r['id']: r for r in svc.db.execute('SELECT id,mobile,profile FROM accounts')}
    for r in svc.db.execute('SELECT driver,payload FROM invoices ORDER BY created'):
        inv = json.loads(r['payload']); owner = drivers.get(r['driver'])
        rows.append({'id': inv['id'], 'role': 'DRIVER', 'orderId': inv.get('orderId'), 'accountId': r['driver'],
                     'accountName': json.loads(owner['profile'])['driver'].get('name') if owner else None,
                     'mobile': owner['mobile'] if owner else None, 'title': inv.get('title'), 'titleType': inv.get('type'),
                     'taxNumber': inv.get('taxNumber') or '', 'email': '', 'amount': inv.get('amount', 0),
                     'invoiceStatus': inv.get('status'), 'createdAt': inv.get('createdAt'),
                     'invoiceKind': '司机会员费', 'source': '司机端', 'issuedAt': inv.get('createdAt'), 'companyName': None, 'simulated': True})
    return rows


def wallet_rows(svc, orders):
    pending = {}
    for o in orders:
        d = o.get('driverId')
        if d and not o.get('credited') and o.get('stage') in ('WAIT_PAY', 'DONE') and type(o.get('fare')) is int and not o.get('refundAmount'):
            pending[d] = pending.get(d, 0) + o['fare']
    rows = []
    for r in svc.db.execute('SELECT id,mobile,state,profile FROM accounts ORDER BY id'):
        st, profile = json.loads(r['state']), json.loads(r['profile'])
        withdrawals = profile.get('wallet', {}).get('withdrawals', [])
        times = [w.get('createdAt') for w in withdrawals if w.get('createdAt')]
        rows.append({'id': r['id'], 'driverId': r['id'], 'accountNo': None, 'accountNoLabel': '未开户（扫呗分账未接入）',
                     'name': profile['driver'].get('name'), 'mobile': r['mobile'], 'city': profile['driver'].get('city'),
                     'balance': st.get('balance', 0), 'pendingAmount': pending.get(r['id'], 0),
                     'withdrawnAmount': sum(w.get('amount', 0) for w in withdrawals if w.get('status') == 'SANDBOX_APPROVED'),
                     'withdrawingAmount': sum(w.get('amount', 0) for w in withdrawals if w.get('status') == 'PENDING'),
                     'lastWithdrawalAt': max(times) if times else None,
                     'bankCardCount': len(profile.get('bankCards', []))})
    return rows


def daily_balance_rows(svc):
    """End-of-day balance per driver, walked back from the current balance through
    wallet entries; every balance write appends an entry. Undated seed entries are
    older than any dated one and only affect days the table does not list."""
    rows = []
    today = now().date()
    for r in svc.db.execute('SELECT id,mobile,state,profile FROM accounts ORDER BY id'):
        st, driver = json.loads(r['state']), json.loads(r['profile'])['driver']
        days = {today: [0, 0]}
        for e in st.get('entries', []):
            try:
                day = parse_time(e.get('createdAt')).astimezone(TZ).date()
            except ApiError:
                continue
            amount = e.get('amount') if type(e.get('amount')) is int else 0
            bucket = days.setdefault(day, [0, 0])
            bucket[0 if amount >= 0 else 1] += amount
        closing = st.get('balance', 0)
        for day in sorted(days, reverse=True):
            income, expense = days[day]
            rows.append({'id': r['id'] + ':' + day.isoformat(), 'driverId': r['id'], 'name': driver.get('name'),
                         'mobile': r['mobile'], 'date': day.isoformat(), 'dateAt': day.isoformat() + 'T23:59:59+08:00',
                         'income': income, 'expense': expense, 'closingBalance': closing})
            closing -= income + expense
    return rows


def support_file(svc, source, owner, file_id):
    require(isinstance(file_id, str) and re.fullmatch(r'[A-Za-z0-9_.:-]{1,120}', file_id) is not None, '附件编号无效')
    if source == 'ORDER_COMPLAINT':
        svc._complaint_file_table()
        row = svc.db.execute('SELECT * FROM passenger_complaint_files WHERE id=?', (file_id,)).fetchone()
        require(row is not None and row['order_id'] == owner, '投诉附件不存在或归属不匹配', 404)
        mime, raw = row['mime'], row['content']
    elif source in ('DRIVER_COMPLAINT', 'DRIVER_FEEDBACK'):
        profile = json.loads(svc.account(owner)['profile'])
        records = profile.get('complaints' if source == 'DRIVER_COMPLAINT' else 'feedback', [])
        require(any(file_id in (r.get('attachmentFileIds') or []) for r in records), '附件不存在或归属不匹配', 404)
        row = svc.db.execute('SELECT mime,content FROM files WHERE id=? AND driver=?', (file_id, owner)).fetchone()
        require(row is not None, '附件不存在或归属不匹配', 404)
        mime, raw = row['mime'], row['content']
    else:
        raise ApiError(400, '此反馈类型没有图片附件')
    require(mime in ('image/png', 'image/jpeg'), '不支持此附件格式', 415)
    return {'fileId': file_id, 'mimeType': mime, 'imageBase64': base64.b64encode(raw).decode()}
