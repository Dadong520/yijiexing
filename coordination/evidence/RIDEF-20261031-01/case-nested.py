from pathlib import Path
import subprocess,xml.etree.ElementTree as E,json,time
R=Path('/Users/dongge/.codex/tmp/yijiexing-ridef-20261031');A=['/opt/homebrew/share/android-commandlinetools/platform-tools/adb','-s','emulator-5584'];U=['python3',str(R/'scripts/ui.py')]
def run(*a):subprocess.run(list(a),check=True)
def snap(n):run(*U,'snap',n)
def texts():return [n.get('text') for n in E.fromstring((R/'screenshots/latest.xml').read_bytes()).iter('node')]
def pair(points):
 t=[]
 for x,y in points:t.append(time.monotonic());run(*A,'shell','input','tap',str(x),str(y))
 return (t[1]-t[0])*1000
assert '添加途经点 1' in texts()
run(*U,'tap','返回','f24-before-early-open')
ms=pair([(927,852),(300,1880)])
snap('f25-early-child-refusal');assert '添加途经点 1' in texts(),texts()
# snapshot takes >700ms; normal click must still work after refusal
run(*U,'tap','UIT历史地点00','f26-normal-stop-selected');assert '您要去哪儿' in texts() and 'UIT历史地点00' in texts(),texts()
print(json.dumps({'earlyOpenIntervalMs':ms,'earlyRejectedAndLaterAccepted':True}))
(R/'fixed-runtime/nested-progress.json').write_text(json.dumps({'earlyOpenIntervalMs':ms,'earlyRejectedAndLaterAccepted':True}))
