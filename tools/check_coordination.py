#!/usr/bin/env python3
"""Small, dependency-free project handoff gate; never calls cloud or AI."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


def check(root, release=None, sha=None):
    errors = []
    required = ['AGENTS.md', 'coordination/STATE.md', 'coordination/BOARD.json']
    for name in required:
        if not (root / name).is_file(): errors.append('missing ' + name)
    if errors: return errors
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
    args=parser.parse_args()
    errors=check(args.root,args.release_task,args.sha)
    if errors:
        print('\n'.join('FAIL: '+e for e in errors));return 1
    print('PASS: compact project records are valid'+('; exact-SHA release gate passed' if args.release_task else '; this is not business acceptance'))
    return 0


if __name__=='__main__':sys.exit(main())
