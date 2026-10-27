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
  'notice': 'seed.profile.notices与passenger_data固定公告已有，缺后台公告维护与多端发布。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'company_support',
  'category': '平台运维',
  'name': '公司客服电话',
  'notice': 'driver profile.support.phone为空，缺公司级后台维护/真实号码配置。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'passenger_features',
  'category': '平台运维',
  'name': '乘客端功能配置',
  'notice': 'passenger_extra_data.features展示能力状态，缺后台功能开关审批。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'heatmap_thresholds',
  'category': '平台运维',
  'name': '热力图阈值',
  'notice': '现权益canHeatmap有，缺后台阈值规则与实际热力聚合。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'driver_wallet_entries',
  'category': '司机管理',
  'name': '钱包明细',
  'notice': '司机state.entries与payments提现台账已有，缺后台按司机钱包明细入口；本批先修结算标签。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'driver_daily_balances',
  'category': '司机管理',
  'name': '每日余额统计',
  'notice': '当前balance与条目不能冒称每日历史快照，需确定日结口径。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'qualification_expiry',
  'category': '司机管理',
  'name': '资质到期提醒',
  'notice': '已有到期日期与接单资格校验，缺后台独立提醒列表/通知策略。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'taxi_vehicles',
  'category': '车辆管理',
  'name': '出租车车辆独立管理',
  'notice': '司机资料含车牌/车型与资质，缺独立车辆生命周期与关联历史。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'order_splits',
  'category': '订单管理',
  'name': '订单分账记录',
  'notice': '没有真实分账供应商，需分账比例/对象/时点规则；不能伪称到账。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'regional_fares',
  'category': '结算管理',
  'name': '区域运价',
  'notice': '当前独立估价与区域会员配置，缺经确认的区域运价规则。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'company_split_accounts',
  'category': '结算管理',
  'name': '公司分账账户',
  'notice': '公司主体/账户/资金接入规则缺失，仅可做草稿配置。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'company_split_entries',
  'category': '结算管理',
  'name': '公司分账明细',
  'notice': '依赖已确认分账模型与模拟/真实账务边界。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'regional_service_fees',
  'category': '结算管理',
  'name': '区域服务费',
  'notice': '现费用合同serviceFee为0，未授权改变金额。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'driver_bid_rules',
  'category': '结算管理',
  'name': '司机竞价规则',
  'notice': '竞价流程已存在，后台缺可配置上下限/有效期业务规则。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'invoice_records',
  'category': '开票管理',
  'name': '开票记录',
  'notice': '司乘模拟申请/记录已有，缺统一后台列表；无真实开票供应商。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'service_fee_invoices',
  'category': '开票管理',
  'name': '技术服务费开票',
  'notice': '依赖服务费/抬头/税率/主体/供应商，不能制造正式凭证。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'invoice_reissues',
  'category': '开票管理',
  'name': '重开申请',
  'notice': '需原票作废/红冲/重开条件与审批规则；供应商缺失。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'service_score_rules',
  'category': '司机服务分',
  'name': '规则配置',
  'notice': '当前初始合成分98，缺用户确认计分阈值/扣分与申诉规则。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'service_score_entries',
  'category': '司机服务分',
  'name': '明细记录',
  'notice': '司机资料有合成初始条目，缺实际评分规则驱动与后台入口。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'service_score_statistics',
  'category': '司机服务分',
  'name': '司机统计',
  'notice': '当前合成分展示不代表业务统计，需规则驱动。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'referral_rules_records',
  'category': '营销拉新',
  'name': '拉新规则与双角色记录',
  'notice': '司机profile.referrals合成结构/乘客演示入口，未接实际奖励/绑定。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'role_points_rules',
  'category': '积分商城',
  'name': '双角色积分规则',
  'notice': '司机完成演示单+1与初始合成积分存在，缺经确认统一规则/乘客账务。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'role_points_entries',
  'category': '积分商城',
  'name': '双角色积分明细统计',
  'notice': '司机条目存在，缺后台统查/乘客真实记录。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'role_points_products',
  'category': '积分商城',
  'name': '双角色商品管理',
  'notice': '需商品/库存/兑换/履约规则，不能只做空入口称完成。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'role_points_redemptions',
  'category': '积分商城',
  'name': '双角色兑换记录',
  'notice': '依赖实际积分幂等/库存/履约，需业务规则。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'points_common_rules',
  'category': '积分商城',
  'name': '通用积分规则',
  'notice': '与双角色规则关系及优先级待明确，避免重复计奖。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'coupon_pool',
  'category': '优惠券',
  'name': '优惠券资金池',
  'notice': '当前乘客券为演示不抵扣，需资金来源/核销/退还规则。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'coupon_creation',
  'category': '优惠券',
  'name': '制作优惠券',
  'notice': '需适用区域/有效期/抵扣/退款/叠加规则，未授权改变现有费用。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'registration_campaigns',
  'category': '营销活动',
  'name': '新用户注册活动',
  'notice': '需资格/奖励/防重复/过期规则，不能伪称奖励到账。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'phone_booking',
  'category': '叫车服务',
  'name': '电话叫车',
  'notice': '司机私有合成PHONE单不是实际电话调度，缺线路/坐席/身份/派单配置。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'proxy_booking',
  'category': '叫车服务',
  'name': '代叫服务',
  'notice': '乘客riders/代叫字段已有，需核验被代叫人联系/通知/授权边界。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'},
 {'id': 'map_places',
  'category': '地图',
  'name': '定位/地点搜索',
  'notice': '地图服务另有专项验收，本页不修改定位或鉴权。此处仅保存配置草稿与人工备注，不发布、不计算、不代表业务已执行。'}]
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
