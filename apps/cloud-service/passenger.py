"""Passenger identities and authoritative shared orders for the isolated experience.
Legacy driver fixtures remain private; shared trips have exactly one canonical row.
All access occurs under Service.lock and the cloud snapshot CAS transaction.
"""
import math
from common import *
from passenger_extras import PassengerExtrasMixin
from order_options import PassengerOrderOptionsMixin
from negotiation import NegotiationMixin
from trip_distance import TripDistanceMixin

PASSENGER_MOBILES = ('13800000101', '13800000102', '13800000103')
PASSENGER_SCHEMA = '''
CREATE TABLE IF NOT EXISTS passengers(id TEXT PRIMARY KEY,mobile TEXT UNIQUE,profile TEXT);
CREATE TABLE IF NOT EXISTS passenger_sessions(token TEXT PRIMARY KEY,passenger TEXT,expires TEXT);
CREATE TABLE IF NOT EXISTS passenger_orders(id TEXT PRIMARY KEY,passenger TEXT,driver TEXT,payload TEXT,created TEXT);
CREATE INDEX IF NOT EXISTS passenger_orders_owner ON passenger_orders(passenger);
CREATE TABLE IF NOT EXISTS passenger_actions(passenger TEXT,request_id TEXT,request_hash TEXT,result TEXT,PRIMARY KEY(passenger,request_id));
'''
UNFINISHED = {'AVAILABLE','BID_SENT','PREPAY','RESERVED','PICKUP','ARRIVED','TRIP','WAIT_PAY','WAIT_CONTINUE'}

class PassengerMixin(TripDistanceMixin,NegotiationMixin,PassengerOrderOptionsMixin,PassengerExtrasMixin):
    def init_passenger(self):
        self.db.executescript(PASSENGER_SCHEMA)
    def passenger_mobile(self, mobile):
        require(isinstance(mobile,str) and mobile in getattr(self,'allowed_passenger_mobiles',PASSENGER_MOBILES),'此手机号未获乘客体验授权',403)
        return mobile
    def passenger_code(self,mobile):
        self.passenger_mobile(mobile)
        # Separate OTP namespace and validation from driver identities.
        code=f'{secrets.randbelow(1000000):06d}'
        self.db.execute('INSERT OR REPLACE INTO codes VALUES (?,?,?,?,?)',(mobile+':PASSENGER',secret_hash(code),stamp(now()+dt.timedelta(minutes=5)),0,'PASSENGER'))
        return {'verificationCode':code,'expirySeconds':300,'sandbox':True,'message':'体验验证码已生成；没有发送真实短信'}
    def passenger_login(self,p):
        mobile=self.passenger_mobile(p.get('mobile'));self.verify(mobile,p.get('code',''),'PASSENGER')
        row=self.db.execute('SELECT * FROM passengers WHERE mobile=?',(mobile,)).fetchone()
        if row:pid=row['id']
        else:
            pid=uid('P');profile={'id':pid,'mobile':mobile,'name':'体验乘客'+mobile[-2:],'city':'420500','cityName':'宜昌市','favorites':[],'feedback':[]}
            self.db.execute('INSERT INTO passengers VALUES (?,?,?)',(pid,mobile,dumps(profile)))
        token=secrets.token_urlsafe(32)
        self.db.execute('INSERT INTO passenger_sessions VALUES (?,?,?)',(token,pid,stamp(now()+dt.timedelta(days=7))))
        result=self.passenger_data(pid);result['token']=token;return result
    def passenger_authenticate(self,token):
        row=self.db.execute('SELECT * FROM passenger_sessions WHERE token=?',(token,)).fetchone()
        require(row is not None and parse_time(row['expires'])>now(),'乘客会话已失效，请重新登录',401)
        self.passenger_mobile(self.passenger_profile(row['passenger'])['mobile']);return row['passenger']
    def passenger_profile(self,pid):
        row=self.db.execute('SELECT * FROM passengers WHERE id=?',(pid,)).fetchone();require(row is not None,'乘客不存在',404)
        return json.loads(row['profile'])
    def shared_order(self,oid):
        require(oid is None or isinstance(oid,str),'订单编号格式无效')
        row=self.db.execute('SELECT payload FROM passenger_orders WHERE id=?',(oid,)).fetchone()
        return json.loads(row['payload']) if row else None
    def own_passenger_order(self,pid,oid):
        require(isinstance(oid,str) and 1<=len(oid)<=120,'订单编号无效')
        row=self.db.execute('SELECT payload FROM passenger_orders WHERE id=? AND passenger=?',(oid,pid)).fetchone()
        require(row is not None,'订单不存在或不属于当前乘客',404);return json.loads(row['payload'])
    def require_passenger_trip_available(self,pid):
        orders=[json.loads(r['payload']) for r in self.db.execute('SELECT payload FROM passenger_orders WHERE passenger=?',(pid,))]
        require(not any(o['stage']=='WAIT_PAY' and not o.get('paid') for o in orders),
            '请先支付已结束行程的欠款后再叫车',409,{'reason':'PASSENGER_OUTSTANDING_PAYMENT'})
        require(not any(o['stage'] in UNFINISHED for o in orders),'请先完成或取消当前订单',409)
    def order_fare_version(self,o):
        return o.get('fareVersion',0)
    def validate_fare_confirmation(self,o,p):
        supplied=('expectedFare' in p,'fareVersion' in p)
        if not any(supplied):
            require(not o.get('adjustments') and self.order_fare_version(o)==0,
                '订单费用已调整，请更新应用或刷新后重新确认金额',409,{'reason':'FARE_CONFIRMATION_REQUIRED'})
            return
        require(all(supplied),'请同时提供确认金额expectedFare和费用版本fareVersion')
        require(type(p['expectedFare']) is int and p['expectedFare']>=0 and type(p['fareVersion']) is int and p['fareVersion']>=0,'确认金额和费用版本须为非负整数')
        require(p['expectedFare']==o['fare'] and p['fareVersion']==self.order_fare_version(o),
            '订单费用或版本已变化，请刷新并重新确认',409,{'reason':'FARE_CONFIRMATION_STALE'})
    def project_driver_order(self,driver,order):
        if not isinstance(order,dict):return order
        o=clone(order)
        oid=o.get('id')
        is_shared=bool(o.get('shared') or (oid and self.shared_order(oid)))
        canonical=self.shared_order(oid) if (oid and is_shared) else None
        if is_shared:
            o=self.trip_order_view(o)
            for key in ('passengerId','bids','complaints'):o.pop(key,None)
            o.setdefault('fareVersion',self.order_fare_version(canonical or o))
            if canonical and canonical.get('driverId')==driver and canonical.get('stage')=='ARRIVED':
                o['phoneTail']=str(canonical.get('phoneTail') or o.get('phoneTail') or '')
            else:
                o['phoneTail']=''
            if isinstance(o.get('rider'),dict):
                r=dict(o['rider'])
                r.pop('mobile',None);r.pop('phone',None)
                if 'mobileMasked' in r or 'mobile' in o['rider']:r['mobileMasked']='已隐藏'
                o['rider']=r
            o.pop('mobile',None);o.pop('phone',None)
        ref=canonical if canonical else (o if not is_shared else None)
        assigned=bool(canonical and canonical.get('driverId')==driver) if is_shared else True
        if ref:
            fare=int(ref.get('fare') or 0)
            cur_refund=bool(ref.get('paymentStatus')=='REFUNDED' or (ref.get('refundAmount') or 0)>0)
            credited=bool(ref.get('credited'))
            paid=bool(ref.get('paid'))
            stage=ref.get('stage')
            if cur_refund and credited:
                status,label,net='REVERSED','已退款冲回',0
            elif cur_refund and not credited:
                status,label,net='REFUNDED','已退款 · 未入账',0
            elif credited and not cur_refund:
                status,label,net='CREDITED','已入账（模拟）',fare if assigned else 0
            elif stage=='DONE' and paid and not credited:
                status,label,net='PENDING_CREDIT','待入账（模拟）',0
            elif stage=='WAIT_PAY' and not paid:
                status,label,net='WAITING_PAYMENT','待乘客模拟支付',0
            elif stage in ('CANCELLED','WAIT_CONTINUE') and not credited:
                status,label,net='CLOSED_NO_INCOME','未计入收入',0
            else:
                status,label,net='WAITING_COMPLETION','待行程完成',0
        else:
            status,label,net='CLOSED_NO_INCOME','未计入收入',0
        o['settlementStatus']=status
        o['settlementLabel']=label
        o['netIncome']=int(net)
        o['settlementUpdatedAt']=stamp()
        return o

    def driver_result_view(self,driver,result):
        # Old idempotency rows may contain a pre-fix shared order snapshot.
        # Sanitize the response copy, keeping the driver's own complaint result.
        value=clone(result)
        order=value.get('order')
        if isinstance(order,dict):
            value['order']=self.project_driver_order(driver,order)
        return value
    def save_shared(self,o,event=None):
        self.refresh_distance_consent(o,persist=False)
        o['updatedAt']=stamp()
        if event:o['events'].append({'type':event,'at':stamp()})
        self.db.execute('UPDATE passenger_orders SET driver=?,payload=? WHERE id=?',(o.get('driverId'),dumps(o),o['id']))
    def driver_public(self,driver):
        a=self.account(driver);d=json.loads(a['profile'])['driver']
        return {'id':driver,'name':d['name'],'plate':d['plate'],'vehicleModel':d.get('vehicleModel',''),'mobileMasked':a['mobile'][:3]+'****'+a['mobile'][-4:]}
    def passenger_view(self,o):
        self.refresh_distance_consent(o)
        value=self.trip_order_view(o);value.pop('passengerId',None)
        value.setdefault('fareVersion',self.order_fare_version(o))
        value['driver']=self.driver_public(o['driverId']) if o.get('driverId') else None
        if value['driver']:
            # Location is available only during this passenger's active trip.
            a=self.account(o['driverId']);value['driver']['location']=json.loads(a['location']) if a['location'] and o['stage'] in ('PREPAY','RESERVED','PICKUP','ARRIVED','TRIP') else None
        return value
    def passenger_data(self,pid):
        self.expire_passenger_bids()
        profile=self.passenger_profile(pid)
        orders=[self.passenger_view(json.loads(r['payload'])) for r in self.db.execute('SELECT payload FROM passenger_orders WHERE passenger=? ORDER BY created DESC,rowid DESC',(pid,))]
        historical=[];seen=set()
        for o in orders:
            for prefix in ('origin','destination'):
                if prefix=='destination' and o.get('destinationPending'):continue
                key=(o[prefix],o[prefix+'Lat'],o[prefix+'Lng'])
                if key not in seen:historical.append({'address':key[0],'latitude':key[1],'longitude':key[2]});seen.add(key)
        regions=[]
        from seed import CITIES
        for city in CITIES:
            cfg,_,_=self.effective(city['id']);regions.append({'id':city['id'],'name':city['name'],'enabled':bool(cfg['systemEnabled'])})
        return {**self.passenger_extra_data(pid),'tripDistanceRules':self.trip_distance_rules(profile['city']),'historicalLocations':historical[:20],'regions':regions,'passenger':{k:v for k,v in profile.items() if k not in ('favorites','feedback')},'orders':orders,'favorites':profile['favorites'],'feedback':profile['feedback'],
            'notices':[{'title':'独立体验环境','content':'双端共享实际创建的订单；短信、支付、退款为模拟。路线仅为坐标示意。后台可查看和回复已保存投诉；实时人工客服、电话中继和推送未配置。'}],
            'features':{'payment':'SIMULATED','sms':'SIMULATED','route':'SCHEMATIC','driverLocation':True,'externalPush':False,'humanSupport':False},
            'environment':{'name':'宜捷行独立云体验环境','sandbox':True,'originalSpringBackend':False,'network':'Google Cloud独立体验服务，不连接原测试环境'},'serverTime':stamp()}
    def sync_shared_orders(self,driver):
        self.expire_passenger_bids()
        a=self.account(driver);st=json.loads(a['state']);city=json.loads(a['profile'])['driver']['city']
        private=[o for o in st['orders'] if not o.get('shared')]
        for value in private:
            value['distanceEnforcement']='SYNTHETIC_DEMO_EXEMPT'
            value['distanceEnforcementNotice']='司机私有合成演示单，无真实乘客确认；接送距离规则仅在共享真实订单执行。'
            # Pairing is opt-in. Restart recovery reveals only lock/expiry metadata,
            # never the one-time code, URI, or hashed invitation lookup value.
            value.pop('negotiationPairingRequired',None);value.pop('pairingExpiresAt',None)
            if value.get('kind')=='NEGOTIATED' and value.get('stage')=='PREPAY' and not value.get('paid') and self.negotiation_private_locked(driver,value.get('id')):
                offer=self.db.execute('SELECT expires FROM negotiation_offers WHERE driver=? AND order_id=? ORDER BY rowid DESC LIMIT 1',(driver,value['id'])).fetchone()
                value['negotiationPairingRequired']=True;value['pairingExpiresAt']=offer['expires']
        visible=[]
        for row in self.db.execute('SELECT payload FROM passenger_orders ORDER BY created DESC,rowid DESC'):
            o=json.loads(row['payload']);self.refresh_distance_consent(o);assigned=o.get('driverId');bid=next((b for b in o['bids'] if b['driverId']==driver),None)
            if assigned==driver or (not assigned and o['stage']=='AVAILABLE' and o['city']==city and self.passenger_driver_allowed(o['passengerId'],driver)):
                value=self.trip_order_view(o);value.pop('passengerId',None);value.pop('bids',None);value.pop('complaints',None)
                value.setdefault('fareVersion',self.order_fare_version(o))
                if bid and not assigned:value['stage']='BID_SENT';value['fare']=bid['amount'];value['bidAt']=bid['createdAt']
                # Keep the shipped driver's Stage.valueOf parser compatible while
                # the passenger retains the canonical continuation decision.
                if value['stage']=='WAIT_CONTINUE':value['stage']='CANCELLED'
                # Optional metadata for newer clients. Keep the old enum stage and
                # visibility unchanged; global continuation is not a driver stage.
                value['canonicalStage']=o['stage']
                value['driverParticipationStage']=value['stage']
                if value.get('destinationPending'):value['remark']='目的地待补充，费用待定（不是免费） · '+value['remark']
                visible.append(value)
        st['orders']=visible+private
        if dumps(st)!=a['state']:self.save_state(driver,st)
        return st
    def shared_credit(self,o):
        if o['stage']!='DONE' or o['credited']:return
        driver=o['driverId'];st=self.sync_shared_orders(driver)
        self.credit(driver,st,o);self.save_state(driver,st)
    def validate_point(self,lat,lng):
        require(type(lat) in (int,float) and math.isfinite(lat) and -90<=lat<=90 and type(lng) in (int,float) and math.isfinite(lng) and -180<=lng<=180,'地图坐标无效')
    def passenger_create_order(self,pid,p):
        profile=self.passenger_profile(pid)
        rider=self.passenger_rider(pid,p['riderId']) if p.get('riderId') else {'id':None,'name':profile['name'],'mobile':profile['mobile']}
        self.require_passenger_trip_available(pid)
        kind=p.get('kind','STANDARD');require(kind in ('STANDARD','RESERVED','BID'),'乘客订单类型无效')
        destination_pending=p.get('destinationPending',False);require(isinstance(destination_pending,bool),'无终点标记无效')
        require(not destination_pending or kind=='STANDARD','仅即时订单支持暂不填写目的地')
        if destination_pending:p={**p,'destination':'待补充目的地','destinationLat':p.get('originLat'),'destinationLng':p.get('originLng')}
        origin=textfield(p.get('origin'),'上车点',2,100);dest=textfield(p.get('destination'),'目的地',2,100)
        require(origin.casefold()!=dest.casefold(),'上车点和目的地不能相同')
        for prefix in ('origin','destination'):self.validate_point(p.get(prefix+'Lat'),p.get(prefix+'Lng'))
        require(destination_pending or abs(p['originLat']-p['destinationLat'])>1e-7 or abs(p['originLng']-p['destinationLng'])>1e-7,'起终点坐标不能相同')
        city=self.city(p.get('city',profile['city']))['id'];scheduled=p.get('scheduledAt')
        cfg,_,_=self.effective(city);require(cfg['systemEnabled'],'此区域暂停乘客下单',403)
        if kind=='RESERVED':require(now()+dt.timedelta(minutes=15)<=parse_time(scheduled)<=now()+dt.timedelta(days=30),'预约时间须在15分钟后至30天内')
        waypoints=p.get('waypoints',[]);require(isinstance(waypoints,list) and len(waypoints)<=2,'最多支持两个途经点')
        require(not destination_pending or not waypoints,'无终点订单暂不支持途经点')
        normalized=[]
        for i,w in enumerate(waypoints):
            require(isinstance(w,dict),'途经点格式无效');name=textfield(w.get('name'),'途经点',2,100);self.validate_point(w.get('latitude'),w.get('longitude'))
            normalized.append({'id':uid('WP'),'sortNo':i+1,'name':name,'latitude':w['latitude'],'longitude':w['longitude']})
        # Explicitly an experience estimate, not a production metered tariff.
        points=[(p['originLat'],p['originLng'])]+[(w['latitude'],w['longitude']) for w in normalized]+[(p['destinationLat'],p['destinationLng'])]
        distance=0
        for a,b in zip(points,points[1:]):
            lat1,lat2=map(math.radians,(a[0],b[0]));dl=math.radians(b[0]-a[0]);dn=math.radians(b[1]-a[1]);h=math.sin(dl/2)**2+math.cos(lat1)*math.cos(lat2)*math.sin(dn/2)**2
            distance+=6371*2*math.asin(min(1,math.sqrt(max(0,h))))
        require(distance<=300,'体验行程须在300公里范围内')
        estimate=max(800,int(round((8+max(0,distance*1.3-2)*2.4)*100)))
        if destination_pending:estimate=0
        o={'id':uid('PORD'),'passengerId':pid,'driverId':None,'kind':kind,'origin':origin,'destination':dest,'city':city,'stage':'AVAILABLE','fare':estimate,'estimatedFare':estimate,'destinationPending':destination_pending,'fareConfirmed':False,
            'originLat':p['originLat'],'originLng':p['originLng'],'destinationLat':p['destinationLat'],'destinationLng':p['destinationLng'],'coordinateSystem':'WGS84','coordinatesSynthetic':False,
            'distance':'1.0','mileage':f'{distance*1.3:.1f}','time':scheduled if kind=='RESERVED' else '现在','scheduledAt':scheduled if kind=='RESERVED' else None,
            'remark':textfield(p.get('remark') or '乘客实际创建 · 独立体验订单','备注',1,500),'phoneTail':rider['mobile'][-4:],'rider':{'id':rider['id'],'name':rider['name'],'mobileMasked':rider['mobile'][:3]+'****'+rider['mobile'][-4:]},'paid':False,'credited':False,'paymentStatus':'UNPAID',
            'paymentMode':'SIMULATED','fareSource':'PENDING_DESTINATION' if destination_pending else 'DEMO_ESTIMATE','pickupDistanceSource':'DEMO_DEFAULT','shared':True,'synthetic':False,'createdAt':stamp(),'updatedAt':stamp(),
            'waypoints':normalized,'arrivedWaypoints':[],'bids':[],'evaluation':None,'complaints':[],'expiresAt':stamp(now()+dt.timedelta(minutes=10)) if kind=='BID' else None,'events':[{'type':'PASSENGER_CREATED','at':stamp()}]}
        self.db.execute('INSERT INTO passenger_orders VALUES (?,?,?,?,?)',(o['id'],pid,None,dumps(o),stamp()));return {'order':self.passenger_view(o)}
    def passenger_action(self,pid,action,p,request_id=None):
        self.expire_passenger_bids()
        require(isinstance(action,str) and 1<=len(action)<=80,'操作名称无效');require(isinstance(p,dict),'操作参数须为JSON对象')
        require(isinstance(request_id,str) and 1<=len(request_id)<=120,'乘客操作必须提供有效requestId')
        fp=hashlib.sha256(dumps({'action':action,'payload':p}).encode()).hexdigest()
        old=self.db.execute('SELECT * FROM passenger_actions WHERE passenger=? AND request_id=?',(pid,request_id)).fetchone()
        if old:
            require(old['request_hash']==fp,'请求标识已被其他操作使用',409);return json.loads(old['result'])
        profile=self.passenger_profile(pid);result={}
        if action=='order.create':result=self.passenger_create_order(pid,p)
        elif action=='order.distanceConsent':result=self.passenger_distance_consent(pid,p)
        elif action=='location.report':result=self.passenger_report_location(pid,p)
        elif action in ('negotiation.claim','negotiation.dispute'):result=self.passenger_negotiation_action(pid,action,p)
        elif action in ('order.blockDriver','order.unblockDriver','order.remark','order.complaint.attach'):result=self.passenger_order_option(pid,action,p)
        elif action.startswith('order.'):
            o=self.own_passenger_order(pid,p.get('id'))
            if action=='order.selectDriver':
                require(o['kind']=='BID' and o['stage']=='AVAILABLE' and not o.get('driverId'),'当前订单不能选择司机',409)
                bid=next((b for b in o['bids'] if b['driverId']==p.get('driverId')),None);require(bid is not None,'此司机尚未报价',409)
                driver=bid['driverId'];require(self.passenger_driver_allowed(pid,driver),'此司机已被屏蔽',403);st=self.sync_shared_orders(driver);projection=self.order(st,o['id'])
                # Other offers cannot consume this driver's availability after selection.
                require(not any(x['stage'] in ('PREPAY','RESERVED','PICKUP','ARRIVED','TRIP') and x['id']!=o['id'] for x in st['orders']),'司机已有其他进行中订单',409)
                require(st['online'],'司机已下线，请重新选择',409)
                eligibility_state={**st,'orders':[x for x in st['orders'] if x['id']!=o['id']]}
                self.new_accept(driver,eligibility_state,{**o,'stage':'AVAILABLE'},'bidding')
                o.update(driverId=driver,fare=bid['amount'],stage='PREPAY');st['todayAccepted']+=1;self.save_state(driver,st)
            elif action=='order.pay':
                outcome=p.get('outcome','SUCCESS');require(outcome in ('SUCCESS','FAILED','PENDING','CANCEL'),'支付结果无效')
                self.validate_negotiation_payment(o,p)
                if o['kind']!='NEGOTIATED':self.validate_fare_confirmation(o,p)
                if o['paid']:return self.record_passenger_action(pid,request_id,fp,{'order':self.passenger_view(o),'idempotent':True})
                require(o['stage'] in ('PREPAY','WAIT_PAY'),'当前订单不能支付',409)
                o['paymentStatus']={'SUCCESS':'SUCCESS','FAILED':'FAILED','PENDING':'PROCESSING','CANCEL':'CANCELLED'}[outcome]
                if outcome=='SUCCESS':
                    o['paid']=True;o['paidAt']=stamp();o['stage']='PICKUP' if o['stage']=='PREPAY' else 'DONE'
                    if o['kind']=='NEGOTIATED':o.update(negotiationStatus='ACCEPTED',fareConfirmed=True,fareConfirmedAt=stamp())
                    self.shared_credit(o)
            elif action=='order.cancel':
                require(o['stage'] in ('AVAILABLE','PREPAY','RESERVED','PICKUP','ARRIVED','WAIT_CONTINUE'),'当前订单不能取消',409)
                o['cancelReason']=textfield(p.get('reason'),'取消原因',2,200);o['cancelledBy']='PASSENGER';o['stage']='CANCELLED'
                if o['paid']:o.update(paid=False,paymentStatus='REFUNDED',refundAmount=o['fare'],refundedAt=stamp(),refundMode='SIMULATED')
            elif action=='order.update':
                require(o['stage'] in ('AVAILABLE','PREPAY','RESERVED','PICKUP','ARRIVED'),'当前阶段不能修改订单',409)
                require(set(p)<={'id','remark','destination','destinationLat','destinationLng'},'不支持修改这些订单字段')
                if 'remark' in p:o['remark']=textfield(p['remark'],'备注',1,500)
                if any(k in p for k in ('destination','destinationLat','destinationLng')):
                    require((o['stage']=='AVAILABLE' and not o['bids']) or (o.get('destinationPending') and o['kind']=='STANDARD' and o['stage'] in ('PICKUP','ARRIVED')),'司机接单或报价后不能改变已约定目的地及估价，请取消后重新下单',409)
                    destination=textfield(p.get('destination'),'目的地',2,100);self.validate_point(p.get('destinationLat'),p.get('destinationLng'))
                    require(destination.casefold()!=o['origin'].casefold(),'起终点名称不能相同')
                    require(abs(p['destinationLat']-o['originLat'])>1e-7 or abs(p['destinationLng']-o['originLng'])>1e-7,'起终点坐标不能相同')
                    points=[(o['originLat'],o['originLng'])]+[(w['latitude'],w['longitude']) for w in o['waypoints']]+[(p['destinationLat'],p['destinationLng'])]
                    distance=0
                    for a,b in zip(points,points[1:]):
                        la,lb=map(math.radians,(a[0],b[0]));h=math.sin(math.radians(b[0]-a[0])/2)**2+math.cos(la)*math.cos(lb)*math.sin(math.radians(b[1]-a[1])/2)**2
                        distance+=12742*math.asin(min(1,math.sqrt(max(0,h))))
                    require(distance<=300,'体验行程须在300公里内');estimate=max(800,int(round((8+max(0,distance*1.3-2)*2.4)*100)))
                    o.update(destination=destination,destinationLat=p['destinationLat'],destinationLng=p['destinationLng'],estimatedFare=estimate,fare=estimate,mileage=f'{distance*1.3:.1f}',destinationPending=False,fareSource='DEMO_ESTIMATE')
            elif action=='order.continue':
                require(o['stage']=='WAIT_CONTINUE','此订单不在等待继续叫车状态',409)
                decision=p.get('type',1);require(type(decision) is int and decision in (1,2),'继续选择须为1继续或2取消')
                if decision==2:o.update(stage='CANCELLED',cancelledBy='PASSENGER',cancelReason='乘客取消继续叫车')
                else:
                    o.setdefault('previousDrivers',[]).append(o.get('driverId'))
                    if o.get('refundAmount'):o.setdefault('refundHistory',[]).append({'amount':o.pop('refundAmount'),'at':o.pop('refundedAt',None),'driverId':o.get('driverId'),'mode':'SIMULATED'})
                    o.pop('paidAt',None);o.pop('refundMode',None)
                    o.update(stage='AVAILABLE',driverId=None,bids=[],paid=False,credited=False,paymentStatus='UNPAID',fare=o['estimatedFare'],fareConfirmed=False,arrivedWaypoints=[],cancelledBy=None,cancelReason=None)
                    if o['kind']=='BID':o['expiresAt']=stamp(now()+dt.timedelta(minutes=10))
            elif action=='order.evaluate':
                require(o['stage']=='DONE','完成行程后才可评价',409);rating=p.get('rating');require(type(rating) is int and 1<=rating<=5,'评价分数须为1到5')
                content=textfield(p.get('content') or '','评价内容',0,1000)
                if o['evaluation']:require(o['evaluation']['rating']==rating and o['evaluation']['content']==content,'此订单已评价',409)
                else:o['evaluation']={'rating':rating,'content':content,'createdAt':stamp()}
            elif action=='order.complaint':
                require(o['stage'] in ('DONE','CANCELLED','WAIT_PAY'),'结束订单后可投诉',409)
                reason=textfield(p.get('reason'),'投诉类型',2,100);content=textfield(p.get('content'),'投诉内容',5,2000)
                if not any(c['reason']==reason and c['content']==content for c in o['complaints']):o['complaints'].append({'id':uid('PC'),'reason':reason,'content':content,'status':'PENDING','reply':'','createdAt':stamp()})
            else:raise ApiError(404,'未知乘客订单操作')
            self.save_shared(o,'PASSENGER_'+action.split('.')[1].upper());result={'order':self.passenger_view(o)}
        elif action=='profile.update':
            require(set(p)<={'name','city'},'仅支持昵称和城市修改')
            if 'name' in p:profile['name']=textfield(p['name'],'昵称',2,30)
            if 'city' in p:
                require(not any(o['stage'] in UNFINISHED for o in self.passenger_data(pid)['orders']),'进行中订单结束后才能更换城市',409)
                city=self.city(p['city']);profile.update(city=city['id'],cityName=city['name'])
        elif action=='favorite.save':
            old=next((x for x in profile['favorites'] if x['id']==p.get('id')),None)
            if p.get('id'):require(old is not None,'常用地址不存在',404)
            require(old or len(profile['favorites'])<20,'最多保存20个常用地址')
            self.validate_point(p.get('latitude'),p.get('longitude'));f={'id':old['id'] if old else uid('FAV'),'label':textfield(p.get('label'),'地址名称',1,30),'address':textfield(p.get('address'),'地址',2,100),'latitude':p['latitude'],'longitude':p['longitude']}
            profile['favorites']=[x for x in profile['favorites'] if x['id']!=f['id']]+[f];result={'favorite':f}
        elif action=='favorite.remove':
            require(any(x['id']==p.get('id') for x in profile['favorites']),'常用地址不存在',404);profile['favorites']=[x for x in profile['favorites'] if x['id']!=p['id']]
        elif action=='feedback.submit':
            f={'id':uid('PF'),'content':textfield(p.get('content'),'反馈内容',5,2000),'status':'PENDING','reply':'','createdAt':stamp()};profile['feedback'].insert(0,f);result={'feedback':f}
        else:result=self.passenger_extra_action(pid,action,p)
        self.db.execute('UPDATE passengers SET profile=? WHERE id=?',(dumps(profile),pid))
        if action!='location.report':self.audit('PASSENGER:'+pid,action,None,{'id':p.get('id')})
        return self.record_passenger_action(pid,request_id,fp,result)
    def record_passenger_action(self,pid,rid,fp,result):
        self.db.execute('INSERT INTO passenger_actions VALUES (?,?,?,?)',(pid,rid,fp,dumps(result)));return result
    def shared_driver_action(self,driver,action,p,request_id=None,sandbox_actor=False):
        require(not sandbox_actor,'共享乘客订单必须由真实乘客操作，禁止沙箱代替',403)
        fp=hashlib.sha256(dumps({'action':action,'payload':p}).encode()).hexdigest()
        if request_id:
            old=self.db.execute('SELECT * FROM actions WHERE driver=? AND request_id=?',(driver,request_id)).fetchone()
            if old:require(old['request_hash']==fp,'请求标识被其他操作占用',409);return self.driver_result_view(driver,json.loads(old['result']))
        st=self.sync_shared_orders(driver);projected=self.order(st,p.get('id'));o=self.shared_order(p['id']);complaint_result=None
        if action in ('order.accept','order.bid'):
            feature={'BID':'bidding','RESERVED':'reservation'}.get(o['kind'],'instant')
            self.new_accept(driver,st,projected,feature)
            require(not o.get('driverId') and o['stage']=='AVAILABLE','订单已被其他司机接单',409)
            if action=='order.bid':
                require(o['kind']=='BID','仅竞价订单可以报价');amount=cents(p.get('amount'),'报价')
                info=self.driver_public(driver);o['bids'].append({'driverId':driver,'name':info['name'],'plate':info['plate'],'vehicleModel':info['vehicleModel'],'amount':amount,'createdAt':stamp()})
            else:
                require(o['kind']!='BID','竞价订单请报价');o['driverId']=driver;o['stage']='RESERVED' if o['kind']=='RESERVED' else 'PICKUP';st['todayAccepted']+=1
        else:
            require(o.get('driverId')==driver or (action=='order.cancel' and any(b['driverId']==driver for b in o['bids'])),'此订单不属于当前司机',403)
            if action=='order.advance':
                self.validate_advance_stage(o,p);stage=o['stage'];require(stage in ('RESERVED','PICKUP','ARRIVED','TRIP'),'当前订单不能推进',409)
                if stage=='ARRIVED':
                    require(not o.get('destinationPending'),'请等待乘客补充目的地后再开始行程',409)
                    require(str(p.get('phoneTail',''))==o['phoneTail'],'乘客手机尾号不正确')
                if stage=='TRIP':require(len(o['arrivedWaypoints'])==len(o['waypoints']),'请先确认全部途经点',409)
                self.gate_shared_advance(o,p)
            elif action=='order.waypoint':
                require(o['stage']=='TRIP','仅行程中可确认途经点',409);i=p.get('index');require(type(i) is int and 0<=i<len(o['waypoints']),'途经点序号无效')
                if i not in o['arrivedWaypoints']:o['arrivedWaypoints'].append(i)
            elif action=='order.cancel':
                reason=textfield(p.get('reason','司机取消'),'取消原因',2,200)
                if o['stage']=='AVAILABLE' and not o.get('driverId'):o['bids']=[b for b in o['bids'] if b['driverId']!=driver]
                else:
                    require(o['stage'] in ('PREPAY','RESERVED','PICKUP','ARRIVED'),'当前订单不能取消',409);o.update(stage='CANCELLED' if o['kind']=='NEGOTIATED' else 'WAIT_CONTINUE',cancelReason=reason,cancelledBy='DRIVER')
                    if o['paid']:o.update(paid=False,paymentStatus='REFUNDED',refundAmount=o['fare'],refundedAt=stamp(),refundMode='SIMULATED')
            elif action in ('order.pay','order.selectBid'):raise ApiError(403,'司机不能代替乘客支付或选择司机')
            elif action=='order.complaint':
                require(o['stage'] in ('DONE','CANCELLED','WAIT_PAY','WAIT_CONTINUE'),'结束订单后可投诉')
                profile=json.loads(self.account(driver)['profile']);f={'id':uid('CP'),'orderId':o['id'],'reason':textfield(p.get('reason'),'投诉类型',2,100),'content':textfield(p.get('content'),'投诉内容',5,2000),'status':'PENDING','reply':'','createdAt':stamp(),'attachmentFileIds':self.attachments(driver,p,9)}
                profile['complaints'].insert(0,f)
                if p.get('blockUser') is True and not any(b['phoneTail']==o['phoneTail'] for b in profile['blacklist']):
                    profile['blacklist'].append({'id':uid('BL'),'phoneTail':o['phoneTail'],'reason':'投诉加入：'+f['reason'],'createdAt':stamp()})
                self.save_profile(driver,profile);complaint_result=f
            else:raise ApiError(403,'共享乘客订单不支持此司机操作')
        self.save_state(driver,st);self.shared_credit(o);self.save_shared(o,'DRIVER_'+action.split('.')[1].upper());self.sync_shared_orders(driver)
        result={'order':self.order(json.loads(self.account(driver)['state']),o['id'])} if any(x['id']==o['id'] for x in json.loads(self.account(driver)['state'])['orders']) else {'orderId':o['id'],'withdrawn':True}
        if complaint_result is not None:result['complaint']=complaint_result
        if request_id:self.db.execute('INSERT INTO actions VALUES (?,?,?,?)',(driver,request_id,fp,dumps(result)))
        self.audit('DRIVER:'+driver,action,None,{'id':o['id'],'shared':True});return self.driver_result_view(driver,result)
    def passenger_route(self,pid,oid):
        o=self.own_passenger_order(pid,oid)
        return {'orderId':oid,'coordinates':[[o['originLng'],o['originLat']]]+[[w['longitude'],w['latitude']] for w in o['waypoints']]+[[o['destinationLng'],o['destinationLat']]],'destinationPending':o.get('destinationPending',False),'source':'synthetic','coordinateSystem':'WGS84','notice':'坐标连线示意，未接实时路径规划，不能替代道路导航'}
    def passenger_fees(self,pid,oid):
        o=self.own_passenger_order(pid,oid)
        return {'orderId':oid,'fare':o['fare'],'estimatedFare':o['estimatedFare'],'paymentFee':0,'serviceFee':0,'paid':o['paid'],'paymentStatus':o['paymentStatus'],'refundAmount':o.get('refundAmount',0),'sandbox':True,'policyLabel':'目的地待补充，费用未确定' if o.get('destinationPending') else '体验估价或选定报价；模拟支付，不收取真实款项'}
