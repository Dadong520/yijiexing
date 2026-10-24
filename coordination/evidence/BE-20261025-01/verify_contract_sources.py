"""Read committed source only; no cloud, database, checkout or product mutation."""
import ast
import hashlib
import json
from pathlib import Path
import subprocess

REPO = '/Users/dongge/Documents/yijiexing-antigravity'
BASE = 'e89f90b0a63b72dc04fca5b3690a8897de1543c7'
TARGET = 'fd44ec08d674900cd0b30d3d3f25cd0fc46f1b36'
BACKEND = 'candidates/shared-backend-v8/cloud-service'
MINI = 'candidates/passenger-miniprogram-0.1.0/miniprogram'
OUT = Path(__file__).resolve().parent

def git(*args):
    return subprocess.check_output(['git', '-C', REPO, *args], text=True)

def source(path):
    return git('show', TARGET + ':' + path)

text = source(BACKEND + '/passenger.py')
tree = ast.parse(text)
create = next(n for n in ast.walk(tree) if isinstance(n, ast.FunctionDef) and n.name == 'passenger_create_order')
read_keys = set()
for node in ast.walk(create):
    if isinstance(node, ast.Subscript) and isinstance(node.value, ast.Name) and node.value.id == 'p' and isinstance(node.slice, ast.Constant):
        read_keys.add(node.slice.value)
    if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and isinstance(node.func.value, ast.Name) and node.func.value.id == 'p' and node.func.attr == 'get' and node.args and isinstance(node.args[0], ast.Constant):
        read_keys.add(node.args[0].value)
order_literal = next(n.value for n in ast.walk(create) if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'o' for t in n.targets))
order_keys = [k.value for k in order_literal.keys if isinstance(k, ast.Constant)]
ignored = ['priorityFee', 'couponId', 'couponDiscount']
assert not set(ignored).intersection(read_keys)
assert not set(ignored).intersection(order_keys)
assert git('rev-parse', BASE + ':' + BACKEND).strip() == git('rev-parse', TARGET + ':' + BACKEND).strip()
home = source(MINI + '/pages/home/index.js')
assert all(('payload.' + field) in home for field in ignored)
assert 'const estimatedFare = 2800' in home
points = source(MINI + '/lib/points.js')
assert "const STORAGE_KEY_POINTS = 'yijiexing.points.v1'" in points and 'totalPoints: 380' in points
assert 'points_ledger' not in text.lower() and 'coupon' not in text.lower()

ranges = {
    BACKEND + '/passenger.py': [(12, 19), (63, 74), (147, 150), (180, 199), (213, 228), (245, 254), (306, 321), (339, 341)],
    BACKEND + '/domain.py': [(130, 136)],
    BACKEND + '/admin_console.py': [(144, 162)],
    MINI + '/pages/home/index.js': [(64, 76), (183, 202)],
    MINI + '/pages/order-detail/index.js': [(179, 211)],
    MINI + '/lib/coupons.js': [(3, 10), (101, 129), (183, 225), (240, 265)],
    MINI + '/lib/points.js': [(3, 11), (60, 87)],
}
evidence = {}
for path, spans in ranges.items():
    content = source(path)
    lines = content.splitlines()
    evidence[path] = {'sha256': hashlib.sha256(content.encode()).hexdigest(), 'lines': {str(n): lines[n-1] for a, b in spans for n in range(a, min(b, len(lines))+1)}}
result = {
    'baseSha': BASE, 'targetSha': TARGET, 'observedHead': git('rev-parse', 'HEAD').strip(),
    'readMethod': 'git show of exact target, not mutable working files',
    'changedPathsVsBase': git('diff', '--name-only', BASE, TARGET).splitlines(),
    'backendTreeUnchangedVsBase': True,
    'createReads': sorted(read_keys), 'createOrderKeys': order_keys,
    'silentlyIgnoredCreateFields': ignored,
    'checks': {'createDoesNotReadOrPersistMarketing': 'PASS', 'clientSendsIgnoredFields': 'PASS', 'fixedEstimateAndDevicePointsConfirmed': 'PASS', 'backendUnchangedFromBase': 'PASS'},
    'businessRuntimeTests': 'NOT_RUN (contract design only; unchanged 159-test suite not rerun)',
    'sourceEvidence': evidence,
}
(OUT / 'source-evidence.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
print('PASS: 4 source assertions; exact target source evidence saved; no business/runtime acceptance claimed.')
