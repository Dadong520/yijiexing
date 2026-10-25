from device_ui import Device,OUT
import json,time,re,datetime
p=Device('passenger');d=Device('driver')
d.adb('shell','am','start','-a','android.settings.LOCATION_SOURCE_SETTINGS');d.tap('Use location');assert d.adb('shell','settings','get','secure','location_mode').strip()==b'3';d.capture('41-location-service-restored')
gps=json.loads((OUT/'gps-before-test.json').read_text())['positions'];rest=[]
for role in ['driver','passenger']:
 x=Device(role);o=gps[role];result=x.adb('emu','geo','fix',o['longitude'],o['latitude'],o['altitude']).decode();rest.append({'role':role,'position':o,'result':result})
(OUT/'gps-restore-commands.json').write_text(json.dumps({'at':datetime.datetime.now().astimezone().isoformat(),'restored':rest},ensure_ascii=False,indent=2)+'\n')
d.adb('shell','input','keyevent','KEYCODE_BACK')
p.tap('我的');p.capture('41-release-account-retained');assert '13800000102' in p.texts();p.tap('订单');p.tap('已完成');texts=p.texts();labels=[t for t in texts if '下单时间' in t and '00:55' in t];assert len(labels)==1,(labels,texts);p.tap(labels[0]);p.capture('42-history-chinese');t=p.texts();assert any('已同意' in s for s in t) and not any('APPROVED' in s for s in t);print('history chinese PASS',t[:8],flush=True)
p.adb('shell','input','keyevent','KEYCODE_BACK');p.tap('出行');p.tap('确认呼叫 · 即时用车');p.capture('43-delta-third-created');p.tap('查看订单编号');texts=p.texts();oid=next(t for t in texts if re.fullmatch('PORD[0-9a-f]+',t));p.capture('44-delta-third-id');p.tap('关闭');(OUT/'third-order.json').write_text(json.dumps({'id':oid,'purpose':'ALERT01 release delta background queue+autoHide retirement','createdAt':datetime.datetime.now().astimezone().isoformat()},ensure_ascii=False,indent=2)+'\n');print('third order',oid,flush=True)
d.adb('shell','input','keyevent','KEYCODE_HOME');deadline=time.monotonic()+30;hit=False
while time.monotonic()<deadline:
 logs=d.adb('shell','logcat','-d','-v','threadtime','-s','YJXOrderOverlay:I').decode()
 if 'shown:'+oid in logs:
  (OUT/'driver-42-release-background-latest.png').write_bytes(d.adb('exec-out','screencap','-p'));hit=True;break
 time.sleep(1)
assert hit,logs
print('background latest card captured',flush=True);time.sleep(31)
logs=d.adb('shell','logcat','-d','-v','threadtime','-s','YJXOrderOverlay:I').decode();(OUT/'driver-release-overlay-log.txt').write_text(logs);n=sum('shown:'+oid in x for x in logs.splitlines());assert n==1,(oid,n);(OUT/'driver-43-release-after-autohide.png').write_bytes(d.adb('exec-out','screencap','-p'));(OUT/'release-alert-delta.json').write_text(json.dumps({'sourceSha':'d896ebf0c58da093e0c25dae16ddbb40593768df','orderId':oid,'latestCardShown':hit,'sameOrderShownLogCount':n,'observeSecondsAfterShown':31,'at':datetime.datetime.now().astimezone().isoformat(),'acceptance':'PASS'},ensure_ascii=False,indent=2)+'\n');print('release alert delta PASS',flush=True)
p.adb('shell','input','swipe',550,1880,550,700,400);p.tap_button('取消本次订单');root=p.nodes();e=next(n for n in root.iter('node') if n.get('class')=='android.widget.EditText');x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')));p.adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2);p.adb('shell','input','text','ALERT01_delta_QA_cancel');p.adb('shell','input','keyevent','KEYCODE_BACK');p.tap_button('确认提交');p.capture('45-third-cancelled');assert '已取消' in p.texts();print('third order CANCELLED',oid,flush=True)
