"""Regressions for checkpoint safety; no cloud, secrets, AI or app builds."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from check_coordination import check, check_memory, memory_summary


class MemorySafetyTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.version = '2026-10-26-v3'
        self.sha = 'a' * 40
        c = self.root/'coordination'
        (c/'handoffs').mkdir(parents=True)
        (c/'tasks').mkdir()
        (self.root/'AGENTS.md').write_text(self.version)
        (c/'STATE.md').write_text('Current checkpoint')
        for name in ('START_HERE.md', 'MEMORY_POLICY.md', 'DECISIONS.md', 'NEW_CHAT_PROMPT.txt'):
            (c/name).write_text(self.version)
        self.board = {'activeTaskIds': [], 'memoryProtocolVersion': self.version}
        self.registry = {
            'schemaVersion': 1, 'protocolVersion': self.version, 'generation': 1,
            'canonicalControlRoot': str(self.root), 'activeCoordinatorRole': 'coordinator',
            'retiredCoordinatorThreadIds': [], 'sessions': {}}
        for role in ('coordinator', 'backend', 'frontend', 'antigravity'):
            (c/'handoffs'/f'{role}.md').write_text(self.version)
            self.registry['sessions'][role] = {
                'threadId': None if role == 'antigravity' else role + '-thread',
                'model': 'gpt-6.1-sol', 'memoryStatus': 'UNKNOWN',
                'contextUsedPercent': None, 'measurementSource': 'not_exposed',
                'measurementAt': None, 'ackVersion': self.version,
                'handoffPath': f'coordination/handoffs/{role}.md'}
        self.save()

    def save(self):
        (self.root/'coordination/BOARD.json').write_text(json.dumps(self.board))
        (self.root/'coordination/SESSION_REGISTRY.json').write_text(json.dumps(self.registry))

    def test_unknown_context_is_valid_and_reported_unknown(self):
        self.assertEqual(check(self.root, memory_role='coordinator', session_id='coordinator-thread'), [])
        self.assertIn('UNKNOWN (no model telemetry)', memory_summary(self.root, 'coordinator'))

    def test_numeric_context_requires_provenance_not_account_quota(self):
        s = self.registry['sessions']['backend']
        s.update(contextUsedPercent=75, measurementSource='account_usage_limits',
                 measurementAt='2026-10-26T02:00:00+08:00')
        self.save()
        self.assertTrue(any('real provenance' in e for e in check_memory(self.root)))

    def test_declared_telemetry_threshold_cannot_be_marked_healthy(self):
        s = self.registry['sessions']['backend']
        s.update(contextUsedPercent=85, measurementSource='explicit product telemetry',
                 measurementAt='2026-10-26T02:00:00+08:00', memoryStatus='NORMAL')
        self.save()
        self.assertTrue(any('>=85%' in e for e in check_memory(self.root)))
        s['memoryStatus'] = 'HANDOFF_REQUIRED'
        self.save()
        self.assertEqual(check_memory(self.root), [])
        self.assertTrue(any('handoff required' in e for e in check_memory(self.root, 'backend', 'backend-thread')))

    def test_nan_boolean_and_out_of_range_percent_rejected(self):
        for bad in (float('nan'), True, -1, 101, '70'):
            with self.subTest(value=bad):
                self.registry['sessions']['backend']['contextUsedPercent'] = bad
                self.save()
                self.assertTrue(any('invalid context percent' in e for e in check_memory(self.root)))

    def test_duplicate_thread_rejected(self):
        self.registry['sessions']['frontend']['threadId'] = 'backend-thread'
        self.save()
        self.assertIn('memory: duplicate active thread id', check_memory(self.root))

    def test_retired_coordinator_cannot_dispatch_again(self):
        self.registry['retiredCoordinatorThreadIds'] = ['coordinator-thread']
        self.save()
        self.assertTrue(any('retired coordinator' in e for e in check_memory(self.root)))

    def test_replaced_coordinator_old_caller_cannot_use_new_registration(self):
        self.registry['retiredCoordinatorThreadIds'] = ['coordinator-thread']
        self.registry['sessions']['coordinator']['threadId'] = 'new-coordinator-thread'
        self.save()
        self.assertTrue(any('actual caller' in e for e in
                            check_memory(self.root, 'coordinator', 'coordinator-thread')))
        self.assertEqual(check_memory(self.root, 'coordinator', 'new-coordinator-thread'), [])

    def test_role_preflight_needs_actual_caller_id(self):
        self.assertTrue(any('actual caller' in e for e in check_memory(self.root, 'frontend')))

    def test_handoff_required_blocks_role_preflight_but_not_other_idle_roles(self):
        self.registry['sessions']['backend']['memoryStatus'] = 'HANDOFF_REQUIRED'
        self.save()
        self.assertEqual(check(self.root), [])
        self.assertTrue(any('handoff required' in e for e in check(self.root, memory_role='backend', session_id='backend-thread')))
        self.assertEqual(check(self.root, memory_role='coordinator', session_id='coordinator-thread'), [])

    def test_recommendation_does_not_block_finishing_checkpoint(self):
        self.registry['sessions']['coordinator']['memoryStatus'] = 'HANDOFF_RECOMMENDED'
        self.save()
        self.assertEqual(check(self.root, memory_role='coordinator', session_id='coordinator-thread'), [])

    def test_ack_cannot_exist_without_matching_handoff(self):
        (self.root/'coordination/handoffs/frontend.md').write_text('old protocol')
        self.assertTrue(any('ACK not reflected' in e for e in check_memory(self.root)))

    def test_dispatch_requires_ack_and_current_protocol(self):
        self.registry['sessions']['frontend']['ackVersion'] = None
        self.save()
        self.assertTrue(any('acknowledge current protocol' in e
                            for e in check_memory(self.root, 'frontend')))
        self.board['memoryProtocolVersion'] = 'old'
        self.save()
        self.assertIn('memory: board protocol mismatch', check_memory(self.root))

    def test_untrusted_handoff_paths_and_malformed_roles_rejected(self):
        self.registry['sessions']['frontend']['handoffPath'] = '../../secret'
        self.registry['sessions']['coordinator'] = None
        self.save()
        errors = check_memory(self.root)
        self.assertTrue(any('invalid handoff path' in e for e in errors))
        self.assertTrue(any('missing role coordinator' in e for e in errors))

    def test_newer_canonical_registry_blocks_stale_worktree_preflight(self):
        central = self.root/'control'
        (central/'coordination').mkdir(parents=True)
        newer = copy.deepcopy(self.registry)
        newer['generation'] = 2
        (central/'coordination/SESSION_REGISTRY.json').write_text(json.dumps(newer))
        self.registry['canonicalControlRoot'] = str(central)
        self.save()
        self.assertEqual(check(self.root), [])  # CI checks its committed snapshot.
        self.assertTrue(any('stale worktree' in e for e in check_memory(self.root, 'coordinator')))

    def test_size_budget_and_missing_policy_rejected(self):
        p = self.root/'coordination/START_HERE.md'
        p.write_bytes(b'x' * 4097)
        self.assertTrue(any('byte budget' in e for e in check_memory(self.root)))
        p.unlink()
        self.assertIn('memory: missing START_HERE.md', check_memory(self.root))

    def add_expert(self):
        expert = copy.deepcopy(self.registry['sessions']['backend'])
        expert.update(threadId='expert-thread', model='gpt-6-astra', thinking='xhigh',
                      activationPolicy='explicit_blocker_dispatch_only',
                      authorizationDecisionId='GOV-06',
                      handoffPath='coordination/handoffs/expert.md')
        self.registry['sessions']['expert'] = expert
        (self.root/'coordination/handoffs/expert.md').write_text(self.version)
        self.save()
        return expert

    def test_expert_is_optional_but_unknown_or_unregistered_caller_is_rejected(self):
        self.assertEqual(check(self.root), [])
        self.assertTrue(any('missing role expert' in e for e in
                            check_memory(self.root, 'expert', 'unregistered')))
        self.assertTrue(any('unknown role' in e for e in check_memory(self.root, 'invented', 'x')))

    def test_registered_expert_needs_own_actual_identity(self):
        self.add_expert()
        self.assertEqual(check(self.root, memory_role='expert', session_id='expert-thread'), [])
        self.assertTrue(any('actual caller' in e for e in
                            check_memory(self.root, 'expert', 'coordinator-thread')))
        self.assertIn('UNKNOWN', memory_summary(self.root, 'expert'))

    def test_expert_model_exception_does_not_change_core_models(self):
        expert = self.add_expert()
        for field, value in [('model', 'gpt-6.1-sol'), ('thinking', 'high')]:
            original = expert[field]
            expert[field] = value
            self.save()
            self.assertTrue(any('Astra/xhigh' in e for e in check_memory(self.root)))
            expert[field] = original
        self.registry['sessions']['backend']['model'] = 'gpt-6-astra'
        self.save()
        self.assertTrue(any('GPT-6.1 preference' in e for e in check_memory(self.root)))

    def test_expert_cannot_duplicate_an_active_identity(self):
        expert = self.add_expert()
        expert['threadId'] = 'coordinator-thread'
        self.save()
        self.assertIn('memory: duplicate active thread id', check_memory(self.root))

    def test_expert_dispatch_and_authorization_policy_required(self):
        expert = self.add_expert()
        expert.update(activationPolicy='always_running', authorizationDecisionId=None)
        self.save()
        errors = check_memory(self.root)
        self.assertTrue(any('explicit blocker' in e for e in errors))
        self.assertTrue(any('GOV-06' in e for e in errors))

    def test_expert_handoff_required_and_ack_are_enforced(self):
        expert = self.add_expert()
        expert['memoryStatus'] = 'HANDOFF_REQUIRED'
        self.save()
        self.assertEqual(check(self.root, memory_role='coordinator', session_id='coordinator-thread'), [])
        self.assertTrue(any('handoff required' in e for e in
                            check_memory(self.root, 'expert', 'expert-thread')))
        expert.update(memoryStatus='NORMAL', ackVersion=None)
        self.save()
        self.assertTrue(any('acknowledge current protocol' in e for e in
                            check_memory(self.root, 'expert', 'expert-thread')))

    def test_expert_short_card_budget_is_enforced(self):
        self.add_expert()
        (self.root/'coordination/handoffs/expert.md').write_text(self.version + 'x' * 6144)
        self.assertTrue(any('expert handoff exceeds' in e for e in check_memory(self.root)))

    def add_claude(self):
        session = copy.deepcopy(self.registry['sessions']['backend'])
        session.update(threadId='claude-session', model='claude-opus-5-5',
                       execution='official_bundled_cli', activationPolicy='root_dispatch_only',
                       authorizationDecisionId='GOV-07', handoffPath='coordination/handoffs/claude.md')
        self.registry['sessions']['claude'] = session
        (self.root/'coordination/handoffs/claude.md').write_text(self.version)
        self.save()
        return session

    def test_claude_is_optional_and_needs_its_actual_cli_session_id(self):
        self.assertEqual(check(self.root), [])
        self.assertTrue(any('missing role claude' in e for e in check_memory(self.root, 'claude', 'x')))
        self.add_claude()
        self.assertEqual(check_memory(self.root, 'claude', 'claude-session'), [])
        self.assertTrue(any('actual caller' in e for e in check_memory(self.root, 'claude', 'coordinator-thread')))

    def test_claude_does_not_expand_core_model_or_duplicate_identity(self):
        session = self.add_claude()
        session['threadId'] = 'backend-thread'
        self.save()
        self.assertIn('memory: duplicate active thread id', check_memory(self.root))
        session['threadId'] = 'claude-session'
        self.registry['sessions']['frontend']['model'] = 'claude-opus-5-5'
        self.save()
        self.assertTrue(any('GPT-6.1 preference' in e for e in check_memory(self.root)))

    def test_claude_authorization_model_and_dispatch_required(self):
        session = self.add_claude()
        for field, value in [('model', 'gpt-6.1-sol'), ('execution', 'unverified'),
                             ('activationPolicy', 'always_running'), ('authorizationDecisionId', None)]:
            original = session[field]
            session[field] = value
            self.save()
            self.assertTrue(check_memory(self.root))
            session[field] = original
        self.save()
        self.assertEqual(check_memory(self.root), [])

    def test_claude_handoff_ack_and_budget_remain_enforced(self):
        session = self.add_claude()
        session['memoryStatus'] = 'HANDOFF_REQUIRED'
        self.save()
        self.assertTrue(any('handoff required' in e for e in check_memory(self.root, 'claude', 'claude-session')))
        session.update(memoryStatus='NORMAL', ackVersion=None)
        self.save()
        self.assertTrue(any('acknowledge current protocol' in e for e in check_memory(self.root, 'claude', 'claude-session')))
        session['ackVersion'] = self.version
        self.save()
        (self.root/'coordination/handoffs/claude.md').write_text(self.version + 'x'*6144)
        self.assertTrue(any('claude handoff exceeds' in e for e in check_memory(self.root)))

    def test_exact_sha_release_gate_remains_effective(self):
        c = self.root/'coordination'
        proof = c/'proof.json'
        proof.write_text('{"status":"PASS"}')
        task = {'id': 'MEM-20261026-01', 'owner': 'author', 'reviewer': 'other',
                'goal': 'checkpoint', 'baseSha': self.sha, 'acceptedSha': self.sha,
                'allow_paths': ['coordination/'], 'checks': [], 'status': 'ACCEPTED',
                'acceptance': 'PASS', 'releaseReady': True, 'nextAction': 'none',
                'deploy_owner': 'coordinator',
                'tests': [{'command': 'independent test', 'status': 'PASS',
                           'exitCode': 0, 'sourceSha': self.sha}],
                'evidence': [{'path': 'coordination/proof.json',
                              'sha256': hashlib.sha256(proof.read_bytes()).hexdigest()}]}
        self.board['activeTaskIds'] = [task['id']]
        self.save()
        t = c/'tasks'/f"{task['id']}.json"
        t.write_text(json.dumps(task))
        self.assertEqual(check(self.root, task['id'], self.sha), [])
        self.assertTrue(any('exact SHA' in e for e in check(self.root, task['id'], 'b' * 40)))
        proof.write_text('changed')
        self.assertTrue(any('hash mismatch' in e for e in check(self.root, task['id'], self.sha)))


if __name__ == '__main__':
    unittest.main()
