from pathlib import Path
import subprocess,datetime,time,json,hashlib,tempfile,io,zipfile,sys
from device_ui import Device,OUT
SOURCE='7ee63cc33d8f08808d24ee1c27477415933a72ab';d=Device('driver');r={'sourceSha':SOURCE,'startedAt':datetime.datetime.now().astimezone().isoformat(),'priorBaselineUiautomator':'null root; no driver install previously occurred'}
def setting():return {k:d.adb('shell',*v).decode().strip() for k,v in {'size':('wm','size'),'density':('wm','density'),'font':('settings','get','system','font_scale'),'wifi':('settings','get','global','wifi_on'),'data':('settings','get','global','mobile_data')}.items()}
original=setting()
try:
 comp=d.pkg+'/cn.hbzjhs.driver.preview.MainActivity';d.adb('shell','input','keyevent','KEYCODE_WAKEUP');r['beforeStartW']=d.adb('shell','am','start','-W','-n',comp,timeout=30).decode();time.sleep(20);(OUT/'driver-10-before-active.png').write_bytes(d.adb('exec-out','screencap','-p'))
 meta=json.loads((OUT.parent/'driver-build-result.json').read_text());assert meta['sourceSha']==SOURCE;assert hashlib.sha256(Path(meta['apkPath']).read_bytes()).hexdigest()==meta['apkSha256']
 d.adb('shell','am','force-stop',d.pkg);installed=d.adb('install','-r',meta['apkPath'],timeout=65).decode();assert 'Success' in installed
 path=d.adb('shell','pm','path',d.pkg).decode().strip().removeprefix('package:');assert path.endswith('base.apk') and '\n' not in path
 b=d.adb('exec-out','cat',path,timeout=30);h=hashlib.sha256(b).hexdigest();assert h==meta['apkSha256']
 with tempfile.TemporaryDirectory(prefix='net01-driver-readback-') as temp:
  a=Path(temp)/'installed.apk';a.write_bytes(b);s=subprocess.check_output(['/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/apksigner','verify','--print-certs',str(a)],stderr=subprocess.STDOUT).decode();sig=next(x for x in s.splitlines() if 'certificate SHA-256 digest' in x);assert meta['signerSha256'] in sig
 with zipfile.ZipFile(io.BytesIO(b)) as z:hits=[n for n in z.namelist() if n.endswith('.dex') and meta['cloudOrigin'].encode() in z.read(n)]
 assert hits
 info=d.adb('shell','dumpsys','package',d.pkg).decode();assert 'versionName='+meta['versionName'] in info and 'versionCode='+str(meta['versionCode'])+' ' in info
 r['installed']={k:meta[k] for k in ['sourceSha','versionName','versionCode','apkSha256','bytes','cloudOrigin','signerSha256','packageId']};r['installed'].update(installedSha256=h,installedSigner=sig,embeddedOriginDexFiles=hits,installResult=installed.strip())
 d.adb('shell','input','keyevent','KEYCODE_WAKEUP');r['afterStartW']=d.adb('shell','am','start','-W','-n',comp,timeout=30).decode();time.sleep(20);(OUT/'driver-11-after-active.png').write_bytes(d.adb('exec-out','screencap','-p'))
 try:
  import xml.etree.ElementTree as E
  nodes=d.nodes_once();(OUT/'driver-11-after-active.xml').write_bytes(E.tostring(nodes,encoding='utf-8'));r['afterHomeTexts']=[n.get('text') or n.get('content-desc') for n in nodes.iter('node') if n.get('text') or n.get('content-desc')]
 except Exception as e:r['uiautomatorAfter']='NOT_AVAILABLE: '+type(e).__name__
finally:
 r['restoration']={'original':original,'final':setting()};r['restoration']['same']=r['restoration']['original']==r['restoration']['final'];d.adb('shell','am','force-stop',d.pkg);d.adb('shell','input','keyevent','KEYCODE_HOME');r['appStoppedAndHome']=True;r['endedAt']=datetime.datetime.now().astimezone().isoformat();(OUT/'driver-focused.json').write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:r.get(k) for k in ['beforeStartW','afterStartW','uiautomatorAfter','afterHomeTexts','endedAt']},ensure_ascii=False))
