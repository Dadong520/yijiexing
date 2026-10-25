"""Stage a reproducible admin image without credentials, databases or caches."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ADMIN = Path(__file__).resolve().parents[1]
BASE = 'us-west1-docker.pkg.dev/yijiexing-driver-demo-1023/demo/admin-ui@sha256:35726abba61d4ddc4ba49733a726ce81218fac400a212a2f554121cfb6c0a15c'


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--backend-source', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--backend-sha', required=True)
    p.add_argument('--frontend-sha', required=True)
    p.add_argument('--manifest', type=Path, required=True)
    args = p.parse_args()
    if args.output.exists():
        raise SystemExit('Output must be a new dedicated build directory')
    root = Path(subprocess.check_output(['git', '-C', str(args.backend_source), 'rev-parse', '--show-toplevel'], text=True).strip())
    for f in args.backend_source.glob('*.py'):
        relative = f.relative_to(root).as_posix()
        expected = subprocess.check_output(['git', '-C', str(root), 'show', args.backend_sha + ':' + relative])
        if hashlib.sha256(expected).digest() != hashlib.sha256(f.read_bytes()).digest():
            raise SystemExit('Backend bytes differ from accepted source: ' + relative)
    admin_root = Path(subprocess.check_output(['git', '-C', str(ADMIN), 'rev-parse', '--show-toplevel'], text=True).strip())
    for f in list((ADMIN / 'web').glob('*')) + [ADMIN / 'backend/iap_browser.py']:
        if not f.is_file(): continue
        expected = subprocess.check_output(['git', '-C', str(admin_root), 'show', args.frontend_sha + ':' + f.relative_to(admin_root).as_posix()])
        if hashlib.sha256(expected).digest() != hashlib.sha256(f.read_bytes()).digest():
            raise SystemExit('Frontend bytes differ from accepted source: ' + str(f.name))
    args.output.mkdir(parents=True, mode=0o700)
    for f in args.backend_source.glob('*.py'):
        shutil.copy2(f, args.output / f.name)
    shutil.copy2(ADMIN / 'backend/iap_browser.py', args.output / 'iap_browser.py')
    shutil.copytree(ADMIN / 'web', args.output / 'static')
    (args.output / 'Dockerfile').write_text('FROM ' + BASE + '\nUSER root\nCOPY --chown=demo:demo *.py /app/\nCOPY --chown=demo:demo static /app/static\nUSER 10001:10001\nCMD ["python", "iap_browser.py"]\n')
    manifest = {'baseImage': BASE, 'backendSourceSha': args.backend_sha, 'frontendSourceSha': args.frontend_sha,
                'scope': 'driver-admin only; existing IAP entry, admin role and shared persistent data',
                'files': {str(f.relative_to(args.output)): hashlib.sha256(f.read_bytes()).hexdigest()
                          for f in sorted(args.output.rglob('*')) if f.is_file()}}
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(manifest, indent=2) + '\n')
    print(args.output)


if __name__ == '__main__':
    main()
