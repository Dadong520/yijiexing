"""Release compatibility selection. Local temporary HTTP/SQLite only; no real Keys."""
import hashlib, json, pathlib, subprocess, sys, unittest
ROOT=pathlib.Path('/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027')
EVIDENCE=pathlib.Path(__file__).resolve().parent
SRC=ROOT/'apps/cloud-service'
sys.path[:0]=[str(SRC),str(SRC/'tests'),str(EVIDENCE.parent/'backend-review')]
NAMES=[
 'reservation_acceptance.ReservationReview.test_available_and_accepted_reservation_allow_immediate_and_active_first',
 'reservation_acceptance.ReservationReview.test_two_drivers_one_passenger_concurrent_departure',
 'reservation_acceptance.ReservationReview.test_one_driver_two_passengers_concurrent_departure',
 'reservation_edge_probe.ReservationEdges.test_existing_reservation_cannot_start_while_passenger_owes_wait_pay',
 'reservation_edge_probe.ReservationEdges.test_conflict_helper_uses_canonical_after_passenger_cancels',
 'acceptance_probe.IndependentPMReview.test_available_reserved_pickup_arrived_trip_boundaries',
 'acceptance_probe.IndependentPMReview.test_old_idempotency_cache_cancel_continue_and_reassign',
 'acceptance_probe.IndependentPMReview.test_settlement_payment_credit_refund_and_cached_current_projection',
 'amap_independent_probe.IndependentAmapReview.test_config_requires_matching_identity_and_stays_out_of_database',
 'test_destination_required.DestinationRequiredTests.test_true_is_rejected_for_all_kinds_without_order_or_idempotency_side_effects',
 'test_destination_required.DestinationRequiredTests.test_historical_pending_row_can_complete_after_destination_update',
 'test_operations_drafts.OperationsDraftTests.test_draft_lifecycle_versions_persistence_and_no_business_change',
 'test_operations_drafts.OperationsDraftTests.test_manual_record_links_query_and_archive_without_ledger_change',
]
def hashes():
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(SRC.rglob('*.py'))}
before=hashes();head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
suite=unittest.defaultTestLoader.loadTestsFromNames(NAMES)
result=unittest.TextTestRunner(verbosity=2).run(suite)
after=hashes()
data={'testedSha':head,'testsRun':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'backendByteUnchangedDuringTest':before==after,'sourceHashes':before,'selectedTests':NAMES,'scope':'Local merge compatibility only. PMAD/PMDEST implementation acceptance reused from coordinator nonauthor review. No cloud data/real SDK/Key access.'}
(EVIDENCE/'backend-results.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
sys.exit(0 if result.wasSuccessful() and before==after else 1)
