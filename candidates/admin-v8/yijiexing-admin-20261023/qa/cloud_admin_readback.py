#!/usr/bin/env python3
"""Read-only cloud acceptance through the fixed local admin proxy.
Only login/logout, state reads and exports. Never invokes a business action.
Reads credentials solely from ../.private/qa-accounts.json; never prints secrets.
"""
import csv
import datetime as dt
import io
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:8790/api/local/admin/v2/'
ORDER_ID = 'PORD29d6c69f4de0'
COMPLAINT_ID = 'PCfe37daedf4a7'
SUMMARY = ROOT / 'qa' / 'cloud-admin-readback-summary.json'

class ReadbackError(Exception):
    pass

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ReadbackError('redirect_denied')

OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

def request(endpoint, session=None, data=None, csv_response=False):
    # Fixed endpoint allowlist prevents accidental arbitrary requests or mutations.
    if endpoint not in {'login', 'logout', 'state', 'export?dataset=orders', 'export?dataset=payments'}:
        raise ReadbackError('endpoint_denied')
    if (endpoint in {'login','logout'}) != (data is not None):
        raise ReadbackError('method_denied')
    headers = {'Content-Type':'application/json'}
    if session:
        headers['X-Admin-Session'] = session
    req = urllib.request.Request(BASE + endpoint, headers=headers,
            data=json.dumps(data).encode() if data is not None else None)
    try:
        with OPENER.open(req, timeout=45) as response:
            raw = response.read()
            if response.status != 200:
                raise ReadbackError('unexpected_http_status')
    except urllib.error.HTTPError as exc:
        status = exc.code
        exc.close()
        raise ReadbackError('http_' + str(status)) from None
    except urllib.error.URLError:
        raise ReadbackError('local_proxy_unavailable') from None
    if csv_response:
        return raw
    obj = json.loads(raw)
    if obj.get('code') != 0 or not isinstance(obj.get('data'), dict):
        raise ReadbackError('invalid_response_envelope')
    return obj['data']

def save_private(path, data):
    # Export files may contain account IDs; keep them private on this computer.
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, 'wb') as file:
            file.write(data)
    except BaseException:
        try:
            os.close(fd)
        except OSError:
            pass
        raise

def main():
    result = {'checkedAt':dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(),
              'environment':'isolated cloud through local proxy 8790',
              'mode':'read-only business data; authenticated login/logout and audited exports',
              'testOrder':{'id':ORDER_ID,'expectedStage':'DONE','expectedFare':2926,'expectedRating':5,'complaintId':COMPLAINT_ID},
              'assertions':[], 'statistics':{}, 'exports':[], 'logoutSucceeded':False}
    session = None
    def check(name, actual, expected):
        result['assertions'].append({'name':name,'passed':actual == expected,'actual':actual,'expected':expected})
    try:
        accounts = json.loads((ROOT / '.private' / 'qa-accounts.json').read_text())
        operator = next((a for a in accounts if a.get('role') == 'OPERATOR'), None)
        if not operator:
            raise ReadbackError('operator_account_unavailable')
        auth = request('login', data={'username':operator['username'],'password':operator['password']})
        session = auth.get('session')
        if not isinstance(session,str) or not session:
            raise ReadbackError('login_session_missing')
        check('operator_role', auth.get('user',{}).get('role'), 'OPERATOR')
        # Do not retain credential objects in the report or any diagnostic output.
        del operator, accounts, auth
        state = request('state', session=session)
        orders = state['orders']; payments = state['payments']; stats = state['statistics']
        selected = [o for o in orders if o['id'] == ORDER_ID]
        check('target_order_unique',len(selected),1)
        if len(selected) != 1:
            raise ReadbackError('target_order_unavailable')
        order = selected[0]
        check('target_stage',order['stage'],'DONE')
        check('target_fare_cents',order['fare'],2926)
        check('target_evaluation_rating',(order.get('evaluation') or {}).get('rating'),5)
        complaints = [c for c in order.get('complaints',[]) if c['id'] == COMPLAINT_ID]
        check('target_complaint_unique',len(complaints),1)
        check('target_complaint_status',complaints[0].get('status') if len(complaints)==1 else None,'REPLIED')
        support = [s for s in state['support'] if s.get('source')=='ORDER_COMPLAINT' and s.get('id')==COMPLAINT_ID]
        check('support_complaint_unique',len(support),1)
        check('support_order_association',support[0].get('orderId') if len(support)==1 else None,ORDER_ID)
        check('support_reply_status',support[0].get('status') if len(support)==1 else None,'REPLIED')
        expected = {'orderCount':len(orders),
                    'completedCount':sum(o['stage']=='DONE' for o in orders),
                    'cancelledCount':sum(o['stage']=='CANCELLED' for o in orders),
                    'activeCount':sum(o['stage'] not in ('DONE','CANCELLED') for o in orders),
                    'paidFare':sum(o['fare'] for o in orders if o['stage']=='DONE' and o['paid']),
                    'refundAmount':sum(o.get('refundAmount',0)+sum(r['amount'] for r in o.get('refundHistory',[])) for o in orders)}
        for field, value in expected.items():
            check('statistics_'+field,stats.get(field),value)
        result['statistics'] = {k:stats.get(k) for k in expected}
        result['statistics']['definition'] = 'Canonical passenger_orders only; paidFare = DONE and paid; refundAmount = current + every historical round, cents.'
        check('canonical_order_ids_unique',len({o['id'] for o in orders}),len(orders))
        ledger_by_id = {p['orderId']:p for p in payments}
        check('payment_ledger_row_count',len(payments),len(orders))
        check('payment_ledger_unique',len(ledger_by_id),len(payments))
        check('payment_ledger_order_set',set(ledger_by_id)=={o['id'] for o in orders},True)
        aggregate_valid = True
        for o in orders:
            ledger = ledger_by_id.get(o['id'],{})
            current = o.get('refundAmount',0)
            historical = sum(r['amount'] for r in o.get('refundHistory',[]))
            aggregate_valid &= (ledger.get('fare')==o['fare'] and ledger.get('refundAmount')==current+historical
                                and ledger.get('currentRefundAmount')==current and ledger.get('historicalRefundAmount')==historical)
        check('all_payment_amounts_and_refund_rounds',aggregate_valid,True)
        for dataset in ('orders','payments'):
            raw = request('export?dataset='+dataset,session=session,csv_response=True)
            filename = 'cloud-admin-readback-'+dataset+'.csv'
            save_private(ROOT/'qa'/filename,raw)
            rows = list(csv.DictReader(io.StringIO(raw.decode('utf-8-sig'))))
            id_field = 'id' if dataset=='orders' else 'orderId'
            matching = [r for r in rows if r.get(id_field)==ORDER_ID]
            check('export_'+dataset+'_row_count',len(rows),len(orders))
            check('export_'+dataset+'_target_unique',len(matching),1)
            check('export_'+dataset+'_target_fare',int(matching[0]['fare']) if len(matching)==1 else None,2926)
            if dataset=='payments':
                check('export_payments_refund_total',sum(int(r['refundAmount']) for r in rows),expected['refundAmount'])
            result['exports'].append({'file':filename,'rows':len(rows),'targetOrderFound':len(matching)==1})
        result['serverTime'] = state.get('serverTime')
    except Exception as exc:
        # Never print exception bodies: HTTP/auth errors could include secrets.
        result['error'] = str(exc) if isinstance(exc,ReadbackError) else type(exc).__name__
    finally:
        if session:
            try:
                result['logoutSucceeded'] = request('logout',session=session,data={}).get('loggedOut') is True
            except Exception as exc:
                result['logoutError'] = str(exc) if isinstance(exc,ReadbackError) else type(exc).__name__
            session = None
        result['passed'] = not result.get('error') and result['logoutSucceeded'] and all(a['passed'] for a in result['assertions'])
        encoded = json.dumps(result,ensure_ascii=False,indent=2).encode()
        save_private(SUMMARY,encoded)
        print(encoded.decode())
    return 0 if result['passed'] else 1

if __name__ == '__main__':
    sys.exit(main())
