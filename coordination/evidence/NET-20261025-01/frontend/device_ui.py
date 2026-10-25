from pathlib import Path
import subprocess,json,re,datetime,xml.etree.ElementTree as E
OUT=Path(__file__).resolve().parent
ADB='/opt/homebrew/share/android-commandlinetools/platform-tools/adb'
class Device:
 def __init__(self,role):
  self.role=role;self.serial,self.pkg,self.version,self.code=('emulator-5586','cn.hbzjhs.dc.driver.cloud','0.3.10-cloud',13) if role=='driver' else ('emulator-5584','cn.hbzjhs.dc.passenger.cloud','1.1.10-experience',12)
 def adb(self,*args,timeout=15):return subprocess.check_output([ADB,'-s',self.serial,*map(str,args)],timeout=timeout)
 def log(self,k,v):
  with (OUT/(self.role+'-steps.jsonl')).open('a') as f:f.write(json.dumps({'at':datetime.datetime.now().astimezone().isoformat(),'kind':k,'value':v},ensure_ascii=False)+'\n')
 def nodes_once(self):
  path='/sdcard/access-independent.xml';self.adb('shell','rm','-f',path);r=self.adb('shell','uiautomator','dump',path,timeout=18).decode();assert 'dumped to' in r
  return E.fromstring(self.adb('exec-out','cat',path))
 def nodes(self):
  import time
  try:return self.nodes_once()
  except AssertionError:
   time.sleep(6)
   return self.nodes_once()
 def texts(self):return [n.get('text') or n.get('content-desc') for n in self.nodes().iter('node') if n.get('text') or n.get('content-desc')]
 def tap(self,label):
  a=[n for n in self.nodes().iter('node') if label in (n.get('text'),n.get('content-desc'))];assert len(a)==1,(label,len(a));n=a[0];assert n.get('enabled')=='true';x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));self.adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2);self.log('tap',label)
 def capture(self,name):
  assert re.fullmatch('[a-z0-9-]+',name);p=OUT/(self.role+'-'+name);assert not p.with_suffix('.png').exists()
  i=self.adb('shell','dumpsys','package',self.pkg).decode();assert 'versionName='+self.version in i and 'versionCode='+str(self.code)+' ' in i
  assert self.adb('shell','settings','get','system','font_scale').decode().strip()=='1.0'
  r=self.nodes()
  for n in r.iter('node'):
   if n.get('class')=='android.widget.EditText' and n.get('text') and (n.get('password')=='true' or re.fullmatch(r'\d{6}',n.get('text'))):raise RuntimeError('Potential sensitive populated field; no capture')
  p.with_suffix('.xml').write_bytes(E.tostring(r,encoding='utf-8'));p.with_suffix('.png').write_bytes(self.adb('exec-out','screencap','-p'));self.log('capture',p.name);return str(p)+'.png'
 def scroll(self,up=True):self.adb('shell','input','swipe',25,1900 if up else 700,25,700 if up else 1900,450)
