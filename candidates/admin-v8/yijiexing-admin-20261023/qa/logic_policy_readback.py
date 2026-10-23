#!/usr/bin/env python3
"""Read-only release checks: preserve pre-existing user records and canonical IDs."""
import datetime as dt
import json
from cloud_admin_readback import ROOT, request, save_private


def main():
    before=json.loads((ROOT/'.private/logic-audit-current-state.json').read_text())
    accounts=json.loads((ROOT/'.private/qa-accounts.json').read_text())
    account=next(a for a in accounts if a['role']=='OPERATOR')
    session=request('login',data={'username':account['username'],'password':account['password']})['session']
    checks=[]
    def check(name,value):checks.append({'name':name,'passed':bool(value)})
    report={'checkedAt':dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(),'mode':'read-only; admin login/state/logout only'}
    try:
        after=request('state',session=session)
        check('all_preexisting_canonical_orders_preserved',{o['id'] for o in before['orders']} <= {o['id'] for o in after['orders']})
        check('canonical_ids_unique',len(after['orders'])==len({o['id'] for o in after['orders']}))
        old=next(d for d in before['drivers'] if d['mobile'].endswith('0001'))
        new=next(d for d in after['drivers'] if d['id']==old['id'])
        previous=next(o for o in old['orders'] if o['id']=='YC1001')
        current=next(o for o in new['orders'] if o['id']=='YC1001')
        fields=('id','kind','stage','fare','paid','credited','updatedAt')
        check('user_001_pending_order_unchanged',all(current.get(k)==previous.get(k) for k in fields))
        check('user_001_wallet_unchanged',old['balance']==new['balance'])
        check('user_001_private_example_not_promoted_to_shared',not current.get('shared') and current.get('synthetic') is True)
        orders=after['orders'];ledger=after['payments']; rows={p['orderId']:p for p in ledger}
        check('ledger_matches_canonical_ids',len(ledger)==len(rows)==len(orders) and set(rows)=={o['id'] for o in orders})
        check('ledger_all_order_amounts_match',all(rows[o['id']]['fare']==o['fare'] and rows[o['id']]['paid']==o['paid'] for o in orders))
        check('ledger_refund_totals_match',all(rows[o['id']]['refundAmount']==o.get('refundAmount',0)+sum(x['amount'] for x in o.get('refundHistory',[])) for o in orders))
        stats=after['statistics']
        check('paid_total_matches',stats['paidFare']==sum(o['fare'] for o in orders if o['stage']=='DONE' and o['paid']))
        report.update({'checks':checks,'statistics':stats,'protectedOrder':{k:current.get(k) for k in fields},'protectedWallet':new['balance']})
    finally:
        report['logoutSucceeded']=request('logout',session=session,data={}).get('loggedOut') is True
    report['passed']=report['logoutSucceeded'] and all(c['passed'] for c in checks)
    output=ROOT/'qa/logic-audit-20261024'/('release-readback-'+dt.datetime.now().strftime('%H%M%S')+'.json')
    save_private(output,json.dumps(report,ensure_ascii=False,indent=2).encode())
    print(json.dumps({'report':str(output),'passed':report['passed'],'checks':len(checks),'statistics':report['statistics']},ensure_ascii=False))
    return 0 if report['passed'] else 1

if __name__=='__main__':raise SystemExit(main())
