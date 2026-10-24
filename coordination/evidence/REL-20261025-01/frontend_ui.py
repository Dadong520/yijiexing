"""Bounded UI evidence, only emulator-5586 / driver0.3.7 code10. No API/business actions."""
from pathlib import Path
import subprocess,json,re,datetime,xml.etree.ElementTree as E
OUT=Path(__file__).resolve().parent
ADB='/opt/homebrew/share/android-commandlinetools/platform-tools/adb'
PKG='cn.hbzjhs.dc.driver.cloud'
def log(kind,value):
 with (OUT/'frontend-steps.jsonl').open('a') as f:f.write(json.dumps({'at':datetime.datetime.now().astimezone().isoformat(),'kind':kind,'value':value},ensure_ascii=False)+'\n')
def adb(*args):return subprocess.check_output([ADB,'-s','emulator-5586',*map(str,args)],timeout=25)
def nodes():
 remote='/sdcard/rel20261025-fresh.xml';adb('shell','rm','-f',remote)
 result=adb('shell','uiautomator','dump',remote).decode()
 assert 'dumped to' in result,'Fresh UI dump failed'
 return E.fromstring(adb('exec-out','cat',remote))
def texts():return [n.get('text') or n.get('content-desc') for n in nodes().iter('node') if n.get('text') or n.get('content-desc')]
def capture(name):
 assert re.fullmatch(r'[a-z0-9-]+',name)
 info=adb('shell','dumpsys','package',PKG).decode()
 assert 'versionName=0.3.7-cloud' in info and 'versionCode=10 ' in info
 tree=nodes()
 for n in tree.iter('node'):
  if n.get('class')=='android.widget.EditText' and n.get('text') and (n.get('password')=='true' or re.fullmatch(r'\d{6}',n.get('text'))):raise RuntimeError('Sensitive populated login view: no capture')
 p=OUT/('frontend-'+name)
 assert not p.with_suffix('.png').exists()
 p.with_suffix('.xml').write_bytes(E.tostring(tree,encoding='utf-8'))
 p.with_suffix('.png').write_bytes(adb('exec-out','screencap','-p'))
 log('capture',str(p)+'.png');return str(p)+'.png'
def tap(label):
 ns=[n for n in nodes().iter('node') if label in (n.get('text'),n.get('content-desc'))]
 assert len(ns)==1,(label,len(ns));n=ns[0];assert n.get('enabled')=='true'
 x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
 adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2);log('tap',label)
def scroll(up=True):
 adb('shell','input','swipe',120,1800 if up else 700,120,700 if up else 1800,450);log('scroll','up' if up else 'down')
