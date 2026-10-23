from common import *
from seed import CITIES, RIGHTS, PARAMS, default_config

class BenefitsMixin:
    def config_row(self,scope,target):
        row=self.db.execute('SELECT * FROM configs WHERE scope=? AND target=?',(scope,target)).fetchone()
        require(row is not None,'配置目标不存在',404)
        return row
    def city(self,city):
        c=next((c for c in CITIES if c['id']==city),None)
        require(c is not None,'城市不在云端演示字典中',400)
        return c
    def effective(self,city):
        c=self.city(city); rows=[self.config_row('national','CN'),self.config_row('region',c['region']),self.config_row('city',city)]
        result={}; sources={}; version=[]
        def merge(dest,patch,path,source):
            for k,v in patch.items():
                full=f'{path}.{k}' if path else k
                if isinstance(v,dict):
                    if not isinstance(dest.get(k),dict): dest[k]={}
                    merge(dest[k],v,full,source)
                else: dest[k]=clone(v);sources[full]=source
        for r in rows:
            merge(result,json.loads(r['published']), '', {'scope':r['scope'],'target':r['target'],'version':r['version'],'label':{'national':'全国默认','region':c['regionName'],'city':c['name']}[r['scope']]})
            version.append(f'{r["scope"]}:{r["target"]}:{r["version"]}')
        return result,sources,'|'.join(version)
    def quota_label(self,q):
        if q['type']=='UNLIMITED': return '完全免费不限额（云端演示模拟）'
        if q['type']=='AMOUNT': return f'每月免费{q["freeAmount"]/100:g}元，超额{q["overagePrice"]/100:g}元/次'
        return f'每月{q["freeOrders"]}单免费，超额{q["overagePrice"]/100:g}元/单'
    def level_payload(self,code,config,sources,version):
        v=clone(config['levels'][code]);p=v['params'];q=clone(p['mapQuota']);q['label']=self.quota_label(q)
        rights=[]
        for key,label,group in RIGHTS:
            enabled=v['rights'].get(key,False)
            if key=='offlineNegotiation': enabled=enabled and p['offlineNegotiation']
            if key=='voice':enabled=enabled and p.get('voiceEnabled',True)
            if key=='map': val=q['label']
            elif key=='nearby': val=f'{p["nearbyKm"]:g}公里'
            elif key=='voice': val=f'{p["voiceCount"]}次 / {p["voiceSpeed"]:g}倍速'
            else: val='支持' if enabled else '暂不开放'
            src=sources.get(f'levels.{code}.rights.{key}',{'label':'全国默认'})
            if key in ('nearby','map','voice','offlineNegotiation'):
                par={'nearby':'nearbyKm','map':'mapQuota.freeOrders','voice':'voiceCount','offlineNegotiation':'offlineNegotiation'}[key]
                src=sources.get(f'levels.{code}.params.{par}',src)
            rights.append({'key':key,'label':label,'group':group,'enabled':bool(enabled),'value':val,'source':src['label']})
        v.update(implementedRightsCount=len(rights),dictionaryIncomplete=True,rightsCountNotice='56/62为原型标称，云端演示已实现25项核心权益，全量字典待确认',rights=rights,mapQuota=q,version=version,source=sources.get(f'levels.{code}.price',{'label':'全国默认'})['label'],saleEnabled=bool(v.get('saleEnabled',False) and v.get('enabled',True) and config['systemEnabled']),sandbox=True)
        return v
    def trial_status(self,account,config):
        start=parse_time(account['trial_start']); elapsed=max(0,(now()-start).total_seconds());used=int(elapsed//86400)
        days=config['trialDays']; limit=config['trialOrders']; completed=account['trial_completed']
        expired=elapsed>=days*86400 or completed>=limit
        reason='ORDER_LIMIT' if completed>=limit else ('DAYS_LIMIT' if expired else '')
        return {'status':'ENDED' if expired else 'ACTIVE','startAt':account['trial_start'],'endAt':stamp(start+dt.timedelta(days=days)),'daysTotal':days,'daysUsed':used,'daysRemaining':max(0,days-used),'ordersLimit':limit,'ordersCompleted':completed,'ordersRemaining':max(0,limit-completed),'endReason':reason,'reason':{'ORDER_LIMIT':'完成订单达到体验上限','DAYS_LIMIT':'体验天数已结束','':'体验中'}[reason],'policy':'全账户首次云端演示登录开始；仅已完成且支付订单计数；取消单不计，跨城不重置。'}
    def subscriptions(self,driver):
        subs=[json.loads(r['payload']) for r in self.db.execute('SELECT payload FROM subscriptions WHERE driver=? ORDER BY created DESC',(driver,))]
        for s in subs:
            if s['status']=='ACTIVE' and parse_time(s['endAt'])<=now():
                s['status']='EXPIRED';s['autoRenew']=False;s['nextChargeAt']=None
                self.db.execute('UPDATE subscriptions SET payload=? WHERE id=?',(dumps(s),s['id']))
            s['daysRemaining']=max(0,int((parse_time(s['endAt'])-now()).total_seconds()+86399)//86400)
        orders=[json.loads(r['payload']) for r in self.db.execute('SELECT payload FROM member_orders WHERE driver=? ORDER BY created DESC',(driver,))]
        invoices=[json.loads(r['payload']) for r in self.db.execute('SELECT payload FROM invoices WHERE driver=? ORDER BY created DESC',(driver,))]
        active=next((s for s in subs if s['status']=='ACTIVE'),None)
        return {'active':active,'activeSubscriptions':[s for s in subs if s['status']=='ACTIVE'],'history':subs,'orders':orders,'invoices':invoices,'sandbox':True}
    def catalog(self,driver,city=None):
        a=self.account(driver);city=city or json.loads(a['profile'])['driver']['city'];c=self.city(city)
        config,sources,version=self.effective(city);sublist=self.subscriptions(driver)
        sub=next((s for s in sublist['activeSubscriptions'] if s['city']==city),None)
        trial=self.trial_status(a,config)
        return {'city':c,'cities':CITIES,'version':version,'source':sources.get('levels.NORMAL.price',{'label':'全国默认'})['label'],'sources':sources,'levels':[self.level_payload(l,config,sources,version) for l in ('TRIAL','NORMAL','VIP')],'trial':trial,'subscription':sub,'active':sub,'subscriptions':sublist['history'],'orders':sublist['orders'],'invoices':sublist['invoices'],'currentLevel':sub['level'] if sub else ('TRIAL' if trial['status']=='ACTIVE' else 'EXPIRED'),'canAccept':bool(sub or (trial['status']=='ACTIVE' and config['systemEnabled'] and config['levels']['TRIAL']['enabled'])),'serverTime':stamp(),'sandbox':True,'notice':'云端演示隔离服务；支付、发票和续费均为沙箱状态，不收款或扣款。','pendingPolicies':['在期订阅不重复购买；提前续费与升级折算尚未开放','首年优惠资格未确认，不自动使用','升级与剩余价值折算未确认，暂不开放升级','订阅限购买城市；跨城规则待业务确认','未接支付机构，续费开关仅记录云端演示意愿，不安排实际扣款']}
    def check_right(self,driver,key,city=None):
        cat=self.catalog(driver,city)
        require(cat['canAccept'],'体验已结束或当前城市等级未开放新增接单；历史订单、钱包、提现、资质与客服仍可使用',403,{'reason':'TRIAL_ENDED' if cat['trial']['status']=='ENDED' else 'LEVEL_DISABLED','trial':cat['trial']})
        sub=cat['subscription']; level=sub['rightsSnapshot'] if sub else next(l for l in cat['levels'] if l['id']=='TRIAL')
        right=next((r for r in level['rights'] if r['key']==key),None)
        require(right is not None and right['enabled'],'当前等级或所在城市未开放此权益',403,{'feature':key,'city':cat['city']['id']})
        # Regional prohibitions apply even if a subscription's level snapshot permits it.
        if key=='offlineNegotiation':
            config,_,_=self.effective(cat['city']['id'])
            require(config['levels'][level['id']]['params']['offlineNegotiation'],'该城市不开放线下议价订单',403)
        return cat
    def create_member_order(self,driver,p):
        city=str(p.get('city',''));code=p.get('level');require(code in ('NORMAL','VIP'),'仅普通与VIP支持年付开通')
        require(p.get('agree') is True,'请先阅读并同意会员服务协议')
        require(p.get('autoRenew',False) in (True,False),'自动续费状态错误')
        method=p.get('paymentMethod','WECHAT');require(method in ('WECHAT','ALIPAY'),'仅支持微信或支付宝沙箱方式')
        key=textfield(p.get('idempotencyKey'),'幂等标识',1,100)
        existing=self.db.execute('SELECT payload FROM member_orders WHERE driver=? AND idem=?',(driver,key)).fetchone()
        identity={k:p.get(k) for k in ('city','level','version','agree','autoRenew','paymentMethod')}
        if existing:
            o=json.loads(existing['payload']);require(o['requestIdentity']==identity,'该幂等标识已用于不同订单',409)
            return {'order':o,'idempotent':True}
        cat=self.catalog(driver,city);require(p.get('version')==cat['version'],'价格配置已变化，请刷新并重新确认',409,{'version':cat['version']})
        lev=next(l for l in cat['levels'] if l['id']==code);require(lev['saleEnabled'],'该城市等级暂未开放购买',403)
        sub=cat['subscription'];require(sub is None,'当前城市已有在期订阅，当前权益保留至到期；提前续费与升级折算尚未开放，请到期后购买',409)
        o={'id':uid('MO'),'city':city,'cityName':cat['city']['name'],'level':code,'levelName':lev['name'],'amount':lev['price'],'originalAmount':lev['price'],'discount':0,'status':'PENDING','entitlementsStatus':'PENDING','startAt':None,'endAt':None,'autoRenew':p.get('autoRenew',False),'paymentMethod':method,'version':cat['version'],'rightsSnapshot':clone(lev),'createdAt':stamp(),'sandbox':True,'requestIdentity':identity,'period':'YEAR','periodLabel':'年付365天','agreementVersion':'local-membership-v1','trialPolicy':cat['trial']['policy']}
        self.db.execute('INSERT INTO member_orders VALUES (?,?,?,?,?)',(o['id'],driver,key,dumps(o),stamp()))
        self.audit('DRIVER:'+driver,'membership.create',None,o)
        return {'order':o}
    def member_order(self,driver,oid):
        r=self.db.execute('SELECT payload FROM member_orders WHERE id=? AND driver=?',(oid,driver)).fetchone();require(r is not None,'会员订单不存在',404)
        return json.loads(r['payload'])
    def pay_member(self,driver,p):
        o=self.member_order(driver,p.get('orderId'));outcome=p.get('outcome');require(outcome in ('SUCCESS','FAILED','CANCEL','PENDING'),'支付结果不合法')
        if p.get('paymentMethod') is not None:
            require(p['paymentMethod'] in ('WECHAT','ALIPAY'),'支付方式无效')
            require(o['status']!='SUCCESS' or p['paymentMethod']==o['paymentMethod'],'成功订单不能更改支付方式',409)
            o['paymentMethod']=p['paymentMethod']
        if o['status']=='SUCCESS':
            sub=next((s for s in self.subscriptions(driver)['history'] if s['orderId']==o['id']),None)
            return {'order':o,'subscription':sub,'idempotent':True}
        if outcome=='SUCCESS':
            cat=self.catalog(driver,o['city']);lev=next(l for l in cat['levels'] if l['id']==o['level'])
            require(lev['saleEnabled'],'等级已停售，云端演示支付未扣款，请返回重新选择',409)
            require(cat['version']==o['version'] and lev['price']==o['amount'],'支付前配置已变化，沙箱未收款，请重新确认新价格',409,{'amount':lev['price'],'version':cat['version']})
            active=cat['subscription'];require(active is None,'已有在期订阅，当前版本不支持提前重复购买或升级折算',409)
            start=parse_time(active['endAt']) if active else now();end=start+dt.timedelta(days=365)
            if active:
                # One aggregate active subscription per city/level; new record preserves old snapshot/history.
                old=clone(active);old['status']='RENEWED';old['autoRenew']=False;old['nextChargeAt']=None
                self.db.execute('UPDATE subscriptions SET payload=? WHERE id=?',(dumps(old),old['id']))
            sub={'id':uid('MS'),'orderId':o['id'],'city':o['city'],'cityName':o['cityName'],'level':o['level'],'levelName':o['levelName'],'amount':o['amount'],'status':'ACTIVE','startAt':stamp(now()) if active else stamp(start),'paidPeriodStartAt':stamp(start),'endAt':stamp(end),'autoRenew':o['autoRenew'],'nextChargeAt':stamp(end) if o['autoRenew'] else None,'paymentMethod':o['paymentMethod'],'version':o['version'],'rightsSnapshot':o['rightsSnapshot'],'createdAt':stamp(),'sandbox':True,'renewalNotice':'仅云端演示续费意愿，不是支付机构签约，不实际扣款'}
            o.update(status='SUCCESS',entitlementsStatus='ACTIVE',startAt=stamp(start),endAt=stamp(end),paidAt=stamp(),subscriptionId=sub['id'])
            self.db.execute('INSERT INTO subscriptions VALUES (?,?,?,?)',(sub['id'],driver,dumps(sub),stamp()))
            self.audit('SANDBOX_PROVIDER','membership.payment.success',None,{'orderId':o['id'],'subscriptionId':sub['id'],'amount':o['amount']})
            result={'order':o,'subscription':sub}
        else:
            o['status']={'FAILED':'FAILED','CANCEL':'CANCELLED','PENDING':'PROCESSING'}[outcome]
            result={'order':o}
        self.db.execute('UPDATE member_orders SET payload=? WHERE id=?',(dumps(o),o['id']))
        return result
    def renewal(self,driver,p):
        r=self.db.execute('SELECT payload FROM subscriptions WHERE id=? AND driver=?',(p.get('subscriptionId'),driver)).fetchone();require(r is not None,'订阅不存在',404)
        s=json.loads(r['payload']);require(s['status']=='ACTIVE','仅在期订阅可调整续费')
        enabled=p.get('enabled');require(isinstance(enabled,bool),'续费状态须为布尔值')
        if enabled:
            cat=self.catalog(driver,s['city']);require(next(l for l in cat['levels'] if l['id']==s['level'])['saleEnabled'],'等级已停售，不支持开启续费',403)
        before=clone(s);s['autoRenew']=enabled;s['nextChargeAt']=s['endAt'] if enabled else None
        self.db.execute('UPDATE subscriptions SET payload=? WHERE id=?',(dumps(s),s['id']))
        self.audit('DRIVER:'+driver,'membership.renewal',before,s)
        return {'subscription':s,'sandbox':True}
    def invoice(self,driver,p):
        o=self.member_order(driver,p.get('orderId'));require(o['status']=='SUCCESS','仅支付成功订单可以申请发票')
        typ=p.get('type','PERSONAL');require(typ in ('PERSONAL','COMPANY'),'发票类型无效')
        title=textfield(p.get('title'),'发票抬头',2,100);tax=str(p.get('taxNumber','')).upper();require(not tax or bool(re.fullmatch('[0-9A-Z]{15,20}',tax)),'税号须为15至20位大写字母或数字')
        if typ=='COMPANY': require(bool(re.fullmatch(r'(?:[0-9A-Z]{15}|[0-9A-Z]{18}|[0-9A-Z]{20})',tax)),'企业税号须为15、18或20位')
        exists=self.db.execute('SELECT payload FROM invoices WHERE driver=? AND order_id=?',(driver,o['id'])).fetchone()
        if exists: return {'invoice':json.loads(exists['payload']),'idempotent':True}
        inv={'id':uid('INV'),'orderId':o['id'],'type':typ,'title':title,'taxNumber':tax,'amount':o['amount'],'status':'SANDBOX_ACCEPTED','statusLabel':'云端演示受理，未开具真实发票','createdAt':stamp(),'sandbox':True}
        self.db.execute('INSERT INTO invoices VALUES (?,?,?,?,?)',(inv['id'],driver,o['id'],dumps(inv),stamp()))
        return {'invoice':inv}
