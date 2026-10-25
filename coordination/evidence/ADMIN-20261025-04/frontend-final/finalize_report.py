from pathlib import Path
import json,hashlib,subprocess,csv,datetime,difflib
P=Path(__file__).resolve().parent;ROOT=P.parents[3]
OLD='fe201d921eb9a5eca5be3b51d404dfc49fa4c71a';NEW='0d7dfc045a137ee93b354acd669edebf6724af29';API='8a02527d025245381ebca517fcd88508c5e9606d'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
assert git('diff','--name-only',OLD,NEW).decode().splitlines()==['apps/admin-console/web/admin.js']
path='apps/admin-console/web/admin.js';old=git('show',OLD+':'+path).decode();new=git('show',NEW+':'+path).decode();assert (ROOT/path).read_text()==new
assert new.count('id:p.withdrawalId||p.id')==2
reply="'support.reply',{id:s.id,source:s.source,ownerId:s.ownerId}"
assert reply in old and reply in new and 'queryViews.clear();pendingRequests.clear();queryTicket++;' in new
(P/'source-delta.txt').write_bytes(git('diff',OLD,NEW,'--',path))
proof=json.loads((P/'withdrawal-http-proof.json').read_text());assert proof['httpStatus']==200 and proof['body']['payload']['id']=='W1' and proof['body']['action']=='wallet.withdrawal.audit'
readback=(P/'06-withdrawal-readback.txt').read_text().replace(chr(92)+chr(34),chr(34));assert '"status": "SANDBOX_APPROVED"' in readback and '"withdrawalId": "W1"' in readback
assert '乘客P1 · P1' in (P/'01-support-fixed.txt').read_text() and '关联订单' in (P/'02-support-detail.txt').read_text()
viewer=(P/'08-viewer-filters-cleared.txt').read_text();assert '已应用过滤：无' in viewer and '导出筛选结果" [disabled]' in viewer and 'strong: "34"' in viewer
assert '金额列单位为分' in (P/'07-export-units.txt').read_text()
csvfile=P/'orders-PAGE-units-cents.csv';rows=list(csv.DictReader(csvfile.read_text(encoding='utf-8-sig').splitlines()));assert len(rows)==27 and all(r['id'].startswith('PAGE-') for r in rows);assert all(int(r['fare'])==1000+int(r['id'].split('-')[1]) for r in rows)
assert sha(csvfile)==sha(P.parent/'frontend'/'orders-filtered27-from-page2.csv')
teardown=json.loads((P/'teardown.json').read_text());baseReport=P.parent/'frontend'/'design-qa.json';apiReport=P.parent/'frontend'/'adminapi-nonauthor-review.json'
apiRoot=Path('/Users/dongge/.codex/tmp/yijiexing-api-20261025');apiHashes={}
for f in ['apps/cloud-service/admin_queries.py','apps/cloud-service/admin_console.py','apps/cloud-service/server.py']:
 b=(apiRoot/f).read_bytes();assert b==subprocess.check_output(['git','-C',str(apiRoot),'show',API+':'+f]);apiHashes[f]=hashlib.sha256(b).hexdigest()
report={
 'schemaVersion':1,'task':'ADMIN-20261025-04','relatedTask':'ADMINAPI-20261025-01','reviewer':'frontend independent non-author','sourceSha':NEW,'apiSourceSha':API,'acceptance':'PASS','verdict':'PASS_SCOPE','scope':'Final4-line admin.js delta plus explicit local synthetic W1 mutation, support display, logout/query isolation and export-unit proof. Earlier fe201d broad matrix inherited only as bound below. Not production or whole three-end lifecycle acceptance.',
 'startedAt':'2026-10-25T21:33:17+08:00','deadline':'2026-10-25T21:38:17+08:00','browserEndedAt':teardown['endedAt'],'reportCompletedAt':datetime.datetime.now().astimezone().isoformat(),'releaseOwner':'coordinator','reviewerEditedSourceBuiltOrDeployed':False,
 'sourceReadback':{'onlyChangedFile':path,'sha256':hashlib.sha256(new.encode()).hexdigest(),'diskEqualsNewCommit':True,'apiFilesUnchangedAt8a':apiHashes,'replyPayloadOwnerIdUnchanged':True,'logoutClearsQueryPendingAndTicket':True,'evidence':'source-delta.txt'},
 'checks':[
  {'number':1,'name':'OC1 associated account fixed','result':'PASS','evidence':['01-support-account-fixed.jpg','01-support-fixed.txt','02-support-detail.txt'],'observed':'List now乘客P1 · P1/orderD, same as actual detail. Reply payload ownerId preserved by exact source check; no reply posted.'},
  {'number':2,'name':'W1 synthetic withdrawal approval succeeds with real business id','result':'PASS','evidence':['03-withdrawals-before.txt','04-withdrawal-confirm.jpg','withdrawal-http-proof.json','05-withdrawal-after.txt','06-withdrawal-readback.txt','06-withdrawal-readback.jpg'],'observed':'Only localhost fixture W1 PENDING, amount300 cents. UI reason+confirmation then actual browser POST action HTTP200, payload.id W1 (not WITHDRAWAL:D1:W1). Query refresh and expanded raw record report withdrawalId W1/status SANDBOX_APPROVED. No bank transfer or cloud mutation.'},
  {'number':3,'name':'Logout isolates filters between accounts','result':'PASS','evidence':['07-export-units.txt','08-viewer-filters-cleared.txt','08-viewer-new-session.jpg'],'observed':'Admin applied searchPAGE- yielding27. Logged out, logged viewer in, orders search empty/applied none/34 records, export disabled. Source clears queryViews, pendingRequests and increments queryTicket. No timing-race stress claim.'},
  {'number':4,'name':'Export copy and actual cents unit','result':'PASS','evidence':['07-export-unit-cents.jpg','07-export-units.txt','orders-PAGE-units-cents.csv'],'observed':'Actual export toast says金额列单位为分. CSV27 PAGE rows, fare1000+i matches prior UI10.00+i/100 yuan. File bytes equal independently accepted baseline filtered27 export.'}
 ],
 'findings':[{'id':'ADMIN-WITHDRAW-P1-01','status':'CLOSED','proof':'Actual correct-id HTTP200 and persisted local SANDBOX_APPROVED readback.'},{'id':'ADMIN-SUPPORT-P2-01','status':'CLOSED','proof':'Actual list+detail agree on P1; reply routing remains ownerId.'}],
 'openBlockingDeltaFindings':[],
 'inheritance':{'sourceSha':OLD,'path':'../frontend/design-qa.json','sha256':sha(baseReport),'originalAcceptance':'REQUIRES_FIX','scope':'All baseline passing9-dataset UI/detail/query/export/page/empty/reset/503-retry/refund/continuation/audit/VIEWER observations inherited; two original findings superseded only by this explicit fixed-source delta. No full matrix repeated.','apiPath':'../frontend/adminapi-nonauthor-review.json','apiSha256':sha(apiReport),'apiAcceptance':'PASS_SCOPE','apiChecks':13,'apiSourceSha':API},
 'csv':{'path':csvfile.name,'sha256':sha(csvfile),'rows':27,'amountUnit':'integer cents','sameBytesAsBaselineFiltered27':True},
 'environmentAndChanges':{'onlyTarget':'http://127.0.0.1:8808/','fixtureBusinessMutation':'Only W1 PENDING -> SANDBOX_APPROVED in isolated local SQLite/CAS fixture, explicitly authorized. Retained for coordinator; not reversed or cleared.','normalAuditWrites':'Login, export and one synthetic approval audit expected.','realCloudOrdersOrBanking':False,'onlineChromeOrEmulatorsTouched':False},
 'teardown':teardown,
 'notRun':['Rejection path actual mutation (same id fallback reviewed statically)','Actual complaint reply (payload statically unchanged)','Concurrent logout/in-flight race stress (source ticket cancellation inspected)','Repeat185 API/full9UI matrix on final4-line delta','Live IAP/cloud deployment/public readback (coordinator owns)','Real bank clearing/payments or new complete3-end transaction'],
 'screenshots':[{'path':f.name,'sha256':sha(f),'savedAndVisuallyInspected':True} for f in sorted(P.glob('*.jpg'))],
 'evidence':[{'path':f.name,'sha256':sha(f)} for f in sorted(P.glob('*.txt'))]+[{'path':'withdrawal-http-proof.json','sha256':sha(P/'withdrawal-http-proof.json')},{'path':'teardown.json','sha256':sha(P/'teardown.json')}],
 'nextAction':'Coordinator binds final accepted source, merges/deploys exact new image and independently reads it back. Reviewer stopped/IDLE and browser operation ownership returned.'
}
(P/'design-qa.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
md=['# 后台修复版限定复测','',f'结论：PASS_SCOPE。产品 `{NEW}`；API `{API}`。首轮完整证据见上级frontend目录，本轮只复查4行改动。','']
for c in report['checks']:md.extend([f"{c['number']}. **{c['name']} — PASS**：{c['observed']}",''])
md.extend(['两项首轮缺陷已关闭。本轮仅本地合成W1做模拟审核，没有银行打款或云端数据变化；未重跑完整三端交易。',''])
for f in sorted(P.glob('*.jpg')):md.extend([f'![{f.stem}]({f})',''])
(P/'REPORT.md').write_text('\n'.join(md))
print(json.dumps({'acceptance':report['acceptance'],'verdict':report['verdict'],'reportSha256':sha(P/'design-qa.json'),'browserEndedAt':teardown['endedAt'],'screenshots':len(report['screenshots'])},ensure_ascii=False))
