#!/usr/bin/env python3
"""Build a local source/evidence archive, excluding credentials and caches."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1]
CLOUD=ROOT.parent/'yijiexing-cloud-20261023'
FROZEN=Path('/private/tmp/yijiexing-passenger-v7/cloud-service')
if not FROZEN.is_dir():FROZEN=CLOUD/'cloud-service' # fallback still requires exact v7 manifest below
OUT=ROOT/'delivery';OUT.mkdir(exist_ok=True)
def archive_name(p):
    if p.is_relative_to(FROZEN):return 'yijiexing-cloud-20261023/cloud-service/'+str(p.relative_to(FROZEN))
    return str(p.relative_to(ROOT.parent))
expected=json.loads((ROOT/'qa/deployed-v7-source-manifest.json').read_text())
for name,digest in expected.items():
    if hashlib.sha256((FROZEN/name).read_bytes()).hexdigest()!=digest:raise SystemExit('v7冻结源校验不符：'+name)
files=[]
for base in [ROOT,FROZEN]:
    for p in base.rglob('*'):
        if not p.is_file() or p.is_symlink():continue
        rel=p.relative_to(base)
        if any(x in {'.private','.git','__pycache__','delivery','node_modules','.DS_Store'} for x in rel.parts):continue
        if p.suffix in {'.pyc','.db','.sqlite','.sqlite3','.zip','.apk'}:continue
        if base!=ROOT and p.suffix=='.log':continue
        files.append(p)
files.extend(p for p in (CLOUD/'cloud-service/tests').rglob('*.py') if p.is_file() and '__pycache__' not in p.parts)
for name in ['cloud-control.sh','deployment.json','暂停.command','恢复.command','查看状态.command']:
    p=CLOUD/'ops'/name
    if p.is_file():files.append(p)
files=sorted(set(files))
# Compare actual local secret values without printing them.
secrets=[]
for p in [ROOT/'.private/admin-bootstrap-password.txt',CLOUD/'.private/admin-token.txt',CLOUD/'.private/driver-access-code.txt']:
    if p.is_file():secrets.append(p.read_text().strip().encode())
p=ROOT/'.private/qa-accounts.json'
if p.is_file():secrets.extend(a['password'].encode() for a in json.loads(p.read_text()))
for p in files:
    data=p.read_bytes()
    if any(secret and secret in data for secret in secrets):
        raise SystemExit('拒绝归档：文件含实际凭据 '+archive_name(p))
manifest={'capturedAt':datetime.now(timezone.utc).isoformat(),'files':{archive_name(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(files)}}
manifest_path=OUT/'source-manifest.json';manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
archive=OUT/'宜捷行后台源码与验收-20261023.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(files):z.write(p,archive_name(p))
    z.write(manifest_path,'source-manifest.json')
print(json.dumps({'archive':str(archive),'files':len(files),'bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()},ensure_ascii=False))
