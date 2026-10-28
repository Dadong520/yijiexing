#!/usr/bin/env python3
"""Small, dependency-free project handoff gate; never calls cloud or AI."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import sys


ROLES = ('coordinator', 'backend', 'frontend', 'antigravity')
OPTIONAL_ROLES = ('expert', 'claude')
MEMORY_STATES = {'UNKNOWN', 'NORMAL', 'CHECKPOINT_REQUIRED',
                 'HANDOFF_RECOMMENDED', 'HANDOFF_REQUIRED'}


def check_memory(root, role=None, session_id=None):
    """Validate declared checkpoints; never infer or query model context usage."""
    errors = []
    if role is not None and role not in ROLES + OPTIONAL_ROLES:
        errors.append('memory: unknown role')
    budgets = {'START_HERE.md': 4096, 'MEMORY_POLICY.md': 10240,
               'DECISIONS.md': 8192, 'NEW_CHAT_PROMPT.txt': 4096,
               'SESSION_REGISTRY.json': 6144, 'BOARD.json': 8192}
    for name, limit in budgets.items():
        p = root / 'coordination' / name
        if not p.is_file(): errors.append('memory: missing ' + name)
        elif p.stat().st_size > limit: errors.append('memory: ' + name + ' exceeds byte budget')
    if errors: return errors
    try:
        registry = json.loads((root/'coordination/SESSION_REGISTRY.json').read_text())
        board = json.loads((root/'coordination/BOARD.json').read_text())
    except (ValueError, OSError): return ['memory: invalid registry or board JSON']
    if not isinstance(registry, dict) or not isinstance(board, dict):
        return ['memory: registry and board must be objects']
    if registry.get('schemaVersion') != 1: errors.append('memory: unsupported registry schema')
    version = registry.get('protocolVersion')
    if not isinstance(version, str) or not re.fullmatch(r'\d{4}-\d{2}-\d{2}-v\d+', version):
        errors.append('memory: invalid protocol version')
    else:
        for name in ('AGENTS.md', 'coordination/MEMORY_POLICY.md'):
            if not (root/name).is_file() or version not in (root/name).read_text():
                errors.append('memory: protocol mismatch in ' + name)
        if board.get('memoryProtocolVersion') != version:
            errors.append('memory: board protocol mismatch')
    control = registry.get('canonicalControlRoot')
    if not isinstance(control, str) or not control.strip(): errors.append('memory: control root missing')
    generation = registry.get('generation')
    if isinstance(generation, bool) or not isinstance(generation, int) or generation < 1:
        errors.append('memory: invalid registry generation')
    sessions = registry.get('sessions')
    if not isinstance(sessions, dict): return errors + ['memory: sessions must be an object']
    seen = set()
    active_roles = ROLES + tuple(name for name in OPTIONAL_ROLES if name in sessions or role == name)
    for name in active_roles:
        session = sessions.get(name)
        if not isinstance(session, dict):
            errors.append('memory: missing role ' + name); continue
        thread = session.get('threadId')
        if name != 'antigravity' and (not isinstance(thread, str) or not thread):
            errors.append('memory: registered thread missing for ' + name)
        if thread:
            if not isinstance(thread, str): errors.append('memory: invalid thread id for ' + name)
            elif thread in seen: errors.append('memory: duplicate active thread id')
            else: seen.add(thread)
        if name == 'expert':
            if session.get('model') != 'gpt-6-astra' or session.get('thinking') != 'xhigh':
                errors.append('memory: expert must match user-approved Astra/xhigh')
            if session.get('activationPolicy') != 'explicit_blocker_dispatch_only':
                errors.append('memory: expert requires explicit blocker dispatch policy')
            if session.get('authorizationDecisionId') != 'GOV-06':
                errors.append('memory: expert requires recorded user model exception GOV-06')
        elif name == 'claude':
            if not str(session.get('model', '')).startswith('claude-'):
                errors.append('memory: claude requires observed Claude model')
            if (session.get('authorizationDecisionId') != 'GOV-07'
                    or session.get('activationPolicy') != 'root_dispatch_only'
                    or session.get('execution') != 'official_bundled_cli'):
                errors.append('memory: claude requires authorized task-scoped official CLI execution')
        elif name != 'antigravity' and not str(session.get('model', '')).startswith('gpt-6.1'):
            errors.append('memory: declared Codex model must match GPT-6.1 preference')
        if session.get('memoryStatus') not in MEMORY_STATES:
            errors.append('memory: invalid status for ' + name)
        expected = 'coordination/handoffs/' + name + '.md'
        if session.get('handoffPath') != expected:
            errors.append('memory: invalid handoff path for ' + name)
        percent = session.get('contextUsedPercent')
        if percent is not None:
            if (isinstance(percent, bool) or not isinstance(percent, (int, float))
                    or not math.isfinite(percent) or not 0 <= percent <= 100):
                errors.append('memory: invalid context percent for ' + name)
            if (session.get('measurementSource') in (None, '', 'UNKNOWN', 'not_exposed',
                                                     'account_usage_limits', 'quota')
                    or not session.get('measurementAt')):
                errors.append('memory: context percent requires real provenance for ' + name)
            if (not isinstance(percent, bool) and isinstance(percent, (int, float))
                    and math.isfinite(percent) and 0 <= percent <= 100):
                if percent >= 85 and session.get('memoryStatus') != 'HANDOFF_REQUIRED':
                    errors.append('memory: declared context >=85% requires handoff for ' + name)
                elif percent >= 70 and session.get('memoryStatus') in ('UNKNOWN', 'NORMAL'):
                    errors.append('memory: declared context >=70% needs rollover warning for ' + name)
        ack = session.get('ackVersion')
        if ack is not None and ack != version: errors.append('memory: stale ACK for ' + name)
        if isinstance(version, str) and ack == version:
            card = root / expected
            if not card.is_file() or version not in card.read_text():
                errors.append('memory: ACK not reflected in role handoff for ' + name)
            if name in OPTIONAL_ROLES and card.is_file() and card.stat().st_size > 6144:
                errors.append('memory: ' + name + ' handoff exceeds 6KB')
        if role == name:
            if name != 'antigravity' and (not session_id or session_id != thread):
                errors.append('memory: actual caller session id must match active registered role')
            if ack != version: errors.append('memory: acknowledge current protocol before work')
            if session.get('memoryStatus') == 'HANDOFF_REQUIRED':
                errors.append('memory: handoff required before new writes or dispatch')
    if registry.get('activeCoordinatorRole') != 'coordinator':
        errors.append('memory: one coordinator role required')
    retired = registry.get('retiredCoordinatorThreadIds')
    if not isinstance(retired, list) or any(not isinstance(x, str) for x in retired):
        errors.append('memory: invalid retired coordinator list')
    elif (isinstance(sessions.get('coordinator'), dict)
          and sessions['coordinator'].get('threadId') in retired):
        errors.append('memory: retired coordinator cannot remain active')
    # Only the explicit local preflight reads the canonical registry. CI validates
    # its own committed snapshot and does not assume another machine's path exists.
    if role and isinstance(control, str):
        central = Path(control)/'coordination/SESSION_REGISTRY.json'
        if central.is_file() and central.resolve() != (root/'coordination/SESSION_REGISTRY.json').resolve():
            try:
                current = json.loads(central.read_text())
                if not isinstance(current, dict):
                    errors.append('memory: canonical registry must be an object')
                elif (current.get('protocolVersion') != version
                        or (isinstance(current.get('generation'), int)
                            and isinstance(generation, int)
                            and current['generation'] > generation)):
                    errors.append('memory: stale worktree registry; read canonical control root first')
            except (ValueError, OSError): errors.append('memory: canonical registry unreadable')
    return errors


def memory_summary(root, role):
    registry = json.loads((root/'coordination/SESSION_REGISTRY.json').read_text())
    session = registry['sessions'][role]
    value = session.get('contextUsedPercent')
    usage = 'UNKNOWN (no model telemetry)' if value is None else str(value) + '% (declared source)'
    return 'MEMORY: ' + role + ' ' + session['memoryStatus'] + '; context=' + usage


def check(root, release=None, sha=None, memory_role=None, session_id=None):
    errors = []
    required = ['AGENTS.md', 'coordination/STATE.md', 'coordination/BOARD.json']
    for name in required:
        if not (root / name).is_file(): errors.append('missing ' + name)
    if errors: return errors
    errors.extend(check_memory(root, memory_role, session_id))
    if (root/'AGENTS.md').stat().st_size > 6500: errors.append('AGENTS.md exceeds 6.5KB startup budget')
    if (root/'coordination/STATE.md').stat().st_size > 8192: errors.append('STATE exceeds 8KB; archive history')
    try: board=json.loads((root/'coordination/BOARD.json').read_text())
    except (ValueError,OSError): return errors+['invalid BOARD.json']
    tasks = {}
    for task_id in board.get('activeTaskIds', []):
        if not re.fullmatch(r'[A-Z]+-\d{8}-\d+', task_id):
            errors.append('invalid task id'); continue
        p=root/'coordination/tasks'/f'{task_id}.json'
        if not p.is_file(): errors.append('missing task '+task_id); continue
        if p.stat().st_size>8192: errors.append(task_id+' exceeds 8KB')
        try: task=json.loads(p.read_text())
        except ValueError: errors.append(task_id+' invalid JSON'); continue
        tasks[task_id]=task
        for field in ('id','owner','goal','baseSha','allow_paths','checks','status','acceptance','tests','evidence','nextAction','deploy_owner'):
            if field not in task: errors.append(task_id+' missing '+field)
        if not re.fullmatch(r'[a-f0-9]{40}',task.get('baseSha','')): errors.append(task_id+' requires full base SHA')
        if task.get('acceptance') not in ('PASS','FAIL','BLOCKED','NOT_RUN'): errors.append(task_id+' invalid acceptance')
        for scope in task.get('allow_paths',[]):
            if scope.startswith('/') or '..' in Path(scope).parts: errors.append(task_id+' unsafe scope')
    for role in ('coordinator','antigravity','backend','frontend'):
        p=root/'coordination/handoffs'/f'{role}.md'
        if not p.is_file(): errors.append('missing handoff '+role)
        elif p.stat().st_size>6144: errors.append(role+' handoff exceeds 6KB; archive history')
    if release:
        task=tasks.get(release)
        if not task: errors.append('release task must be in active board')
        elif not sha or task.get('acceptedSha')!=sha or task.get('acceptance')!='PASS':
            errors.append('release blocked: independent PASS must match exact SHA')
        elif task.get('releaseReady') is not True:
            errors.append('release blocked: deployment prerequisites or required checks remain incomplete')
        elif not task.get('reviewer') or task['reviewer']==task.get('owner'):
            errors.append('release blocked: reviewer must differ from author')
        elif not task.get('tests') or not task.get('evidence'):
            errors.append('release blocked: tests and evidence required')
        else:
            for test in task['tests']:
                if (not isinstance(test,dict) or not test.get('command')
                        or test.get('status')!='PASS' or test.get('exitCode')!=0
                        or test.get('sourceSha')!=sha):
                    errors.append('release blocked: actual PASS test records must match source SHA')
            for evidence in task['evidence']:
                if not isinstance(evidence,dict):
                    errors.append('release blocked: evidence needs path and sha256');continue
                name=evidence.get('path','');path=root/name
                if not name or Path(name).is_absolute() or '..' in Path(name).parts or not path.is_file():
                    errors.append('release blocked: missing or unsafe evidence file');continue
                if hashlib.sha256(path.read_bytes()).hexdigest()!=evidence.get('sha256'):
                    errors.append('release blocked: evidence hash mismatch')
    return errors


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    parser.add_argument('--release-task')
    parser.add_argument('--sha')
    parser.add_argument('--memory-role', choices=ROLES + OPTIONAL_ROLES,
                        help='local preflight; validates role ACK and handoff status, not model telemetry')
    parser.add_argument('--session-id', help='actual caller thread id; do not copy another role id')
    args=parser.parse_args()
    errors=check(args.root,args.release_task,args.sha,args.memory_role,args.session_id)
    if errors:
        print('\n'.join('FAIL: '+e for e in errors));return 1
    print('PASS: compact project records are valid'+('; exact-SHA release gate passed' if args.release_task else '; this is not business acceptance'))
    if args.memory_role: print(memory_summary(args.root, args.memory_role))
    return 0


if __name__=='__main__':sys.exit(main())
