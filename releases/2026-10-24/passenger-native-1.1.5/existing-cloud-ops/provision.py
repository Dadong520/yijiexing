"""Provision only the isolated demo project; never changes gcloud defaults."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / '.private'
PROJECT = 'yijiexing-driver-demo-1023'
NUMBER = '491829048305'
REGION = 'us-west1'
STATE = f'yijiexing-demo-state-{NUMBER}'
BUILD = f'yijiexing-demo-build-{NUMBER}'
IMAGE = f'{REGION}-docker.pkg.dev/{PROJECT}/demo/service:20261023-v2'

def run(args, label=None):
    print(label or ' '.join(args[:4]), flush=True)
    result = subprocess.run(['gcloud', *args, '--project=' + PROJECT, '--quiet'],
                            text=True, capture_output=True)
    if result.returncode:
        print(result.stderr[-7000:], flush=True)
        raise SystemExit(result.returncode)
    if result.stdout.strip():
        print(result.stdout[-4000:], flush=True)
    return result.stdout

def exists(args):
    return subprocess.run(['gcloud', *args, '--project=' + PROJECT, '--quiet'],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0

def sa(short):
    return f'{short}@{PROJECT}.iam.gserviceaccount.com'

def init():
    PRIVATE.mkdir(mode=0o700, exist_ok=True)
    os.chmod(PRIVATE, 0o700)
    for short in ['demo-driver', 'demo-admin', 'demo-builder']:
        if not exists(['iam', 'service-accounts', 'describe', sa(short)]):
            run(['iam', 'service-accounts', 'create', short, '--display-name=' + short])
    for name in [STATE, BUILD]:
        if not exists(['storage', 'buckets', 'describe', 'gs://' + name]):
            run(['storage', 'buckets', 'create', 'gs://' + name,
                 '--location=' + REGION, '--default-storage-class=STANDARD',
                 '--uniform-bucket-level-access', '--public-access-prevention',
                 '--soft-delete-duration=0'])
        run(['storage', 'buckets', 'update', 'gs://' + name,
             '--no-versioning', '--soft-delete-duration=0'])
    for short in ['demo-driver', 'demo-admin']:
        run(['storage', 'buckets', 'add-iam-policy-binding', 'gs://' + STATE,
             '--member=serviceAccount:' + sa(short), '--role=roles/storage.objectAdmin'])
    run(['storage', 'buckets', 'add-iam-policy-binding', 'gs://' + BUILD,
         '--member=serviceAccount:' + sa('demo-builder'), '--role=roles/storage.objectAdmin'])
    run(['storage', 'buckets', 'add-iam-policy-binding', 'gs://' + BUILD,
         '--member=serviceAccount:' + sa('demo-builder'), '--role=roles/storage.legacyBucketReader'])
    if not exists(['artifacts', 'repositories', 'describe', 'demo', '--location=' + REGION]):
        run(['artifacts', 'repositories', 'create', 'demo', '--repository-format=docker',
             '--location=' + REGION, '--description=Isolated Android demo container'])
    run(['artifacts', 'repositories', 'add-iam-policy-binding', 'demo', '--location=' + REGION,
         '--member=serviceAccount:' + sa('demo-builder'), '--role=roles/artifactregistry.writer'])
    run(['projects', 'add-iam-policy-binding', PROJECT,
         '--member=serviceAccount:' + sa('demo-builder'), '--role=roles/logging.logWriter', '--format=none'])
    for secret_name, filename, short, nbytes in [
        ('driver-demo-access', 'driver-access-code.txt', 'demo-driver', 24),
        ('driver-demo-admin', 'admin-token.txt', 'demo-admin', 32),
    ]:
        path = PRIVATE / filename
        if not exists(['secrets', 'describe', secret_name]):
            path.write_text(secrets.token_urlsafe(nbytes), encoding='utf-8')
            os.chmod(path, 0o600)
            run(['secrets', 'create', secret_name, '--replication-policy=automatic',
                 '--data-file=' + str(path)], label='Create isolated role secret (value hidden)')
        elif not path.exists():
            raise SystemExit(f'Existing secret {secret_name} has no local recovery file; stop for review')
        run(['secrets', 'add-iam-policy-binding', secret_name,
             '--member=serviceAccount:' + sa(short), '--role=roles/secretmanager.secretAccessor',
             '--format=none'])
    config = dict(project=PROJECT, projectNumber=NUMBER, region=REGION, stateBucket=STATE,
                  buildBucket=BUILD, image=IMAGE, services=['driver-demo', 'driver-admin'])
    (ROOT / 'ops' / 'deployment.json').write_text(json.dumps(config, indent=2) + '\n')

def build():
    run(['builds', 'submit', str(ROOT / 'cloud-service'), '--tag=' + IMAGE,
         '--service-account=projects/' + PROJECT + '/serviceAccounts/' + sa('demo-builder'),
         '--gcs-source-staging-dir=gs://' + BUILD + '/source',
         '--gcs-log-dir=gs://' + BUILD + '/logs', '--region=' + REGION,
         '--timeout=900s'], label='Build isolated cloud service image')

def deploy():
    urls = {}
    for role, service, short, envsecret in [
        ('driver', 'driver-demo', 'demo-driver', 'DEMO_ACCESS_CODE=driver-demo-access:1'),
        ('admin', 'driver-admin', 'demo-admin', 'DEMO_ADMIN_TOKEN=driver-demo-admin:1,ADMIN_BOOTSTRAP_PASSWORD=admin-console-bootstrap:1'),
    ]:
        run(['run', 'deploy', service, '--image=' + IMAGE, '--region=' + REGION,
             '--service-account=' + sa(short), '--execution-environment=gen2',
             '--cpu=1', '--memory=512Mi', '--concurrency=1', '--timeout=300s',
             '--min=0', '--max=1', '--scaling=auto', '--cpu-throttling', '--no-cpu-boost',
             '--ingress=all', '--no-invoker-iam-check' if role == 'driver' else '--invoker-iam-check',
             '--allow-unauthenticated' if role == 'driver' else '--no-allow-unauthenticated',
             '--set-env-vars=CLOUD_ROLE=' + role + ',SNAPSHOT_BUCKET=' + STATE + ',SNAPSHOT_OBJECT=demo/state.sqlite',
             '--set-secrets=' + envsecret, '--startup-probe=httpGet.path=/health,httpGet.port=8080,initialDelaySeconds=0,periodSeconds=10,timeoutSeconds=2,failureThreshold=12',
             '--labels=application=yijiexing,environment=isolated-demo'])
        urls[role] = run(['run', 'services', 'describe', service, '--region=' + REGION,
                          '--format=value(status.url)']).strip()
    config_path = ROOT / 'ops' / 'deployment.json'
    config = json.loads(config_path.read_text())
    config['canonicalUrls'] = urls.copy()
    # Both URLs are provided by Cloud Run for this service. Use the project-number
    # origin for the mobile build and keep the canonical URL for diagnostics.
    urls['driver'] = f'https://driver-demo-{NUMBER}.{REGION}.run.app'
    config['urls'] = urls
    config['image'] = IMAGE
    config['mobileOrigin'] = urls['driver']
    config['downloadUrl'] = urls['driver'] + '/download'
    config['customDomain'] = {'hostname': 'yijiexing-demo.hbzjhs.xyz', 'certificateStatus': 'PENDING_AT_DELIVERY_CHECK'}
    config_path.write_text(json.dumps(config, indent=2) + '\n')

if __name__ == '__main__':
    {'init': init, 'build': build, 'deploy': deploy}[sys.argv[1]]()
