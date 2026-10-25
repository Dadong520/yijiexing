from pathlib import Path
import sys,json,subprocess,re,hashlib,zipfile,io,tempfile,datetime,concurrent.futures,time
from device_ui import Device,OUT
SOURCE='e1a1240e1948ccd62ee286f8a55cd7c61653eb23'
def identity(d):
 info=d.adb('shell','dumpsys','package',d.pkg).decode();path=d.adb('shell','pm','path',d.pkg).decode().strip().removeprefix('package:');assert path.endswith('base.apk') and '\n' not in path
 b=d.adb('exec-out','cat',path,timeout=30)
 with tempfile.TemporaryDirectory(prefix='alert-installed-') as td:
  f=Path(td)/'installed.apk';f.write_bytes(b);s=subprocess.check_output(['/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/apksigner','verify','--print-certs',str(f)]).decode();sig=next(x for x in s.splitlines() if 'certificate SHA-256 digest' in x)
 with zipfile.ZipFile(io.BytesIO(b)) as z:origins=[n for n in z.namelist() if n.endswith('.dex') and b'https://yijiexing-demo.hbzjhs.xyz' in z.read(n)]
 return {'versionName':re.search(r'versionName=(\S+)',info).group(1),'versionCode':int(re.search(r'versionCode=(\d+)',info).group(1)),'sha256':hashlib.sha256(b).hexdigest(),'bytes':len(b),'signer':sig,'originDexFiles':origins,'permissionLines':[x.strip() for x in info.splitlines() if any(y in x for y in ['ACCESS_FINE_LOCATION:','ACCESS_COARSE_LOCATION:','POST_NOTIFICATIONS:','SYSTEM_ALERT_WINDOW:','ACCESS_BACKGROUND_LOCATION:'])]}
def environment(d):
 vals={k:d.adb('shell',*v).decode().strip() for k,v in {'size':('wm','size'),'density':('wm','density'),'font':('settings','get','system','font_scale'),'wifi':('settings','get','global','wifi_on'),'data':('settings','get','global','mobile_data'),'locationMode':('settings','get','secure','location_mode'),'zen':('settings','get','global','zen_mode')}.items()}
 vals['appops']=d.adb('shell','cmd','appops','get',d.pkg).decode();loc=d.adb('shell','dumpsys','location').decode();vals['locationLines']=[x.strip() for x in loc.splitlines() if 'Location[' in x or 'location enabled' in x];return vals
def test(role):
 SOURCE='c74f3d95faa9d4a06fa70421f8bbedeef5ca6cb4' if role=='driver' else 'e1a1240e1948ccd62ee286f8a55cd7c61653eb23'
 d=Device(role);r={'sourceSha':SOURCE,'serial':d.serial,'pkg':d.pkg,'startedAt':datetime.datetime.now().astimezone().isoformat()}
 try:
  assert d.adb('shell','getprop','sys.boot_completed').strip()==b'1';r['initialEnvironment']=environment(d);r['beforeIdentity']=identity(d);(OUT/(role+'-install.json')).write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n')
  meta=json.loads((OUT/(role+'-initial-build-result.json')).read_text());assert meta['sourceSha']==SOURCE;assert hashlib.sha256(Path(meta['apkPath']).read_bytes()).hexdigest()==meta['apkSha256']
  d.adb('shell','am','force-stop',d.pkg);result=d.adb('install','-r',meta['apkPath'],timeout=70).decode();assert 'Success' in result;r['installResult']=result.strip();r['afterIdentity']=identity(d)
  assert r['afterIdentity']['sha256']==meta['apkSha256'];assert r['afterIdentity']['versionName']==meta['versionName'];assert r['afterIdentity']['versionCode']==meta['versionCode'];assert meta['signerSha256'] in r['afterIdentity']['signer'];assert r['afterIdentity']['originDexFiles']
  c='cn.hbzjhs.driver.preview.MainActivity' if role=='driver' else 'cn.hbzjhs.passenger.MainActivity';d.adb('shell','input','keyevent','KEYCODE_WAKEUP');r['startW']=d.adb('shell','am','start','-W','-n',d.pkg+'/'+c,timeout=30).decode();time.sleep(12);r['guideScreenshot']=d.capture('01-upgrade-permission-guide');r['texts']=d.texts();r['installAcceptance']='PASS'
 finally:
  r['endedAt']=datetime.datetime.now().astimezone().isoformat();(OUT/(role+'-install.json')).write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n')
 print(role, json.dumps({k:r.get(k) for k in ['installAcceptance','texts']},ensure_ascii=False),flush=True)
if __name__=='__main__':
 with concurrent.futures.ThreadPoolExecutor(2) as pool:
  for _ in pool.map(test,sys.argv[1:] or ['driver','passenger']):pass
