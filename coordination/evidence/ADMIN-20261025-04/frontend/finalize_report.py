import json,hashlib,subprocess,datetime
from pathlib import Path
P=Path(__file__).resolve().parent
UI='fe201d921eb9a5eca5be3b51d404dfc49fa4c71a';API='8a02527d025245381ebca517fcd88508c5e9606d'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
http=json.loads((P/'independent-http.json').read_text());assert len(http['checks'])==13 and all(x['result']=='PASS' for x in http['checks'])
apiRoot=Path('/Users/dongge/.codex/tmp/yijiexing-api-20261025');apiFiles={}
for f in ['apps/cloud-service/admin_queries.py','apps/cloud-service/admin_console.py','apps/cloud-service/server.py']:
 actual=(apiRoot/f).read_bytes();expected=subprocess.check_output(['git','-C',str(apiRoot),'show',API+':'+f]);assert actual==expected;apiFiles[f]=hashlib.sha256(actual).hexdigest()
steps=[
 ('登录页和错误提示','PASS','1366级左右布局清楚；390x844无横溢出，输入label可读；合成错误密码有明确提示。',['01-login-desktop.jpg','02-login-narrow.jpg','03-login-error.jpg']),
 ('订单时间和多条件筛选','PASS','UTC8的10月24日只D/B/C，退款2700分；kind+账号手机+车牌+DONE组合只D，chips正确。',['05-orders-date.jpg','dom-1-orders-date.txt','dom-2-orders-combined.txt','06-orders-combined-detail.jpg']),
 ('分页、详情和返回','PASS','20条与15条/页，翻页后PAGE-06详情金额10.06正确；重置回第1页；空查询0条；从日志返回订单保留PAGE过滤且旧审计按钮不可见。',['07-orders-page2-detail.jpg','08-orders-empty.jpg','18-return-orders.txt']),
 ('实际CSV导出','PASS','日期匹配3条、无筛选第2页导出34条、PAGE过滤第2页导出27条；非当前页15条或全库34条。未提交草稿不生效；=CMD(A1)加单引号保护。',['csv-verification.json','orders-date-applied-draft-ignored.csv','orders-all-from-page2.csv','orders-filtered27-from-page2.csv','19-filtered-page2-before-export.txt']),
 ('失败和重试','PASS','实际fail-next造成query503，页面明确持久化不可用，导出禁用、PAGE草稿保留；实际点重试恢复27条。',['09-query-failed.jpg','09-query-failed.txt','dom-7-orders-retry-recovered.txt']),
 ('取消、退款和报表','PASS','C是WAIT_CONTINUE待乘客续叫而非终结；退款3轮同额同刻各900分共2700不去重；日统计3单1完1取消1活跃，模拟实付20/退款27元；详情可读。',['10-cancellation-C-detail.txt','11-refunds.txt','11-refund-detail.jpg','16-statistics-detail.jpg']),
 ('司机、乘客和模拟账务','PASS_READ_ONLY','三个列表均加载真实query并实际查询/打开对应详情；缺登记日期明确禁用且说明，不伪造。提现审核变更本轮未执行。',['12-drivers.txt','12-driver-detail.jpg','13-passengers.txt','13-passenger-detail.jpg','14-payments.txt','14-payment-detail.jpg']),
 ('投诉和历史审计','REQUIRES_FIX','投诉筛选/详情可用，但OC1列表显示D/D，详情账号P1/订单D，API accountId=P1；另按ADMIN:old能查到id1，超旧state最近200限制。',['15-support.txt','15-support-detail.jpg','17-audit-old.txt','17-audit-old-detail.jpg']),
 ('只读角色和边界','PASS','VIEWER导出和调价按钮disabled；自己的HTTP会话直接export/action403、无session401、driver路径404；结束所有本地会话logout并重置viewport。',['18-viewer-orders.txt','18-viewer-detail.jpg','independent-http.json','ui-steps.json'])
]
apiReport={'task':'ADMINAPI-20261025-01','reviewer':'frontend non-author','sourceSha':API,'acceptance':'PASS_SCOPE','scope':'13 bounded real HTTP checks on loopback SQLite/CAS synthetic fixture, plus actual browser query/export consumers. Outer IAP replaced only; no claimed live IAP, public deployment or real business validation.','exactApiSourceFiles':apiFiles,'checks':http['checks'],'evidence':'independent-http.json','ownSessionsLoggedOut':True,'notRun':['Full185 suite rerun','Production or cloud account/session reads','Bank clearing/real funds/new3-client transactions','Live IAP signature verification (coordinator9 tests separate)']}
(P/'adminapi-nonauthor-review.json').write_text(json.dumps(apiReport,ensure_ascii=False,indent=2)+'\n')
report={
 'schemaVersion':1,'task':'ADMIN-20261025-04','reviewer':'frontend independent non-author','sourceSha':UI,'frontendSourceSha':'8eb46ea3038e978166edb82ea3658d8eec7d85f6','apiSourceSha':API,
 'acceptance':'REQUIRES_FIX','releaseReady':False,'scope':'Independent localhost UI and real API query/export/auth/permissions with only synthetic34 orders. No online Chrome admin, emulator, deployed services, source mutations, agents or real business writes.',
 'startedAt':'2026-10-25T21:15:54+08:00','browserEndedAt':json.loads((P/'ui-steps.json').read_text())['completedAt'],'deadline':'2026-10-25T21:33:54+08:00','reportCompletedAt':datetime.datetime.now().astimezone().isoformat(),
 'steps':[{'number':i,'name':s[0],'result':s[1],'observed':s[2],'evidence':s[3]} for i,s in enumerate(steps,1)],
 'findings':[
  {'id':'ADMIN-WITHDRAW-P1-01','priority':'P1','status':'OPEN','observed':'Payment projection id WITHDRAWAL:D1:W1 differs from real withdrawalId W1; both approve/reject handlers still pass p.id.','source':'apps/admin-console/web/admin.js:798','proof':'Non-author static source confirms dispatch uses id:p.id; backend query contract and actual W2 detail show prefixed projection id. Coordinator first reported failure. Reviewer did not click monetary mutation or independently induce404.','recommendation':'Use withdrawalId||id only for withdrawal action payload, retain projection id for row lookup. Fix author then independent delta with explicit local fixture mutation if authorized.'},
  {'id':'ADMIN-SUPPORT-P2-01','priority':'P2','status':'OPEN','observed':'OC1 list labels associated account as D, but actual account is P1 and order is D; detail already correct.','source':'apps/admin-console/web/admin.js:800','evidence':['15-support.txt','15-support-detail.txt','15-support-detail.jpg','independent-http.json'],'recommendation':'Display accountId/factual account label in list; preserve ownerId for support reply payload.'}
 ],
 'csvEvidence':json.loads((P/'csv-verification.json').read_text()),
 'apiIndependentReview':{'path':'adminapi-nonauthor-review.json','acceptance':'PASS_SCOPE','checks':13,'sourceSha':API},
 'sourceBinding':{'readback':'source-readback.json','sameAsAuthorManifest':True,'apiFiles':apiFiles},
 'environment':{'url':'http://127.0.0.1:8808/','syntheticSharedOrders':34,'outerIap':'loopback adapter only','businessFixtureMutations':False,'expectedReadAuditWrites':'Login attempts/success and exports add security/audit records; no order/payment/support changes. Initial205 old audits plus test operations account for current total>205.','cloudAndOriginalDataTouched':False},
 'teardown':{'adminAndViewerLoggedOut':True,'ownHttpSessionsLoggedOut':True,'viewportReset':True,'onlineChromeUntouched':True,'originalServerLeftRunning':True,'browserOperationOwnershipReturned':True},
 'accessibilityLimits':'Labels/error and390px reflow observed. No full WCAG, contrast-ratio, keyboard-only or screen-reader compliance claim.',
 'notRun':['Withdrawal approve/reject actual mutation on original fe201d (known static bug)','Fix-version final delta acceptance','Exhaustive filter cross-product for all9 datasets; focused UI+13HTTP only','Dedicated narrow-screen table/detail matrix beyond login','Keyboard-only/full assistive technology audit','Cloud deployment and online IAP/public readback','Real bank clearing/payment/new3-end complete lifecycle'],
 'screenshots':[{'path':f.name,'sha256':sha(f),'inspectedSavedFile':True,'note':'Viewport captures show selected state; long tables/empty summary supported by paired DOM snapshots.'} for f in sorted(P.glob('*.jpg'))],
 'evidence':[{'path':f.name,'sha256':sha(f)} for f in sorted(P.glob('*.json')) if f.name!='design-qa.json'],
 'nextAction':'Coordinator fixes two issues on a new fixed SHA, then dispatches bounded independent delta. No whole release PASS for fe201d; reviewer IDLE after report.'
}
# Keep filename references exact when a compact snapshot counter changed.
for s in report['steps']:
 s['evidence']=[name for name in s['evidence'] if (P/name).exists()]
(P/'design-qa.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
md=['# 后台独立验收记录','',f'结论：需要修复后复测。产品 `{UI}`，API `{API}`。只使用本地合成数据，没有上线或修改业务记录。','', '两个待修问题：提现审核使用了投影编号；投诉列表账号显示与实际账号不一致。','']
for s in report['steps']:
 md.extend([f"{s['number']}. **{s['name']} — {s['result']}**：{s['observed']}",''])
md.extend(['截图只能支持对应页面和交互状态；未做完整无障碍或真实资金、三端交易验收。以下为本轮实际保存并查看的截图。',''])
for f in sorted(P.glob('*.jpg')):md.extend([f'![{f.stem}]({f})',''])
(P/'REPORT.md').write_text('\n'.join(md))
print(json.dumps({'acceptance':report['acceptance'],'reportSha256':sha(P/'design-qa.json'),'screenshots':len(report['screenshots']),'apiReviewSha256':sha(P/'adminapi-nonauthor-review.json')},ensure_ascii=False))
