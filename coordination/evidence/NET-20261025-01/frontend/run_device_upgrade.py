import concurrent.futures,datetime,hashlib,io,json,re,subprocess,tempfile,time,zipfile
from pathlib import Path
from device_ui import Device,OUT
SOURCE='7ee63cc33d8f08808d24ee1c27477415933a72ab';ORIGIN='https://yijiexing-demo.hbzjhs.xyz'

def settings(d):return {k:d.adb('shell',*v).decode().strip() for k,v in {'size':('wm','size'),'density':('wm','density'),'font':('settings','get','system','font_scale'),'wifi':('settings','get','global','wifi_on'),'data':('settings','get','global','mobile_data')}.items()}
def openapp(d):
 d.adb('shell','input','keyevent','KEYCODE_WAKEUP');c='cn.hbzjhs.driver.preview.MainActivity' if d.role=='driver' else 'cn.hbzjhs.passenger.MainActivity';d.adb('shell','am','start','-n',d.pkg+'/'+c)
def capture(d,name):
 d.capture(name)
 import xml.etree.ElementTree as E
 return [n.get('text') or n.get('content-desc') for n in E.fromstring((OUT/(d.role+'-'+name+'.xml')).read_text()).iter('node') if n.get('text') or n.get('content-desc')]
def test(role):
 d=Device(role);r={'role':role,'sourceSha':SOURCE,'startedAt':datetime.datetime.now().astimezone().isoformat()};original=None
 try:
  assert d.adb('shell','getprop','sys.boot_completed',timeout=8).strip()==b'1';original=settings(d);assert original['font']=='1.0'
  oldinfo=d.adb('shell','dumpsys','package',d.pkg).decode();d.version=re.search(r'versionName=(\S+)',oldinfo).group(1);d.code=int(re.search(r'versionCode=(\d+)',oldinfo).group(1));r['beforeVersion']={'name':d.version,'code':d.code}
  openapp(d);time.sleep(12);r['beforeHomeTexts']=capture(d,'01-before-home')
  assert ('云端验收司机03' if role=='driver' else '确认呼叫 · 即时用车') in r['beforeHomeTexts']
  d.tap('我的');time.sleep(.5);r['beforeProfileTexts']=capture(d,'02-before-profile')
  meta=json.loads((OUT.parent/(role+'-build-result.json')).read_text());assert meta['sourceSha']==SOURCE and meta['cloudOrigin']==ORIGIN
  assert hashlib.sha256(Path(meta['apkPath']).read_bytes()).hexdigest()==meta['apkSha256']
  d.adb('shell','am','force-stop',d.pkg);res=d.adb('install','-r',meta['apkPath'],timeout=65).decode();assert 'Success' in res
  installedPath=d.adb('shell','pm','path',d.pkg).decode().strip().removeprefix('package:');assert installedPath.endswith('base.apk') and '\n' not in installedPath
  b=d.adb('exec-out','cat',installedPath,timeout=30);installedSha=hashlib.sha256(b).hexdigest();assert installedSha==meta['apkSha256']
  with tempfile.TemporaryDirectory(prefix='net01-installed-') as temp:
   ap=Path(temp)/'installed.apk';ap.write_bytes(b);signed=subprocess.check_output(['/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/apksigner','verify','--print-certs',str(ap)],stderr=subprocess.STDOUT).decode();sig=next(x for x in signed.splitlines() if 'certificate SHA-256 digest' in x);assert meta['signerSha256'] in sig
  with zipfile.ZipFile(io.BytesIO(b)) as z:
   dexhits=[n for n in z.namelist() if n.endswith('.dex') and ORIGIN.encode() in z.read(n)]
  assert dexhits
  info=d.adb('shell','dumpsys','package',d.pkg).decode();assert 'versionName='+meta['versionName'] in info and 'versionCode='+str(meta['versionCode'])+' ' in info;d.version=meta['versionName'];d.code=meta['versionCode']
  r['installed']={k:meta[k] for k in ['sourceSha','versionName','versionCode','apkSha256','bytes','cloudOrigin','signerSha256','packageId']};r['installed'].update(installedSha256=installedSha,installedSigner=sig,embeddedOriginDexFiles=dexhits,installResult=res.strip())
  openapp(d);time.sleep(9);r['afterHomeTexts']=capture(d,'03-after-domain-home')
  if ('云端验收司机03' if role=='driver' else '确认呼叫 · 即时用车') not in r['afterHomeTexts']:
   if '重试连接' in r['afterHomeTexts']:d.tap('重试连接');time.sleep(8);r['afterHomeTexts']=capture(d,'04-domain-retry-home');r['retryConnectionTapped']=True
  assert ('云端验收司机03' if role=='driver' else '确认呼叫 · 即时用车') in r['afterHomeTexts']
  if role=='passenger':d.tap('刷新订单');time.sleep(3)
  d.tap('我的');time.sleep(.5);r['afterProfileTexts']=capture(d,'05-after-domain-profile')
  identity='云端验收司机03师傅，您好' if role=='driver' else '13800000102';assert identity in r['beforeProfileTexts'] and identity in r['afterProfileTexts']
  stable=['¥ 557.74','15','10','服务分 98分'] if role=='driver' else ['体验乘客02','13800000102','宜昌市']
  r['unchangedProfileFields']=[s for s in stable if s in r['beforeProfileTexts'] and s in r['afterProfileTexts']];assert len(r['unchangedProfileFields'])==len(stable),(role,r['unchangedProfileFields'])
  d.adb('shell','am','force-stop',d.pkg);openapp(d);time.sleep(5);r['relaunchHomeTexts']=capture(d,'06-relaunch-domain-home');assert ('云端验收司机03' if role=='driver' else '确认呼叫 · 即时用车') in r['relaunchHomeTexts'];r['retainedLoginAfterRelaunch']=True;r['acceptance']='PASS'
 finally:
  if original:
   final=settings(d);r['resourceRestoration']={'original':original,'final':final,'same':original==final};d.adb('shell','am','force-stop',d.pkg);d.adb('shell','input','keyevent','KEYCODE_HOME');r['appStoppedAndHome']=True
  r['endedAt']=datetime.datetime.now().astimezone().isoformat();(OUT/(role+'-upgrade.json')).write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n')
 print(role,r.get('acceptance'),flush=True)
with concurrent.futures.ThreadPoolExecutor(2) as pool:
 for _ in pool.map(test,['driver','passenger']):pass
