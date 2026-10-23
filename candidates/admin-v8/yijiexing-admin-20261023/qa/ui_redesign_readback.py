#!/usr/bin/env python3
"""Candidate UI business readback. No business writes, no guessed order IDs.
Uses only fixed login/state/logout endpoints from cloud_admin_readback.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import re
import sys
from cloud_admin_readback import ROOT, ReadbackError, request, save_private


def identifier(value):
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_-]{2,119}', value):
        raise argparse.ArgumentTypeError('订单/投诉标识格式无效')
    return value


def amount(value):
    if not value.isdigit() or not 1 <= int(value) <= 100000000:
        raise argparse.ArgumentTypeError('预期金额须为正整数分')
    return int(value)


def inspect_state(state, standard_id, bid_id, complaint_id=None, standard_fare=None, bid_fare=None):
    """Pure data checks: safe to exercise with local fixtures without networking."""
    report = {'orders':{}, 'statistics':{}, 'assertions':[], 'notes':[
        'PASSENGER_PAY事件含失败/处理中等尝试，不按事件数推断支付成功次数。',
        '司机连续推进是合法多事件；只对本轮已知应恰好一次的关键动作检查唯一性。',
        '仅核权威数据与台账，不能替代候选APK实际UI操作证据。']}
    def check(name, actual, expected):
        report['assertions'].append({'name':name,'actual':actual,'expected':expected,'passed':actual==expected})
    orders=state['orders']; ledger=state['payments']; support=state['support']; stats=state['statistics']
    check('distinct_test_orders',standard_id != bid_id,True)
    check('canonical_ids_unique',len({o['id'] for o in orders}),len(orders))
    expected_stats={
        'orderCount':len(orders),
        'completedCount':sum(o['stage']=='DONE' for o in orders),
        'cancelledCount':sum(o['stage']=='CANCELLED' for o in orders),
        'activeCount':sum(o['stage'] not in ('DONE','CANCELLED') for o in orders),
        'paidFare':sum(o['fare'] for o in orders if o['stage']=='DONE' and o['paid']),
        'refundAmount':sum(o.get('refundAmount',0)+sum(r['amount'] for r in o.get('refundHistory',[])) for o in orders),
    }
    for key,value in expected_stats.items():
        check('statistics_'+key,stats.get(key),value)
    report['statistics']={key:stats.get(key) for key in expected_stats}
    check('ledger_ids_match_canonical',{p['orderId'] for p in ledger}=={o['id'] for o in orders},True)
    check('ledger_row_count',len(ledger),len(orders))
    for label,oid,expected_fare in [('standard',standard_id,standard_fare),('bid',bid_id,bid_fare)]:
        matches=[o for o in orders if o['id']==oid]
        check(label+'_unique_order',len(matches),1)
        if len(matches)!=1:continue
        o=matches[0]
        check(label+'_kind',o.get('kind'),'STANDARD' if label=='standard' else 'BID')
        check(label+'_stage',o.get('stage'),'DONE' if label=='standard' else 'CANCELLED')
        check(label+'_payment_status',o.get('paymentStatus'),'SUCCESS' if label=='standard' else 'REFUNDED')
        check(label+'_paid',o.get('paid'),label=='standard')
        check(label+'_credited',o.get('credited'),label=='standard')
        if expected_fare is not None:check(label+'_expected_fare',o.get('fare'),expected_fare)
        current=o.get('refundAmount',0);history=sum(r['amount'] for r in o.get('refundHistory',[]))
        if label=='bid':
            check('bid_refund_equals_fare',current,o['fare'])
            check('bid_refund_timestamp_present',bool(o.get('refundedAt')),True)
            check('bid_single_round_no_previous_refund',history,0)
        else:
            check('standard_evaluation_rating',(o.get('evaluation') or {}).get('rating'),5)
        rows=[p for p in ledger if p['orderId']==oid]
        check(label+'_ledger_unique',len(rows),1)
        if len(rows)==1:
            row=rows[0]
            for key in ('fare','paid','paymentStatus','driverId','passengerId'):
                # Account identifiers are compared as booleans, never copied to report.
                check(label+'_ledger_'+key,row.get(key)==o.get(key),True)
            for key,value in [('currentRefundAmount',current),('historicalRefundAmount',history),('refundAmount',current+history)]:
                check(label+'_ledger_'+key,row.get(key),value)
        counts={}
        for event in o.get('events',[]):
            event_type=event.get('type')
            counts[event_type]=counts.get(event_type,0)+1
        unique=['PASSENGER_CREATED','DRIVER_ACCEPT','PASSENGER_EVALUATE'] if label=='standard' else ['PASSENGER_CREATED','PASSENGER_SELECTDRIVER','DRIVER_CANCEL','PASSENGER_CONTINUE']
        if label=='standard' and complaint_id:unique+=['PASSENGER_COMPLAINT','ADMIN_COMPLAINT_REPLIED']
        for event in unique:check(label+'_once_'+event,counts.get(event,0),1)
        report['orders'][label]={'id':oid,'kind':o.get('kind'),'stage':o.get('stage'),'fare':o.get('fare'),
            'paymentStatus':o.get('paymentStatus'),'paid':o.get('paid'),'credited':o.get('credited'),
            'currentRefundAmount':current,'historicalRefundAmount':history,
            'checkedEventCounts':{key:counts.get(key,0) for key in unique}}
        if label=='standard' and complaint_id:
            complaints=[c for c in o.get('complaints',[]) if c.get('id')==complaint_id]
            check('complaint_on_standard_unique',len(complaints),1)
            check('complaint_status',complaints[0].get('status') if len(complaints)==1 else None,'REPLIED')
            records=[s for s in support if s.get('id')==complaint_id and s.get('source')=='ORDER_COMPLAINT']
            check('support_complaint_unique',len(records),1)
            check('support_same_order',records[0].get('orderId') if len(records)==1 else None,standard_id)
            check('support_replied',records[0].get('status') if len(records)==1 else None,'REPLIED')
            if len(complaints)==1 and len(records)==1:
                check('support_reply_consistent',bool(complaints[0].get('reply')) and complaints[0].get('reply')==records[0].get('reply'),True)
            report['orders'][label]['complaintId']=complaint_id
    if not complaint_id:report['notes'].append('未提供投诉ID，投诉处理断言未执行；不自动猜测或创建投诉。')
    report['passed']=all(a['passed'] for a in report['assertions'])
    return report


def main():
    parser=argparse.ArgumentParser(description='两端UI候选只读对账（不会创建订单或处理投诉）')
    parser.add_argument('--standard-id',required=True,type=identifier)
    parser.add_argument('--bid-id',required=True,type=identifier)
    parser.add_argument('--complaint-id',type=identifier)
    parser.add_argument('--standard-fare',type=amount,help='预期标准单金额，整数分')
    parser.add_argument('--bid-fare',type=amount,help='预期选定竞价金额，整数分')
    args=parser.parse_args()
    result={'checkedAt':dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(),
            'mode':'read-only canonical/ledger; login/state/logout only', 'passed':False,'logoutSucceeded':False}
    session=None
    try:
        accounts=json.loads((ROOT/'.private'/'qa-accounts.json').read_text())
        operator=next((a for a in accounts if a.get('role')=='OPERATOR'),None)
        if not operator:raise ReadbackError('operator_account_unavailable')
        auth=request('login',data={'username':operator['username'],'password':operator['password']})
        session=auth.get('session')
        if not isinstance(session,str) or not session:raise ReadbackError('login_session_missing')
        if auth.get('user',{}).get('role')!='OPERATOR':raise ReadbackError('operator_role_mismatch')
        del accounts,operator,auth
        result.update(inspect_state(request('state',session=session),args.standard_id,args.bid_id,args.complaint_id,args.standard_fare,args.bid_fare))
    except Exception as exc:
        result['error']=str(exc) if isinstance(exc,ReadbackError) else type(exc).__name__
        result['passed']=False
    finally:
        if session:
            try:result['logoutSucceeded']=request('logout',session=session,data={}).get('loggedOut') is True
            except Exception as exc:result['logoutError']=str(exc) if isinstance(exc,ReadbackError) else type(exc).__name__
            session=None
        result['passed']=result['passed'] and result['logoutSucceeded']
        output=ROOT/'qa'/'ui-redesign-readback.json'
        if output.exists():
            output=output.with_name('ui-redesign-readback-'+dt.datetime.now().strftime('%Y%m%dT%H%M%S%f')+'.json')
        result['evidenceFile']=output.name
        raw=json.dumps(result,ensure_ascii=False,indent=2).encode()
        save_private(output,raw)
        print(raw.decode())
    return 0 if result['passed'] else 1

if __name__=='__main__':sys.exit(main())
