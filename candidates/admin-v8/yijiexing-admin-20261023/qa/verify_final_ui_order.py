#!/usr/bin/env python3
"""Read-only final BID cancellation checks; no business actions are sent."""
import datetime as dt
import json
import re
import sys
from pathlib import Path
from cloud_admin_readback import request, save_private

ROOT=Path(__file__).resolve().parents[1]

def main(order_id):
    if not re.fullmatch(r'PORD[0-9a-f]{12}',order_id):
        raise SystemExit('Invalid test order ID')
    account=next(a for a in json.loads((ROOT/'.private/qa-accounts.json').read_text()) if a['role']=='OPERATOR')
    session=request('login',data={'username':account['username'],'password':account['password']})['session']
    del account
    try:
        state=request('state',session=session)
        orders=[o for o in state['orders'] if o['id']==order_id]
        assert len(orders)==1, 'Expected one canonical order'
        order=orders[0]
        ledger=[p for p in state['payments'] if p['orderId']==order_id]
        assert len(ledger)==1, 'Expected one ledger row'
        checks={
            'cancelled':order['stage']=='CANCELLED',
            'fare_3600':order['fare']==3600,
            'refunded_3600':order.get('refundAmount')==3600,
            'refund_status':order.get('paymentStatus')=='REFUNDED',
            'not_paid':order.get('paid') is False,
            'not_credited':order.get('credited') is False,
            'ledger_refund_3600':ledger[0].get('refundAmount')==3600,
            'no_active_orders':state['statistics'].get('activeCount')==0,
        }
        safe={k:order.get(k) for k in ['id','kind','stage','fare','paid','credited','paymentStatus','refundAmount','refundedAt','refundHistory','events','cancelReason']}
        stats={k:state['statistics'].get(k) for k in ['orderCount','completedCount','cancelledCount','activeCount','paidFare','refundAmount']}
        result={'checkedAt':dt.datetime.now().astimezone().isoformat(),'scope':'Final real dual-App UI order, independently read through admin; read-only business data','order':safe,'statistics':stats,'assertions':checks,'passed':all(checks.values())}
    finally:
        logged_out=request('logout',session=session,data={}).get('loggedOut') is True
        session=None
    result['logoutSucceeded']=logged_out
    result['passed'] &= logged_out
    save_private(ROOT/'qa/final-ui-order-readback.json',json.dumps(result,ensure_ascii=False,indent=2).encode())
    print(json.dumps({'orderId':order_id,'passed':result['passed'],'statistics':stats,'assertions':checks},ensure_ascii=False))
    return 0 if result['passed'] else 1

if __name__=='__main__':
    raise SystemExit(main(sys.argv[1]))
