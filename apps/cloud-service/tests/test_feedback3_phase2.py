"""Phase2 exact-cent and local HTTP regressions; no live accounts or services."""
import datetime as dt
import json
import unittest
from unittest.mock import patch
from common import ApiError, now, stamp, parse_time, clone
from pricing import settlement_split, DEFAULT_TARIFF
from growth import DEFAULT_SCORE_RULE, is_day_order
from passenger import PASSENGER_MOBILES
from policy_helpers import PolicyCase
import test_feedback3 as phase1


class Phase2(PolicyCase):
    complete = phase1.Feedback3.complete
    def ops(self, action, payload):
        return self.mutate_fixture(lambda s: s.operations.admin_action(action, payload, 'ADMIN:phase2'))

    def company(self, driver=None, signed=True):
        c=self.ops('ops.company.save',dict(name='模拟出租车公司',supportPhone=None,enabled=True,
                    taxNumber='A'*18,invoiceTitle='模拟公司',mapServiceFee=50,platformRate=50))['company']
        if driver:
            self.ops('ops.driverCompany.set',dict(driverId=driver,companyId=c['id'],expectedVersion=0))
        if signed:
            self.ops('ops.companySplit.open',dict(companyId=c['id'],expectedVersion=0,accountName='模拟公司',bankName='模拟银行',bankAccount='62'+'0'*14))
        return c

    def template(self, **kw):
        return self.ops('ops.couponTemplate.save',dict(name='模拟优惠券',type='DIRECT',amount=500,validity='DAYS',validDays=7,
                    totalQuantity=200,perUserLimit=10,claimable=True,enabled=True,**kw))['couponTemplate']

    def fund(self, amount=1000000):
        return self.ops('ops.couponPool.recharge',dict(amount=amount,reference='test-reference-'+str(amount)))

    def test_exact_cent_reference_and_both_difference_identities(self):
        s=settlement_split(1000,500,0,50,50)
        self.assertEqual([s[k] for k in ('orderFee','paidAmount','tripPayFee','tripSplitFee','paidPayFee','paidSplitFee',
            'platformDifference','commissionable','companyCommission','platformCommission','driverAmount','scanPayIncome','scanPaySplit')],
            [1500,1500,6,2,9,2,3,497,248,299,942,1491,1489])
        for a in (1,100,999,1000,10001):
            for service in (0,1,500):
                for coupon in (0,min(a,200),a):
                    with self.subTest(a=a,service=service,coupon=coupon):
                        x=settlement_split(a,service,coupon,0,50)
                        self.assertEqual(x['scanPaySplit'],x['driverAmount']+x['platformCommission']+x['companyCommission'])
                        self.assertEqual(a+service,x['driverAmount']+x['platformCommission']+x['companyCommission']+x['platformDifference']+x['tripPayFee']+x['tripSplitFee'])
        self.assertLess(settlement_split(1000,0,500,50,50)['platformDifference'],0)

    def test_tariff_night_0500_return_and_integer_ceiling(self):
        def check(s):
            morning=dt.datetime(2026,11,12,5,0,tzinfo=now().tzinfo)
            self.assertTrue(s.is_night(DEFAULT_TARIFF,morning))
            self.assertFalse(s.is_night(DEFAULT_TARIFF,morning+dt.timedelta(minutes=1)))
            self.assertEqual(s.unit_price(DEFAULT_TARIFF,9000,morning),152)
            self.assertEqual(s.estimate_trip_fare('420500',2000,morning)[0],800)
            self.assertEqual(s.estimate_trip_fare('420500',2001,morning)[0],912)
            self.assertEqual(s.estimate_trip_fare('420500',9500,morning)[0],800+14*112+152)
        self.mutate_fixture(check)

    def test_company_gate_rebind_mask_and_versions(self):
        p,d=self.setup_pair();did=d['profile']['driver']['id'];c=self.company(did,False)
        blocked=self.request('/api/action',dict(action='driver.online',payload={'online':True},requestId='gate'),token=d['token'],expected=403)
        self.assertEqual(blocked['details']['reason'],'COMPANY_SPLIT_ACCOUNT_REQUIRED')
        o=self.create(p['token'])
        with self.assertRaises(ApiError): self.mutate_fixture(lambda s:s.require_negotiation_driver(did,{'online':True,'orders':[]}))
        # Competition rechecks the signing gate even if this account was already online.
        self.request('/api/action',dict(action='order.accept',payload={'id':o['id'],'competitionRound':1},requestId='grab-block'),token=d['token'],expected=403)
        a=self.ops('ops.companySplit.open',dict(companyId=c['id'],expectedVersion=0,accountName='模拟公司',bankName='模拟银行',bankAccount='62'+'1'*14))['companySplit']
        b=self.ops('ops.companySplit.rebind',dict(companyId=c['id'],expectedVersion=1,accountName='新公司',bankName='新银行',bankAccount='62'+'2'*14))['companySplit']
        self.assertNotEqual(a['virtualAccount'],b['virtualAccount']);self.assertEqual(b['bankAccountMask'],'****2222')
        self.assertNotIn('bankAccount',b)
        query=self.ops('ops.companySplit.query',{'companyId':c['id']})['companySplit']
        self.assertEqual(query['relationStatus'],'SIGNED');self.assertIn('未连接真实',query['channelNotice'])
        self.da(d['token'],'driver.online',{'online':True}); self.da(d['token'],'order.accept',{'id':o['id']})
        self.assertEqual(self.get_order(p['token'],o['id'])['stage'],'PICKUP')
        audit=self.mutate_fixture(lambda s:' '.join(r['after_value'] for r in s.db.execute("SELECT after_value FROM audits WHERE action LIKE 'ops.companySplit.%'")))
        self.assertNotIn('62'+'2'*14,audit)

    def test_company_tax_required_and_legacy_company_fields_preserved(self):
        c=self.ops('ops.company.save',dict(name='旧公司',supportPhone=None,enabled=True))['company']
        payload=dict(companyId=c['id'],expectedVersion=0,accountName='公司',bankName='银行',bankAccount='62'+'0'*14)
        with self.assertRaises(ApiError) as err:self.ops('ops.companySplit.open',payload)
        self.assertEqual(err.exception.status,409)
        c=self.ops('ops.company.save',dict(id=c['id'],expectedVersion=1,name='新公司',supportPhone=None,enabled=True,mapServiceFee=50,platformRate=25,taxNumber='A'*18))['company']
        c=self.ops('ops.company.save',dict(id=c['id'],expectedVersion=2,name='再次命名',supportPhone=None,enabled=True))['company']
        self.assertEqual((c['mapServiceFee'],c['platformRate'],c['taxNumber']),(50,25,'A'*18))
        for k,v in [('mapServiceFee',True),('platformRate',101),('taxNumber','lowercaseinvalid')]:
            with self.assertRaises(ApiError):self.ops('ops.company.save',dict(id=c['id'],expectedVersion=3,name='非法公司',supportPhone=None,enabled=True,**{k:v}))

    def test_company_split_credit_and_withdraw_idempotent(self):
        p,d=self.setup_pair();did=d['profile']['driver']['id'];c=self.company(did)
        self.ops('ops.serviceFee.save',dict(city='420500',expectedVersion=0,amount=500,enabled=True))
        o=self.complete(p,d)
        rows=self.mutate_fixture(lambda s:s.operations.rows('companySplitEntry'))
        a=self.mutate_fixture(lambda s:s.operations.get('companySplitAccount',c['id']))
        self.assertEqual((a['availableBalance'],a['totalCredited']),(o['settlement']['companyCommission'],)*2)
        self.assertEqual([r['type'] for r in rows],['ORDER_SPLIT'])
        self.mutate_fixture(lambda s:s.shared_credit(s.shared_order(o['id'])))
        self.assertEqual(len(self.mutate_fixture(lambda s:s.operations.rows('companySplitEntry'))),1)
        with self.assertRaises(ApiError):self.ops('ops.companySplit.withdraw',dict(companyId=c['id'],expectedVersion=a['version'],amount=a['availableBalance']+1))
        b=self.ops('ops.companySplit.withdraw',dict(companyId=c['id'],expectedVersion=a['version'],amount=a['availableBalance']))['companySplit']
        self.assertEqual(b['availableBalance'],0)
        with self.assertRaises(ApiError):self.ops('ops.companySplit.withdraw',dict(companyId=c['id'],expectedVersion=a['version'],amount=1))

    def test_coupon_pool_lifecycle_expiry_alerts_and_fuse(self):
        p,d=self.setup_pair();self.fund();t=self.template()
        coupon=self.pa(p['token'],'coupon.claim',{'templateId':t['id']})['result']['coupon']
        before=self.mutate_fixture(lambda s:s.coupon_pool());self.assertEqual((before['available'],before['frozen']),(999500,500))
        # A coupon cannot reduce the service fee; unused face value returns at completion.
        self.ops('ops.serviceFee.save',dict(city='420500',expectedVersion=0,amount=500,enabled=True))
        o=self.create(p['token'],couponId=coupon['id']);self.da(d['token'],'order.accept',{'id':o['id']});self.finish(d['token'],o['id'])
        def cheaper(s):
            current=s.shared_order(o['id']);s.apply_order_price(current,300);s.save_shared(current)
        self.mutate_fixture(cheaper);current=self.get_order(p['token'],o['id']);self.assertEqual(current['fare'],500)
        self.pa(p['token'],'order.pay',dict(id=o['id'],expectedFare=current['fare'],fareVersion=current.get('fareVersion',0)))
        pool=self.mutate_fixture(lambda s:s.coupon_pool());self.assertEqual((pool['available'],pool['frozen'],pool['used']),(999700,0,300))
        second=self.pa(p['token'],'coupon.claim',{'templateId':t['id']})['result']['coupon']
        def expire(s):
            data=s._passenger_extra_store(p['passenger']['id']);next(c for c in data['coupons'] if c['id']==second['id'])['expiresAt']=stamp(now()-dt.timedelta(seconds=1));s._save_passenger_extras(p['passenger']['id'],data);s.expire_coupons();s.expire_coupons()
        self.mutate_fixture(expire);pool2=self.mutate_fixture(lambda s:s.coupon_pool());self.assertEqual(pool2['frozen'],0)
        def low(s):
            s.operations.write('couponPool','POOL',dict(id='POOL',total=49999,available=49999,frozen=0,used=0,version=1));s.check_pool_alerts();s.check_pool_alerts()
        self.mutate_fixture(low)
        self.assertEqual(len(self.mutate_fixture(lambda s:s.operations.rows('couponPoolAlert'))),2)
        response=self.pa(p['token'],'coupon.claim',{'templateId':t['id']},expected=409);self.assertEqual(response['details']['reason'],'COUPON_POOL_FUSED')

    def test_cancel_returns_coupon_for_both_parties_and_no_ghost_discount(self):
        p,d=self.setup_pair();self.fund();t=self.template();cid=self.pa(p['token'],'coupon.claim',{'templateId':t['id']})['result']['coupon']['id']
        o=self.create(p['token'],couponId=cid);self.da(d['token'],'order.accept',{'id':o['id']})
        self.da(d['token'],'order.cancel',{'id':o['id'],'reason':'模拟车辆故障'})
        current=self.get_order(p['token'],o['id']);self.assertEqual(current['stage'],'AVAILABLE');self.assertEqual(current['couponAmount'],0)
        coupons=self.request('/api/passenger/state',token=p['token'])['coupons'];self.assertEqual(coupons[0]['status'],'UNUSED')
        self.pa(p['token'],'order.cancel',dict(id=o['id'],reason='计划有变'))
        nxt=self.create(p['token'],couponId=cid);self.pa(p['token'],'order.cancel',dict(id=nxt['id'],reason='再次取消'))
        self.assertEqual(self.mutate_fixture(lambda s:s.coupon_pool())['frozen'],500)

    def test_cancel_fee_debt_blocks_new_order_until_exact_payment(self):
        p,d=self.setup_pair()
        self.ops('ops.tariff.save',dict(DEFAULT_TARIFF,city='420500',expectedVersion=0,emptyFee=300))
        o=self.create(p['token']);self.da(d['token'],'order.accept',{'id':o['id']});self.da(d['token'],'order.advance',{'id':o['id']})
        before=self.state(d['token'])['balance']
        self.pa(p['token'],'order.cancel',{'id':o['id'],'reason':'乘客取消'})
        self.assertEqual(self.get_order(p['token'],o['id'])['cancelFee']['status'],'UNPAID')
        debt=self.pa(p['token'],'order.create',dict(kind='STANDARD',origin='测试起点',destination='测试终点',originLat=30.6687,originLng=111.3226,destinationLat=30.6645,destinationLng=111.3694),expected=409)
        self.assertEqual(debt['details']['reason'],'PASSENGER_OUTSTANDING_CANCEL_FEE')
        self.pa(p['token'],'order.payCancelFee',dict(id=o['id'],expectedAmount=299),expected=409)
        self.pa(p['token'],'order.payCancelFee',dict(id=o['id'],expectedAmount=300))
        self.assertEqual(self.state(d['token'])['balance'],before+300)
        self.pa(p['token'],'order.payCancelFee',dict(id=o['id'],expectedAmount=300),expected=409)
        self.create(p['token'])

    def test_prepaid_cancel_fee_deducted_from_refund(self):
        p,d=self.setup_pair();self.ops('ops.tariff.save',dict(DEFAULT_TARIFF,city='420500',expectedVersion=0,emptyFee=300))
        o=self.create(p['token'],kind='BID');self.da(d['token'],'order.bid',dict(id=o['id'],amount=1000))
        self.pa(p['token'],'order.selectDriver',dict(id=o['id'],driverId=d['profile']['driver']['id']))
        o=self.get_order(p['token'],o['id']);self.pa(p['token'],'order.pay',dict(id=o['id'],expectedFare=o['fare'],fareVersion=o['fareVersion']))
        self.da(d['token'],'order.advance',{'id':o['id']});before=self.state(d['token'])['balance']
        cancelled=self.pa(p['token'],'order.cancel',dict(id=o['id'],reason='已到达取消'))['result']['order']
        self.assertEqual((cancelled['refundAmount'],cancelled['cancelFee']['amount'],cancelled['cancelFee']['status']),(700,300,'PAID'))
        self.assertEqual(self.state(d['token'])['balance'],before+300)

    def test_pricing_projection_and_new_points_rules(self):
        p,d=self.setup_pair();self.ops('ops.serviceFee.save',dict(city='420500',expectedVersion=0,amount=500,enabled=True))
        state=self.request('/api/passenger/state',token=p['token']);self.assertEqual(state['operations']['pricing']['serviceFee'],500);self.assertEqual(len(state['operations']['cityPricing']),3)
        rules=[dict(trigger='ORDER',mode='TIERED',tiers=[{'from':1,'points':5},{'from':2,'points':10}]),dict(trigger='AMOUNT',mode='FIXED',points=1)]
        self.ops('ops.pointsRule.save',dict(audience='PASSENGER',expectedVersion=0,enabled=True,rules=rules))
        first=self.complete(p,d);second=self.complete(p,d)
        points=self.request('/api/passenger/state',token=p['token'])['points']['balance']
        self.assertEqual(points,15+first['fare']//100+second['fare']//100)
        self.assertEqual(self.projected(d['token'],second['id'])['fare'],second['tripFare'])
        self.assertEqual(self.projected(d['token'],second['id'])['payableFare'],second['fare'])
        for audience,trigger in [('PASSENGER','DAY'),('DRIVER','ORDER')]:
            with self.assertRaises(ApiError):self.ops('ops.pointsRule.save',dict(audience=audience,expectedVersion=0 if audience=='DRIVER' else 1,enabled=True,rules=[dict(trigger=trigger,mode='FIXED',points=1)]))

    def test_invoice_reissue_apply_decide_and_monthly_tech_invoice(self):
        p,d=self.setup_pair();c=self.company(d['profile']['driver']['id']);self.ops('ops.serviceFee.save',dict(city='420500',expectedVersion=0,amount=500,enabled=True))
        o=self.complete(p,d);inv=self.pa(p['token'],'invoice.apply',dict(orderId=o['id'],title='原抬头'))['result']['invoice']
        request=dict(invoiceId=inv['id'],reason='抬头有误',type='PERSONAL',title='新抬头')
        pending=self.pa(p['token'],'invoice.reissue.apply',request)['result']['reissue']
        self.pa(p['token'],'invoice.reissue.apply',request,expected=409)
        self.assertEqual(len(self.request('/api/passenger/state',token=p['token'])['invoiceReissues']),1)
        self.ops('ops.invoiceReissue.decide',dict(id=pending['id'],expectedVersion=1,approve=True,reason='审核通过'))
        invs=self.request('/api/passenger/state',token=p['token'])['invoices'];old=next(x for x in invs if x['id']==inv['id'])
        self.assertEqual(old['status'],'RED_FLUSHED');self.assertEqual(old['reissueStatus'],'APPROVED');self.assertEqual(invs[0]['reissueOf'],old['id'])
        self.ops('ops.invoice.redFlush',dict(passengerId=p['passenger']['id'],invoiceId=invs[0]['id'],reason='再次调整'))
        self.ops('ops.invoice.reissue',dict(passengerId=p['passenger']['id'],invoiceId=invs[0]['id'],reason='冲红后重开',title='最终抬头'))
        self.ops('ops.invoiceIssuer.save',dict(expectedVersion=0,name='模拟平台',taxNumber='B'*18,taxRate=6))
        row=self.mutate_fixture(lambda s:s.tech_fee_rows()[0]);self.assertEqual(row['platformCommission'],o['settlement']['platformCommission'])
        issued=self.ops('ops.techInvoice.issue',dict(companyId=c['id'],month=row['month'],expectedAmount=row['platformCommission']))['techInvoice']
        self.assertEqual(issued['amountExcludingTax']+issued['taxAmount'],issued['amount'])
        def half_cent(s):
            with patch.object(s,'invoice_issuer',return_value=dict(name='模拟平台',taxNumber='B'*18,taxRate=20)), patch.object(s,'tech_fee_rows',return_value=[dict(id=c['id']+':2020-01',companyId=c['id'],month='2020-01',platformCommission=3,issued=None,companyTaxNumber='A'*18,companyTitle='模拟公司',orderCount=1)]):
                tax=s.issue_tech_invoice(dict(companyId=c['id'],month='2020-01',expectedAmount=3),'ADMIN:test')['techInvoice']
                self.assertEqual(tax['taxAmount'],1)
        self.mutate_fixture(half_cent)
        with self.assertRaises(ApiError):self.ops('ops.techInvoice.issue',dict(companyId=c['id'],month=row['month'],expectedAmount=row['platformCommission']))

    def test_actual_meter_low_speed_wait_and_estimate_fallback(self):
        p,d=self.setup_pair();o=self.create(p['token']);did=d['profile']['driver']['id']
        def verify(s):
            current=s.shared_order(o['id']);start=now();current.update(driverId=did,stage='TRIP',tripStartedAt=stamp(start),arrivedAt=stamp(start),originLat=30.,originLng=111.,destinationLat=30.01,destinationLng=111.)
            s.save_shared(current)
            def point(lat,seconds):return dict(latitude=lat,longitude=111.,accuracy=5,receivedAt=stamp(start+dt.timedelta(seconds=seconds)))
            with patch.object(s,'usable_trip_location',return_value=(True,None)):
                s.meter_location(did,point(30.,0));s.meter_location(did,point(30.018,60));s.meter_location(did,point(30.0181,960))
            metered=s.shared_order(o['id']);s.finalize_trip_fare(metered)
            self.assertEqual(metered['fareDetail']['source'],'METER');self.assertEqual(metered['fareDetail']['lowSpeedSeconds'],900)
            self.assertEqual(metered['fareDetail']['waitFee'],500);self.assertGreater(metered['fareDetail']['meters'],2000)
            fallback={**current,'meter':{},'mileage':'3.0'};s.finalize_trip_fare(fallback)
            self.assertEqual((fallback['fareDetail']['source'],fallback['fareDetail']['meters']),('ESTIMATE_FALLBACK',3000))
        self.mutate_fixture(verify)

    def test_new_user_coupon_once_and_register_invite_rewards_cap(self):
        p,d=self.setup_pair();self.fund();t=self.template()
        self.ops('ops.newUserCoupon.save',dict(expectedVersion=0,enabled=True,templateIds=[t['id']]))
        self.ops('ops.referralRule.save',dict(role='DRIVER',expectedVersion=0,enabled=True,trigger='REGISTER',tiered=True,points=0,
            tiers=[{'from':1,'points':3},{'from':2,'points':5}],cumulative=True,bonuses=[{'count':2,'points':10}]))
        invite=d['profile']['referrals']['code']
        mobile=PASSENGER_MOBILES[1]
        code=self.request('/api/passenger/auth/code',{'mobile':mobile})['verificationCode']
        fresh=self.request('/api/passenger/auth/login',dict(mobile=mobile,code=code,inviteCode=' '+invite.lower()+' '))
        self.assertEqual(len(fresh['coupons']),1)
        code=self.request('/api/passenger/auth/code',{'mobile':mobile})['verificationCode']
        again=self.request('/api/passenger/auth/login',dict(mobile=mobile,code=code,inviteCode=invite))
        self.assertEqual(len(again['coupons']),1)
        self.assertEqual(self.request('/api/state',token=d['token'])['profile']['referrals']['awarded'],3)
        did=d['profile']['driver']['id']
        def cap(s):
            for n in range(1,101):s.register_referral('PASSENGER','local-invitee-'+str(n),invite)
            rows=s.referral_records('DRIVER',did)
            self.assertEqual(sum(r['status']=='REWARDED' for r in rows),100)
            self.assertEqual(sum(r['status']=='LIMIT_REACHED' for r in rows),1)
            first=next(r for r in rows if r['status']=='REWARDED');before=s.referral_view('DRIVER',did)['reward'];s.reward_referral(first['id'],'REGISTER')
            self.assertEqual(s.referral_view('DRIVER',did)['reward'],before)
        self.mutate_fixture(cap)

    def test_first_order_invite_and_driver_day_night_points(self):
        p,d=self.setup_pair();pid=p['passenger']['id'];did=d['profile']['driver']['id']
        self.ops('ops.referralRule.save',dict(role='PASSENGER',expectedVersion=0,enabled=True,trigger='FIRST_ORDER',tiered=False,points=7,tiers=[],cumulative=False,bonuses=[]))
        self.ops('ops.pointsRule.save',dict(audience='DRIVER',expectedVersion=0,enabled=True,rules=[dict(trigger='DAY',mode='FIXED',points=3),dict(trigger='NIGHT',mode='TIERED',tiers=[{'from':1,'points':8}])]))
        def verify(s):
            invite=s.invite_code('PASSENGER',pid);s.register_referral('DRIVER',did,invite)
            self.assertEqual(s.referral_view('PASSENGER',pid)['reward'],0)
            s.referral_first_order('DRIVER',did);s.referral_first_order('DRIVER',did)
            self.assertEqual(s.referral_view('PASSENGER',pid)['reward'],7)
            order={'id':'local','createdAt':now().replace(hour=5,minute=30).isoformat(),'fare':1000}
            self.assertTrue(is_day_order(order['createdAt']));self.assertEqual(s.order_points('DRIVER',order,did)[0],3)
            order['createdAt']=now().replace(hour=23,minute=0).isoformat();self.assertFalse(is_day_order(order['createdAt']));self.assertEqual(s.order_points('DRIVER',order,did)[0],8)
        self.mutate_fixture(verify)

    def test_service_score_24h_slots_online_both_rating_and_complaints(self):
        p,d=self.setup_pair();did=d['profile']['driver']['id']
        rule=clone(DEFAULT_SCORE_RULE);rule.pop('version');rule.pop('enabledAt')
        rule.update(expectedVersion=0,acceptSlots=dict(enabled=True,items=[dict(start='00:00',end='12:00',points=2),dict(start='12:00',end='00:00',points=2)]),
            onlineAdd=dict(enabled=True,items=[dict(min=0,max=1440,points=3)]),onlineDeduct=dict(enabled=True,items=[dict(min=0,max=1440,points=1)]),
            ratingAdd=dict(enabled=True,points=4),ratingDeduct=dict(enabled=True,items={'4.5':2}),complaintDeduct=dict(enabled=True,cycleDays=7,items=[dict(min=1,max=None,points=5)]))
        invalid=clone(rule);invalid['acceptSlots']['items'][1]['start']='13:00'
        with self.assertRaises(ApiError):self.ops('ops.serviceScoreRule.save',invalid)
        invalid=clone(rule);invalid['levels'].append(None)
        with self.assertRaises(ApiError):self.ops('ops.serviceScoreRule.save',invalid)
        self.ops('ops.serviceScoreRule.save',rule)
        o=self.complete(p,d)
        score=self.request('/api/state',token=d['token'])['profile']['serviceScore']['value']
        self.pa(p['token'],'order.evaluate',dict(id=o['id'],rating=4.5,content='测试半分档'))
        self.assertEqual(self.request('/api/state',token=d['token'])['profile']['serviceScore']['value'],score-2)
        self.pa(p['token'],'order.complaint',dict(id=o['id'],reason='服务问题',content='用于测试周期内投诉扣分'))
        self.assertEqual(self.request('/api/state',token=d['token'])['profile']['serviceScore']['value'],score-7)
        def daily(s):
            yesterday=(now().date()-dt.timedelta(days=1)).isoformat();two_days=(now().date()-dt.timedelta(days=2)).isoformat()
            s.db.execute('UPDATE driver_service_scores SET evaluated_through=? WHERE driver=?',(two_days,did))
            before=s.driver_score(did);s.evaluate_daily_scores();self.assertEqual(s.driver_score(did),before+2)
            s.evaluate_daily_scores();self.assertEqual(s.driver_score(did),before+2)
        self.mutate_fixture(daily)
        nxt=self.complete(p,d);before=self.request('/api/state',token=d['token'])['profile']['serviceScore']['value']
        self.pa(p['token'],'order.evaluate',dict(id=nxt['id'],rating=5));self.assertEqual(self.request('/api/state',token=d['token'])['profile']['serviceScore']['value'],before+4)

    def test_admin_settlement_query_export_and_permission_boundary(self):
        from admin_console import AdminConsole
        p,d=self.setup_pair();o=self.complete(p,d)
        def verify(s):
            console=AdminConsole(s);u=dict(username='local',role='SUPER_ADMIN',permissions=['payments.read','export'])
            r=console.query(u,dict(dataset='orderSettlements',city='420500',search=o['id'],startDate=now().date().isoformat(),endDate=now().date().isoformat()))
            self.assertEqual(r['total'],1);self.assertEqual(r['summary']['driverAmount'],o['settlement']['driverAmount'])
            self.assertIn(o['id'],console.export(u,'orderSettlements'))
            self.assertEqual(console.query(u,dict(dataset='orderSettlements',companyId='missing'))['total'],0)
            with self.assertRaises(ApiError):console.query(dict(username='denied',role='SUPER_ADMIN',permissions=['orders.read']),dict(dataset='orderSettlements'))
            limited=console.state(dict(username='limited',role='SUPER_ADMIN',permissions=['config.read']))
            self.assertNotIn('orderSettlements',limited['operations']);self.assertNotIn('companySplitAccounts',limited['operations'])
            for k in ('defaultTariff','tariffs','serviceFees','couponTemplates','referralRules','serviceScoreRule'):self.assertIn(k,limited['operations'])
        self.mutate_fixture(verify)

    def test_pool_exact_thresholds_claim_window_and_template_validation(self):
        p,d=self.setup_pair()
        def threshold(s):
            for available,warning,fuse in [(500000,False,False),(499999,True,False),(50000,True,False),(49999,True,True)]:
                s.operations.write('couponPool','POOL',dict(id='POOL',total=available,available=available,frozen=0,used=0,version=0))
                s.check_pool_alerts();levels={r['level'] for r in s.operations.rows('couponPoolAlert')}
                self.assertEqual('WARNING' in levels,warning);self.assertEqual(s.pool_fused(),fuse)
        self.mutate_fixture(threshold)
        self.fund()
        t=self.template(claimStartAt=stamp(now()+dt.timedelta(days=1)))
        self.pa(p['token'],'coupon.claim',{'templateId':t['id']},expected=409)
        with self.assertRaises(ApiError):self.ops('ops.newUserCoupon.save',dict(expectedVersion=0,enabled=True,templateIds=[{}]))
        with self.assertRaises(ApiError):self.ops('ops.pointsRule.save',dict(audience='PASSENGER',expectedVersion=0,enabled=True,rules=[dict(trigger='ORDER',mode='TIERED',tiers=[{}]*6)]))

    def test_invoice_reissue_rejection_and_cross_owner_isolation(self):
        p,d=self.setup_pair();o=self.complete(p,d);inv=self.pa(p['token'],'invoice.apply',dict(orderId=o['id'],title='原抬头'))['result']['invoice']
        other=self.passenger_login(PASSENGER_MOBILES[1])
        body=dict(invoiceId=inv['id'],reason='抬头有误',title='新抬头')
        self.pa(other['token'],'invoice.reissue.apply',body,expected=404)
        r=self.pa(p['token'],'invoice.reissue.apply',body)['result']['reissue']
        rejected=self.ops('ops.invoiceReissue.decide',dict(id=r['id'],expectedVersion=1,approve=False,reason='信息不完整'))['invoiceReissue']
        self.assertEqual(rejected['status'],'REJECTED')
        old=self.request('/api/passenger/state',token=p['token'])['invoices'][0]
        self.assertEqual(old['status'],'SIMULATED_ISSUED');self.assertEqual(old['reissueStatus'],'REJECTED')
        with self.assertRaises(ApiError):self.ops('ops.invoiceReissue.decide',dict(id=r['id'],expectedVersion=2,approve=True,reason='重复决定'))
        self.pa(p['token'],'invoice.reissue.apply',body)

    def test_admin_cancel_coupon_and_prepaid_driver_cancel_returns_coupon(self):
        from admin_console import AdminConsole
        p,d=self.setup_pair();self.fund();t=self.template();cid=self.pa(p['token'],'coupon.claim',{'templateId':t['id']})['result']['coupon']['id']
        o=self.create(p['token'],couponId=cid)
        def cancel(s):
            current=s.shared_order(o['id']);AdminConsole(s).order_action('order.cancel',dict(id=o['id'],expectedStage=current['stage'],expectedFare=current['fare'],fareVersion=current.get('fareVersion',0),confirmed=True,reason='模拟后台取消',responsible=False),'ADMIN:test')
        self.mutate_fixture(cancel)
        self.assertEqual(self.request('/api/passenger/state',token=p['token'])['coupons'][0]['status'],'UNUSED')
        bid=self.create(p['token'],kind='BID',couponId=cid);self.da(d['token'],'order.bid',dict(id=bid['id'],amount=1000))
        self.pa(p['token'],'order.selectDriver',dict(id=bid['id'],driverId=d['profile']['driver']['id']))
        current=self.get_order(p['token'],bid['id']);self.pa(p['token'],'order.pay',dict(id=bid['id'],expectedFare=current['fare'],fareVersion=current.get('fareVersion',0)))
        self.da(d['token'],'order.cancel',dict(id=bid['id'],reason='司机取消测试'))
        self.assertEqual(self.request('/api/passenger/state',token=p['token'])['coupons'][0]['status'],'UNUSED')
        self.pa(p['token'],'order.continue',dict(id=bid['id'],type=1))
        self.assertEqual(self.get_order(p['token'],bid['id'])['couponAmount'],0)

    def test_company_refund_reversal_and_pending_wallet_income(self):
        from admin_console import AdminConsole
        from admin_extras import wallet_rows
        p,d=self.setup_pair();did=d['profile']['driver']['id'];c=self.company(did)
        self.ops('ops.serviceFee.save',dict(city='420500',expectedVersion=0,amount=500,enabled=True))
        o=self.create(p['token']);self.da(d['token'],'order.accept',dict(id=o['id']));self.finish(d['token'],o['id'])
        def pending(s):
            current=s.shared_order(o['id']);row=next(x for x in wallet_rows(s,[current]) if x['driverId']==did)
            self.assertEqual(row['pendingAmount'],s.order_settlement(current)['driverAmount'])
        self.mutate_fixture(pending)
        current=self.get_order(p['token'],o['id']);self.pa(p['token'],'order.pay',dict(id=o['id'],expectedFare=current['fare'],fareVersion=current.get('fareVersion',0)))
        def refund(s):
            current=s.shared_order(o['id']);AdminConsole(s).order_action('order.refund',dict(id=o['id'],expectedStage='DONE',expectedFare=current['fare'],fareVersion=current.get('fareVersion',0),confirmed=True,reason='模拟全额退款'),'ADMIN:test')
            self.assertEqual(s.operations.get('companySplitAccount',c['id'])['availableBalance'],0)
            self.assertEqual(s.order_settlement_rows()[0]['settlementStatus'],'REFUNDED')
        self.mutate_fixture(refund)

    def test_full_reduction_threshold_rechecked_after_final_fare_changes(self):
        p,d=self.setup_pair();self.fund()
        payload=dict(name='满减模拟券',type='FULL_REDUCTION',threshold=1000,amount=300,validity='DAYS',validDays=7,
                     totalQuantity=10,perUserLimit=2,claimable=True,enabled=True)
        t=self.ops('ops.couponTemplate.save',payload)['couponTemplate']
        cid=self.pa(p['token'],'coupon.claim',{'templateId':t['id']})['result']['coupon']['id']
        o=self.create(p['token'],couponId=cid)
        def cheaper(s):
            current=s.shared_order(o['id']);s.apply_order_price(current,900)
            self.assertEqual(current['couponAmount'],0);self.assertEqual(current['fare'],900)
            s.apply_order_price(current,1000);self.assertEqual(current['couponAmount'],300)
        self.mutate_fixture(cheaper)

    def test_excessive_map_fee_rejects_payment_without_negative_credit(self):
        p,d=self.setup_pair();c=self.company(d['profile']['driver']['id'])
        self.ops('ops.company.save',dict(id=c['id'],expectedVersion=c['version'],name=c['name'],supportPhone=None,enabled=True,mapServiceFee=100000))
        o=self.create(p['token']);self.da(d['token'],'order.accept',dict(id=o['id']));self.finish(d['token'],o['id'])
        current=self.get_order(p['token'],o['id']);before=self.state(d['token'])['balance']
        r=self.pa(p['token'],'order.pay',dict(id=o['id'],expectedFare=current['fare'],fareVersion=current['fareVersion']),expected=409)
        self.assertEqual(r['details']['reason'],'SETTLEMENT_NEGATIVE_DRIVER_AMOUNT')
        self.assertEqual(self.get_order(p['token'],o['id'])['stage'],'WAIT_PAY');self.assertEqual(self.state(d['token'])['balance'],before)
        self.assertEqual(self.mutate_fixture(lambda s:s.operations.rows('companySplitEntry')),[])

    def test_amount_tiers_use_monthly_order_index_times_whole_yuan_and_reset(self):
        p,d=self.setup_pair();pid=p['passenger']['id']
        rule=dict(trigger='AMOUNT',mode='TIERED',tiers=[{'from':1,'points':2},{'from':2,'points':4}])
        self.ops('ops.pointsRule.save',dict(audience='PASSENGER',expectedVersion=0,enabled=True,rules=[rule]))
        first=self.complete(p,d);second=self.complete(p,d)
        points=self.request('/api/passenger/state',token=p['token'])['points']['balance']
        self.assertEqual(points,(first['fare']//100)*2+(second['fare']//100)*4)
        # A different owner starts at tier one despite this passenger's two orders.
        other=self.passenger_login(PASSENGER_MOBILES[1])['passenger']['id']
        def reset(s):
            sample=dict(id='unsaved-next-order',fare=1299,createdAt=stamp())
            self.assertEqual(s.order_points('PASSENGER',sample,other)[0],24)
            self.assertEqual(s.order_points('PASSENGER',sample,pid)[0],48)
            month=(now().replace(day=1)+dt.timedelta(days=32)).replace(day=1)
            with patch('growth.now',return_value=month):
                self.assertEqual(s.month_completed('PASSENGER',pid),0)
                self.assertEqual(s.order_points('PASSENGER',sample,pid)[0],24)
            sample['fare']=99
            self.assertEqual(s.order_points('PASSENGER',sample,pid)[0],0)
        self.mutate_fixture(reset)
