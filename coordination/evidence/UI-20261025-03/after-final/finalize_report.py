import hashlib
import json
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = 'b9bd90a58f01a4e6f09e37366ed8f8b3feaaa51b'
BASE = '8499d6e5b6a19fc64c4375c6d70cd6edfccfa80f'
def read(name):
    return json.loads((HERE / name).read_text())
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

driver, passenger = read('driver-apk.json'), read('passenger-apk.json')
retry = read('map-retry-final.json')
assert all(x['sourceSha'] == SOURCE and x['apkSha256'] == x['installedSha256'] for x in [driver, passenger])
assert retry['retryTappedWhileOffline'] and retry['networkAtRetry'] == {'wifi_on':'0','mobile_data':'0'}
assert retry['originalSdkCacheRestored'] and retry['networkRestored'] == retry['originalNetwork']
old = HERE.parent / 'after-unlocked' / 'design-qa.json'
old_report = json.loads(old.read_text())
assert old_report['sourceSha'] == BASE

screens = []
for png in sorted(HERE.glob('*.png')):
    rejected = '-01-' in png.name
    xml = png.with_suffix('.xml')
    screens.append({'png':png.name, 'pngSha256':sha(png), 'xml':xml.name,
                    'xmlSha256':sha(xml), 'visuallyInspected':True,
                    'acceptanceEvidence':not rejected,
                    'note':'Excluded: OS heads-up notification overlays header.' if rejected else 'Actual emulator image inspected; fresh UI hierarchy paired.'})

report = {
 'schemaVersion':1, 'task':'UI-20261025-03', 'reviewer':'frontend',
 'acceptance':'PASS', 'sourceSha':SOURCE,
 'scope':'Independent final Android UI delta acceptance. Full unchanged UI observations inherited only from the explicitly bound 8499 round; no claim of new end-to-end transaction acceptance.',
 'startedAt':'2026-10-25T19:05:21+08:00',
 'deviceTestingEndedAt':retry['endedAt'],
 'reportCompletedAt':datetime.now().astimezone().isoformat(),
 'deviceTimeLimit':'2026-10-25T19:13:21+08:00',
 'releaseOwner':'coordinator', 'reviewerBuiltOrDeployed':False,
 'apkBindings':[{k:x[k] for k in ['client','sourceSha','versionName','versionCode','apkSha256','installedSha256','bytes','serial','packageId','signerSha256']} for x in [driver,passenger]],
 'sourceDelta':{
   'baselineSha':BASE, 'targetSha':SOURCE, 'evidence':'source-delta.txt',
   'evidenceSha256':sha(HERE/'source-delta.txt'),
   'files':['candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/CoreScreens.kt','apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/PassengerMap.kt'],
   'observed':'Only driver map-height allocation and passenger density-aware asymmetric camera padding/effect key changed. No payload, account, amount, identity, order callback or map retry/key lifecycle changed.'},
 'checks':[
   {'check':'Final install-r and independent installed base.apk hash readback', 'result':'PASS','evidence':['driver-apk.json','passenger-apk.json'], 'note':'Both retained existing logged-in sessions; no logout or new OTP request.'},
   {'check':'Driver first order CTA complete on normal screen', 'result':'PASS','evidence':['driver-03-home-normal-clean-font10.png'], 'observed':'1080x2400 /420dpi /font1.0. Whole first green CTA including rounded bottom visible above navigation without scrolling.'},
   {'check':'Driver first order CTA complete on smaller screen', 'result':'PASS','evidence':['driver-02-home-small-1024x2216-font10.png','driver-screen-restore.json'], 'observed':'Temporary 1024x2216 /420dpi, about390x844dp. Whole CTA visible; original physical1080x2400 restored.'},
   {'check':'Passenger point fit avoids preview badge/recenter overlay', 'result':'PASS','evidence':['passenger-02-home-clean-font10.png','passenger-04-map-recentered-font10.png'], 'observed':'Both selected origin/destination point circles fully visible after initial fit and recenter.'},
   {'check':'Passenger map pan and recenter', 'result':'PASS','evidence':['passenger-03-map-panned-font10.png','passenger-04-map-recentered-font10.png'], 'observed':'Map responds to gesture and actual recenter tap; fitted points return within reserved overlay padding.'},
   {'check':'Uncached offline map failure with address fallback', 'result':'PASS','evidence':['passenger-05-map-offline-failed-font10.png','map-retry-final.json'], 'observed':'Only identified mbgl SDK cache reversibly isolated. Offline map unavailable, address fields and real map reload button remain visible.'},
   {'check':'Actual map reload button tapped while still offline', 'result':'PASS','evidence':['passenger-06-map-retry-while-offline-font10.png','map-retry-final.json','passenger-steps.jsonl'], 'observed':'WiFi/data both0 at19:10:11.372865+08. Actual reload tap changes failure panel to 地图加载中… before network restoration. This closes prior exact-retry NOT_RUN.'},
   {'check':'Network restoration after actual offline retry', 'result':'PASS','evidence':['passenger-07-map-retry-network-restored-font10.png'], 'observed':'Actual road tiles and both point markers restored. Failure/loading overlays gone. Cloud connection retry is separately recorded; no call submitted. No second online map-reload tap was needed.'},
   {'check':'Map attribution after retry recovery', 'result':'PASS','evidence':['passenger-08-map-retry-attribution-font10.png'], 'observed':'Actual popup shows MapLibre Android, OPENFREEMAP, © OPENMAPTILES and OPENSTREETMAP.'},
 ],
 'findings':[
   {'id':'UI03-DRV-P2-01','priority':'P2','status':'CLOSED','evidence':['driver-02-home-small-1024x2216-font10.png','driver-03-home-normal-clean-font10.png'],'resolution':'Smaller map allocation leaves complete first CTA visible in both tested screen sizes.'},
   {'id':'UI03-PAX-P2-02','priority':'P2','status':'CLOSED','evidence':['passenger-02-home-clean-font10.png','passenger-04-map-recentered-font10.png'],'resolution':'Density-aware asymmetric padding keeps fitted point circles clear of overlays.'}
 ],
 'openBlockingUiFindings':[],
 'referenceComparison':{
   'references':['../after/reference/modao-driver-map-home-clean.png','../after/reference/modao-driver-profile-clean.png','../after/reference/provided-passenger-map-home.png'],
   'result':'PASS_SCOPE',
   'note':'Driver map with bottom order panel and passenger map with route/call panel preserve the supplied visual hierarchy. Existing grouped driver-profile rows differ from reference icon grid; recorded nonblocking design difference in prior full round. Final delta changes only viewport geometry. Existing Chinese labels remain readable; arbitrary long-string stress not run.'},
 'inheritedFrom':{
   'path':'../after-unlocked/design-qa.json','sha256':sha(old),'sourceSha':BASE,
   'driverApkSha256':'63764e489cb121a4627d8940f5ba18295747d98ce7f5fa4e7085106efe75dc1f',
   'passengerApkSha256':'4a99709011fd1be8be421801dd80f52357a67e153a9616787952cd3afa61bc2c',
   'note':'These observations were actually run on8499 and were NOT repeated wholesale on b9. Exact two-file delta was inspected before inheritance. Prior2P2 and map-reload NOT_RUN are superseded only by current evidence.',
   'checks':['Orders/profile tabs both clients','Driver acceptance modal opened and Return only','Passenger address keyboard and Cancel','Sampled app control hit targets>=48dp','Chinese existing route/amount labels','Both map surfaces/attribution and driver pan/recenter','Cached offline map availability and cloud connection recovery']},
 'resourceRestoration':{
   'driverWm':read('driver-screen-restore.json'), 'fontScale':'1.0 both, unchanged',
   'passengerNetwork':retry['networkRestored'], 'driverNetwork':'unchanged',
   'originalSdkCacheRestored':retry['originalSdkCacheRestored'],
   'preservedSdkCacheBackup':retry['backupDirectory'],
   'generatedSdkCachePreserved':retry['generatedPreserved'],
   'teardown':'Both apps force-stopped and HOME; original AVDs retained running for coordinator. Sole device operation ownership returned after report.',
   'accountsAndData':'Existing driver003/passenger0102 sessions retained. No prefs/token read, no pm clear, no data deletion, no order acceptance/call/payment/cancellation/complaint writes.',
   'evidence':['driver-screen-restore.json','driver-steps.jsonl','map-retry-final.json']},
 'buildTestProvenance':{'owner':'coordinator','driverUnitTests':55,'passengerUnitTests':20,'result':'PASS per bound build metadata; reviewer did not rerun or rebuild'},
 'notRun':[
   'New login screen/OTP, because existing sessions retained without logout',
   'Physical phone, landscape, TalkBack/full accessibility',
   'Dedicated elder/large-font matrix paused by task',
   'External Amap endpoint text and complaint submission in this UI delta round',
   'New real acceptance/call/cancellation/payment/refund and full three-client business lifecycle',
   'Arbitrary ultra-long address stress test'
 ],
 'screenshots':screens,
 'evidenceManifest':[{'path':n,'sha256':sha(HERE/n)} for n in ['driver-apk.json','passenger-apk.json','driver-screen-restore.json','map-retry-final.json','source-delta.txt','driver-steps.jsonl','passenger-steps.jsonl']],
 'nextAction':'Coordinator merges accepted UI03 source first. Per subsequent ACCESS-20261025-01 dispatch, do not publish039/119 separately; release later with no-access-code version after its own independent acceptance. No deployment performed by reviewer.'
}
(HERE/'design-qa.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'acceptance':report['acceptance'],'sourceSha':SOURCE,'screenshots':len(screens),'reportSha256':sha(HERE/'design-qa.json')},ensure_ascii=False))
