"""Published non-financial operations, separate from free-text operations drafts.

All writes join the existing SQLite transaction and snapshot CAS. No I/O, jobs,
payments, company signing or notification delivery are performed here.
"""
from common import *

LIVE_OPS_ACTIONS = frozenset({'ops.company.save', 'ops.driverCompany.set',
    'ops.announcement.save', 'ops.announcement.publish', 'ops.announcement.withdraw',
    'ops.passengerFeatures.save', 'ops.passengerFeatures.publish'})
CLIENTS = ('MINIPROGRAM', 'PASSENGER_ANDROID')
FEATURES = ('instant', 'reservation', 'bidding', 'invoice')


class LiveOperations:
    def __init__(self, svc):
        self.svc, self.db = svc, svc.db
        self.db.execute('CREATE TABLE IF NOT EXISTS live_operations(kind TEXT,id TEXT,payload TEXT NOT NULL,PRIMARY KEY(kind,id))')
        self.db.execute('''CREATE TABLE IF NOT EXISTS live_announcement_acks(
            passenger TEXT,announcement TEXT,version INTEGER,created TEXT,
            PRIMARY KEY(passenger,announcement,version))''')

    def fields(self, p, allowed, required=()):
        require(isinstance(p, dict) and set(required) <= set(p) and not set(p)-set(allowed), '操作字段缺失或不支持')

    def identifier(self, value):
        require(isinstance(value, str) and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,119}', value), '编号格式无效')
        return value

    def get(self, kind, oid):
        row = self.db.execute('SELECT payload FROM live_operations WHERE kind=? AND id=?', (kind, oid)).fetchone()
        return json.loads(row['payload']) if row else None

    def rows(self, kind):
        return [json.loads(r['payload']) for r in self.db.execute(
            'SELECT payload FROM live_operations WHERE kind=? ORDER BY id', (kind,))]

    def write(self, kind, oid, value):
        self.db.execute('INSERT OR REPLACE INTO live_operations VALUES (?,?,?)', (kind, oid, dumps(value)))

    def expected(self, before, p):
        v = p.get('expectedVersion', 0 if before is None else None)
        require(type(v) is int and v >= 0, '请提供整数版本expectedVersion')
        require(v == (before['version'] if before else 0), '数据已更新，请刷新后重新确认', 409)

    def scope(self, scope, target):
        require(isinstance(scope, str) and scope in ('NATIONAL', 'REGION', 'CITY'), '地区范围无效')
        self.identifier(target)
        self.svc.config_row({'NATIONAL': 'national', 'REGION': 'region', 'CITY': 'city'}[scope], target)
        return scope, target

    def company_view(self, company_id, source='NONE'):
        c = self.get('company', company_id) if company_id else None
        return {'status': 'CONFIGURED' if c and c['enabled'] else 'DISABLED' if c else 'UNCONFIGURED',
            'id': c['id'] if c else None, 'name': c['name'] if c else None,
            'supportPhone': c['supportPhone'] if c and c['enabled'] else None,
            'version': c['version'] if c else None, 'source': source if c else 'NONE'}

    def public_companies(self):
        return [clone(c) for c in self.rows('company') if c['enabled']]

    def driver_state(self, driver):
        link = self.get('driverCompany', driver)
        return {'company': self.company_view(link['companyId'] if link else None, 'DRIVER')}

    def select_company(self, pid, p):
        self.fields(p, {'companyId'}, {'companyId'})
        cid = p['companyId']
        if cid is not None:
            self.identifier(cid)
            c = self.get('company', cid)
            require(c is not None and c['enabled'], '公司不存在或已停用', 404)
        before = self.get('passengerCompany', pid)
        self.write('passengerCompany', pid, {'companyId': cid})
        self.svc.audit('PASSENGER:'+pid, 'ops.company.select', before, {'companyId': cid})
        return {'company': self.company_view(cid, 'SELECTED')}

    def selected_company(self, pid):
        value = self.get('passengerCompany', pid)
        return self.company_view(value['companyId'] if value else None, 'SELECTED')

    def capture_order_company(self, o):
        # Legacy orders have no marker: do not manufacture historical ownership.
        driver = o.get('driverId')
        if 'companyAssignmentRecorded' not in o or not driver:
            return
        if o.get('companyAssignmentDriverId') == driver:
            return
        if o.get('companyAssignmentRecorded'):
            o.setdefault('companyHistory', []).append({'companyId': o.get('companyId'),
                'driverId': o.get('companyAssignmentDriverId'), 'at': stamp()})
        link = self.get('driverCompany', driver)
        o.update(companyId=link['companyId'] if link else None,
                 companyAssignmentRecorded=True, companyAssignmentDriverId=driver)

    def validate_client(self, client):
        require(isinstance(client, str) and client in CLIENTS, '公告客户端类型无效')
        return client

    def announcement_content(self, p):
        scope, target = self.scope(p.get('scope'), p.get('target'))
        audiences = p.get('audiences', ['MINIPROGRAM'])
        require(isinstance(audiences, list) and 1 <= len(audiences) <= 2, '公告受众无效')
        for a in audiences:
            self.validate_client(a)
        require(len(set(audiences)) == len(audiences), '公告受众不能重复')
        start, end = parse_time(p.get('startAt')), parse_time(p.get('endAt'))
        require(end > start, '公告结束时间须晚于开始时间')
        return {'title': textfield(p.get('title'), '公告标题', 1, 120),
            'content': textfield(p.get('content'), '公告内容', 1, 2000),
            'scope': scope, 'target': target, 'audiences': audiences,
            'startAt': stamp(start.astimezone(TZ)), 'endAt': stamp(end.astimezone(TZ))}

    def matches(self, snapshot, pid, client):
        if client not in snapshot['audiences']:
            return False
        city = self.svc.city(self.svc.passenger_profile(pid)['city'])
        return snapshot['target'] == {'NATIONAL': 'CN', 'REGION': city['region'], 'CITY': city['id']}[snapshot['scope']]

    def active(self, record):
        s = record.get('published')
        return bool(record.get('status') == 'PUBLISHED' and s and
            parse_time(s['startAt']) <= now() < parse_time(s['endAt']))

    def eligible(self, pid, client):
        self.validate_client(client)
        result = []
        for record in self.rows('announcement'):
            s = record.get('published')
            if not self.active(record) or not self.matches(s, pid, client):
                continue
            if not self.db.execute('SELECT 1 FROM live_announcement_acks WHERE passenger=? AND announcement=? AND version=?',
                (pid, record['id'], s['version'])).fetchone():
                result.append({'id': record['id'], **clone(s)})
        return result

    def ack(self, pid, p):
        self.fields(p, {'id', 'version', 'client'}, {'id', 'version', 'client'})
        oid = self.identifier(p['id']); client = self.validate_client(p['client'])
        require(type(p['version']) is int and p['version'] > 0, '公告版本无效')
        record = self.get('announcement', oid)
        require(record is not None, '公告不存在', 404)
        s = record.get('published')
        require(self.active(record) and s['version'] == p['version'], '公告已过期、撤回或更新，请刷新', 409)
        require(self.matches(s, pid, client), '当前用户不在公告展示范围', 403)
        first = self.db.execute('INSERT OR IGNORE INTO live_announcement_acks VALUES (?,?,?,?)',
            (pid, oid, s['version'], stamp())).rowcount == 1
        if first:
            self.svc.audit('PASSENGER:'+pid, 'ops.announcement.ack', None, {'id': oid, 'version': s['version']})
        return {'acknowledged': True, 'firstDisplay': first, 'id': oid, 'version': s['version']}

    def effective_features(self, city):
        c = self.svc.city(city)
        features = {k: True for k in FEATURES}
        sources = {k: {'scope': 'DEFAULT', 'target': None, 'version': 0} for k in FEATURES}
        vector = []
        for scope, target in (('NATIONAL', 'CN'), ('REGION', c['region']), ('CITY', c['id'])):
            row = self.get('features', scope+':'+target)
            published = row.get('published') if row else None
            version = published['version'] if published else 0
            vector.append(scope+':'+target+':'+str(version))
            for k, value in (published['features'] if published else {}).items():
                features[k] = value
                sources[k] = {'scope': scope, 'target': target, 'version': version}
        return {'city': city, 'features': features, 'sources': sources, 'version': '|'.join(vector)}

    def require_feature(self, city, key):
        value = self.effective_features(city)
        require(value['features'][key], '此地区暂未开放该乘客功能', 403,
                {'reason': 'PASSENGER_FEATURE_DISABLED', 'feature': key, 'city': city})
        return value

    def passenger_state(self, pid, client, orders):
        city = self.svc.passenger_profile(pid)['city']
        selected = self.selected_company(pid)
        for o in orders:
            if o.get('stage') not in ('DONE', 'CANCELLED') and o.get('companyId'):
                selected = self.company_view(o['companyId'], 'ORDER')
                break
        return {'companies': self.public_companies(), 'company': selected,
            'announcements': self.eligible(pid, client), 'passengerFeatures': self.effective_features(city)}

    def state(self):
        return {'companies': self.rows('company'), 'driverCompanies': self.rows('driverCompany'),
            'announcements': self.rows('announcement'), 'passengerFeatureConfigs': self.rows('features'),
            'serverDate': now().date().isoformat()}

    def admin_action(self, action, p, actor):
        require(action in LIVE_OPS_ACTIONS, '不支持的运营操作', 404)
        if action == 'ops.company.save':
            self.fields(p, {'id','expectedVersion','name','supportPhone','enabled'}, {'name','supportPhone','enabled'})
            oid = self.identifier(p['id']) if 'id' in p else uid('COMP')
            before = self.get('company', oid)
            if 'id' in p: require(before is not None, '公司不存在', 404)
            self.expected(before, p)
            require(type(p['enabled']) is bool, '启用状态须为布尔值')
            phone = p['supportPhone']
            require(phone is None or (isinstance(phone,str) and 3 <= len(phone.strip()) <= 30
                    and re.fullmatch(r'[+0-9][0-9 +\-]*', phone.strip()) and
                    len(re.sub(r'\D', '', phone)) >= 3), '客服电话格式无效')
            value = {'id': oid, 'name': textfield(p['name'],'公司名称',1,120),
                'supportPhone': phone.strip() if phone else None, 'enabled': p['enabled'],
                'version': before['version']+1 if before else 1, 'updatedAt': stamp()}
            kind, result = 'company', 'company'
        elif action == 'ops.driverCompany.set':
            self.fields(p, {'driverId','companyId','expectedVersion'}, {'driverId','companyId','expectedVersion'})
            oid = self.identifier(p['driverId']); self.svc.account(oid)
            before = self.get('driverCompany', oid); self.expected(before, p)
            cid = p['companyId']
            if cid is not None:
                self.identifier(cid); company = self.get('company', cid)
                require(company is not None and company['enabled'], '公司不存在或已停用', 404)
            value = {'driverId': oid, 'companyId': cid, 'version': before['version']+1 if before else 1}
            kind, result = 'driverCompany', 'driverCompany'
        elif action.startswith('ops.announcement.'):
            save = action.endswith('.save')
            self.fields(p, {'id','expectedVersion','title','content','scope','target','audiences','startAt','endAt'}
                if save else {'id','expectedVersion'}, () if save else {'id','expectedVersion'})
            oid = self.identifier(p['id']) if 'id' in p else uid('ANN')
            before = self.get('announcement', oid)
            if 'id' in p: require(before is not None, '公告不存在', 404)
            self.expected(before, p)
            value = clone(before) if before else {'id':oid,'status':'DRAFT','published':None}
            value.update(version=before['version']+1 if before else 1, updatedAt=stamp())
            if save:
                value['draft'] = self.announcement_content(p)
            elif action.endswith('.publish'):
                require(parse_time(value['draft']['endAt']) > now(), '不能发布已过期公告', 409)
                value.update(status='PUBLISHED', published={**clone(value['draft']), 'version':value['version']})
            else:
                value.update(status='WITHDRAWN', published=None)
            kind, result = 'announcement', 'announcement'
        else:
            save = action.endswith('.save')
            self.fields(p, {'scope','target','expectedVersion','features'} if save else
                        {'scope','target','expectedVersion'}, {'scope','target','expectedVersion'})
            scope, target = self.scope(p['scope'], p['target']); oid = scope+':'+target
            before = self.get('features', oid); self.expected(before, p)
            require(save or before is not None, '尚无乘客功能草稿', 404)
            value = clone(before) if before else {'scope':scope,'target':target,'published':None}
            value.update(version=before['version']+1 if before else 1)
            if save:
                features = p.get('features')
                require(isinstance(features,dict) and not set(features)-set(FEATURES)
                        and all(type(v) is bool for v in features.values()), '功能须为支持的布尔字段')
                value['draft'] = clone(features)
            else:
                value['published'] = {'features':clone(value['draft']), 'version':value['version']}
            kind, result = 'features', 'passengerFeatureConfig'
        self.write(kind, oid, value)
        self.svc.audit(actor, action, before, value)
        return {result: clone(value)}
