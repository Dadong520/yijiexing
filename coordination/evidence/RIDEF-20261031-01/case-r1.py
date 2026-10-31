from pathlib import Path
import subprocess,xml.etree.ElementTree as E,re,time,json
R=Path('/Users/dongge/.codex/tmp/yijiexing-ridef-20261031');A=['/opt/homebrew/share/android-commandlinetools/platform-tools/adb','-s','emulator-5584']
def run(*args):subprocess.run(list(args),check=True)
def texts():return [n.get('text') for n in E.fromstring((R/'screenshots/latest.xml').read_bytes()).iter('node')]
# This script starts only on the verified destination picker, with source callerf15 selected originA.
assert '您要去哪儿' in texts(),texts()
root=E.fromstring((R/'screenshots/latest.xml').read_bytes());parents={c:p for p in root.iter() for c in p};node=next(n for n in root.iter('node') if n.get('text')=='UIT历史地点06');owner=node
while owner.get('clickable')!='true':owner=parents[owner]
b=list(map(int,re.findall(r'\d+',owner.get('bounds'))));assert b[0]<=269<=b[2] and b[1]<=1971<=b[3],b
# Row body, rather than its text-only rectangle, is the actual hit target.
ts=[];start=time.monotonic()
for i in range(2):
 if i:
  wait=.049-(time.monotonic()-start)
  if wait>0:time.sleep(wait)
 ts.append(time.monotonic());run(*A,'shell','input','tap','269','1971')
inputData={'point':[269,1971],'target':'UIT历史地点06','clickableRowBounds':b,'intervalMs':(ts[1]-ts[0])*1000,'sourceSha':'b3bc07dbecbd49d3b5213d96cb11aef53e9ad644'}
run('python3',str(R/'scripts/ui.py'),'snap','f18b-r1-verified-return')
t=texts();assert '您要去哪儿？' in t and 'RIDEF SameName Place' in t and 'UIT历史地点06' in t,t
run('python3',str(R/'scripts/control-fixed.py'),'{"type":"state","role":"passenger"}')
d=json.loads((R/'fixed-runtime/last-control.json').read_text())['data'];assert not d['orders'],len(d['orders'])
(R/'fixed-runtime/r1-native-verified.json').write_text(json.dumps({'input':inputData,'beforeOrders':0,'afterOrders':0,'origin':'RIDEF SameName Place','destination':'UIT历史地点06','status':'PASS_NO_AUTO_ORDER','explicitCtaAfterWindow':'Pending, will be combined with final route submission'},ensure_ascii=False,indent=2))
print('VERIFIED R1 native PASS, correct row and filled origin/destination, orders0→0, intervalMs',round(inputData['intervalMs']))
