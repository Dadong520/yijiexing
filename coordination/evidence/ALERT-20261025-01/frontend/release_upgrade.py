from install_upgrade import identity
from device_ui import Device,OUT
import subprocess,json,hashlib,time,datetime,concurrent.futures
from pathlib import Path
SHA='d896ebf0c58da093e0c25dae16ddbb40593768df'
def go(role):
 d=Device(role);m=json.loads((OUT/(role+'-release-build-result.json')).read_text());assert m['sourceSha']==SHA;assert hashlib.sha256(Path(m['apkPath']).read_bytes()).hexdigest()==m['apkSha256'];r={'sourceSha':SHA,'at':datetime.datetime.now().astimezone().isoformat(),'before':identity(d)};d.adb('shell','am','force-stop',d.pkg);res=d.adb('install','-r',m['apkPath'],timeout=70).decode();assert 'Success' in res;r['after']=identity(d);assert r['after']['sha256']==m['apkSha256'];assert m['signerSha256'] in r['after']['signer'];assert r['after']['versionName']==m['versionName'] and r['after']['versionCode']==m['versionCode'];c='cn.hbzjhs.driver.preview.MainActivity' if role=='driver' else 'cn.hbzjhs.passenger.MainActivity';r['startW']=d.adb('shell','am','start','-W','-n',d.pkg+'/'+c,timeout=30).decode();time.sleep(5);d.capture('40-release-upgrade');r['texts']=d.texts();r['acceptance']='PASS';(OUT/(role+'-release-install.json')).write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');print(role,json.dumps(r['texts'],ensure_ascii=False),flush=True)
with concurrent.futures.ThreadPoolExecutor(2) as pool:
 for _ in pool.map(go,['driver','passenger']):pass
