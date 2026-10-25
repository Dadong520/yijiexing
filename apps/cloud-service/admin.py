from common import *
from seed import CITIES, RIGHTS, PARAMS

class AdminMixin:
    def validate_patch(self,patch,scope):
        require(isinstance(patch,dict),'配置内容须为JSON对象')
        require(set(patch)<= {'systemEnabled','levels','trialDays','trialOrders','remarks'},'含未知配置字段')
        if 'systemEnabled' in patch: require(isinstance(patch['systemEnabled'],bool),'总开关须为布尔值')
        for key in ('trialDays','trialOrders'):
            if key in patch: require(isinstance(patch[key],int) and not isinstance(patch[key],bool) and 1<=patch[key]<= (365 if key=='trialDays' else 10000),key+'须为有效正整数')
        if 'remarks' in patch: textfield(patch['remarks'],'备注',0,500)
        require(isinstance(patch.get('levels',{}),dict),'等级配置须为对象')
        for code,v in patch.get('levels',{}).items():
            require(code in ('TRIAL','NORMAL','VIP') and isinstance(v,dict),'等级标识或内容无效')
            allowed={'id','name','price','saleEnabled','enabled','rightsCount','rights','params','firstYearPrice','upgradePrice'}
            require(set(v)<=allowed,'含未知等级字段')
            if 'id' in v:require(v['id']==code,'等级稳定标识不得修改')
            if 'name' in v:textfield(v['name'],'等级名称',2,20)
            if 'rightsCount' in v:require(type(v['rightsCount']) is int and 0<=v['rightsCount']<=200,'权益标称数量无效')
            # Regional overrides cannot modify globally maintained rights.
            if scope!='national': require(not any(k in v for k in ('rights','rightsCount','id','name','enabled')),'地区仅覆盖价格、售卖和允许的参数，全局权益开关请编辑全国等级')
            for key in ('saleEnabled','enabled'):
                if key in v: require(isinstance(v[key],bool),key+'须为布尔值')
            if 'price' in v: cents(v['price'],'年度售价',code=='TRIAL')
            for key in ('firstYearPrice','upgradePrice'):
                if v.get(key) is not None: cents(v[key],key,True)
            if v.get('firstYearPrice') is not None and 'price' in v: require(v['firstYearPrice']<=v['price'],'首年优惠不得高于年度售价')
            for k,val in v.get('rights',{}).items():
                require(k in {r[0] for r in RIGHTS} and isinstance(val,bool),'权益开关标识或值无效')
            params=v.get('params',{});require(isinstance(params,dict) and set(params)<=PARAMS,'含不可覆盖参数')
            if 'nearbyKm' in params: require(isinstance(params['nearbyKm'],(int,float)) and not isinstance(params['nearbyKm'],bool) and 0<params['nearbyKm']<=100,'距离须为0至100公里之间正数')
            if 'offlineNegotiation' in params: require(isinstance(params['offlineNegotiation'],bool),'议价地区开关须为布尔值')
            if 'voiceEnabled' in params: require(isinstance(params['voiceEnabled'],bool),'城市语音开关须为布尔值')
            if 'voiceCount' in params: require(type(params['voiceCount']) is int and 1<=params['voiceCount']<=20,'播报次数须为1至20')
            if 'voiceSpeed' in params: require(type(params['voiceSpeed']) in (int,float) and .5<=params['voiceSpeed']<=2,'语速须为0.5至2倍')
            q=params.get('mapQuota')
            if q is not None:
                require(isinstance(q,dict) and q.get('type') in ('ORDERS','AMOUNT','UNLIMITED'),'地图额度类型无效')
                require(set(q)<={'type','freeOrders','freeAmount','overagePrice'},'地图额度含未知参数')
                if q['type']!='UNLIMITED':
                    for key in ('freeOrders','freeAmount','overagePrice'):
                        if key in q: cents(q[key],key,True)
                    require(('freeOrders' if q['type']=='ORDERS' else 'freeAmount') in q and 'overagePrice' in q,'请补齐额度及超额单价')
    def merge_patch(self,current,patch,remove=None):
        out=clone(current)
        def merge(o,p):
            for k,v in p.items():
                if isinstance(v,dict):
                    if not isinstance(o.get(k),dict):o[k]={}
                    merge(o[k],v)
                else:o[k]=clone(v)
        merge(out,patch)
        for path in remove or []:
            require(isinstance(path,str) and not path.startswith('__'),'继承字段路径错误')
            parts=path.split('.');node=out
            for part in parts[:-1]: node=node.get(part,{})
            node.pop(parts[-1],None)
        return out
    def admin_state(self):
        configs=[]
        for r in self.db.execute('SELECT * FROM configs ORDER BY scope,target'):
            configs.append({'scope':r['scope'],'target':r['target'],'version':r['version'],'revision':r['revision'],'published':json.loads(r['published']),'draft':json.loads(r['draft']) if r['draft'] else None,'updatedAt':r['updated']})
        cities=[]
        for c in CITIES:
            cfg,src,v=self.effective(c['id']);cityrow=self.config_row('city',c['id'])
            cities.append({**c,'version':v,'customized':bool(json.loads(cityrow['published'])),'source':src.get('levels.NORMAL.price',{'label':'全国默认'})['label'],'systemEnabled':cfg['systemEnabled'],'normalPrice':cfg['levels']['NORMAL']['price'],'vipPrice':cfg['levels']['VIP']['price'],'levels':[self.level_payload(code,cfg,src,v) for code in ('TRIAL','NORMAL','VIP')]})
        datasets={}
        for table in ('approvals','scheduled','batches'):
            datasets[table]=[json.loads(r['payload']) for r in self.db.execute(f'SELECT payload FROM {table} ORDER BY created DESC')]
        drivers=[]
        for a in self.db.execute('SELECT * FROM accounts ORDER BY id'):
            profile=json.loads(a['profile']);cat=self.catalog(a['id']);st=json.loads(a['state'])
            drivers.append({'id':a['id'],'mobile':a['mobile'],'name':profile['driver']['name'],'city':profile['driver']['city'],'trial':cat['trial'],'currentLevel':cat['currentLevel'],'certificates':profile['certificates'],'feedback':profile['feedback'],'complaints':profile.get('complaints',[]),'orders':st['orders'],'online':st['online'],'balance':st['balance'],'wallet':profile['wallet'],'subscription':cat['subscription']})
        datasets.update(configs=configs,cities=cities,drivers=drivers,audit=[dict(r) for r in self.db.execute('SELECT * FROM audits ORDER BY id DESC LIMIT 200')],serverTime=stamp(),sandbox=True,policies=['退款与转级按待确认规则生成待处理任务，不结束原权益，不执行资金操作。','云端演示审批强制申请人/两名审批人不同；角色仅为云端演示演练身份，不连接现网组织权限。','首年折扣与升级差价仅保留配置，服务端不自动使用。'])
        return datasets
    def config_draft(self,p,actor):
        scope=p.get('scope','city');target=p.get('target');r=self.config_row(scope,target)
        require(p.get('expectedRevision',p.get('expectedVersion'))==r['revision'],'配置已被更新，请重新加载',409,{'revision':r['revision']})
        patch=p.get('patch',{});self.validate_patch(patch,scope)
        require(scope!='national' or not p.get('removePaths'),'全国基准字段不能取消覆盖')
        base=json.loads(r['draft'] or r['published']);out=self.merge_patch(base,patch,p.get('removePaths'))
        self.validate_patch(out,scope)
        before=json.loads(r['draft'] or r['published'])
        self.db.execute('UPDATE configs SET draft=?,revision=revision+1,updated=? WHERE scope=? AND target=?',(dumps(out),stamp(),scope,target))
        self.audit(actor,'config.draft',before,{'scope':scope,'target':target,'value':out,'reason':p.get('reason','云端演示编辑')})
        return {'config':{'scope':scope,'target':target,'draft':out,'revision':r['revision']+1,'version':r['version']}}
    def closes_sale(self,before,after):
        if before.get('systemEnabled',True) and after.get('systemEnabled') is False:return True
        return any(v.get('saleEnabled') is False and before.get('levels',{}).get(code,{}).get('saleEnabled',True) for code,v in after.get('levels',{}).items())
    def commit_config(self,scope,target,value,actor,reason='云端演示发布'):
        r=self.config_row(scope,target);before=json.loads(r['published'])
        self.db.execute('UPDATE configs SET published=?,draft=NULL,version=version+1,revision=revision+1,updated=? WHERE scope=? AND target=?',(dumps(value),stamp(),scope,target))
        self.audit(actor,'config.publish',{'scope':scope,'target':target,'value':before},{'value':value,'version':r['version']+1,'reason':reason})
        return {'scope':scope,'target':target,'version':r['version']+1,'revision':r['revision']+1}
    def config_publish(self,p,actor):
        scope=p.get('scope','city');target=p.get('target');r=self.config_row(scope,target)
        require(p.get('expectedRevision',p.get('expectedVersion'))==r['revision'],'配置已更新，禁止覆盖发布',409,{'revision':r['revision']})
        require(r['draft'] is not None,'没有待发布草稿')
        after=json.loads(r['draft']);before=json.loads(r['published']);self.validate_patch(after,scope)
        # Check price relationship also against inherited annual prices.
        if scope=='city':
            effective,_,_=self.effective(target);effective=self.merge_patch(effective,after)
            for v in effective['levels'].values():
                if v.get('firstYearPrice') is not None: require(v['firstYearPrice']<=v['price'],'首年价不得高于继承或覆盖后的年度售价')
        changes=[{'scope':scope,'target':target,'value':after,'revision':r['revision']}]
        if self.closes_sale(before,after):
            return self.new_approval(changes,p,actor)
        when=p.get('effectiveAt')
        if when:
            require(parse_time(when)>now(),'指定生效时间必须晚于服务端当前时间')
            task={'id':uid('SCH'),'changes':changes,'effectiveAt':stamp(parse_time(when)),'status':'PENDING','createdAt':stamp(),'actor':actor,'reason':p.get('reason','定时发布')}
            self.db.execute('INSERT INTO scheduled VALUES (?,?,?)',(task['id'],dumps(task),stamp()))
            self.audit(actor,'config.schedule',None,task);return {'scheduled':task}
        return {'published':self.commit_config(scope,target,after,actor,p.get('reason','云端演示发布'))}
    def run_scheduled(self):
        for r in self.db.execute('SELECT payload FROM scheduled').fetchall():
            task=json.loads(r['payload'])
            if task['status']!='PENDING' or parse_time(task['effectiveAt'])>now():continue
            changes=task['changes']
            if any(self.config_row(c['scope'],c['target'])['revision']!=c['revision'] for c in changes):
                task.update(status='FAILED',error='配置版本已变化，未覆盖新编辑结果',executedAt=stamp())
            else:
                for c in changes:self.commit_config(c['scope'],c['target'],c['value'],task['actor'],task['reason'])
                task.update(status='APPLIED',executedAt=stamp())
            self.db.execute('UPDATE scheduled SET payload=? WHERE id=?',(dumps(task),task['id']))
            if task.get('batchId'):
                row=self.db.execute('SELECT payload FROM batches WHERE id=?',(task['batchId'],)).fetchone()
                if row:
                    b=json.loads(row['payload']);b['status']=task['status']
                    for v in b['results']:
                        if v['status']!='SKIPPED':v['status']=task['status']
                    b['error']=task.get('error');self.db.execute('UPDATE batches SET payload=? WHERE id=?',(dumps(b),b['id']))
    def batch_preview(self,p):
        targets=set(p.get('cities',[]))
        regions=p.get('regions',[]);provinces=p.get('provinces',[])
        targets.update(c['id'] for c in CITIES if c['region'] in regions or c['provinceCode'] in provinces or c['province'] in provinces)
        require(targets,'请至少选择一个有效地区')
        for target in targets:self.city(target)
        policy=p.get('policy');require(policy in ('SKIP_EXISTING','OVERWRITE_ALL','SWITCH_ONLY'),'请选择正确覆盖策略')
        patch=p.get('patch',{});self.validate_patch(patch,'city');require(patch,'请选择要修改的字段')
        require(not p.get('removePaths'),'批量操作不使用取消继承字段')
        if policy=='SWITCH_ONLY':
            require(set(patch)<={'systemEnabled','levels'} and all(set(v)<={'saleEnabled'} for v in patch.get('levels',{}).values()),'仅覆盖开关策略不能修改价格或参数')
        results=[];changes=[]
        for target in sorted(targets):
            c=self.city(target);r=self.config_row('city',target);before=json.loads(r['published']);skip=policy=='SKIP_EXISTING' and bool(before)
            # Overwrite specified fields; preserve unrelated identity/overrides. No accidental price loss.
            after=self.merge_patch(before,patch)
            change={'scope':'city','target':target,'revision':r['revision'],'value':after}
            effective,_,_=self.effective(target)
            reopening=any(v.get('saleEnabled') is True and not effective['levels'][code]['saleEnabled'] for code,v in patch.get('levels',{}).items())
            results.append({'city':target,'cityName':c['name'],'drivers':c['drivers'],'paidDrivers':c['paidDrivers'],'customized':bool(before),'status':'SKIPPED' if skip else 'READY','reopening':reopening,'before':before,'after':after})
            if not skip: changes.append(change)
        return {'uniqueCityCount':len(targets),'driverCount':sum(self.city(t)['drivers'] for t in targets),'affectedCities':len(changes),'customCities':sum(bool(r['customized']) for r in results),'results':results,'changes':changes,'requiresApproval':any(self.closes_sale(json.loads(self.config_row(c['scope'],c['target'])['published']),c['value']) for c in changes)}
    def batch_execute(self,p,actor):
        key=textfield(p.get('idempotencyKey'),'批次幂等标识',1,100)
        existing=self.db.execute('SELECT payload FROM batches WHERE idem=?',(key,)).fetchone()
        if existing:
            b=json.loads(existing['payload']);require(b['requestHash']==hashlib.sha256(dumps(p).encode()).hexdigest(),'幂等批次标识已用于其他请求',409);return {'batch':b,'idempotent':True}
        preview=self.batch_preview(p);require(preview['changes'],'目标均已跳过，没有可下发变更')
        expected=p.get('expectedRevisions',{})
        require(all(expected.get(c['target'])==c['revision'] for c in preview['changes']),'请先预览并提交全部目标当前版本',409,{'currentRevisions':{c['target']:c['revision'] for c in preview['changes']}})
        b={'id':uid('BAT'),'policy':p['policy'],'createdAt':stamp(),'actor':actor,'uniqueCityCount':preview['uniqueCityCount'],'driverCount':preview['driverCount'],'results':preview['results'],'status':'APPLIED','requestHash':hashlib.sha256(dumps(p).encode()).hexdigest()}
        if preview['requiresApproval']:
            app=self.new_approval(preview['changes'],p,actor)['approval'];b.update(status='WAIT_APPROVAL',approvalId=app['id'])
            for row in b['results']:
                if row['status']!='SKIPPED':row['status']='WAIT_APPROVAL'
        elif p.get('effectiveAt'):
            require(parse_time(p['effectiveAt'])>now(),'批量指定时间必须晚于服务端当前时间')
            task={'id':uid('SCH'),'batchId':b['id'],'changes':preview['changes'],'effectiveAt':stamp(parse_time(p['effectiveAt'])),'status':'PENDING','createdAt':stamp(),'actor':actor,'reason':p.get('reason','批量定时发布')}
            self.db.execute('INSERT INTO scheduled VALUES (?,?,?)',(task['id'],dumps(task),stamp()));b.update(status='SCHEDULED',scheduledId=task['id'])
        else:
            for c in preview['changes']:self.commit_config(c['scope'],c['target'],c['value'],actor,p.get('reason','批量发布'))
            for row in b['results']:
                if row['status']!='SKIPPED':row['status']='APPLIED'
        self.db.execute('INSERT INTO batches VALUES (?,?,?,?)',(b['id'],key,dumps(b),stamp()));self.audit(actor,'config.batch',None,b)
        return {'batch':b}
    def new_approval(self,changes,p,actor):
        require(not p.get('effectiveAt'),'云端演示停售暂支持二级审批通过后立即生效，请清除指定时间；其他配置仍可定时发布')
        require(p.get('confirmed') is True,'停售需勾选影响确认')
        policy=p.get('subscriptionPolicy');require(policy in ('KEEP_UNTIL_EXPIRY','REFUND_REVIEW','CONVERT_REVIEW'),'请选择存量会员处理方式')
        reason=textfield(p.get('reason'),'停售原因',2,500)
        app={'id':uid('APP'),'status':'WAIT_FIRST_APPROVAL','changes':changes,'requestedBy':actor,'reason':reason,'subscriptionPolicy':policy,'createdAt':stamp(),'decisions':[],'sandbox':True}
        self.db.execute('INSERT INTO approvals VALUES (?,?,?)',(app['id'],dumps(app),stamp()));self.audit(actor,'sale.close.request',None,app)
        return {'approval':app,'message':'已提交二级审批，生效配置未改变'}
    def approval_decide(self,p,actor):
        r=self.db.execute('SELECT payload FROM approvals WHERE id=?',(p.get('id'),)).fetchone();require(r is not None,'审批不存在',404)
        a=json.loads(r['payload']);require(a['status'] in ('WAIT_FIRST_APPROVAL','WAIT_SECOND_APPROVAL'),'审批已结束',409)
        require(actor!=a['requestedBy'] and actor not in [d['actor'] for d in a['decisions']],'申请人和两级审批人必须不同',403)
        decision=p.get('decision');require(decision in ('APPROVE','REJECT'),'审批决定无效')
        a['decisions'].append({'actor':actor,'decision':decision,'reason':p.get('reason','云端演示审批演练'),'at':stamp()})
        if decision=='REJECT':a['status']='REJECTED'
        elif len(a['decisions'])==1:a['status']='WAIT_SECOND_APPROVAL'
        else:
            require(all(self.config_row(c['scope'],c['target'])['revision']==c['revision'] for c in a['changes']),'配置已更新，此审批不能覆盖新版本，请重新申请',409)
            for c in a['changes']:self.commit_config(c['scope'],c['target'],c['value'],actor,'二级审批通过：'+a['reason'])
            # Sale closure preserves paid snapshots, turns off sandbox renewal for closed products.
            tasks=[]
            affected=set()
            for c in a['changes']:
                if c['scope']=='city':affected.add(c['target'])
                elif c['scope']=='region':affected.update(v['id'] for v in CITIES if v['region']==c['target'])
                else:affected.update(v['id'] for v in CITIES)
            for r in self.db.execute('SELECT id,driver,payload FROM subscriptions').fetchall():
                s=json.loads(r['payload'])
                if s['city'] not in affected:continue
                cfg,_,_=self.effective(s['city'])
                if not cfg['systemEnabled'] or not cfg['levels'][s['level']]['saleEnabled']:
                    s['autoRenew']=False;s['nextChargeAt']=None
                    if a['subscriptionPolicy']!='KEEP_UNTIL_EXPIRY':
                        tasks.append({'subscriptionId':s['id'],'status':'AWAITING_POLICY_CONFIRMATION','policy':a['subscriptionPolicy'],'message':'折算与资金规则未确认，保留原权益，未退款或转级'})
                    self.db.execute('UPDATE subscriptions SET payload=? WHERE id=?',(dumps(s),s['id']))
            a.update(status='APPROVED',effectiveAt=stamp(),manualTasks=tasks)
        self.db.execute('UPDATE approvals SET payload=? WHERE id=?',(dumps(a),a['id']));self.audit(actor,'sale.close.decision',None,a)
        for row in self.db.execute('SELECT id,payload FROM batches').fetchall():
            b=json.loads(row['payload'])
            if b.get('approvalId')==a['id'] and a['status'] in ('APPROVED','REJECTED'):
                b['status']='APPLIED' if a['status']=='APPROVED' else 'REJECTED'
                for v in b['results']:
                    if v['status']=='WAIT_APPROVAL':v['status']=b['status']
                self.db.execute('UPDATE batches SET payload=? WHERE id=?',(dumps(b),b['id']))
        return {'approval':a}
    def admin_action(self,action,p,actor):
        require(isinstance(action,str) and isinstance(p,dict),'管理操作或参数格式无效')
        if action=='config.draft':return self.config_draft(p,actor)
        if action=='config.publish':return self.config_publish(p,actor)
        if action=='config.restore':
            scope=p.get('scope','city');r=self.config_row(scope,p.get('target'));require(scope!='national','全国基准不能清空')
            require(p.get('expectedRevision')==r['revision'],'版本冲突',409)
            # Restore is a draft until explicit publish; identity is held in city dictionary.
            self.db.execute('UPDATE configs SET draft=?,revision=revision+1,updated=? WHERE scope=? AND target=?',('{}',stamp(),scope,p.get('target')))
            self.audit(actor,'config.restore.draft',json.loads(r['published']),{})
            return {'revision':r['revision']+1,'message':'已建立恢复继承草稿，发布后才生效'}
        if action=='config.copy':
            require(p.get('confirmed') is True,'复制覆盖须主动确认')
            source=p.get('source');target=p.get('level');require(source in ('TRIAL','NORMAL','VIP') and target in ('NORMAL','VIP') and source!=target,'来源或目标等级无效')
            r=self.config_row('national','CN');require(p.get('expectedRevision')==r['revision'],'版本冲突',409)
            cfg=json.loads(r['draft'] or r['published']);v=clone(cfg['levels'][source]);keep=cfg['levels'][target]
            for k in ('id','name','price','saleEnabled','enabled'):v[k]=keep[k]
            cfg['levels'][target]=v
            return self.config_draft({'scope':'national','target':'CN','expectedRevision':r['revision'],'patch':cfg},actor)
        if action=='batch.preview':return {'preview':self.batch_preview(p)}
        if action=='batch.execute':return self.batch_execute(p,actor)
        if action=='approval.decide':return self.approval_decide(p,actor)
        if action=='schedule.cancel':
            r=self.db.execute('SELECT payload FROM scheduled WHERE id=?',(p.get('id'),)).fetchone();require(r is not None,'任务不存在',404)
            task=json.loads(r['payload']);require(task['status']=='PENDING','任务已执行或取消',409);task['status']='CANCELLED'
            self.db.execute('UPDATE scheduled SET payload=? WHERE id=?',(dumps(task),task['id']));self.audit(actor,'schedule.cancel',None,task)
            return {'scheduled':task}
        if action=='trial.set':
            driver=p.get('driverId');a=self.account(driver)
            days=p.get('daysUsed',0);completed=p.get('ordersCompleted',0)
            require(type(days) is int and 0<=days<=366 and type(completed) is int and 0<=completed<=10000,'试用演练参数无效')
            self.db.execute('UPDATE accounts SET trial_start=?,trial_completed=? WHERE id=?',(stamp(now()-dt.timedelta(days=days)),completed,driver))
            self.audit(actor,'sandbox.trial.set',{'start':a['trial_start'],'completed':a['trial_completed']},p)
            return {'trial':self.catalog(driver)['trial']}
        if action=='wallet.withdrawal.audit':
            driver=p.get('driverId');a=self.account(driver);profile=json.loads(a['profile']);st=json.loads(a['state'])
            w=next((w for w in profile['wallet']['withdrawals'] if w['id']==p.get('id')),None);require(w is not None,'提现记录不存在',404)
            require(w['status']=='PENDING','云端演示提现已处理，不能重复出账或退款',409);approved=p.get('approved');require(isinstance(approved,bool),'处理结果无效')
            w.update(status='SANDBOX_APPROVED' if approved else 'REJECTED',statusLabel='云端演示审核通过，未实际银行打款' if approved else '云端演示驳回，已退回沙箱余额',reply=p.get('reason','云端演示沙箱处理'),auditedAt=stamp())
            if not approved:
                st['balance']+=w['amount'];st['entries'].insert(0,{'title':'沙箱提现驳回退回','amount':w['amount'],'note':w['id'],'createdAt':stamp()})
            self.save_profile(driver,profile);self.save_state(driver,st);self.audit(actor,'wallet.withdrawal.audit',None,{'driverId':driver,'id':w['id'],'approved':approved});return {'withdrawal':w}
        if action=='sandbox.event':return self.sandbox_event(p,actor)
        if action=='certificate.audit':return self.certificate_audit(p,actor)
        if action=='feedback.reply':
            driver=p.get('driverId');a=self.account(driver);profile=json.loads(a['profile']);kind='complaints' if p.get('kind')=='complaints' else 'feedback'
            item=next((i for i in profile[kind] if i['id']==p.get('id')),None);require(item is not None,'记录不存在',404)
            item.update(reply=textfield(p.get('reply'),'回复',1,1000),status='REPLIED',repliedAt=stamp())
            self.save_profile(driver,profile);self.audit(actor,'support.reply',None,{'driverId':driver,'id':item['id']});return {'record':item}
        raise ApiError(404,'未知后台操作')
