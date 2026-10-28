"""Offline synthetic document audit. Does not import or execute product services."""
from decimal import Decimal as D, ROUND_HALF_UP, ROUND_DOWN
from pathlib import Path
import hashlib, json, subprocess

OUT = Path(__file__).resolve().parent
ROOT = Path('/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027')
BASE = 'b77e8f7af03bd2a8af535de34bd13d5acd0a387a'
RECEIPT = 'c3c876ef3359f8bc4bb306f515637e1fc72ca621'
Q = D('.01')

def round_money(x):
    return x.quantize(Q, rounding=ROUND_HALF_UP)

def case(label, coupon, rounded, fare='100', service='10', map_fee='.30', platform_ratio='.60', company_ratio='.40'):
    f, s, m, a, b = map(D, (fare, service, map_fee, platform_ratio, company_ratio))
    c = D(coupon)
    p = f + s - c
    rnd = round_money if rounded else lambda x: x
    fare_pay, fare_split = rnd(f * D('.006')), rnd(f * D('.0015'))
    paid_pay, paid_split = rnd(p * D('.006')), rnd(p * D('.0015'))
    delta = paid_pay + paid_split - fare_pay - fare_split
    commissionable = s - delta  # Most charitable interpretation of the undefined wording.
    driver = f - fare_pay - fare_split - m
    company = rnd(commissionable * b)
    platform_doc = m + rnd(commissionable * a) - delta
    gateway = p - paid_pay + c - paid_split
    recipients_doc = driver + company + platform_doc
    # Candidate ONLY: no second delta deduction; residual is assigned to platform.
    # BIZ-07: for these non-negative bases, company gets whole fen and the
    # fractional fen goes to platform; provider fee rounding is still unverified.
    company_candidate = (commissionable*b).quantize(Q, rounding=ROUND_DOWN) if rounded else commissionable*b
    platform_candidate = m + commissionable - company_candidate
    recipients_candidate = driver + company_candidate + platform_candidate
    result = dict(label=label, mode='PER_FEE_HALF_UP_FEN' if rounded else 'EXACT_DECIMAL',
        fare=f, service=s, coupon=c, passenger_paid=p, map_fee=m,
        fare_payment_fee=fare_pay, fare_split_fee=fare_split,
        paid_payment_fee=paid_pay, paid_split_fee=paid_split,
        delta=delta, commissionable=commissionable, driver=driver,
        company=company, platform_document=platform_doc, gateway_split=gateway,
        document_recipient_total=recipients_doc,
        document_cash_residual=recipients_doc-gateway,
        document_commission_identity_residual=platform_doc+company-m-commissionable,
        document_final_identity_residual=recipients_doc+delta+fare_pay+fare_split-(f+s),
        company_candidate=company_candidate, platform_candidate=platform_candidate, candidate_cash_residual=recipients_candidate-gateway,
        candidate_commission_identity_residual=platform_candidate+company_candidate-m-commissionable,
        candidate_final_identity_residual=recipients_candidate+delta+fare_pay+fare_split-(f+s))
    assert recipients_doc - gateway == -delta
    assert all(result[k] == 0 for k in ('candidate_cash_residual',
        'candidate_commission_identity_residual', 'candidate_final_identity_residual'))
    return {k: str(v) if isinstance(v, D) else v for k, v in result.items()}

cases = [case(label, coupon, rounded) for rounded in (False, True)
         for label, coupon in [('NO_COUPON','0'), ('SMALL_COUPON','5'),
                               ('LARGE_COUPON','20'), ('ZERO_DELTA_CONTROL','10')]]
cases.extend(case('COORDINATOR_100_40_0_1_50_50', '0', rounded,
    service='40', map_fee='1', platform_ratio='.50', company_ratio='.50')
    for rounded in (False, True))
assert sum(D(c['document_cash_residual']) != 0 for c in cases) == 8
small = D('.67')
fee_rounding = {'paid':'0.67', 'payment_fee':str(round_money(small*D('.006'))),
    'split_fee':str(round_money(small*D('.0015'))),
    'separately_rounded_total':str(round_money(small*D('.006'))+round_money(small*D('.0015'))),
    'combined_rounded_total':str(round_money(small*D('.0075')))}
assert fee_rounding['separately_rounded_total'] != fee_rounding['combined_rounded_total']
tie = {'commissionable':'0.01','platform_ratio':'0.5','company_ratio':'0.5',
    'independent_half_up_platform':str(round_money(D('.005'))),
    'independent_half_up_company':str(round_money(D('.005'))),
    'independent_half_up_total':'0.02',
    'company_half_up_then_platform_remainder':{'company':'0.01','platform':'0.00'},
    'company_down_then_platform_remainder':{'company':'0.00','platform':'0.01'},
    'decision':'BIZ-07 confirms fractional fen to platform for simulation only; company HALF_UP would not satisfy that direction. Negative bases remain undefined.'}
result = {'classification':'DOCUMENT_FORMULA_FAIL_UNDER_STATED_INTERPRETATION',
    'scope':'BIZ-07 single deduction and platform residual confirmed for simulation only; not production code or provider reconciliation.',
    'assumptions':['coupon full face amount is added from an external subsidy source',
        'platform/company ratios sum to one; synthetic 60/40 or 50/50, not a business default',
        'undefined difference-fee wording means delta including both fee components',
        'rounded cases round each fee to integer fen HALF_UP; provider rule NOT_VERIFIED'],
    'cases':cases,'rounding_example':fee_rounding,'one_fen_tie':tie,
    'assertions':'PASS: 10 candidate closures, 8 document counterexamples, 2 zero-delta controls, fee-rounding difference.'}
(OUT/'FORMULA-CASES.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')

def git(*args):
    return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()

assert git('rev-parse','HEAD') == BASE
manifest = {p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest()
    for p in git('ls-files','apps/cloud-service').splitlines() if (ROOT/p).is_file()}
binding = {'sourceRoot':str(ROOT),'branch':git('branch','--show-current'),'head':BASE,
    'receiptMain':RECEIPT,'localMainObserved':git('rev-parse','main'),
    'localMainNote':'Local main is stale; no fetch/checkout/mutation performed.',
    'headBackendTree':git('rev-parse',BASE+':apps/cloud-service'),
    'receiptBackendTree':git('rev-parse',RECEIPT+':apps/cloud-service'),
    'productStatus':git('status','--porcelain','--','apps/cloud-service'),
    'files':manifest,
    'sourceTextSHA256':hashlib.sha256((OUT.parent/'SOURCE_TEXT.txt').read_bytes()).hexdigest()}
assert binding['headBackendTree'] == binding['receiptBackendTree']
assert not binding['productStatus']
(OUT/'source-binding.json').write_text(json.dumps(binding,ensure_ascii=False,indent=2)+'\n')
print(result['assertions'])
for c in cases:
    if c['mode']=='PER_FEE_HALF_UP_FEN':
        print(c['label'], 'delta='+c['delta'], 'doc_total='+c['document_recipient_total'],
              'gateway='+c['gateway_split'], 'residual='+c['document_cash_residual'])
print('Product binding PASS; no product execution or cloud/data access.')
