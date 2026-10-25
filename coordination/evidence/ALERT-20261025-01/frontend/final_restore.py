from device_ui import Device,OUT
from install_upgrade import environment
import json,time,datetime,subprocess,re
p=Device('passenger');d=Device('driver');p.tap('Allow only while using the app');p.adb('shell','am','start','-W','-n',p.pkg+'/cn.hbzjhs.passenger.MainActivity');d.adb('shell','am','force-stop',d.pkg);d.adb('shell','am','start','-W','-n',d.pkg+'/cn.hbzjhs.driver.preview.MainActivity');orig=json.loads((OUT/'gps-before-test.json').read_text())['positions']
for role in ['driver','passenger']:
 x=Device(role);o=orig[role];x.adb('emu','geo','fix',o['longitude'],o['latitude'],o['altitude'])
time.sleep(5);r={'at':datetime.datetime.now().astimezone().isoformat(),'gps':{}}
for role in ['driver','passenger']:
 x=Device(role);r['gps'][role]=[v.strip() for v in x.adb('shell','dumpsys','location').decode().splitlines() if 'last location=Location[gps' in v]
print('GPS originals readback',r['gps'],flush=True)
p.adb('shell','am','start','-a','android.settings.APPLICATION_DETAILS_SETTINGS','-d','package:'+p.pkg);t=p.texts()
if 'Permissions' in t:p.tap('Permissions');t=p.texts()
if 'Location permission' not in t and 'Location' in t:p.tap('Location');t=p.texts()
assert 'Don’t allow' in t,t;p.tap('Don’t allow');p.capture('47-final-location-denied')
d.adb('shell','am','start','-a','android.settings.action.MANAGE_OVERLAY_PERMISSION','-d','package:'+d.pkg);t=d.texts()
if 'Allow display over other apps' not in t:
 assert '宜捷行云端体验版' in t,t;d.tap('宜捷行云端体验版')
d.tap('Allow display over other apps');d.capture('46-final-overlay-denied')
for role in ['driver','passenger']:
 x=Device(role);r[role]={'environment':environment(x),'permissions':[v.strip() for v in x.adb('shell','dumpsys','package',x.pkg).decode().splitlines() if any(k in v for k in ['ACCESS_FINE_LOCATION:','ACCESS_COARSE_LOCATION:','POST_NOTIFICATIONS:','SYSTEM_ALERT_WINDOW:'])]};x.adb('shell','am','force-stop',x.pkg);x.adb('shell','input','keyevent','KEYCODE_HOME');r[role]['appStoppedAndHome']=True
r['beforeShutdownAt']=datetime.datetime.now().astimezone().isoformat();(OUT/'resource-restoration.json').write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');print('semantic settings and permission readback saved',flush=True)
for role in ['driver','passenger']:
 x=Device(role);r[role]['normalEmulatorShutdown']=x.adb('emu','kill',timeout=15).decode()
r['shutdownCommandsEndedAt']=datetime.datetime.now().astimezone().isoformat();(OUT/'resource-restoration.json').write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');print('normal shutdown commands done',r['shutdownCommandsEndedAt'],flush=True)
