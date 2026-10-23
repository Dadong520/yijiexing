#!/usr/bin/env python3
"""Verify the jointly operated UI order through read-only admin state.

Credentials and pairing codes are never retained in the evidence.
"""
import datetime as dt
import json
from pathlib import Path
from cloud_admin_readback import request, save_private

ROOT = Path(__file__).resolve().parents[1]
ORDER_ID = 'NEG1bafda5d1f03'

def main():
    account = next(a for a in json.loads((ROOT/'.private/qa-accounts.json').read_text()) if a['role']=='OPERATOR')
    auth = request('login',data={'username':account['username'],'password':account['password']})
    session = auth['session']
    del account, auth
    try:
        state = request('state',session=session)
        matches = [o for o in state['orders'] if o['id']==ORDER_ID]
        assert len(matches)==1, 'Expected exactly one canonical negotiated order'
        order = matches[0]
        payment = next(p for p in state['payments'] if p['orderId']==ORDER_ID)
        safe = {k:order.get(k) for k in ['id','kind','stage','fare','paid','credited','paymentStatus','passengerId','driverId','quoteVersion','negotiationStatus','quoteHistory','priceDisputes','events']}
        checks = {
            'stage_done':order['stage']=='DONE',
            'fare_2600_cents':order['fare']==2600,
            'paid':order['paid'] is True,
            'payment_ledger_fare':payment['fare']==2600,
            'quote_v2':order.get('quoteVersion')==2,
            'both_quote_versions_retained':len(order.get('quoteHistory',[]))==2,
            'price_dispute_retained':len(order.get('priceDisputes',[]))==1,
        }
        result = {'checkedAt':dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(),
            'scope':'Read-only final cloud state for separately recorded real dual-App/browser UI test',
            'order':safe,'assertions':checks,'passed':all(checks.values())}
    finally:
        logged_out = request('logout',session=session,data={}).get('loggedOut') is True
        session = None
    result['logoutSucceeded'] = logged_out
    result['passed'] &= logged_out
    save_private(ROOT/'qa/negotiation-admin-final.json',json.dumps(result,ensure_ascii=False,indent=2).encode())
    print(json.dumps({'orderId':ORDER_ID,'passed':result['passed'],'assertions':checks,'logoutSucceeded':logged_out},ensure_ascii=False))
    return 0 if result['passed'] else 1

if __name__=='__main__':
    raise SystemExit(main())
