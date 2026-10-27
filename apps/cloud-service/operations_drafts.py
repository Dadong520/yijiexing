"""Admin-only draft configuration and explicitly manual notes.

Uses the caller's existing SQLite transaction and snapshot CAS. These tables are
not read by pricing, wallet, points, dispatch, invoice or score execution paths.
"""
from common import *

POLICY_LABEL = '仅保存未生效配置草稿及人工记录；不执行费率、分账、处罚、积分、优惠券或开票，不代表真实到账。'
OPS_ACTIONS = frozenset({'ops.draft.save','ops.draft.archive','ops.record.add','ops.record.archive'})
CATALOG = [{'id': 'announcements',
  'category': '平台运维',
  'name': '小程序公告',
  'notice': '拟定公告内容和展示范围，发布与审核安排待确认。此处仅保存草稿和人工记录，不向乘客发布。'},
 {'id': 'company_support',
  'category': '平台运维',
  'name': '公司客服电话',
  'notice': '登记公司客服号码与服务时间，号码核实和对外启用待确认。此处仅保存草稿和人工记录，不提供实时客服。'},
 {'id': 'passenger_features',
  'category': '平台运维',
  'name': '乘客端功能配置',
  'notice': '整理乘客功能开通需求，开关条件和审批规则待确认。此处仅保存草稿和人工记录，不改变现有功能。'},
 {'id': 'heatmap_thresholds',
  'category': '平台运维',
  'name': '热力图阈值',
  'notice': '记录热力显示范围和阈值建议，订单统计口径待确认。此处仅保存草稿和人工记录，不生成实时热力数据。'},
 {'id': 'driver_wallet_entries',
  'category': '司机管理',
  'name': '钱包明细',
  'notice': '登记钱包核对事项和处理说明。此处仅保存草稿和人工记录，不生成交易流水或修改司机余额。'},
 {'id': 'driver_daily_balances',
  'category': '司机管理',
  'name': '每日余额统计',
  'notice': '明确每日余额的统计时点和核对口径。此处仅保存草稿和人工记录，不生成每日余额报表。'},
 {'id': 'qualification_expiry',
  'category': '司机管理',
  'name': '资质到期提醒',
  'notice': '现有资质有效期检查仍保留，可拟定提醒时间和处理安排。此处仅保存草稿和人工记录，不自动发送提醒。'},
 {'id': 'taxi_vehicles',
  'category': '车辆管理',
  'name': '出租车车辆独立管理',
  'notice': '现有司机车辆资料仍保留，可登记车辆新增、变更和停用的管理需求。此处仅保存草稿和人工记录，不直接变更车辆状态。'},
 {'id': 'order_splits',
  'category': '订单管理',
  'name': '订单分账记录',
  'notice': '登记分账核对事项，分配比例、参与方和结算时点待确认。此处仅保存草稿和人工记录，不代表实际分账或到账。'},
 {'id': 'regional_fares',
  'category': '结算管理',
  'name': '区域运价',
  'notice': '拟定不同区域的运价方案，计费规则和适用条件待确认。此处仅保存草稿和人工记录，不改变现有订单费用。'},
 {'id': 'company_split_accounts',
  'category': '结算管理',
  'name': '公司分账账户',
  'notice': '整理公司主体和分账账户的管理要求，账户核实与资金服务待接入。此处仅保存草稿和人工记录，不开立或启用资金账户。'},
 {'id': 'company_split_entries',
  'category': '结算管理',
  'name': '公司分账明细',
  'notice': '登记公司分账的核对说明，结算规则和资金服务待确认。此处仅保存草稿和人工记录，不作为到账凭证。'},
 {'id': 'regional_service_fees',
  'category': '结算管理',
  'name': '区域服务费',
  'notice': '拟定各区域服务费方案，收费对象、比例和时点待确认。此处仅保存草稿和人工记录，不新增或扣收服务费。'},
 {'id': 'driver_bid_rules',
  'category': '结算管理',
  'name': '司机竞价规则',
  'notice': '现有竞价流程仍保留，可拟定报价范围和有效期方案。此处仅保存草稿和人工记录，不改变现有竞价规则。'},
 {'id': 'invoice_records',
  'category': '开票管理',
  'name': '开票记录',
  'notice': '现有模拟发票申请仍保留，可登记开票核对与处理说明。此处仅保存草稿和人工记录，不开具正式发票。'},
 {'id': 'service_fee_invoices',
  'category': '开票管理',
  'name': '技术服务费开票',
  'notice': '整理服务费开票的主体、抬头和税率要求，开票服务待接入。此处仅保存草稿和人工记录，不产生正式凭证。'},
 {'id': 'invoice_reissues',
  'category': '开票管理',
  'name': '重开申请',
  'notice': '登记发票重开需求，原票处理条件和审批流程待确认。此处仅保存草稿和人工记录，不执行作废、红冲或重开。'},
 {'id': 'service_score_rules',
  'category': '司机服务分',
  'name': '规则配置',
  'notice': '拟定服务分计分、扣分和申诉方案，规则待确认。此处仅保存草稿和人工记录，不自动调整司机服务分。'},
 {'id': 'service_score_entries',
  'category': '司机服务分',
  'name': '明细记录',
  'notice': '登记服务问题的核对说明和处理意见。此处仅保存草稿和人工记录，不生成实际加扣分记录。'},
 {'id': 'service_score_statistics',
  'category': '司机服务分',
  'name': '司机统计',
  'notice': '明确服务分统计范围、周期和计算口径。此处仅保存草稿和人工记录，不生成真实司机评分统计。'},
 {'id': 'referral_rules_records',
  'category': '营销拉新',
  'name': '拉新规则与双角色记录',
  'notice': '拟定司机和乘客的拉新条件与奖励方案。此处仅保存草稿和人工记录，不建立实际邀请关系或发放奖励。'},
 {'id': 'role_points_rules',
  'category': '积分商城',
  'name': '双角色积分规则',
  'notice': '拟定司机和乘客的积分获取、使用与失效规则。此处仅保存草稿和人工记录，不改变现有积分余额。'},
 {'id': 'role_points_entries',
  'category': '积分商城',
  'name': '双角色积分明细统计',
  'notice': '登记积分核对事项，明确双方积分统计口径。此处仅保存草稿和人工记录，不生成实际积分流水或统计结果。'},
 {'id': 'role_points_products',
  'category': '积分商城',
  'name': '双角色商品管理',
  'notice': '整理积分商品、库存和兑换条件，履约安排待确认。此处仅保存草稿和人工记录，不上架商品或扣减库存。'},
 {'id': 'role_points_redemptions',
  'category': '积分商城',
  'name': '双角色兑换记录',
  'notice': '登记兑换咨询和处理说明，积分扣减与履约流程待确认。此处仅保存草稿和人工记录，不执行实际兑换或发货。'},
 {'id': 'points_common_rules',
  'category': '积分商城',
  'name': '通用积分规则',
  'notice': '拟定通用积分规则及其与双方积分规则的优先关系。此处仅保存草稿和人工记录，不计算或重复发放积分。'},
 {'id': 'coupon_pool',
  'category': '优惠券',
  'name': '优惠券资金池',
  'notice': '整理优惠券资金来源、核销和退回方案。此处仅保存草稿和人工记录，不建立真实资金池或改变订单抵扣。'},
 {'id': 'coupon_creation',
  'category': '优惠券',
  'name': '制作优惠券',
  'notice': '拟定优惠券适用范围、有效期和使用条件。此处仅保存草稿和人工记录，不发券、不核销、不改变订单金额。'},
 {'id': 'registration_campaigns',
  'category': '营销活动',
  'name': '新用户注册活动',
  'notice': '拟定新用户资格、奖励和活动有效期。此处仅保存草稿和人工记录，不自动发放奖励或优惠。'},
 {'id': 'phone_booking',
  'category': '叫车服务',
  'name': '电话叫车',
  'notice': '登记电话叫车的线路、坐席和派单需求，实际调度服务待接入。此处仅保存草稿和人工记录，不自动拨号或创建叫车订单。'},
 {'id': 'proxy_booking',
  'category': '叫车服务',
  'name': '代叫服务',
  'notice': '现有乘车人管理仍保留，可整理代叫联系、通知和授权要求。此处仅保存草稿和人工记录，不新增代叫订单或发送通知。'},
 {'id': 'map_places',
  'category': '地图',
  'name': '定位/地点搜索',
  'notice': '登记地点搜索、地图选点和定位问题的核对说明。此处仅保存草稿和人工记录，不改变地图服务或设备定位。'}]
MODULES = frozenset(item['id'] for item in CATALOG)


class OperationsDrafts:
    def __init__(self, svc):
        self.svc, self.db = svc, svc.db
        # execute, not executescript: never commit or replace the outer CAS transaction.
        for table in ('operation_drafts', 'operation_records'):
            self.db.execute(f'''CREATE TABLE IF NOT EXISTS {table}(
                id TEXT PRIMARY KEY,module TEXT NOT NULL,payload TEXT NOT NULL,
                version INTEGER NOT NULL,status TEXT NOT NULL,updated TEXT NOT NULL)''')

    def state(self):
        return {'catalog': clone(CATALOG),
                'drafts': self.rows('operation_drafts'),
                'records': self.rows('operation_records'),
                'policyLabel': POLICY_LABEL, 'draftOnly': True}

    def rows(self, table):
        return [json.loads(r['payload']) for r in self.db.execute(
            f'SELECT payload FROM {table} ORDER BY updated DESC,id DESC')]

    def fields(self, payload, allowed, required=()):
        require(isinstance(payload, dict) and set(required) <= set(payload)
                and not (set(payload) - set(allowed)), '字段缺失或含不支持的字段')

    def module(self, value):
        require(isinstance(value, str) and value in MODULES, '不支持的业务模块')
        return value

    def identifier(self, value):
        require(isinstance(value, str) and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,119}', value), '关联编号格式无效')
        return value

    def current(self, table, payload):
        oid = self.identifier(payload.get('id'))
        version = payload.get('expectedVersion')
        require(type(version) is int and version >= 1, '请提供当前正整数版本expectedVersion')
        row = self.db.execute(f'SELECT payload,version FROM {table} WHERE id=?', (oid,)).fetchone()
        require(row is not None, '草稿或人工记录不存在', 404)
        require(row['version'] == version, '版本已变化，请刷新后重新核对', 409)
        value = json.loads(row['payload'])
        require(value['status'] != 'ARCHIVED', '已归档内容不可再修改', 409)
        return value

    def write(self, table, value, expected_version=None):
        if expected_version is None:
            self.db.execute(f'''INSERT INTO {table}(id,module,payload,version,status,updated)
                VALUES (?,?,?,?,?,?)''', (value['id'], value['module'], dumps(value),
                value['version'], value['status'], value['updatedAt']))
        else:
            changed = self.db.execute(f'''UPDATE {table} SET module=?,payload=?,version=?,status=?,updated=?
                WHERE id=? AND version=? AND status!='ARCHIVED' ''',
                (value['module'], dumps(value), value['version'], value['status'], value['updatedAt'],
                 value['id'], expected_version)).rowcount
            require(changed == 1, '版本已变化，请刷新后重新核对', 409)

    def parameters(self, values):
        require(isinstance(values, list) and len(values) <= 40, '草稿参数须为列表且最多40项')
        result, names = [], set()
        for item in values:
            self.fields(item, {'name', 'value', 'unit'}, {'name', 'value'})
            name = textfield(item['name'], '参数名称', 1, 80)
            value = textfield(item['value'], '参数草稿文本', 0, 500)
            unit = textfield(item.get('unit', ''), '参数单位', 0, 40)
            require(name.casefold() not in names, '参数名称不能重复')
            names.add(name.casefold())
            result.append({'name': name, 'value': value, 'unit': unit})
        return result

    def draft_save(self, payload, actor):
        required = {'module', 'name', 'scope', 'target', 'description', 'parameters'}
        self.fields(payload, required | {'id', 'expectedVersion'}, required)
        module = self.module(payload['module'])
        name = textfield(payload['name'], '草稿名称', 1, 120)
        scope = payload['scope']
        require(isinstance(scope, str) and scope in {'NATIONAL', 'REGION', 'CITY', 'CUSTOM'}, '草稿范围无效')
        target = textfield(payload['target'], '草稿对象', 1, 120)
        if scope != 'CUSTOM':
            config_scope = {'NATIONAL': 'national', 'REGION': 'region', 'CITY': 'city'}[scope]
            require(self.db.execute('SELECT 1 FROM configs WHERE scope=? AND target=?', (config_scope, target)).fetchone() is not None,
                    '草稿范围对象不存在；待确认对象请使用CUSTOM')
        description = textfield(payload['description'], '草稿说明', 0, 2000)
        parameters = self.parameters(payload['parameters'])
        before = self.current('operation_drafts', payload) if 'id' in payload else None
        if before:
            require(before['module'] == module, '更新时不能变更草稿所属模块')
        else:
            require('expectedVersion' not in payload or
                    (type(payload['expectedVersion']) is int and payload['expectedVersion'] == 0), '新草稿版本应省略或为0')
        at = stamp()
        value = {**(before or {'id': uid('OPD'), 'createdAt': at, 'createdBy': actor}),
                 'module': module, 'name': name, 'scope': scope, 'target': target,
                 'description': description, 'parameters': parameters, 'status': 'DRAFT',
                 'version': before['version'] + 1 if before else 1,
                 'draftOnly': True, 'policyLabel': POLICY_LABEL,
                 'updatedAt': at, 'updatedBy': actor}
        self.write('operation_drafts', value, before['version'] if before else None)
        self.svc.audit(actor, 'ops.draft.save', before, value)
        return {'draft': value}

    def record_add(self, payload, actor):
        required = {'module', 'title', 'description'}
        links = {'orderId', 'driverId', 'passengerId'}
        self.fields(payload, required | links | {'occurredAt'}, required)
        module = self.module(payload['module'])
        title = textfield(payload['title'], '人工记录标题', 1, 120)
        description = textfield(payload['description'], '人工记录说明', 1, 2000)
        associations = {key: self.identifier(payload[key]) for key in links if key in payload}
        order = self.svc.shared_order(associations['orderId']) if 'orderId' in associations else None
        if 'orderId' in associations: require(order is not None, '关联共享订单不存在', 404)
        for key, table in [('driverId', 'accounts'), ('passengerId', 'passengers')]:
            if key in associations:
                require(self.db.execute(f'SELECT 1 FROM {table} WHERE id=?', (associations[key],)).fetchone() is not None,
                        '关联司机或乘客不存在', 404)
                if order is not None:
                    require(order.get(key) == associations[key], '关联人员与订单归属不一致')
        occurred = None
        if 'occurredAt' in payload:
            occurred = stamp(parse_time(textfield(payload['occurredAt'], '记录发生时间', 1, 50)))
        at = stamp()
        value = {'id': uid('OPR'), 'module': module, 'title': title, 'description': description,
                 **associations, 'occurredAt': occurred, 'source': 'MANUAL',
                 'status': 'RECORDED', 'version': 1, 'draftOnly': True,
                 'policyLabel': POLICY_LABEL, 'createdAt': at, 'createdBy': actor,
                 'updatedAt': at, 'updatedBy': actor}
        self.write('operation_records', value)
        self.svc.audit(actor, 'ops.record.add', None, value)
        return {'record': value}

    def archive(self, payload, actor, record=False):
        self.fields(payload, {'id', 'expectedVersion'}, {'id', 'expectedVersion'})
        table = 'operation_records' if record else 'operation_drafts'
        before = self.current(table, payload)
        value = {**before, 'status': 'ARCHIVED', 'version': before['version'] + 1,
                 'archivedAt': stamp(), 'archivedBy': actor, 'updatedAt': stamp(), 'updatedBy': actor}
        self.write(table, value, before['version'])
        self.svc.audit(actor, 'ops.record.archive' if record else 'ops.draft.archive', before, value)
        return {'record' if record else 'draft': value}

    def action(self, action, payload, actor):
        if action == 'ops.draft.save': return self.draft_save(payload, actor)
        if action == 'ops.record.add': return self.record_add(payload, actor)
        if action in ('ops.draft.archive', 'ops.record.archive'):
            return self.archive(payload, actor, action == 'ops.record.archive')
        raise ApiError(404, '不支持的草稿操作；没有发布或生效入口')

    def query(self, params):
        allowed = {'dataset', 'module', 'status', 'search', 'page', 'pageSize', 'orderId', 'driverId', 'passengerId'}
        self.fields(params, allowed, {'dataset'})
        flat = {}
        for key, value in params.items():
            if isinstance(value, list):
                require(len(value) == 1, '查询参数不得重复')
                value = value[0]
            require(isinstance(value, str), '查询参数格式无效')
            flat[key] = value
        dataset = flat['dataset']
        require(dataset in ('ops.drafts', 'ops.records'), '不支持的草稿查询')
        table = 'operation_records' if dataset == 'ops.records' else 'operation_drafts'
        module, status = flat.get('module'), flat.get('status')
        if module: self.module(module)
        statuses = {'RECORDED', 'ARCHIVED'} if dataset == 'ops.records' else {'DRAFT', 'ARCHIVED'}
        require(not status or status in statuses, '草稿查询状态无效')
        search = textfield(flat.get('search', ''), '查询文本', 0, 100).casefold()
        def integer(key, default, maximum):
            raw = flat.get(key, str(default))
            require(re.fullmatch(r'[1-9]\d{0,6}', raw) and int(raw) <= maximum, '分页范围无效')
            return int(raw)
        page, size = integer('page', 1, 1000000), integer('pageSize', 20, 100)
        links = {key: self.identifier(flat[key]) for key in ('orderId', 'driverId', 'passengerId') if key in flat}
        require(not links or dataset == 'ops.records', '仅人工记录支持关联对象查询')
        selected = [item for item in self.rows(table)
                    if (not module or item['module'] == module) and (not status or item['status'] == status)
                    and (not search or search in dumps(item).casefold())
                    and all(item.get(key) == value for key, value in links.items())]
        return {'dataset': dataset, 'items': selected[(page-1)*size:page*size], 'total': len(selected),
                'page': page, 'pageSize': size, 'draftOnly': True, 'definition': POLICY_LABEL}
