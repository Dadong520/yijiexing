"""Executes exact SDK request generation in JVM, then sends two public POI queries.
Android Context/PackageManager are synthetic; signer certificate is from released APK.
Raw stdout second line is SDK request data: capture only; NEVER stream to console.
"""
from pathlib import Path
import json,hashlib,subprocess,base64,urllib.request,urllib.parse,ssl,datetime
root=Path('/Users/dongge/.codex/tmp/yijiexing-astra-amap-20261028')
jar=next(Path('/Users/dongge/.gradle/caches/modules-2/files-2.1/com.amap.api').rglob('*.jar'))
assert hashlib.sha256(jar.read_bytes()).hexdigest()=='ac3ebaafa350784474178e9dfe0cc5083d4f616f52d410e223aed792ce9d6e52'
android=Path('/opt/homebrew/share/android-commandlinetools/platforms/android-34/android.jar')
certificate=(root/'published-passenger-public-cert.der').read_bytes()
assert hashlib.sha1(certificate).hexdigest()=='d06f0a9eac9dfddc0830b98af06e2f68b6d20246'
credentials=json.loads(Path('/Users/dongge/.codex/private/yijiexing/amap-20261027.json').read_text())
cp=':'.join(map(str,[root/'jvm-classes',jar,android]))
results=[]
for role in ['passenger','driver']:
 key=credentials[role+'AndroidKey'];package='cn.hbzjhs.dc.'+role+'.cloud'
 p=subprocess.run(['java','-cp',cp,'SdkProbe',package],input=base64.b64encode(certificate).decode()+'\n'+key+'\n',capture_output=True,text=True,timeout=15)
 lines=p.stdout.splitlines();checks=json.loads(lines[0]);assert p.returncode==0 and all(checks.values()) and len(lines)==2
 entity=base64.b64decode(lines[1]);params=urllib.parse.parse_qs(entity.decode(),keep_blank_values=True)
 assert params['key']==[key]
 result={'role':role,'package':package,'keySha256':hashlib.sha256(key.encode()).hexdigest(),'sdkChecks':checks,'entityKeyMatches':True,'entityFieldNames':sorted(params),'sdkStdErrBytes':len(p.stderr)}
 req=urllib.request.Request('https://restapi.amap.com/v3/place/text',data=entity,headers={'Content-Type':'application/x-www-form-urlencoded','User-Agent':'AMAP SDK Android Search 9.8.1','platinfo':'platform=Android&sdkversion=9.8.1&product=sea','logversion':'2.1'})
 try:
  opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile='/etc/ssl/cert.pem')))
  with opener.open(req,timeout=20) as response:
   payload=json.loads(response.read(1024*1024));result.update(httpStatus=response.status,serverDate=response.headers.get('Date'))
  result.update(status=str(payload.get('status')),infocode=str(payload.get('infocode')),returnedPoiCount=len(payload.get('pois',[])) if isinstance(payload.get('pois'),list) else None)
 except Exception as exc:result.update(exceptionType=type(exc).__name__)
 results.append(result)
report={'at':datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat(),'scope':'Exact SDK Query/Handler/signing bytecode plus released APK certificate; mocked Android Context/PackageManager; request transmitted by Python HTTPS, X-INFO omitted equally','sdkSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'androidDeviceSdkSuccess':'NOT_RUN','privateSigningMaterialRead':False,'rawRequestPersisted':False,'cases':results}
Path(__file__).with_name('sdk-generated-request-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False,indent=2))
