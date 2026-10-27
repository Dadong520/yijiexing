"""Opt-in one-time pairing of a private driver negotiation with a real passenger.
No provider calls, deployed configuration changes, or background work are performed.
"""
from urllib.parse import urlencode
from common import *


class NegotiationMixin:
    def _negotiation_init(self):
        # Avoid executescript: it commits a caller's transaction implicitly.
        self.db.execute('''CREATE TABLE IF NOT EXISTS negotiation_offers(
            code_hash TEXT PRIMARY KEY,driver TEXT NOT NULL,order_id TEXT NOT NULL,
            expires TEXT NOT NULL,status TEXT NOT NULL,passenger TEXT,created TEXT NOT NULL)''')
        self.db.execute('CREATE INDEX IF NOT EXISTS negotiation_offer_order ON negotiation_offers(driver,order_id)')

    def negotiation_private_locked(self,driver,oid):
        if not isinstance(oid,str):return False
        table=self.db.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='negotiation_offers'").fetchone()
        return bool(table and self.db.execute('SELECT 1 FROM negotiation_offers WHERE driver=? AND order_id=?',(driver,oid)).fetchone())

    def driver_negotiation_action(self,driver,action,p,request_id=None,sandbox_actor=False):
        require(not sandbox_actor,'议价配对只能由已登录司机操作',403)
        require(isinstance(request_id,str) and 1<=len(request_id)<=120,'议价配对操作必须提供requestId')
        self._negotiation_init()
        fp=hashlib.sha256(dumps({'action':action,'payload':p}).encode()).hexdigest()
        old=self.db.execute('SELECT * FROM actions WHERE driver=? AND request_id=?',(driver,request_id)).fetchone()
        if old:
            require(old['request_hash']==fp,'请求标识已被其他操作使用',409);return self.driver_result_view(driver,json.loads(old['result']))
        oid=p.get('id');require(isinstance(oid,str) and 1<=len(oid)<=120,'订单编号无效')
        if action=='order.pairOffer':
            require(self.shared_order(oid) is None,'此订单已配对，不能重新生成配对码',409)
            st=json.loads(self.account(driver)['state']);o=self.order(st,oid)
            require(o['kind']=='NEGOTIATED' and o['stage']=='PREPAY' and not o['paid'],'仅本人未支付的线下议价订单可生成配对码',409)
            cents(o['fare'],'已报价金额')
            code=secrets.token_urlsafe(24);expires=stamp(now()+dt.timedelta(minutes=5))
            self.db.execute("UPDATE negotiation_offers SET status='REVOKED' WHERE driver=? AND order_id=? AND status='ACTIVE'",(driver,oid))
            self.db.execute('INSERT INTO negotiation_offers VALUES (?,?,?,?,?,?,?)',(hashlib.sha256(code.encode()).hexdigest(),driver,oid,expires,'ACTIVE',None,stamp()))
            # The idempotency response contains the invitation so the same request
            # can recover a lost response. Storage is the existing private CAS DB;
            # neither invitation nor URI is written to application/audit logs.
            result={'orderId':oid,'pairCode':code,'pairUri':'yijiexing://negotiation?'+urlencode({'code':code}),'expiresAt':expires,'validitySeconds':300,'paymentMode':'SIMULATED'}
        elif action=='order.requote':
            o=self.shared_order(oid);require(o is not None and o.get('driverId')==driver,'议价订单不存在或不属于当前司机',404)
            require(o['kind']=='NEGOTIATED' and o['stage']=='PREPAY' and not o['paid'],'只有未付款的已配对议价订单可以重新报价',409)
            version=p.get('quoteVersion');require(type(version) is int and version==o.get('quoteVersion'),'报价版本已变化，请刷新后重试',409)
            amount=cents(p.get('amount'),'新报价');o['quoteVersion']=version+1;o['fare']=amount;o['negotiationStatus']='OFFERED';o['fareConfirmed']=False;o['paymentStatus']='UNPAID'
            o.setdefault('quoteHistory',[]).append({'version':o['quoteVersion'],'amount':amount,'createdAt':stamp(),'actor':'DRIVER'})
            self.save_shared(o,'DRIVER_REQUOTE');self.sync_shared_orders(driver);result={'order':self.order(json.loads(self.account(driver)['state']),oid)}
        else:raise ApiError(404,'未知司机议价配对操作')
        self.db.execute('INSERT INTO actions VALUES (?,?,?,?)',(driver,request_id,fp,dumps(result)))
        self.audit('DRIVER:'+driver,action,None,{'id':oid,'quoteVersion':result.get('order',{}).get('quoteVersion')})
        return self.driver_result_view(driver,result)

    def passenger_negotiation_action(self,pid,action,p):
        self._negotiation_init()
        if action=='negotiation.claim':
            code=p.get('pairCode');require(isinstance(code,str) and bool(re.fullmatch(r'[A-Za-z0-9_-]{32}',code)),'配对码无效',400)
            hashed=hashlib.sha256(code.encode()).hexdigest()
            offer=self.db.execute('SELECT * FROM negotiation_offers WHERE code_hash=?',(hashed,)).fetchone()
            require(offer is not None,'配对码无效或不可用',404)
            require(parse_time(offer['expires'])>now(),'配对码已过期，请司机重新生成',409)
            if offer['status']=='CLAIMED':
                require(offer['passenger']==pid,'配对码已经被使用',409)
                return {'order':self.passenger_view(self.own_passenger_order(pid,offer['order_id'])),'idempotent':True}
            require(offer['status']=='ACTIVE','配对码已撤销，请司机重新生成',409)
            self.require_passenger_trip_available(pid)
            driver=offer['driver'];oid=offer['order_id'];a=self.account(driver);st=json.loads(a['state']);o=self.order(st,oid)
            require(not o.get('shared') and self.shared_order(oid) is None,'此订单已被其他乘客认领',409)
            require(o['kind']=='NEGOTIATED' and o['stage']=='PREPAY' and not o['paid'],'此议价订单已结束或不可配对',409)
            self.require_negotiation_driver(driver,st,oid)
            self.assert_no_trip_conflict(driver_id=driver,passenger_id=pid,exclude_order_id=oid)
            require(self.passenger_driver_allowed(pid,driver),'你已屏蔽此司机，不能配对',403)
            profile=self.passenger_profile(pid)
            require(not any(b['phoneTail']==profile['mobile'][-4:] for b in json.loads(a['profile'])['blacklist']),'此司机不可与当前乘客配对',403)
            amount=cents(o['fare'],'报价')
            o=clone(o);o.pop('negotiationPairingRequired',None);o.pop('pairingExpiresAt',None);o.pop('distanceEnforcement',None);o.pop('distanceEnforcementNotice',None);o['remark']=str(o.get('remark','')).replace('待沙箱乘客支付','待已配对乘客确认并模拟支付');o.update(passengerId=pid,driverId=driver,shared=True,synthetic=False,paymentMode='SIMULATED',paymentStatus='UNPAID',paid=False,credited=False,
                phoneTail=profile['mobile'][-4:],rider={'id':None,'name':profile['name'],'mobileMasked':profile['mobile'][:3]+'****'+profile['mobile'][-4:]},
                estimatedFare=amount,fareSource='DRIVER_NEGOTIATED',fareConfirmed=False,destinationPending=False,negotiationStatus='OFFERED',quoteVersion=1,
                quoteHistory=[{'version':1,'amount':amount,'createdAt':stamp(),'actor':'DRIVER'}],priceDisputes=[],bids=[],evaluation=None,complaints=[],
                waypoints=o.get('waypoints',[]),arrivedWaypoints=o.get('arrivedWaypoints',[]),events=[{'type':'PASSENGER_NEGOTIATION_CLAIMED','at':stamp()}],updatedAt=stamp())
            self.db.execute('INSERT INTO passenger_orders VALUES (?,?,?,?,?)',(oid,pid,driver,dumps(o),o['createdAt']))
            st['orders']=[x for x in st['orders'] if x['id']!=oid];self.save_state(driver,st)
            self.db.execute("UPDATE negotiation_offers SET status='CLAIMED',passenger=? WHERE code_hash=? AND status='ACTIVE'",(pid,hashed))
            self.sync_shared_orders(driver)
            return {'order':self.passenger_view(o)}
        if action=='negotiation.dispute':
            o=self.own_passenger_order(pid,p.get('id'));require(o['kind']=='NEGOTIATED' and o['stage']=='PREPAY' and not o['paid'],'当前议价订单不能提出价格异议',409)
            version=p.get('quoteVersion');require(type(version) is int and version==o.get('quoteVersion'),'报价版本已变化，请刷新后重试',409)
            content=textfield(p.get('content'),'价格异议',2,500)
            o['negotiationStatus']='DISPUTED';o.setdefault('priceDisputes',[]).append({'quoteVersion':version,'content':content,'createdAt':stamp()})
            self.save_shared(o,'PASSENGER_PRICE_DISPUTE');return {'order':self.passenger_view(o)}
        raise ApiError(404,'未知乘客议价操作')

    def validate_negotiation_payment(self,o,p):
        if o['kind']!='NEGOTIATED':return
        version=p.get('quoteVersion');amount=p.get('amount')
        require(type(version) is int and version==o.get('quoteVersion') and type(amount) is int and amount==o['fare'],'议价版本或金额已变化，请核对最新报价后确认',409)
        require(o.get('negotiationStatus') in ('OFFERED','ACCEPTED'),'价格异议尚未解决，请等待司机重新报价',409)
