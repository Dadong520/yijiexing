from pathlib import Path
import json,re,subprocess,hashlib
root=Path('/Users/dongge/.codex/tmp/yijiexing-astra-amap-20261028')
private=json.loads(Path('/Users/dongge/.codex/private/yijiexing/amap-20261027.json').read_text())
files=['/Users/dongge/Downloads/企业微信截图_17931501089874.png','/Users/dongge/Downloads/企业微信截图_17931500955135.png']
results=[]
for f in files:
 if not Path(f).exists():
  results.append({'file':Path(f).name,'sourceExists':False,'error':'REFERENCED_FILE_NOT_FOUND'});continue
 p=subprocess.run(['/usr/bin/swift',str(root/'ocr.swift'),f],capture_output=True,timeout=60)
 if p.returncode:results.append({'file':Path(f).name,'error':'OCR_FAILED','exitCode':p.returncode});continue
 rows=json.loads(p.stdout); joined='\n'.join(r['text'] for r in rows)
 keys=re.findall(r'(?<![0-9a-f])[0-9a-f]{32}(?![0-9a-f])',joined)
 results.append({'file':Path(f).name,'imageSha256':hashlib.sha256(Path(f).read_bytes()).hexdigest(),'ocrRows':len(rows),
 'hex32Count':len(keys),'roleKeyMatches':{k:any(v in r['text'] for r in rows) for k,v in private.items() if k.endswith('Key')},
 'packages':list(dict.fromkeys(re.findall(r'cn\.hbzjhs\.[a-z.]+',joined))),
 'sha1Matches':joined.count('D0:6F:0A:9E:AC:9D:FD:DC:08:30:B9:8A:F0:6E:2F:68:B6:D2:02:46')})
result={'scope':'Original setting images OCR, raw text held only in subprocess pipe/memory; no image or key copied', 'images':results}
Path(__file__).with_name('settings-ocr-comparison.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False,indent=2))
