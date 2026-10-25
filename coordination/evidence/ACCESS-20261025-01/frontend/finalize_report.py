import hashlib,json,datetime,subprocess
from pathlib import Path
P=Path(__file__).resolve().parent
ROOT=P.parents[3]
SOURCE='2e5dd4e7784467f1ee417f9c7ff2144d907e1467'
def read(n):return json.loads((P/n).read_text())
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
inst=[read(r+'-installed.json') for r in ['driver','passenger']]
checks={r:read(r+'-login-checks.json') for r in ['driver','passenger']}
restore=[read(r+'-restoration.json') for r in ['driver','passenger']]
for d in inst:assert d['sourceSha']==SOURCE and d['apkSha256']==d['installedSha256']
for r,d in checks.items():
 assert d['noCodeFields'] and d['consentGateDisabled'] and d['threeAccountChoicesSelectable'] and d['offlineFailureLeavesLogin'] and d['onlineEntrySuccess'] and d['expectedIdentity'] and d['relaunchRetainsLogin'],r
for d in restore:assert d['same'] and d['appStoppedAndHome']
sourceReview=[]
for path in ['candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/ConnectedStore.kt','apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/PassengerStore.kt']:
 data=subprocess.check_output(['git','show',SOURCE+':'+path],cwd=ROOT)
 assert b'X-Demo-Access' not in data
 sourceReview.append({'path':path,'sha256AtBoundCommit':hashlib.sha256(data).hexdigest(),'demoAccessHeaderAbsent':True,'note':'Bound source reviewed: code/login/state sequence uses role token, checks expected identity before persisting session. Runtime request bodies/tokens not intercepted or read.'})
report={
 'schemaVersion':1,'task':'ACCESS-20261025-01','reviewer':'frontend (independent non-author)',
 'sourceSha':SOURCE,'acceptance':'PASS_WITH_ONE_P2','mainFlowAcceptance':'PASS','releaseReady':False,
 'scope':'Native Android upgrade + new no-access-code login UI, network failure/recovery, correct identity and process relaunch persistence. Open residual passenger logout copy needs coordinator resolution. Not a full business lifecycle or server-security review.',
 'startedAt':'2026-10-25T20:07:56+08:00','deviceDeadline':'2026-10-25T20:17:56+08:00',
 'deviceTestingEndedAt':max(d['endedAt'] for d in restore),'reportCompletedAt':datetime.datetime.now().astimezone().isoformat(),
 'releaseOwner':'coordinator','reviewerEditedProductBuiltOrDeployed':False,
 'apkBindings':[{k:d[k] for k in ['client','sourceSha','versionName','versionCode','packageId','apkSha256','installedSha256','signerSha256','signerReadback','installResult','serial','bytes']} for d in inst],
 'actualCommands':[
  'adb -s SERIAL install -r APK_PATH (no data clear)',
  'adb -s SERIAL shell pm path PACKAGE; adb -s SERIAL exec-out cat BASE_APK_PATH; local SHA256 equals bound build APK',
  '/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/apksigner verify --print-certs APK_PATH',
  'python3 coordination/evidence/ACCESS-20261025-01/frontend/run_login_checks.py (two independent AVDs, no same-device concurrent actions)',
  'fresh uiautomator dump + screencap for every named capture; evidence device_ui.py and per-role steps.jsonl',
  'driver relaunch capture wake recovery and both device restore completed separately; both restoration.json attest final settings and stopped/HOME'
 ],
 'checks':[
  {'name':'Same-signature upgrade, installed bytes and original session retained','result':'PASS','evidence':['driver-installed.json','passenger-installed.json','driver-01-upgrade-session.png','passenger-01-upgrade-session.png']},
  {'name':'Existing role account refresh/profile with new public API','result':'PASS','evidence':['driver-02-upgrade-profile.png','passenger-02-upgrade-profile.png','driver-05-connection-dialog.png','driver-steps.jsonl','passenger-steps.jsonl'],'note':'Passenger actual Refresh orders tap; driver actual connection Retry tap. Driver03 and passenger02 retained. Driver wallet display557.74, score98,15/10 counts retained; no wallet operation.'},
  {'name':'Normal in-app logout reaches fresh no-code login','result':'PASS','evidence':['driver-06-login-not-agreed.png','passenger-04-logout-confirm.png','passenger-06-login-not-agreed.png'],'note':'No pm clear, preferences/token reads or account storage manipulation. No code/OTP input fields visible or in hierarchy.'},
  {'name':'Consent gate and account menus','result':'PASS','evidence':['driver-06-login-not-agreed.xml','passenger-06-login-not-agreed.xml','driver-07-account-menu.png','passenger-07-account-menu.png'],'note':'Clickable button ancestor disabled before consent; Text child enabled=true is normal Compose semantics. Three allowed accounts displayed and default03/02 selected through menu. Other accounts not authenticated.'},
  {'name':'Connection dialogs/settings no longer ask access code or reset it','result':'PASS','evidence':['driver-03-settings-top.png','driver-04-settings-bottom.png','driver-05-connection-dialog.png','passenger-08-connection-dialog.png'],'note':'Both connection dialogs opened. Driver top/bottom settings and dialog show no access-code/reset fields or actions.'},
  {'name':'Offline entry fails clearly and stays logged out','result':'PASS','evidence':['driver-09-offline-login-failed.png','passenger-09-offline-login-failed.png','driver-login-checks.json','passenger-login-checks.json'],'note':'Both devices separately wifi/data0/0 at entry. Explicit Chinese cloud/network error; no home entered. Consent remains selected, user can retry.'},
  {'name':'Online entry after failure reaches actual account and map','result':'PASS','evidence':['driver-10-online-login-home.png','passenger-10-online-login-home.png','driver-11-online-login-profile.png','passenger-11-online-login-profile.png'],'note':'After wifi/data restoration, driver one tap and passenger two immediate taps at current observed button coordinates. Both reached correct default role profile with actual tiles. No access code or manually entered verification code.'},
  {'name':'Rapid double tap outcome','result':'PASS_LIMITED','evidence':['passenger-steps.jsonl','passenger-10-online-login-home.png','passenger-11-online-login-profile.png'],'note':'Two actual consecutive entry taps, then one correct logged-in UI identity. Source busy guard reviewed. No wire-level count or transient busy-frame claim.'},
  {'name':'Process restart retains new login','result':'PASS','evidence':['driver-12-relaunch-session.png','passenger-12-relaunch-session.png'],'note':'Both force-stop/relaunch without new login tap. Driver first snapshot null root due display sleep; KEYCODE_WAKEUP then fresh successful capture. No data reset.'},
  {'name':'Existing pending-payment banner does not block available offers','result':'PASS_READ_ONLY','evidence':['driver-01-upgrade-session.png','driver-10-online-login-home.png','driver-12-relaunch-session.png'],'note':'Pending2 banner and available offer CTA coexist. No accept tap, no online toggle. Exact29.26 order detail not reopened in this bounded login round.'},
  {'name':'Original network/font/size and default accounts restored','result':'PASS','evidence':['driver-restoration.json','passenger-restoration.json','driver-login-checks.json','passenger-login-checks.json'],'note':'Final settings equal captured originals, wifi/data1/1,font1.0,physical1080x2400/420dpi. Default03/02 sessions retained, apps stopped/HOME, original AVDs left running.'}
 ],
 'findings':[{'id':'ACCESS-PAX-P2-01','priority':'P2','status':'OPEN','file':'apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/ProfileScreens.kt','observed':'Logout confirmation still says 下次可重新获取体验验证码登录, which conflicts with the new one-tap UI.','evidence':['passenger-04-logout-confirm.png','passenger-04-logout-confirm.xml'],'recommendation':'Replace with 下次可选择体验账号一键进入. Coordinator owns source fix/rebuild and final targeted acceptance; reviewer has not changed product or accepted a newer binary.'}],
 'sourceReview':sourceReview,
 'resourceRestoration':restore,
 'limitations':[
  'No TLS interception, token/prefs/code reads or wire request-count claim; no-access-header evidence is bound source plus successful live UI auth/state chain. Root separately owns live server boundary verification.',
  'No new real orders/acceptance/cancel/payment/refund/complaint writes, no online-status toggle, no wallet actions.',
  'No full3-client lifecycle, high-stakes business completion, external Amap endpoint/navigation accuracy, real SMS or identity verification claim.',
  'Driver AVD default GPS may show emulator Googleplex after startup; this round does not validate real location accuracy.',
  'Normal fonts only; full small/large-font landscape, TalkBack, physical phone, long-string matrix not rerun. UI03 maps/cache isolation inherited scope remains separate.'
 ],
 'harnessNotes':['Initial install verification succeeded but first uiautomator root was null with sleeping display; wake resolved.', 'Early consent assertion incorrectly inspected Text child rather than clickable disabled parent; corrected without product changes.', 'Main login script completed passenger; driver final capture null-root handled with bounded wake/readback recovery, then both teardown records completed. These were harness/environment corrections, not hidden product failures.'],
 'notRun':['Other account actual login','Transient busy screenshot or precise auth request-count trace','Exact29.26 native order detail in this run','Dedicated physical-phone/accessibility/large-font matrix','External Amap and complaints workflow','New transaction/full three-client business lifecycle','Publication/download readback by reviewer'],
 'screenshots':[{'png':f.name,'pngSha256':sha(f),'xml':f.with_suffix('.xml').name,'xmlSha256':sha(f.with_suffix('.xml')),'visuallyInspected':True} for f in sorted(P.glob('*.png'))],
 'evidence':[{'path':f.name,'sha256':sha(f)} for f in sorted(P.glob('*.json')) if f.name!='design-qa.json'],
 'nextAction':'Coordinator resolves ACCESS-PAX-P2-01, binds final source/APK/independent delta evidence, merges and publishes once gates pass. Frontend is idle and device ownership returned; no unrequested new tests or deployments.'
}
(P/'design-qa.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'acceptance':report['acceptance'],'deviceEndedAt':report['deviceTestingEndedAt'],'screenshots':len(report['screenshots']),'reportSha256':sha(P/'design-qa.json')},ensure_ascii=False))
