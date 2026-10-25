import concurrent.futures,datetime,json,re,time
from pathlib import Path
from device_ui import Device,OUT

def chain(root,n):
 parents={c:a for a in root.iter() for c in a}
 while n is not None:
  yield n;n=parents.get(n)

def test(role):
 d=Device(role);result={'sourceSha':'2e5dd4e7784467f1ee417f9c7ff2144d907e1467','startedAt':datetime.datetime.now().astimezone().isoformat(),'role':role}
 try:
  root=d.nodes();nodes=list(root.iter('node'));entry=next(n for n in nodes if n.get('text')=='进入体验')
  assert any(n.get('enabled')=='false' and n.get('clickable')=='true' for n in chain(root,entry))
  assert not any(n.get('class')=='android.widget.EditText' for n in nodes)
  assert not any('访问码' in (n.get('text') or '') for n in nodes)
  result['noCodeFields']=True;result['consentGateDisabled']=True
  d.tap('切换账号');d.capture('07-account-menu')
  default='体验司机03 (13800000003)' if role=='driver' else '体验乘客02 (13800000102)'
  assert len([s for s in d.texts() if re.fullmatch(r'体验(?:司机|乘客)0[123] \(1\d{10}\)',s)])==3
  d.tap(default);result['threeAccountChoicesSelectable']=True
  if role=='passenger':
   d.tap('连接状态');d.capture('08-connection-dialog');print(role,'connection',json.dumps(d.texts(),ensure_ascii=False),flush=True);d.tap('返回体验')
  consent='我已了解体验说明和共享演示数据范围' if role=='driver' else '我已阅读并同意下方体验说明'
  d.tap(consent)
  d.adb('shell','svc','wifi','disable');d.adb('shell','svc','data','disable');time.sleep(2)
  result['offlineAtEntry']={k:d.adb('shell','settings','get','global',k).decode().strip() for k in ['wifi_on','mobile_data']}
  assert result['offlineAtEntry']=={'wifi_on':'0','mobile_data':'0'}
  d.tap('进入体验');time.sleep(23)
  d.capture('09-offline-login-failed');texts=d.texts();result['offlineTexts']=texts
  assert '进入体验' in texts and not any(s in texts for s in ['云端验收司机03','确认呼叫 · 即时用车'])
  assert any(any(x in s.lower() for x in ['连接','网络','failed','unable','unreachable']) for s in texts)
  result['offlineFailureLeavesLogin']=True
  print(role,'offline',json.dumps(texts,ensure_ascii=False),flush=True)
 finally:
  d.adb('shell','svc','wifi','enable');d.adb('shell','svc','data','enable')
  result['networkRestored']={k:d.adb('shell','settings','get','global',k).decode().strip() for k in ['wifi_on','mobile_data']}
  (OUT/(role+'-login-checks.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
 time.sleep(2)
 root=d.nodes();entry=next(n for n in root.iter('node') if n.get('text')=='进入体验');x1,y1,x2,y2=map(int,re.findall(r'\d+',entry.get('bounds')))
 d.adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2);d.log('tap','进入体验 online')
 if role=='passenger':
  d.adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2);d.log('tap','进入体验 immediate second tap same observed bounds');result['rapidDoubleTap']=True
 else:result['singleOnlineEntryTap']=True
 time.sleep(12)
 d.capture('10-online-login-home');texts=d.texts();result['onlineHomeTexts']=texts
 assert ('云端验收司机03' if role=='driver' else '确认呼叫 · 即时用车') in texts
 result['onlineEntrySuccess']=True
 d.tap('我的');time.sleep(1);d.capture('11-online-login-profile');texts=d.texts();result['profileTexts']=texts
 assert ('云端验收司机03师傅，您好' if role=='driver' else '13800000102') in texts
 result['expectedIdentity']=True
 d.adb('shell','am','force-stop',d.pkg)
 component='cn.hbzjhs.driver.preview.MainActivity' if role=='driver' else 'cn.hbzjhs.passenger.MainActivity'
 d.adb('shell','am','start','-n',d.pkg+'/'+component);time.sleep(6)
 d.capture('12-relaunch-session');texts=d.texts();result['relaunchTexts']=texts
 assert ('云端验收司机03' if role=='driver' else '确认呼叫 · 即时用车') in texts
 result['relaunchRetainsLogin']=True
 result['endedAt']=datetime.datetime.now().astimezone().isoformat()
 (OUT/(role+'-login-checks.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
 print(role,'PASS',flush=True)
with concurrent.futures.ThreadPoolExecutor(2) as pool:
 for _ in pool.map(test,['driver','passenger']):pass
