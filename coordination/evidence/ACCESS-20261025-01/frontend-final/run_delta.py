import concurrent.futures,datetime,hashlib,json,time,subprocess
from pathlib import Path
from device_ui import Device,OUT
SOURCE='eb54e70f27c53aaf569390039349cf3487ff008f'
EXPECTED={'driver':'95c948f29ba44fdf30904e5e7880ce298d33d67036686be19e0b7da152441042','passenger':'c2a640c69e2781ca75d2b0754dc14b2d84c9ed8e30c0f3da06f9879bf84842f9'}
def settings(d):return {k:d.adb('shell',*args).decode().strip() for k,args in {'wmSize':('wm','size'),'wmDensity':('wm','density'),'fontScale':('settings','get','system','font_scale'),'wifi':('settings','get','global','wifi_on'),'mobileData':('settings','get','global','mobile_data')}.items()}
def test(role):
 d=Device(role);r={'role':role,'sourceSha':SOURCE,'startedAt':datetime.datetime.now().astimezone().isoformat()};original=settings(d)
 try:
  meta=json.loads((OUT.parent/(role+'-build-result.json')).read_text());assert meta['sourceSha']==SOURCE and meta['apkSha256']==EXPECTED[role]
  apk=Path(meta['apkPath']);assert hashlib.sha256(apk.read_bytes()).hexdigest()==EXPECTED[role]
  result=d.adb('install','-r',str(apk),timeout=65).decode();assert 'Success' in result
  devicePath=d.adb('shell','pm','path',d.pkg).decode().strip().removeprefix('package:');assert devicePath.endswith('base.apk') and '\n' not in devicePath
  installedSha=hashlib.sha256(d.adb('exec-out','cat',devicePath,timeout=30)).hexdigest();assert installedSha==EXPECTED[role]
  cert=subprocess.check_output(['/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/apksigner','verify','--print-certs',str(apk)],stderr=subprocess.STDOUT).decode();signer=next(x for x in cert.splitlines() if 'certificate SHA-256 digest' in x);assert meta['signerSha256'] in signer
  meta.update(installedSha256=installedSha,installResult=result.strip(),signerReadback=signer,serial=d.serial)
  (OUT/(role+'-installed.json')).write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
  d.adb('shell','input','keyevent','KEYCODE_WAKEUP');component='cn.hbzjhs.driver.preview.MainActivity' if role=='driver' else 'cn.hbzjhs.passenger.MainActivity';d.adb('shell','am','start','-n',d.pkg+'/'+component);time.sleep(7)
  d.capture('01-home-retained');texts=d.texts();r['homeTexts']=texts
  assert ('云端验收司机03' if role=='driver' else '确认呼叫 · 即时用车') in texts
  r['retainedLoginHome']=True
  if role=='passenger':
   d.tap('我的');time.sleep(.5);d.capture('02-profile-retained');texts=d.texts();assert '13800000102' in texts;r['expectedPassengerIdentity']=True
   for _ in range(7):
    if '退出当前账号' in d.texts():break
    d.scroll()
   d.tap('退出当前账号');d.capture('03-logout-copy-fixed');texts=d.texts();r['logoutDialogTexts']=texts
   assert '本机登录凭证将移除，云端订单与资料仍保留。下次可选择体验账号一键进入。' in texts
   assert not any('验证码' in s for s in texts)
   d.tap('取消');r['p2Closed']=True;r['logoutCancelled']=True
 finally:
  final=settings(d);r['resourceRestoration']={'original':original,'final':final,'same':original==final}
  d.adb('shell','am','force-stop',d.pkg);d.adb('shell','input','keyevent','KEYCODE_HOME');r['appStoppedAndHome']=True;r['endedAt']=datetime.datetime.now().astimezone().isoformat()
  (OUT/(role+'-delta.json')).write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n')
 print(role,'PASS',flush=True)
with concurrent.futures.ThreadPoolExecutor(2) as pool:
 for _ in pool.map(test,['driver','passenger']):pass
