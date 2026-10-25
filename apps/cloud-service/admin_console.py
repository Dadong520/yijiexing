"""Admin console extension. Runs inside the EXISTING Service transaction/snapshot.
No database connection, web server, credentials or external dependencies here.
"""
import csv
import io
from common import *
from admin_queries import AdminQueries

READ = {'orders.read','drivers.read','passengers.read','payments.read','support.read','config.read','statistics.read','audit.read'}
ROLES = {
 'SUPER_ADMIN': READ | {'users.manage','order.cancel','order.adjustFare','order.refund','support.reply','certificate.audit','wallet.withdrawal.audit','config.write','export'},
 'OPERATOR': READ | {'order.cancel','support.reply','certificate.audit','export'},
 'FINANCE': READ | {'order.adjustFare','order.refund','wallet.withdrawal.audit','export'},
 'VIEWER': READ,
 'CONFIG_ADMIN': READ | {'config.write','export'},
}
SCHEMA = [
 'CREATE TABLE IF NOT EXISTS console_users(username TEXT PRIMARY KEY,password_hash TEXT NOT NULL,role TEXT NOT NULL,enabled INTEGER NOT NULL,created TEXT NOT NULL)',
 'CREATE TABLE IF NOT EXISTS console_sessions(token_hash TEXT PRIMARY KEY,username TEXT NOT NULL,expires TEXT NOT NULL)',
 'CREATE TABLE IF NOT EXISTS console_login_attempts(username TEXT PRIMARY KEY,failures INTEGER NOT NULL,window_start TEXT NOT NULL)',
 'CREATE TABLE IF NOT EXISTS console_actions(username TEXT,request_id TEXT,request_hash TEXT,result TEXT,PRIMARY KEY(username,request_id))',
]
CONFIG_ACTIONS = {'config.draft','config.publish','config.restore','config.copy','batch.preview','batch.execute','approval.decide','schedule.cancel'}

class ConsoleLoginError(ApiError):
    """Handler must commit SECURITY-ONLY failed login writes before re-raising.
    CloudHandler still withholds the failure response until snapshot CAS finishes.
    """
    pass

class AdminConsole:
    def __init__(self, svc, bootstrap_password=None):
        self.svc, self.db = svc, svc.db
        # execute, never executescript: preserve caller's transaction boundary.
        for ddl in SCHEMA:self.db.execute(ddl)
        raw_password = bootstrap_password or os.environ.get('ADMIN_BOOTSTRAP_PASSWORD','')
        if raw_password and not self.db.execute('SELECT 1 FROM console_users LIMIT 1').fetchone():
            password = self.password(raw_password)
            self.db.execute('INSERT INTO console_users VALUES (?,?,?,?,?)',('admin',secret_hash(password),'SUPER_ADMIN',1,stamp()))
            svc.audit('SYSTEM','console.bootstrap',None,{'username':'admin'})
        elif raw_password and raw_password != raw_password.strip():
            # Narrow repair for the historical bootstrap whitespace bug. Never
            # reset a changed password, another user, role, enabled state or data.
            initial = self.db.execute('SELECT * FROM console_users WHERE username=?',('admin',)).fetchone()
            if initial and secret_check(raw_password, initial['password_hash']):
                password = self.password(raw_password)
                self.db.execute('UPDATE console_users SET password_hash=? WHERE username=?',(secret_hash(password),'admin'))
                # Failed attempts caused by the inaccessible old hash must not
                # keep this repaired bootstrap account locked for 15 minutes.
                self.db.execute('DELETE FROM console_login_attempts WHERE username=?',('admin',))
                svc.audit('SYSTEM','console.bootstrap.normalize',None,{'username':'admin','reason':'legacy bootstrap whitespace repair'})
    def password(self, value):
        return textfield(value,'管理密码',12,128)
    def user(self,row):
        return {'username':row['username'],'role':row['role'],'enabled':bool(row['enabled']),'createdAt':row['created'],'permissions':sorted(ROLES[row['role']])}
    def login(self,p):
        username=textfield(p.get('username'),'账号',1,64);password=textfield(p.get('password'),'密码',1,128)
        attempt=self.db.execute('SELECT * FROM console_login_attempts WHERE username=?',(username,)).fetchone()
        recent=attempt and parse_time(attempt['window_start'])>now()-dt.timedelta(minutes=15)
        if recent and attempt['failures']>=5:
            self.svc.audit('LOGIN:'+username,'console.login.blocked',None,{'windowMinutes':15})
            raise ConsoleLoginError(429,'尝试次数过多，请15分钟后重试')
        row=self.db.execute('SELECT * FROM console_users WHERE username=?',(username,)).fetchone()
        if not (row is not None and row['enabled'] and secret_check(password,row['password_hash'])):
            self.db.execute('INSERT OR REPLACE INTO console_login_attempts VALUES (?,?,?)',(username,attempt['failures']+1 if recent else 1,attempt['window_start'] if recent else stamp()))
            self.svc.audit('LOGIN:'+username,'console.login.failed',None,{'username':username})
            raise ConsoleLoginError(401,'账号或密码错误，或账号已停用')
        self.db.execute('DELETE FROM console_login_attempts WHERE username=?',(username,))
        token=secrets.token_urlsafe(32)
        self.db.execute('DELETE FROM console_sessions WHERE expires<=?',(stamp(),))
        self.db.execute('INSERT INTO console_sessions VALUES (?,?,?)',(hashlib.sha256(token.encode()).hexdigest(),username,stamp(now()+dt.timedelta(hours=8))))
        self.svc.audit('ADMIN:'+username,'console.login',None,{'username':username})
        return {'session':token,'user':self.user(row)}
    def authenticate(self,token):
        require(isinstance(token,str) and token,'请先登录管理账号',401)
        row=self.db.execute('SELECT u.* FROM console_sessions s JOIN console_users u ON u.username=s.username WHERE s.token_hash=? AND s.expires>?',(hashlib.sha256(token.encode()).hexdigest(),stamp())).fetchone()
        require(row is not None and row['enabled'],'管理会话已失效，请重新登录',401)
        return self.user(row)
    def logout(self,token):
        self.db.execute('DELETE FROM console_sessions WHERE token_hash=?',(hashlib.sha256(token.encode()).hexdigest(),));return {'loggedOut':True}
    def permit(self,user,permission):
        require(permission in ROLES.get(user['role'],set()),'当前账号没有此操作权限',403)
    def orders(self):
        orders=[json.loads(r['payload']) for r in self.db.execute('SELECT payload FROM passenger_orders ORDER BY created DESC,rowid DESC')]
        for o in orders:
            self.svc.refresh_distance_consent(o)
            o.setdefault('fareVersion',self.svc.order_fare_version(o))
            o['distanceRules']=self.svc.trip_distance_rules(o['city']) if o.get('city') else None
        return orders
    def state(self,user):
        orders=self.orders();base=self.svc.admin_state()
        passengers=[json.loads(r['profile']) for r in self.db.execute('SELECT profile FROM passengers ORDER BY id')]
        support=[]
        for o in orders:
            for c in o.get('complaints',[]):support.append({**c,'source':'ORDER_COMPLAINT','ownerId':o['id'],'orderId':o['id']})
            if o.get('evaluation'):support.append({**o['evaluation'],'id':'EVAL:'+o['id'],'source':'EVALUATION','ownerId':o['passengerId'],'orderId':o['id'],'status':'RECORDED'})
        for p in passengers:
            for f in p.get('feedback',[]):support.append({**f,'source':'PASSENGER_FEEDBACK','ownerId':p['id']})
        for d in base['drivers']:
            profile=json.loads(self.svc.account(d['id'])['profile']);d['profile']=profile['driver']
            for key,source in [('feedback','DRIVER_FEEDBACK'),('complaints','DRIVER_COMPLAINT')]:
                for f in profile.get(key,[]):support.append({**f,'source':source,'ownerId':d['id']})
        for city in base['cities']:
            actual=[d for d in base['drivers'] if d['city']==city['id']]
            city['drivers']=len(actual)
            city['paidDrivers']=sum(bool(d.get('subscription')) for d in actual)
            city['countSource']='实际已注册司机；paidDrivers为具有订阅记录的司机'
        payments=[]
        for o in orders:
            # order.continue POPs the current refund into history. These are
            # disjoint refunds; equal amounts/timestamps may be distinct rounds.
            history=clone(o.get('refundHistory',[]))
            current=o.get('refundAmount',0)
            historical=sum(r['amount'] for r in history)
            records=[{**r,'source':'HISTORY','roundIndex':i} for i,r in enumerate(history)]
            if current:
                records.append({'amount':current,'at':o.get('refundedAt'),'driverId':o.get('driverId'),'mode':o.get('refundMode','SIMULATED'),'source':'CURRENT','roundIndex':len(history)})
            payments.append({**{k:o.get(k) for k in ('passengerId','driverId','fare','paymentStatus','paid','paidAt','refundedAt','paymentMode')},'id':o['id'],'orderId':o['id'],'refundAmount':current+historical,'currentRefundAmount':current,'historicalRefundAmount':historical,'refundHistory':history,'refundRecords':records})
        stats={'orderCount':len(orders),'completedCount':sum(o['stage']=='DONE' for o in orders),'cancelledCount':sum(o['stage']=='CANCELLED' for o in orders),'activeCount':sum(o['stage'] not in ('DONE','CANCELLED') for o in orders),'paidFare':sum(o['fare'] for o in orders if o['stage']=='DONE' and o['paid']),'refundAmount':sum(p['refundAmount'] for p in payments),'driverCount':len(base['drivers']),'passengerCount':len(passengers),'definition':'共享订单全量；排除司机私有演示订单。paidFare为完成且仍已支付的订单金额合计（分），refundAmount为当前退款与继续叫车前历史退款之和；退款记录按各轮分别计入，不按金额去重。'}
        out={'user':user,'roles':[{ 'id':k,'permissions':sorted(v)} for k,v in ROLES.items()],'users':[],'approvals':base['approvals'],'scheduled':base['scheduled'],'batches':base['batches'],'orders':orders,'drivers':base['drivers'],'passengers':passengers,'payments':payments,'support':support,'configs':base['configs'],'cities':base['cities'],'statistics':stats,'audit':base['audit'],'serverTime':stamp(),'sandbox':True,'policies':base['policies']+['共享订单使用passenger_orders权威记录；金额为分。所有支付、退款和提现均为模拟。','外部短信、实时路由、实名认证、厂商推送与电子发票未接通。']}
        mapping={'approvals':'config.read','scheduled':'config.read','batches':'config.read','orders':'orders.read','drivers':'drivers.read','passengers':'passengers.read','payments':'payments.read','support':'support.read','configs':'config.read','cities':'config.read','statistics':'statistics.read','audit':'audit.read'}
        for key,permission in mapping.items():
            if permission not in user['permissions']:out[key]=None if key=='statistics' else []
        if 'users.manage' in user['permissions']:out['users']=[self.user(r) for r in self.db.execute('SELECT * FROM console_users ORDER BY username')]
        return out
    def action(self,user,action,p,request_id):
        require(isinstance(p,dict) and isinstance(action,str),'操作格式错误')
        self.permit(user,'config.write' if action in CONFIG_ACTIONS else 'users.manage' if action=='user.save' else action)
        rid=textfield(request_id,'请求标识',1,120);fp=hashlib.sha256(dumps({'action':action,'payload':p}).encode()).hexdigest()
        old=self.db.execute('SELECT * FROM console_actions WHERE username=? AND request_id=?',(user['username'],rid)).fetchone()
        if old:
            require(old['request_hash']==fp,'请求标识已用于不同操作',409);return json.loads(old['result'])
        actor='ADMIN:'+user['username']
        if action in ('order.cancel','order.adjustFare','order.refund'):result=self.order_action(action,p,actor)
        elif action=='support.reply':result=self.reply(p,actor)
        elif action=='user.save':result=self.save_user(user,p,actor)
        elif action in CONFIG_ACTIONS or action in ('certificate.audit','wallet.withdrawal.audit'):
            if action=='wallet.withdrawal.audit':require(p.get('confirmed') is True,'请确认模拟提现审核')
            result=self.svc.admin_action(action,p,actor)
        else:raise ApiError(404,'不支持的管理操作')
        self.db.execute('INSERT INTO console_actions VALUES (?,?,?,?)',(user['username'],rid,fp,dumps(result)))
        return result
    def order_action(self,action,p,actor):
        require(p.get('confirmed') is True,'请确认人工订单操作')
        reason=textfield(p.get('reason'),'处理原因',2,500)
        o=self.svc.shared_order(p.get('id'));require(o is not None,'共享订单不存在',404)
        require(p.get('expectedStage')==o['stage'],'订单状态已变化，请刷新',409)
        self.svc.validate_fare_confirmation(o,p)
        before=clone(o)
        if action=='order.cancel':
            require(o['stage'] in ('AVAILABLE','WAIT_CONTINUE','PREPAY','RESERVED','PICKUP','ARRIVED'),'当前状态不允许后台取消',409)
            o.update(stage='CANCELLED',cancelReason=reason,cancelledBy='ADMIN')
            if o['paid']:o.update(paid=False,paymentStatus='REFUNDED',refundAmount=o['fare'],refundedAt=stamp(),refundMode='SIMULATED')
        elif action=='order.adjustFare':
            require(o['stage']=='WAIT_PAY' and not o['paid'],'仅待支付且未付款订单可调整费用',409)
            amount=cents(p.get('amount'),'调整后费用');version=self.svc.order_fare_version(o)+1
            o.setdefault('adjustments',[]).append({'before':o['fare'],'after':amount,'fareVersion':version,'reason':reason,'actor':actor,'at':stamp()});o.update(fare=amount,fareVersion=version,fareSource='ADMIN_SIMULATED_ADJUSTMENT')
        else:
            require(o['stage']=='DONE' and o['paid'] and not o.get('refundAmount'),'仅已完成已支付且未退款订单可模拟全退',409)
            if o.get('credited'):
                st=self.svc.sync_shared_orders(o['driverId']);require(st['balance']>=o['fare'],'司机可用模拟余额不足，退款需人工规则确认',409)
                st['balance']-=o['fare'];st['entries'].insert(0,{'id':uid('WE'),'title':'模拟订单退款 '+o['id'],'amount':-o['fare'],'note':reason,'createdAt':stamp(),'time':stamp()});self.svc.save_state(o['driverId'],st)
            o.update(paid=False,paymentStatus='REFUNDED',refundAmount=o['fare'],refundedAt=stamp(),refundMode='SIMULATED',refundReason=reason)
        o.setdefault('adminOperations',[]).append({'action':action,'actor':actor,'reason':reason,'at':stamp()})
        self.svc.save_shared(o,'ADMIN_'+action.split('.')[1].upper())
        if o.get('driverId'):self.svc.sync_shared_orders(o['driverId'])
        self.svc.audit(actor,action,before,o);return {'order':o}
    def reply(self,p,actor):
        source=p.get('source');owner=p.get('ownerId');reply=textfield(p.get('reply'),'处理回复',2,2000)
        if source=='ORDER_COMPLAINT':
            obj=self.svc.shared_order(owner);require(obj is not None,'订单不存在',404);rows=obj.get('complaints',[])
        elif source=='PASSENGER_FEEDBACK':obj=self.svc.passenger_profile(owner);rows=obj.get('feedback',[])
        elif source in ('DRIVER_FEEDBACK','DRIVER_COMPLAINT'):
            obj=json.loads(self.svc.account(owner)['profile']);rows=obj.get('feedback' if source=='DRIVER_FEEDBACK' else 'complaints',[])
        else:raise ApiError(400,'不支持此反馈类型')
        item=next((r for r in rows if r['id']==p.get('id')),None);require(item is not None,'反馈记录不存在',404);before=clone(item)
        item.update(reply=reply,status='REPLIED',repliedAt=stamp(),handledBy=actor)
        if source=='ORDER_COMPLAINT':
            self.svc.save_shared(obj,'ADMIN_COMPLAINT_REPLIED')
            if obj.get('driverId'):self.svc.sync_shared_orders(obj['driverId'])
        elif source=='PASSENGER_FEEDBACK':self.db.execute('UPDATE passengers SET profile=? WHERE id=?',(dumps(obj),owner))
        else:self.svc.save_profile(owner,obj)
        self.svc.audit(actor,'support.reply',before,{'source':source,'ownerId':owner,'record':item});return {'record':item}
    def save_user(self,user,p,actor):
        username=textfield(p.get('username'),'账号',2,64);require(re.fullmatch(r'[a-zA-Z0-9_.-]+',username),'账号仅允许字母、数字、下划线、点和短横线')
        role=p.get('role');enabled=p.get('enabled');require(role in ROLES and type(enabled) is bool,'角色或启用状态无效')
        require(username!=user['username'] or (enabled and role=='SUPER_ADMIN'),'不能停用或降权当前超级管理员',409)
        old=self.db.execute('SELECT * FROM console_users WHERE username=?',(username,)).fetchone()
        if old and old['role']=='SUPER_ADMIN' and old['enabled'] and (not enabled or role!='SUPER_ADMIN'):
            require(self.db.execute("SELECT count(*) FROM console_users WHERE role='SUPER_ADMIN' AND enabled=1").fetchone()[0]>1,'必须保留至少一个超级管理员',409)
        password=p.get('password');require(old is not None or password,'新账号必须设置密码')
        hashed=secret_hash(self.password(password)) if password else old['password_hash']
        self.db.execute('INSERT OR REPLACE INTO console_users VALUES (?,?,?,?,?)',(username,hashed,role,int(enabled),old['created'] if old else stamp()))
        self.db.execute('DELETE FROM console_sessions WHERE username=?',(username,))
        result={'username':username,'role':role,'enabled':enabled};self.svc.audit(actor,'user.save',self.user(old) if old else None,result);return {'user':result}
    def certificate_file(self,user,driver_id,certificate_type,side='front'):
        self.permit(user,'drivers.read')
        require(side in ('front','back'),'附件面别无效')
        profile=json.loads(self.svc.account(driver_id)['profile'])
        cert=next((c for c in profile.get('certificates',[]) if c['type']==certificate_type),None)
        require(cert is not None,'资质类型不存在',404)
        fid=cert.get('fileId' if side=='front' else 'backFileId')
        require(bool(fid),'此资质尚未上传'+('正面' if side=='front' else '背面')+'附件',404)
        row=self.db.execute('SELECT id,mime,content FROM files WHERE id=? AND driver=?',(fid,driver_id)).fetchone()
        require(row is not None,'资质附件不存在或归属不匹配',404)
        require(row['mime'] in ('image/png','image/jpeg'),'不支持此附件格式',415)
        self.svc.audit('ADMIN:'+user['username'],'certificate.view',None,{'driverId':driver_id,'type':certificate_type,'side':side,'fileId':fid})
        return {'fileId':row['id'],'mimeType':row['mime'],'imageBase64':base64.b64encode(row['content']).decode()}
    def query(self,user,params):
        return AdminQueries(self,user).select(params)
    def export(self,user,dataset='orders',params=None):
        self.permit(user,'export')
        filters=dict(params or {})
        filters.setdefault('dataset',dataset)
        result=AdminQueries(self,user).select(filters,paginate=False)
        dataset=result['dataset'];rows=result['items']
        order_fields=['id','passengerId','driverId','stage','fare','paymentStatus','origin','destination','createdAt','passengerMobile','driverName','driverMobile','plate','city','kind','fareSource','cancellationType','cancelledAt']
        fields={'orders':order_fields,'drivers':['id','mobile','name','city','currentLevel','online','certificateStatus','vehicleType','plate'],'passengers':['id','mobile','name','city'],'payments':['orderId','fare','paymentStatus','refundAmount','currentRefundAmount','historicalRefundAmount','refundRecords','paymentMode','id','recordType','accountId','createdAt','amount','status'],'support':['id','source','ownerId','orderId','status','content','reply','rowId','accountId','createdAt'],'audit':['id','actor','action','created','before_value','after_value'],'cancellations':order_fields+['cancellationStatus'],'refunds':['id','orderId','accountId','driverId','amount','refundedAt','refundSource','roundIndex','paymentMode'],'statistics':['date','orderCount','completedCount','cancelledCount','activeCount','paidFare','refundAmount']}[dataset]
        buf=io.StringIO();writer=csv.writer(buf);writer.writerow(fields)
        def safe(v):
            s=dumps(v) if isinstance(v,(dict,list)) else '' if v is None else str(v)
            return "'"+s if s.lstrip().startswith(('=','+','-','@','\t','\r')) else s
        for row in rows:writer.writerow([safe(row.get(k)) for k in fields])
        self.svc.audit('ADMIN:'+user['username'],'console.export',None,{'dataset':dataset,'rows':len(rows),'filters':filters})
        return '\ufeff'+buf.getvalue()
