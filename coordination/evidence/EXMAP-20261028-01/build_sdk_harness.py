from pathlib import Path
import subprocess,hashlib,json
here=Path(__file__).resolve().parent
root=Path('/Users/dongge/.codex/tmp/yijiexing-astra-amap-20261028')
jar=next(Path('/Users/dongge/.gradle/caches/modules-2/files-2.1/com.amap.api').rglob('*.jar'))
assert hashlib.sha256(jar.read_bytes()).hexdigest()=='ac3ebaafa350784474178e9dfe0cc5083d4f616f52d410e223aed792ce9d6e52'
android=Path('/opt/homebrew/share/android-commandlinetools/platforms/android-34/android.jar')
sources=sorted(str(p) for p in (here/'sdk-harness-src').rglob('*.java'))
root.mkdir(parents=True,exist_ok=True)
p=subprocess.run(['javac','-cp',str(jar)+':'+str(android),'-d',str(root/'jvm-classes')]+sources,capture_output=True,text=True)
print(json.dumps({'javacExit':p.returncode,'sourceCount':len(sources),'diagnosticBytes':len(p.stderr),'sdkDigestVerified':True}))
raise SystemExit(p.returncode)
