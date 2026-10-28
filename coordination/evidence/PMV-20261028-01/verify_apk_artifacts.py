"""Public APK identity + private-pattern counts; never outputs credential values."""
import hashlib
import json
import os
import pathlib
import re
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path('/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027')
BUILD = pathlib.Path('/Users/dongge/.codex/tmp/yijiexing-pmv-release-build-20261028')
TOOLS = pathlib.Path('/opt/homebrew/share/android-commandlinetools/build-tools/34.0.0')
ENV = {**os.environ, 'JAVA_HOME': '/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home'}
secrets = json.loads(pathlib.Path('/Users/dongge/.codex/private/yijiexing/amap-20261027.json').read_text())
labels = ['webJsKey', 'webJsSecurityCode', 'passengerAndroidKey', 'driverAndroidKey']
assert all(isinstance(secrets.get(k), str) and len(secrets[k]) >= 30 for k in labels)
patterns = {k: secrets[k].encode() for k in labels}


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024*1024), b''):
            value.update(chunk)
    return value.hexdigest()


def verify(role, package, version, code, component_commit):
    apk = BUILD/role/'_app/outputs/apk/debug/app-debug.apk'
    assert apk.is_file(), role+' APK missing'
    badging = subprocess.check_output([str(TOOLS/'aapt'), 'dump', 'badging', str(apk)], text=True)
    identity = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    assert identity and identity.groups() == (package, str(code), version), role+' identity mismatch'
    result = subprocess.run([str(TOOLS/'apksigner'), 'verify', '--print-certs', str(apk)], env=ENV,
                            capture_output=True, text=True)
    assert result.returncode == 0, role+' signature failed'
    sha256 = re.search(r'certificate SHA-256 digest:\s*([0-9a-fA-F]+)', result.stdout).group(1).lower()
    sha1 = re.search(r'certificate SHA-1 digest:\s*([0-9a-fA-F]+)', result.stdout).group(1).upper()
    assert sha256 == '9ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f'
    assert sha1 == 'D06F0A9EAC9DFDDC0830B98AF06E2F68B6D20246'
    matches = {k: [] for k in labels}
    max_len = max(map(len, patterns.values()))
    with zipfile.ZipFile(apk) as archive:
        for item in archive.infolist():
            if item.is_dir():
                continue
            tail = b''
            found = set()
            with archive.open(item) as stream:
                for chunk in iter(lambda: stream.read(1024*1024), b''):
                    window = tail+chunk
                    for label, pattern in patterns.items():
                        if pattern in window:
                            found.add(label)
                    tail = window[-max_len:]
            for label in found:
                matches[label].append(item.filename)
    assert not any(matches.values()), role+' credential pattern present in APK'
    path = 'apps/passenger-android' if role == 'passenger' else 'candidates/driver-0.3.6-r2/android'
    equivalent = subprocess.run(['git', 'diff', '--quiet', component_commit, 'HEAD', '--', path], cwd=ROOT).returncode == 0
    assert equivalent, role+' build source changed after compilation'
    reports = list((BUILD/role/'_app/test-results/testDebugUnitTest').glob('TEST-*.xml'))
    tests = sum(int(ET.parse(p).getroot().get('tests', 0)) for p in reports)
    failures = sum(int(ET.parse(p).getroot().get('failures', 0))+int(ET.parse(p).getroot().get('errors', 0)) for p in reports)
    assert reports and tests and failures == 0, role+' unit report incomplete'
    return {'path': str(apk), 'bytes': apk.stat().st_size, 'sha256': digest(apk),
            'package': package, 'versionName': version, 'versionCode': code,
            'signerSha256': sha256, 'signerSha1': ':'.join(sha1[i:i+2] for i in range(0, len(sha1), 2)),
            'privateCredentialPatternCounts': {k: len(v) for k, v in matches.items()},
            'componentSourceCommit': component_commit, 'sameComponentTreeAsIntegration': equivalent,
            'unitTests': tests, 'unitFailures': failures}


data = {'integrationSha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'scope': 'Build + package/signature/credential scan; not device GUI or successful SDK authentication',
        'passenger': verify('passenger', 'cn.hbzjhs.dc.passenger.cloud', '1.1.14-experience', 16,
                            '770781cc5c5d5ac58e046db7c70e7389c995f6b2'),
        'driver': verify('driver', 'cn.hbzjhs.dc.driver.cloud', '0.3.14-cloud', 17,
                         'd5afe915eab8d0ad3cfc638a26740954eb86acf0')}
target = pathlib.Path(__file__).parent/'APK_PROOF.json'
target.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
print(json.dumps({r: {k: data[r][k] for k in ('bytes', 'sha256', 'versionName', 'versionCode', 'unitTests')}
                  for r in ('passenger', 'driver')}, ensure_ascii=False))
