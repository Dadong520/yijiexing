from common import *
from seed import *
from benefits import BenefitsMixin
from admin import AdminMixin
from passenger import PassengerMixin
import sqlite3, threading

SCHEMA='''
CREATE TABLE IF NOT EXISTS accounts(id TEXT PRIMARY KEY,mobile TEXT UNIQUE,state TEXT,profile TEXT,password_hash TEXT,trial_start TEXT,trial_completed INTEGER DEFAULT 2,location TEXT);
CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY,driver TEXT,expires TEXT);
CREATE TABLE IF NOT EXISTS codes(mobile TEXT PRIMARY KEY,code_hash TEXT,expires TEXT,attempts INTEGER,kind TEXT);
CREATE TABLE IF NOT EXISTS configs(scope TEXT,target TEXT,published TEXT,draft TEXT,version INTEGER,revision INTEGER,updated TEXT,PRIMARY KEY(scope,target));
CREATE TABLE IF NOT EXISTS member_orders(id TEXT PRIMARY KEY,driver TEXT,idem TEXT,payload TEXT,created TEXT,UNIQUE(driver,idem));
CREATE TABLE IF NOT EXISTS subscriptions(id TEXT PRIMARY KEY,driver TEXT,payload TEXT,created TEXT);
CREATE TABLE IF NOT EXISTS invoices(id TEXT PRIMARY KEY,driver TEXT,order_id TEXT,payload TEXT,created TEXT,UNIQUE(driver,order_id));
CREATE TABLE IF NOT EXISTS approvals(id TEXT PRIMARY KEY,payload TEXT,created TEXT);
CREATE TABLE IF NOT EXISTS scheduled(id TEXT PRIMARY KEY,payload TEXT,created TEXT);
CREATE TABLE IF NOT EXISTS batches(id TEXT PRIMARY KEY,idem TEXT UNIQUE,payload TEXT,created TEXT);
CREATE TABLE IF NOT EXISTS audits(id INTEGER PRIMARY KEY AUTOINCREMENT,actor TEXT,action TEXT,before_value TEXT,after_value TEXT,created TEXT);
CREATE TABLE IF NOT EXISTS files(id TEXT PRIMARY KEY,driver TEXT,mime TEXT,content BLOB,created TEXT);
CREATE TABLE IF NOT EXISTS actions(driver TEXT,request_id TEXT,request_hash TEXT,result TEXT,PRIMARY KEY(driver,request_id));
'''
ACTIVE={'BID_SENT','PREPAY','RESERVED','PICKUP','ARRIVED','TRIP'}
class Service(PassengerMixin,BenefitsMixin,AdminMixin):
    def __init__(self,db_path):
        self.lock=threading.RLock();self.db=sqlite3.connect(str(db_path),check_same_thread=False);self.db.row_factory=sqlite3.Row
        self.db.execute('PRAGMA journal_mode=WAL');self.db.execute('PRAGMA foreign_keys=ON');self.db.executescript(SCHEMA);self.init_passenger()
        if not self.db.execute('SELECT 1 FROM configs').fetchone():
            self.db.execute('INSERT INTO configs VALUES (?,?,?,?,?,?,?)',('national','CN',dumps(default_config()),None,1,1,stamp()))
            for code in ('CENTRAL','SOUTH'):self.db.execute('INSERT INTO configs VALUES (?,?,?,?,?,?,?)',('region',code,'{}',None,1,1,stamp()))
            for c in CITIES:
                p={'levels':{'NORMAL':{'price':19900,'firstYearPrice':16900,'upgradePrice':12900,'params':{'nearbyKm':2.5,'mapQuota':{'type':'ORDERS','freeOrders':200,'freeAmount':0,'overagePrice':30},'offlineNegotiation':False}},'VIP':{'price':32800,'params':{'offlineNegotiation':False}},'TRIAL':{'params':{'offlineNegotiation':False}}}} if c['id']=='420500' else {}
                self.db.execute('INSERT INTO configs VALUES (?,?,?,?,?,?,?)',('city',c['id'],dumps(p),None,1,1,stamp()))
        self.db.commit()
    def close(self):self.db.close()
    def account(self,driver):
        row=self.db.execute('SELECT * FROM accounts WHERE id=?',(driver,)).fetchone();require(row is not None,'司机账户不存在',404);return row
    def save_state(self,driver,state):self.db.execute('UPDATE accounts SET state=? WHERE id=?',(dumps(state),driver))
    def save_profile(self,driver,profile):self.db.execute('UPDATE accounts SET profile=? WHERE id=?',(dumps(profile),driver))
    def audit(self,actor,action,before,after):
        self.db.execute('INSERT INTO audits(actor,action,before_value,after_value,created) VALUES (?,?,?,?,?)',(actor,action,dumps(before),dumps(after),stamp()))
    def mobile(self,m):
        require(isinstance(m,str) and bool(re.fullmatch(r'1\d{10}',m)),'请输入11位云端演示演示手机号')
        return m
    def code(self,mobile,kind='LOGIN'):
        self.mobile(mobile);code=f'{secrets.randbelow(1000000):06d}'
        self.db.execute('INSERT OR REPLACE INTO codes VALUES (?,?,?,?,?)',(mobile+':'+kind,secret_hash(code),stamp(now()+dt.timedelta(minutes=5)),0,kind))
        return {'verificationCode':code,'expirySeconds':300,'sandbox':True,'message':'云端演示验证码已生成，没有发送真实短信'}
    def verify(self,mobile,code,kind):
        row=self.db.execute('SELECT * FROM codes WHERE mobile=?',(mobile+':'+kind,)).fetchone()
        require(row is not None and parse_time(row['expires'])>now() and row['attempts']<5,'验证码已过期或尝试次数过多，请重新获取',400)
        if not secret_check(code,row['code_hash']):
            self.db.execute('UPDATE codes SET attempts=attempts+1 WHERE mobile=?',(mobile+':'+kind,))
            # Persist attempt count even though caller will return a business error.
            self.db.commit();raise ApiError(400,'验证码不正确')
        self.db.execute('DELETE FROM codes WHERE mobile=?',(mobile+':'+kind,))
    def login(self,p):
        m=self.mobile(p.get('mobile'));self.verify(m,p.get('code',''),'LOGIN')
        a=self.db.execute('SELECT * FROM accounts WHERE mobile=?',(m,)).fetchone()
        if not a:
            driver=uid('D');profile=initial_profile(m);profile['driver']['id']=driver;st=initial_state()
            coords=[(30.6687,111.3226,30.6645,111.3694),(30.7066,111.2842,30.7342,111.2982),(30.6991,111.2866,30.5531,111.4815),(30.6645,111.3694,30.6813,111.3007),(30.7253,111.3066,30.7702,111.3191)]
            for i,o in enumerate(st['orders']):
                aLat,aLng,bLat,bLng=coords[i%len(coords)];o.update(originLat=aLat,originLng=aLng,destinationLat=bLat,destinationLng=bLng,coordinateSystem='WGS84',synthetic=True)
                for key in ('distance','mileage'):o[key]=re.sub('公里','',o[key])
            for entry in st['entries']:entry['note']=entry.get('subtitle','云端演示沙箱')+' · '+entry['time']
            self.db.execute('INSERT INTO accounts VALUES (?,?,?,?,?,?,?,?)',(driver,m,dumps(st),dumps(profile),'',stamp(),2,None))
        else:driver=a['id']
        token=secrets.token_urlsafe(32);self.db.execute('INSERT INTO sessions VALUES (?,?,?)',(token,driver,stamp(now()+dt.timedelta(days=7))))
        self.audit('DRIVER:'+driver,'auth.login',None,{'sandbox':True})
        data=self.data(driver);data['token']=token;return data
    def authenticate(self,token):
        s=self.db.execute('SELECT * FROM sessions WHERE token=?',(token,)).fetchone()
        require(s is not None and parse_time(s['expires'])>now(),'云端演示会话已失效，请重新登录',401)
        return s['driver']
    def features(self,driver):
        cat=self.catalog(driver);level=cat['subscription']['rightsSnapshot'] if cat['subscription'] else next(l for l in cat['levels'] if l['id']=='TRIAL')
        r={r['key']:r['enabled'] for r in level['rights']};active=cat['canAccept']
        cfg,_,_=self.effective(cat['city']['id'])
        return {'canAcceptOrders':active and any(r.get(k,False) for k in ('instant','reservation','dispatch','bidding','offlineNegotiation')),'canBid':active and bool(r.get('bidding')),'canNegotiate':active and bool(r.get('offlineNegotiation')) and cfg['levels'][level['id']]['params']['offlineNegotiation'],'canVoice':active and bool(r.get('voice')),'canFloating':active and bool(r.get('floating')),'canHeatmap':active and bool(r.get('heatmap')),'canRanking':active and bool(r.get('ranking')),'trialEndReason':cat['trial']['endReason'],'currentLevel':cat['currentLevel'],'sandbox':True,'map':'公开底图与设备定位；订单路线为合成示意，不作真实导航','payment':'云端演示沙箱，不收款','push':'云端演示订单轮询，未接厂商推送','autoRenew':'仅保存云端演示续费意愿，无支付机构签约或扣款'}
    def data(self,driver):
        self.sync_shared_orders(driver)
        a=self.account(driver);st=json.loads(a['state']);st.setdefault('floating',False);profile=json.loads(a['profile']);cat=self.catalog(driver)
        if not cat['canAccept'] and st['online']:
            st['online']=False;self.save_state(driver,st)
        profile['wallet']['passwordSet']=profile['wallet']['hasPassword']=bool(a['password_hash'])
        profile['ranking']['completed']=st['todayCompleted']
        features=self.features(driver)
        if not features['canRanking']:profile['ranking']={'period':'体验结束或未开放','myRank':None,'completed':None,'entries':[],'restricted':True}
        st['orders']=sorted([self.project_driver_order(driver,o) for o in st.get('orders',[])],key=lambda o: 0 if o.get('stage') in ('PICKUP','ARRIVED','TRIP','PREPAY','BID_SENT') else (1 if o.get('kind')=='RESERVED' and o.get('stage') in ('AVAILABLE','RESERVED') else 2))
        return {'mapConfig':self.amap_public_config('driver'),'state':st,'profile':profile,'benefits':cat,'features':features,'tripDistanceRules':self.trip_distance_rules(cat['city']['id']),'serverTime':stamp(),'environment':{'name':'云端演示隔离沙箱','sandbox':True,'originalSpringBackend':False,'persistence':'SQLite','network':'Google Cloud独立演示服务，不连接原测试环境'}}
    def order(self,st,oid):
        o=next((o for o in st['orders'] if o['id']==oid),None);require(o is not None,'订单不存在',404);return o
    def require_driver_certified(self,driver):
        profile=json.loads(self.account(driver)['profile']);require(profile['driver']['certificationStatus']=='APPROVED','司机资质未通过，不能新接单',403)
        today=now().date() # common.now already uses UTC+8; validUntil includes this date.
        for certificate in profile.get('certificates',[]):
            for field in ('validFrom','validUntil'):
                value=certificate.get(field)
                # Legacy missing/unrecognized values do not establish an expiry.
                if not isinstance(value,str) or not re.fullmatch(r'\d{4}-\d{2}-\d{2}',value):continue
                try: date=dt.date.fromisoformat(value)
                except ValueError:continue
                valid=date<=today if field=='validFrom' else date>=today
                require(valid,'司机证件尚未生效，不能新接单' if field=='validFrom' else '司机证件已到期，不能新接单',403,
                    {'reason':'CERTIFICATE_NOT_EFFECTIVE' if field=='validFrom' else 'CERTIFICATE_EXPIRED','certificateType':certificate.get('type'),'field':field})
    def validate_advance_stage(self,o,p):
        if 'expectedStage' in p:
            require(p['expectedStage']==o['stage'],'订单状态已变化，请刷新后重新确认操作',409,{'reason':'ORDER_STAGE_STALE'})
    def require_negotiation_driver(self,driver,st,order_id=None):
        self.check_right(driver,'offlineNegotiation');require(st['online'],'请先上线',409)
        require(not any(o['stage'] in ACTIVE and not (o.get('kind')=='RESERVED' and o.get('stage') in ('AVAILABLE','RESERVED')) and o['id']!=order_id for o in st['orders']),'请先完成当前订单',409)
        self.require_driver_certified(driver)


    def new_accept(self,driver,st,o,key):
        require(st['online'],'请先上线听单',409);require(not any(x['stage'] in ACTIVE and not (x.get('kind')=='RESERVED' and x.get('stage') in ('AVAILABLE','RESERVED')) for x in st['orders']),'请先完成进行中订单',409)
        require(o['stage']=='AVAILABLE','订单已被处理，请刷新',409)
        pid=o.get('passengerId') or o.get('passenger')
        if pid and o.get('kind')!='RESERVED':self.assert_no_trip_conflict(passenger_id=pid,exclude_order_id=o.get('id'))
        catalog=self.check_right(driver,key,o.get('city'))
        level=catalog['subscription']['rightsSnapshot'] if catalog['subscription'] else next(l for l in catalog['levels'] if l['id']=='TRIAL')
        rights={r['key']:r['enabled'] for r in level['rights']}
        require(rights.get('allArea') or rights.get('nearby'),'当前等级未开放接单范围',403)
        try:pickup_distance=float(str(o['distance']).replace('公里',''))
        except (ValueError,TypeError):raise ApiError(409,'云端演示订单缺少有效接驾距离，不能判定接单范围')
        require(pickup_distance>=0,'云端演示接驾距离无效',409)
        if not rights.get('allArea'):
            maxkm=level['params']['nearbyKm'];require(pickup_distance<=maxkm,'订单超出当前等级近距离接单范围',403,{'distanceKm':pickup_distance,'allowedKm':maxkm,'source':'SYNTHETIC_SERVER_ORDER'})
        require(pickup_distance<=st['range'],'订单超出当前接单偏好设置的范围',403,{'distanceKm':pickup_distance,'preferenceKm':st['range']})
        settings_key={'instant':'instant','reservation':'reservation','bidding':'bidding'}.get(key)
        if settings_key:require(st[settings_key],'当前订单类型已在接单偏好中关闭',403)
        require(not any(b['phoneTail']==o['phoneTail'] for b in json.loads(self.account(driver)['profile'])['blacklist']),'乘客在黑名单中，不能接此订单',403)
        self.require_driver_certified(driver)
    def credit(self,driver,st,o):
        if o['stage']!='DONE' or o['credited']:return
        o['credited']=True;o['completedAt']=stamp();st['balance']+=o['fare'];st['todayIncome']+=o['fare'];st['todayCompleted']+=1
        st['entries'].insert(0,{'id':uid('WE'),'title':'行程收入 '+o['id'],'amount':o['fare'],'note':'云端演示沙箱，不代表真实资金','createdAt':stamp(),'time':stamp()})
        self.db.execute('UPDATE accounts SET trial_completed=trial_completed+1 WHERE id=?',(driver,))
        p=json.loads(self.account(driver)['profile']);p['points']['balance']+=1;p['points']['total']+=1;p['points']['entries'].insert(0,{'title':'完成云端演示订单','delta':1,'date':stamp()});self.save_profile(driver,p)
    def save_image(self,driver,data):
        require(isinstance(data,str) and len(data)<=1400000,'图片须小于1MB')
        if data.startswith('data:'):data=data.split(',',1)[-1]
        try:raw=base64.b64decode(data,validate=True)
        except Exception:raise ApiError(400,'图片Base64格式错误')
        require(len(raw)<=1024*1024 and len(raw)>20,'图片大小无效')
        mime='image/png' if raw.startswith(b'\x89PNG\r\n\x1a\n') else ('image/jpeg' if raw.startswith(b'\xff\xd8\xff') else None)
        require(mime is not None,'仅接受PNG/JPEG图片')
        fid=uid('FILE');self.db.execute('INSERT INTO files VALUES (?,?,?,?,?)',(fid,driver,mime,raw,stamp()));return fid
    def attachments(self,driver,p,maxcount):
        images=p.get('imagesBase64',[])
        require(isinstance(images,list) and len(images)<=maxcount,'附件数量超出限制')
        if not images and p.get('imageBase64'):images=[p['imageBase64']]
        return [self.save_image(driver,img) for img in images]
    def certificate_audit(self,p,actor):
        driver=p.get('driverId');a=self.account(driver);profile=json.loads(a['profile']);cert=next((c for c in profile['certificates'] if c['type']==p.get('type')),None)
        require(cert is not None and cert['status']=='PENDING','资质不在待审核状态',409);approved=p.get('approved');require(isinstance(approved,bool),'审核结果无效')
        if not approved:textfield(p.get('reason'),'退回原因',2,500)
        cert.update(status='APPROVED' if approved else 'REJECTED',statusCode=1 if approved else 4,rejectionReason='' if approved else p['reason'],reply='' if approved else p['reason'],auditedAt=stamp(),auditedBy=actor)
        cert.setdefault('auditRecords',[]).append({'actor':actor,'approved':approved,'reason':p.get('reason',''),'at':stamp()})
        profile['driver']['certificationStatus']='APPROVED' if all(c['status']=='APPROVED' for c in profile['certificates']) else 'PENDING';self.save_profile(driver,profile);self.audit(actor,'certificate.audit',None,{'driverId':driver,'type':cert['type'],'approved':approved});return {'certificate':cert}
    def sandbox_event(self,p,actor):
        driver=p.get('driverId');self.account(driver);event=p.get('event');oid=p.get('orderId')
        if event=='BID_SELECTED':result=self.action(driver,'order.selectBid',{'id':oid},sandbox_actor=True)
        elif event in ('PASSENGER_PAY_SUCCESS','PASSENGER_PAY_FAILED','PASSENGER_PAY_PENDING','PASSENGER_PAY_CANCEL'):
            result=self.action(driver,'order.pay',{'id':oid,'outcome':{'PASSENGER_PAY_SUCCESS':'SUCCESS','PASSENGER_PAY_FAILED':'FAILED','PASSENGER_PAY_PENDING':'PENDING','PASSENGER_PAY_CANCEL':'CANCEL'}[event]},sandbox_actor=True)
        elif event=='NEW_ORDER':
            a=self.account(driver);st=json.loads(a['state']);profile=json.loads(a['profile'])
            kind=p.get('kind','STANDARD');require(kind in ('STANDARD','RESERVED','PHONE','BID'),'订单类型无效')
            supplied=p.get('waypoints',[])
            require(isinstance(supplied,list) and len(supplied)<=2,'云端演示合成订单最多支持两个途经点')
            waypoints=[]
            for index,value in enumerate(supplied):
                if isinstance(value,str):item={'name':textfield(value,'途经点名称',2,100)}
                else:
                    require(isinstance(value,dict),'途经点须为名称字符串或对象')
                    name=next((value.get(k) for k in ('name','waypointName','address','title') if value.get(k)),None)
                    item={'name':textfield(name,'途经点名称',2,100)}
                    lat=value.get('latitude',value.get('waypointLat'));lng=value.get('longitude',value.get('waypointLng'))
                    if lat is not None or lng is not None:
                        require(type(lat) in (int,float) and -90<=lat<=90 and type(lng) in (int,float) and -180<=lng<=180,'途经点坐标须为完整且范围有效的数值')
                        item.update(latitude=lat,longitude=lng)
                item.update(id=uid('WP'),sortNo=index+1);waypoints.append(item)
            # Always create from an untouched fixture. Never clone an account order that
            # may carry a paid/completed/cancelled/bidding state, personal inputs or old waypoints.
            o=initial_state()['orders'][0]
            o.update(id=uid('YC'),stage='AVAILABLE',paid=False,credited=False,createdAt=stamp(),kind=kind,
                city=profile['driver']['city'],originLat=30.6687,originLng=111.3226,destinationLat=30.6645,destinationLng=111.3694,
                coordinateSystem='WGS84',coordinateSource='SYNTHETIC_DEFAULT',coordinatesSynthetic=True,synthetic=True,
                distance='1.2',mileage='8.6',waypoints=waypoints,arrivedWaypoints=[])
            st['orders'].insert(0,o);self.save_state(driver,st);result={'order':o}
        else:raise ApiError(400,'沙箱事件类型不支持')
        self.audit(actor,'sandbox.event',None,{'driverId':driver,'orderId':oid,'event':event});return result
    def action(self,driver,action,p,request_id=None,sandbox_actor=False):
        require(isinstance(action,str) and 1<=len(action)<=80,'操作名称无效')
        require(isinstance(p,dict),'操作参数须为JSON对象')
        if not sandbox_actor and not action.startswith('benefits.'):
            require(isinstance(request_id,str) and 1<=len(request_id)<=120,'司机操作必须提供有效requestId')
        elif request_id is not None:require(isinstance(request_id,str) and 1<=len(request_id)<=120,'请求幂等标识无效')
        if action=='complaint.submit':action='order.complaint'
        self.sync_shared_orders(driver)
        if action in ('order.pairOffer','order.requote'):
            return self.driver_negotiation_action(driver,action,p,request_id,sandbox_actor)
        if sandbox_actor and action.startswith('order.'):
            require(not self.negotiation_private_locked(driver,p.get('id')),'此议价单已启用真实乘客配对，沙箱不能代替乘客操作',403)
        if action.startswith('order.') and self.shared_order(p.get('id')):
            return self.shared_driver_action(driver,action,p,request_id,sandbox_actor)
        fingerprint=hashlib.sha256(dumps({'action':action,'payload':p}).encode()).hexdigest()
        if request_id:
            req=self.db.execute('SELECT * FROM actions WHERE driver=? AND request_id=?',(driver,request_id)).fetchone()
            if req:
                require(req['request_hash']==fingerprint,'请求标识被其他操作占用',409);return self.driver_result_view(driver,json.loads(req['result']))
        a=self.account(driver);st=json.loads(a['state']);profile=json.loads(a['profile']);result={}
        if action.startswith('benefits.'):
            handlers={'benefits.orders':self.create_member_order,'benefits.pay':self.pay_member,'benefits.renewal':self.renewal,'benefits.invoices':self.invoice}
            require(action in handlers,'未知会员操作',404);return handlers[action](driver,p)
        if action=='driver.online':
            value=p.get('online');require(isinstance(value,bool),'上线状态无效')
            if value:
                catalog=self.catalog(driver);require(catalog['canAccept'],'体验结束或当前城市等级未开放新增接单，请查看会员权益',403,{'trial':catalog['trial']})
                level=catalog['subscription']['rightsSnapshot'] if catalog['subscription'] else next(l for l in catalog['levels'] if l['id']=='TRIAL')
                require(any(r['enabled'] and r['key'] in ('instant','reservation','dispatch','bidding','offlineNegotiation') for r in level['rights']),'当前等级未开放任何接单类型',403)
            require(value or not any(o['stage'] in ('PICKUP','ARRIVED','TRIP') for o in st['orders']),'请完成行程后下线',409);st['online']=value
        elif action=='settings.update':
            if p.get('floating') is True:require(self.features(driver)['canFloating'],'当前等级或体验状态未开放悬浮提醒',403)
            if p.get('voice') is True:require(self.features(driver)['canVoice'],'当前等级或体验状态未开放订单语音',403)
            for k in ('voice','floating','largeText','instant','reservation','bidding'):
                if k in p: require(isinstance(p[k],bool),'设置值须为布尔值');st[k]=p[k]
            if 'range' in p:require(type(p['range']) is int and 1<=p['range']<=100,'范围须为1至100公里');st['range']=p['range']
        elif action in ('order.accept','order.bid'):
            o=self.order(st,p.get('id'));feature={'RESERVED':'reservation','BID':'bidding','PHONE':'dispatch'}.get(o['kind'],'instant');self.new_accept(driver,st,o,feature)
            if action=='order.bid':require(o['kind']=='BID','仅竞价订单可以报价');o['fare']=cents(p.get('amount'),'报价');o['stage']='BID_SENT';o['bidAt']=stamp()
            else:require(o['kind']!='BID','竞价订单请先报价');o['stage']='RESERVED' if o['kind']=='RESERVED' else 'PICKUP';st['todayAccepted']+=1
            result={'order':o}
        elif action=='order.selectBid':
            require(sandbox_actor,'乘客选择请在云端演示沙箱控制台触发，司机不能代替乘客选择',403)
            o=self.order(st,p.get('id'));require(o['stage']=='BID_SENT','订单不在待选择状态',409);self.assert_no_trip_conflict(driver_id=driver,exclude_order_id=o['id']);o['stage']='PREPAY';st['todayAccepted']+=1;result={'order':o,'sandbox':True}
        elif action=='order.advance':
            o=self.order(st,p.get('id'));self.validate_advance_stage(o,p);stage=o['stage'];require(stage in ('RESERVED','PICKUP','ARRIVED','TRIP'),'当前订单无法推进',409)
            if stage=='ARRIVED':require(str(p.get('phoneTail',''))==o['phoneTail'],'乘客手机尾号不正确')
            if stage=='TRIP':require(len(o.get('arrivedWaypoints',[]))>=len(o.get('waypoints',[])),'请先确认所有途经点')
            if stage=='RESERVED':self.assert_no_trip_conflict(driver_id=driver,exclude_order_id=o['id'])
            o['stage']={'RESERVED':'PICKUP','PICKUP':'ARRIVED','ARRIVED':'TRIP','TRIP':('DONE' if o['paid'] else 'WAIT_PAY')}[stage];o['updatedAt']=stamp();self.credit(driver,st,o);result={'order':o}
        elif action=='order.waypoint':
            o=self.order(st,p.get('id'));require(o['stage']=='TRIP','仅行程中可到达途经点');idx=p.get('index');require(type(idx) is int and 0<=idx<len(o.get('waypoints',[])),'途经点无效')
            if idx not in o['arrivedWaypoints']:o['arrivedWaypoints'].append(idx)
            result={'order':o}
        elif action=='order.pay':
            require(sandbox_actor,'乘客支付请在云端演示沙箱控制台触发，司机不能确认真实付款',403)
            o=self.order(st,p.get('id'));outcome=p.get('outcome','SUCCESS');require(outcome in ('SUCCESS','FAILED','CANCEL','PENDING'),'支付状态无效')
            if o['paid']:result={'order':o,'idempotent':True}
            else:
                require(o['stage'] in ('PREPAY','WAIT_PAY'),'当前订单不能支付',409);o['paymentStatus']={'SUCCESS':'SUCCESS','FAILED':'FAILED','CANCEL':'CANCELLED','PENDING':'PROCESSING'}[outcome]
                if outcome=='SUCCESS':
                    if o['stage']=='PREPAY':self.assert_no_trip_conflict(driver_id=driver,exclude_order_id=o['id'])
                    o['paid']=True;o['stage']=('TRIP' if o['kind']=='NEGOTIATED' else 'PICKUP') if o['stage']=='PREPAY' else 'DONE';self.credit(driver,st,o)
                result={'order':o,'sandbox':True}
        elif action=='order.cancel':
            o=self.order(st,p.get('id'));require(o['stage'] in ('AVAILABLE','BID_SENT','RESERVED','PICKUP','ARRIVED','PREPAY'),'当前订单不能取消',409);o['cancelReason']=textfield(p.get('reason','司机取消'),'取消原因',2,200)
            require(not o['paid'],'已支付订单退款需要人工审核，不能直接取消',409);o['stage']='CANCELLED';result={'order':o}
        elif action=='order.negotiate':
            self.require_negotiation_driver(driver,st)
            origin=textfield(p.get('origin'),'起点',2,100);destination=textfield(p.get('destination'),'终点',2,100)
            require(re.sub(r'\s+','',origin).casefold()!=re.sub(r'\s+','',destination).casefold(),'上下车地点名称不能相同')
            coord_keys=('originLat','originLng','destinationLat','destinationLng');has_coordinates=any(k in p for k in coord_keys)
            if has_coordinates:
                require(all(k in p for k in coord_keys),'请同时提供上下车点的四个坐标')
                for k in coord_keys:
                    bound=90 if k.endswith('Lat') else 180
                    require(type(p[k]) in (int,float) and -bound<=p[k]<=bound,'地图选点坐标无效',400,{'field':k})
                require(abs(p['originLat']-p['destinationLat'])>1e-7 or abs(p['originLng']-p['destinationLng'])>1e-7,'上下车坐标不能为同一地点')
                coordinates={k:p[k] for k in coord_keys}
            else:coordinates={'originLat':30.6687,'originLng':111.3226,'destinationLat':30.6645,'destinationLng':111.3694}
            o={'id':uid('NEG'),'kind':'NEGOTIATED','origin':origin,'destination':destination,'fare':cents(p.get('amount'),'议价金额'),'distance':'0.0','mileage':'待确认','time':'现在','stage':'PREPAY','remark':'云端演示线下议价，待沙箱乘客支付','phoneTail':'6758','credited':False,'paid':False,'city':profile['driver']['city'],'createdAt':stamp(),**coordinates,'coordinateSystem':'WGS84','coordinateSource':'USER_SELECTED' if has_coordinates else 'SYNTHETIC_DEFAULT','coordinatesSynthetic':not has_coordinates,'synthetic':True}
            st['orders'].insert(0,o);st['todayAccepted']+=1;result={'order':o}
        elif action=='wallet.password.code':result=self.code(a['mobile'],'PASSWORD')
        elif action in ('wallet.password.set','wallet.password.change','wallet.password.reset'):
            pw=check_password(p.get('password'))
            if action.endswith('.set'):require(not a['password_hash'],'已设置密码，请使用修改功能',409)
            elif action.endswith('.change'):require(secret_check(p.get('oldPassword',''),a['password_hash']),'原交易密码不正确',403)
            else:self.verify(a['mobile'],p.get('code',''),'PASSWORD')
            self.db.execute('UPDATE accounts SET password_hash=? WHERE id=?',(secret_hash(pw),driver));profile['wallet']['passwordSet']=profile['wallet']['hasPassword']=True;result={'passwordSet':True,'sandbox':True}
        elif action=='wallet.withdraw':
            amount=cents(p.get('amount'),'提现金额');require(secret_check(p.get('password',''),a['password_hash']),'交易密码未设置或不正确',403);require(amount<=st['balance'],'余额不足',409)
            bank=next((b for b in profile['bankCards'] if b['id']==p.get('bankCardId')),None);require(bank is not None,'请选择有效演示银行卡')
            require(sum(1 for w in profile['wallet']['withdrawals'] if w['createdAt'][:10]==stamp()[:10])<5,'云端演示每日最多5次提现演练',429)
            w={'id':uid('WD'),'amount':amount,'status':'PENDING','statusLabel':'云端演示沙箱审核中，冻结云端演示余额，不会银行打款','createdAt':stamp(),'bankName':bank['bankName'],'bankTail':bank['lastFour'],'sandbox':True};profile['wallet']['withdrawals'].insert(0,w);st['balance']-=amount;st['entries'].insert(0,{'title':'云端演示提现演练','amount':-amount,'note':'不进行真实银行打款','createdAt':stamp()});result={'withdrawal':w}
        elif action=='bank.add':
            num=str(p.get('number','')).replace(' ','');require(bool(re.fullmatch(r'9000\d{12,15}',num)),'仅接受9000开头16至19位云端演示演示卡号，请勿输入真实银行卡')
            h=hashlib.sha256(num.encode()).hexdigest();require(not any(b.get('numberHash')==h for b in profile['bankCards']),'该卡已绑定',409)
            b={'id':uid('BANK'),'bankName':textfield(p.get('bankName'),'银行名称',2,30),'numberMasked':num[:4]+' **** **** '+num[-4:],'number':num[:4]+' **** **** '+num[-4:],'lastFour':num[-4:],'numberHash':h,'holder':textfield(p.get('holder'),'演示持卡人',2,30),'isDefault':not profile['bankCards'],'sandbox':True};profile['bankCards'].append(b);result={'bankCard':{k:v for k,v in b.items() if k!='numberHash'}}
        elif action in ('bank.default','bank.remove'):
            b=next((b for b in profile['bankCards'] if b['id']==p.get('id')),None);require(b is not None,'银行卡不存在',404)
            if action=='bank.default':
                for c in profile['bankCards']:c['isDefault']=c['id']==b['id']
            else:
                profile['bankCards'].remove(b)
                if b['isDefault'] and profile['bankCards']:profile['bankCards'][0]['isDefault']=True
        elif action=='certificate.submit':
            typ=p.get('type');cert=next((c for c in profile['certificates'] if c['type']==typ),None);require(cert is not None,'证件类型无效')
            require(cert['status']!='PENDING','资质审核中，不能重复提交',409)
            if cert['status']=='APPROVED':require(cert['validUntil']!='LONG_TERM' and dt.date.fromisoformat(cert['validUntil'])<=now().date()+dt.timedelta(days=30),'未到临期更新窗口，当前证件仅支持查看',409)
            vf=p.get('validFrom');vu=p.get('validUntil');
            try: start=dt.date.fromisoformat(vf)
            except (ValueError,TypeError):raise ApiError(400,'请输入有效起始日期')
            require(start<=now().date(),'有效起始日期不能晚于今天')
            if vu=='LONG_TERM':require(typ!='insurance','保单不能设置长期')
            else:
                try:end=dt.date.fromisoformat(vu)
                except (ValueError,TypeError):raise ApiError(400,'请输入有效到期日期')
                require(end>start and end>now().date(),'证件到期日须晚于起始日与今天')
            file=self.save_image(driver,p.get('imageBase64'));back=self.save_image(driver,p.get('backImageBase64')) if typ=='license' else None
            cert.update(validFrom=vf,validUntil=vu,fileId=file,imageId=file,backFileId=back,status='PENDING',statusCode=3,submittedAt=stamp(),rejectionReason='',reply='')
            profile['driver']['certificationStatus']='PENDING';result={'certificate':cert,'fileId':file,'backFileId':back}
        elif action=='blacklist.add':
            tail=str(p.get('phoneTail',''));require(bool(re.fullmatch(r'\d{4}',tail)),'请输入4位演示乘客尾号');require(not any(b['phoneTail']==tail for b in profile['blacklist']),'该乘客已在黑名单',409)
            b={'id':uid('BL'),'phoneTail':tail,'reason':textfield(p.get('reason'),'原因',2,200),'createdAt':stamp()};profile['blacklist'].append(b);result={'blacklist':b}
        elif action=='blacklist.remove':
            require(any(b['id']==p.get('id') for b in profile['blacklist']),'黑名单记录不存在',404);profile['blacklist']=[b for b in profile['blacklist'] if b['id']!=p.get('id')]
        elif action=='feedback.submit':
            f={'id':uid('FB'),'content':textfield(p.get('content'),'反馈内容',5,2000),'status':'PENDING','reply':'','createdAt':stamp(),'attachmentFileIds':self.attachments(driver,p,3)};profile['feedback'].insert(0,f);result={'feedback':f}
        elif action=='order.complaint':
            o=self.order(st,p.get('id'));require(o['stage'] in ('DONE','CANCELLED','WAIT_PAY'),'请先完成或结束订单后投诉')
            f={'id':uid('CP'),'orderId':o['id'],'reason':textfield(p.get('reason'),'投诉类型',2,100),'content':textfield(p.get('content'),'投诉内容',5,2000),'status':'PENDING','reply':'','createdAt':stamp(),'attachmentFileIds':self.attachments(driver,p,9)};profile['complaints'].insert(0,f)
            if p.get('blockUser') is True and not any(b['phoneTail']==o['phoneTail'] for b in profile['blacklist']):profile['blacklist'].append({'id':uid('BL'),'phoneTail':o['phoneTail'],'reason':'投诉加入：'+f['reason'],'createdAt':stamp()})
            result={'complaint':f}
        elif action=='profile.update':
            allowed={'name','city'};require(set(p)<=allowed,'仅支持昵称和云端演示城市修改')
            if 'name'in p:profile['driver']['name']=textfield(p['name'],'昵称',2,30)
            if 'city'in p:
                require(not any(o['stage'] in ACTIVE for o in st['orders']),'进行中订单结束后才能切换服务城市',409);city=self.city(p['city']);profile['driver']['city']=city['id'];profile['driver']['cityName']=city['name'];st['online']=False
                for o in st['orders']:
                    if o['stage']=='AVAILABLE':o['city']=city['id']
        elif action=='location.report':
            location=self.trip_location(p);self.db.execute('UPDATE accounts SET location=? WHERE id=?',(dumps(location),driver));result={'location':location}
        elif action=='local.reset':
            require(not self.subscriptions(driver)['history'],'账户已有会员记录，不能清空交易历史',409)
            fresh=initial_state()
            for o in fresh['orders']:
                o.update(originLat=30.6687,originLng=111.3226,destinationLat=30.6645,destinationLng=111.3694,synthetic=True)
                o['distance']=o['distance'].replace('公里','');o['mileage']=o['mileage'].replace('公里','')
            st=fresh;profile=initial_profile(a['mobile']);profile['driver']['id']=driver
            self.db.execute('UPDATE accounts SET password_hash=?,trial_start=?,trial_completed=? WHERE id=?',('',stamp(),2,driver));self.db.execute('DELETE FROM files WHERE driver=?',(driver,));self.db.execute('DELETE FROM actions WHERE driver=?',(driver,))
        else:raise ApiError(404,'未知司机操作：'+str(action))
        # credit() can update points while completing a trip; avoid overwriting that profile update.
        if action in ('order.advance','order.pay'):
            profile=json.loads(self.account(driver)['profile'])
        self.save_state(driver,st);self.save_profile(driver,profile)
        if request_id:self.db.execute('INSERT INTO actions VALUES (?,?,?,?)',(driver,request_id,fingerprint,dumps(result)))
        if action!='location.report':self.audit('SANDBOX_PROVIDER' if sandbox_actor else 'DRIVER:'+driver,action,None,{'id':p.get('id'),'resultIds':[v.get('id') for v in result.values() if isinstance(v,dict)]})
        return self.driver_result_view(driver,result)
    def route(self,driver,oid):
        self.sync_shared_orders(driver)
        st=json.loads(self.account(driver)['state']);o=self.order(st,oid);a=[o['originLng'],o['originLat']];b=[o['destinationLng'],o['destinationLat']]
        coords=[a,[a[0]+(b[0]-a[0])*.3,a[1]+(b[1]-a[1])*.15],[a[0]+(b[0]-a[0])*.5,a[1]+(b[1]-a[1])*.6],b]
        return {'orderId':oid,'coordinates':coords,'source':'synthetic','coordinateSystem':'WGS84','notice':'云端演示合成路线，仅为界面示意，不能替代真实道路导航'}
    def fees(self,driver,oid):
        self.sync_shared_orders(driver)
        st=json.loads(self.account(driver)['state']);o=self.order(st,oid)
        return {'orderId':oid,'fare':o['fare'],'income':o['fare'],'paymentFee':0,'splitFee':0,'mapServiceFee':0,'serviceFee':0,'splitMode':'SANDBOX_NO_DEDUCTION','policyLabel':'云端演示沙箱：未接原计费策略，不收取服务费','sandbox':True}
