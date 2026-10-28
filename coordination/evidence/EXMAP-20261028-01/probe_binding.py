"""Bounded AMap identity probe; never prints URLs, keys, scodes or raw replies.
Uses the signature formula inspected in the exact app SDK (ClientInfo + AppInfo).
This tests server credential acceptance; it is NOT an Android SDK/device test.
"""
from pathlib import Path
import json, hashlib, time, urllib.request, urllib.parse, datetime, ssl

PRIVATE = Path('/Users/dongge/.codex/private/yijiexing/amap-20261027.json')
SHA1 = 'D0:6F:0A:9E:AC:9D:FD:DC:08:30:B9:8A:F0:6E:2F:68:B6:D2:02:46'

def probe(role, package_override=None):
    secret = json.loads(PRIVATE.read_text())[role + 'AndroidKey']
    package = package_override or ('cn.hbzjhs.dc.' + role + '.cloud')
    params = dict(key=secret, keywords='宜昌东站', city='宜昌市', output='json', offset='1', page='1')
    ts = str(int(time.time()*1000)); ts = ts[:-2] + '1' + ts[-1]
    canonical = '&'.join(k+'='+v for k,v in sorted(params.items()))
    scode = hashlib.md5((SHA1+':'+package+':'+ts[:-3]+':'+canonical).encode()).hexdigest()
    params.update(ts=ts, scode=scode)
    body = urllib.parse.urlencode(params).encode()
    request = urllib.request.Request('https://restapi.amap.com/v3/place/text', data=body, headers={
      'Content-Type':'application/x-www-form-urlencoded',
      'User-Agent':'AMAP SDK Android Search 9.8.1',
      'platinfo':'platform=Android&sdkversion=9.8.1&product=sea', 'logversion':'2.1'})
    result = {'role':role, 'package':package, 'keySha256':hashlib.sha256(secret.encode()).hexdigest(),
      'mode':'direct_https_sdk_formula_synthetic_identity', 'androidSdkExecuted':False}
    try:
      opener=urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile='/etc/ssl/cert.pem')))
      with opener.open(request,timeout=20) as response:
        payload=json.loads(response.read(1024*1024))
        result.update(httpStatus=response.status, serverDate=response.headers.get('Date'))
      result.update(status=str(payload.get('status')), infocode=str(payload.get('infocode')),
        returnedPoiCount=len(payload.get('pois',[])) if isinstance(payload.get('pois'),list) else None)
      result['result']='AUTH_ACCEPTED' if result['status']=='1' and result['infocode']=='10000' else 'AUTH_NOT_ACCEPTED'
    except Exception as exc:
      result.update(result='TRANSPORT_ERROR', exceptionType=type(exc).__name__)
    return result

if __name__ == '__main__':
    results={'at':datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat(),
      'scope':'Two public-place read-only requests to AMap; no application backend, GUI or ADB',
      'cases':[probe('passenger'),probe('driver')]}
    out=Path(__file__).with_name('binding-probe-system-trust.json')
    out.write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(results,ensure_ascii=False,indent=2))
