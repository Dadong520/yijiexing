package cn.hbzjhs.driver.preview

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionRulesTest {
    private fun comp(status: String, round: Int = 1, my: String = "NONE", remaining: Long = 0) =
        OrderCompetition(round, status, if (status == "READY") null else "2026-11-01T08:00:05.000000+08:00", remaining, my)

    private fun avail(c: OrderCompetition?, deadline: Long? = null, now: Long = 0, pending: Boolean = false, kind: String = "STANDARD", stage: String = "AVAILABLE") =
        CompetitionRules.availability(kind, stage, c, deadline, now, pending)

    @Test
    fun readyOpenDeadlineAndPendingStates() {
        // READY: no first contender yet, deadline null, may grab.
        assertEquals(GrabAvailability.CAN_GRAB, avail(comp("READY")))
        // OPEN: non-participant may still join before the local deadline, not at/after it.
        assertEquals(GrabAvailability.CAN_GRAB, avail(comp("OPEN"), deadline = 5_000, now = 4_999))
        assertEquals(GrabAvailability.CLOSED_WAITING, avail(comp("OPEN"), deadline = 5_000, now = 5_000))
        // OPEN without a usable anchor (remaining already 0): conservatively closed.
        assertEquals(GrabAvailability.CLOSED_WAITING, avail(comp("OPEN"), deadline = null))
        // Already joined (server PENDING or local in-flight): competing, no duplicate grab.
        assertEquals(GrabAvailability.COMPETING, avail(comp("OPEN", my = "PENDING"), deadline = 5_000, now = 1))
        assertEquals(GrabAvailability.COMPETING, avail(comp("READY"), pending = true))
        assertEquals(GrabAvailability.CLOSED_WAITING, avail(comp("ASSIGNED")))
        assertEquals(GrabAvailability.CLOSED_WAITING, avail(comp("CLOSED")))
        // Withdrawn/lost stay out of this round; INELIGIBLE (no location) may retry while open.
        assertEquals(GrabAvailability.UNAVAILABLE, avail(comp("OPEN", my = "WITHDRAWN"), deadline = 5_000, now = 1))
        assertEquals(GrabAvailability.CAN_GRAB, avail(comp("OPEN", my = "INELIGIBLE"), deadline = 5_000, now = 1))
        assertEquals(GrabAvailability.UNAVAILABLE, avail(comp("READY"), stage = "PICKUP"))
    }

    @Test
    fun bidNegotiatedAndPrivateRowsKeepOriginalFlow() {
        assertEquals(GrabAvailability.LEGACY, avail(null))
        assertEquals(GrabAvailability.LEGACY, avail(comp("READY"), kind = "BID"))
        assertEquals(GrabAvailability.LEGACY, avail(comp("READY"), kind = "NEGOTIATED"))
        assertNull(parseCompetition(JSONObject().put("id", "p")))
    }

    @Test
    fun windowAnchorsOnMonotonicReceiptAndNeverExtends() {
        val first = CompetitionWindow.anchor(null, receivedAt = 10_000, remainingMs = 4_000)
        assertEquals(14_000 - CompetitionWindow.SAFETY_MS, first)
        // A later poll reporting more time (clock skew/latency) cannot extend the fixed window.
        assertEquals(first, CompetitionWindow.anchor(first, receivedAt = 11_000, remainingMs = 4_000))
        // Less time shortens it: the stricter of the two wins.
        assertEquals(12_000 - CompetitionWindow.SAFETY_MS, CompetitionWindow.anchor(first, receivedAt = 11_000, remainingMs = 1_000))
        // Network delay already ate the window: closed on arrival, no auto resubmit.
        val late = CompetitionWindow.anchor(null, receivedAt = 20_000, remainingMs = 200)
        assertEquals(GrabAvailability.CLOSED_WAITING, avail(comp("OPEN", remaining = 200), deadline = late, now = 20_000))
    }

    @Test
    fun parsesContractNamesAndAliases() {
        val row = JSONObject().put("competition", JSONObject().put("round", 2).put("status", "OPEN")
            .put("deadline", "2026-11-01T08:00:05.123456+08:00").put("serverNow", "2026-11-01T08:00:02.000000+08:00")
            .put("remainingMs", 3123).put("selfStatus", "PENDING"))
        val c = parseCompetition(row)!!
        assertEquals(2, c.round)
        assertEquals(3123L, c.remainingMs)
        assertEquals("PENDING", c.myOutcome)
        val ready = parseCompetition(JSONObject().put("competition", JSONObject().put("round", 1).put("status", "READY").put("deadline", JSONObject.NULL)))!!
        assertNull(ready.deadline)
        assertEquals("NONE", ready.myOutcome)
        assertNull(parseCompetition(JSONObject().put("competition", JSONObject().put("round", 0).put("status", "READY"))))
        assertNull(parseCompetition(JSONObject().put("competition", JSONObject().put("round", 1).put("status", "WHATEVER"))))
    }

    private fun payload(account: String, vararg rows: JSONObject, results: List<Triple<String, Int, String>> = emptyList()) =
        JSONObject()
            .put("profile", JSONObject().put("driver", JSONObject().put("id", account)))
            .put("state", JSONObject().put("orders", JSONArray().apply { rows.forEach { put(it) } }))
            .put("competitionResults", JSONArray().apply {
                results.forEach { (id, round, outcome) -> put(JSONObject().put("orderId", id).put("round", round).put("outcome", outcome)) }
            })

    private fun row(id: String, stage: String, round: Int?, status: String = "ASSIGNED", my: String = "NONE") =
        JSONObject().put("id", id).put("kind", "STANDARD").put("stage", stage).apply {
            if (round != null) put("competition", JSONObject().put("round", round).put("status", status).put("myOutcome", my))
        }

    private val key = GrabAlertKey("d1", "o1", "2")

    @Test
    fun onlyOwnMatchingRoundOwnedRowIsAssignedAndMayNavigate() {
        val assigned = CompetitionVerdicts.resolve(key, payload("d1", row("o1", "PICKUP", 2, my = "WON")), true)
        assertTrue(assigned is GrabOutcome.Assigned)
        assertTrue(GrabOutcomes.mayNavigate(assigned))
        // Another account's payload (switched session) decides nothing.
        assertTrue(CompetitionVerdicts.resolve(key, payload("d2", row("o1", "PICKUP", 2)), true) is GrabOutcome.Pending)
        // Owned row of a different round is not this attempt's assignment.
        assertFalse(CompetitionVerdicts.resolve(key, payload("d1", row("o1", "PICKUP", 3)), true) is GrabOutcome.Assigned)
    }

    @Test
    fun historicalWonWithoutOwnedRowIsFeedbackOnly() {
        // Won round 2, then cancelled before boarding: order is back as round 3 READY.
        val reopened = payload("d1", row("o1", "AVAILABLE", 3, status = "READY"), results = listOf(Triple("o1", 2, "WON")))
        val outcome = CompetitionVerdicts.resolve(key, reopened, true)
        assertTrue(outcome is GrabOutcome.WonFeedback)
        assertFalse(GrabOutcomes.mayNavigate(outcome))
        // WON but the order is gone from this driver's state: still no navigation.
        val gone = CompetitionVerdicts.resolve(key, payload("d1", results = listOf(Triple("o1", 2, "WON"))), true)
        assertFalse(GrabOutcomes.mayNavigate(gone))
    }

    @Test
    fun candidateWaitsThenGetsAnHonestFinalResult() {
        val pending = CompetitionVerdicts.resolve(key, payload("d1", row("o1", "AVAILABLE", 2, "OPEN", "PENDING")), true)
        assertTrue(pending is GrabOutcome.Pending)
        assertFalse(GrabOutcomes.mayNavigate(pending))
        assertFalse(GrabOutcomes.isFinal(pending))
        // Lost and no longer in the hall: competitionResults closes it.
        val lost = CompetitionVerdicts.resolve(key, payload("d1", results = listOf(Triple("o1", 2, "LOST"))), true)
        assertTrue(lost is GrabOutcome.Lost && GrabOutcomes.isFinal(lost))
        // No winner → same order new round READY: this round is over for us, nothing carried over.
        val next = CompetitionVerdicts.resolve(key, payload("d1", row("o1", "AVAILABLE", 3, "READY")), true)
        assertTrue(next is GrabOutcome.Lost)
        // A request that never reached the server (still NONE) is retryable, not "competing".
        val notJoined = CompetitionVerdicts.resolve(key, payload("d1", row("o1", "AVAILABLE", 2, "OPEN", "NONE")), false)
        assertTrue(notJoined is GrabOutcome.Failed && notJoined.retryable)
        // Withdrawn by order.cancel {id, competitionRound}.
        assertTrue(CompetitionVerdicts.resolve(key, payload("d1", row("o1", "AVAILABLE", 2, "OPEN", "WITHDRAWN")), true) is GrabOutcome.Lost)
    }

    @Test
    fun contractFailureReasonsAreExplainedWithoutClaimingAWinner() {
        val location = CompetitionVerdicts.fromFailure("o1", 409, "COMPETITION_LOCATION_REQUIRED", "x")
        assertTrue(location is GrabOutcome.Failed && !location.retryable)
        assertTrue(GrabOutcomes.message(location).contains("定位"))
        assertFalse(GrabOutcomes.message(location).contains("获派"))
        assertTrue(CompetitionVerdicts.fromFailure("o1", 409, "COMPETITION_ROUND_STALE", "") is GrabOutcome.Lost)
        val network = CompetitionVerdicts.fromFailure("o1", 0, "", "")
        assertTrue(network is GrabOutcome.Failed && network.retryable)
        assertTrue((CompetitionVerdicts.fromFailure("o1", 401, "", "") as GrabOutcome.Failed).let { !it.retryable })
    }

    @Test
    fun repeatedClickIsIgnoredAndRetryReusesRequestId() {
        val watch = CompetitionWatch().also { it.bind("d1") }
        var ids = 0
        val first = watch.begin(key, 0, true) { "req-${++ids}" }!!
        assertNull(watch.begin(key, 10, true) { "req-${++ids}" })
        assertTrue(watch.sent(key, first.token, CompetitionWatch.Phase.RETRYABLE))
        val retry = watch.begin(key, 20, true) { "req-${++ids}" }!!
        assertEquals(first.requestId, retry.requestId)
        assertNotEquals(first.token, retry.token)
        // The superseded attempt's late completion changes nothing.
        assertFalse(watch.sent(key, first.token, CompetitionWatch.Phase.PENDING))
        assertNull(watch.finish(key, first.token))
        assertTrue(watch.isPending(key))
        // Other account or a non-competition key never starts.
        assertNull(watch.begin(GrabAlertKey("d2", "o1", "2"), 0, true) { "x" })
        assertNull(watch.begin(GrabAlertKey("d1", "p1", ""), 0, true) { "x" })
    }

    @Test
    fun accountSwitchCleanupNeverReleasesTheNewSessionsBusyAttempt() {
        val watch = CompetitionWatch().also { it.bind("d1") }
        val old = watch.begin(key, 0, false) { "old" }!!
        val oldGeneration = watch.generation
        watch.bind("d2")
        watch.bind("d1")
        assertNotEquals(oldGeneration, watch.generation)
        assertFalse(watch.isPending(key))
        val fresh = watch.begin(key, 100, true) { "new" }!!
        assertEquals("new", fresh.requestId)
        // The old HTTP's finally/callback arrives late.
        assertFalse(watch.sent(key, old.token, CompetitionWatch.Phase.RETRYABLE))
        assertNull(watch.finish(key, old.token))
        assertTrue(watch.isPending(key))
    }

    @Test
    fun fiveSecondCandidacyOutlivesTheThreeSecondReminder() {
        val book = GrabAlertBook().also { it.bind("d1") }
        val watch = CompetitionWatch().also { it.bind("d1") }
        book.admit(listOf(GrabAlert(key, "STANDARD", "a", "b", 0)), false, 0)
        val attempt = watch.begin(key, 500, false) { "r" }!!
        watch.sent(key, attempt.token, CompetitionWatch.Phase.PENDING)
        book.tick(3_000)
        book.clear() // e.g. the won order turned PICKUP and reminders were cleared
        assertNull(book.current)
        assertTrue(watch.isPending(key))
        assertEquals(listOf(key), watch.awaiting().map { it.key })
    }

    @Test
    fun candidatePollIsShortThenBoundedAndStops() {
        val watch = CompetitionWatch(maxWaitMs = 20_000).also { it.bind("d1") }
        assertNull(watch.nextPollDelay(0))
        val a = watch.begin(key, 0, false) { "r" }!!
        // SENDING is not polled; the HTTP response itself is awaited.
        assertNull(watch.nextPollDelay(0))
        watch.sent(key, a.token, CompetitionWatch.Phase.PENDING)
        assertEquals(1_000L, watch.nextPollDelay(1_000))
        assertEquals(2_000L, watch.nextPollDelay(9_000))
        assertEquals(4_000L, watch.nextPollDelay(15_000))
        assertTrue(watch.timedOut(19_000).isEmpty())
        assertEquals(1, watch.timedOut(20_001).size)
        watch.finish(key, a.token)
        assertNull(watch.nextPollDelay(20_001))
    }

    @Test
    fun notificationActionIdentityIsPerAccountOrderRound() {
        val a = GrabAlertKey("d1", "o1", "1")
        val b = GrabAlertKey("d1", "o1", "2")
        val c = GrabAlertKey("d2", "o1", "1")
        assertNotEquals(GrabActionIdentity.uri(a), GrabActionIdentity.uri(b))
        assertNotEquals(GrabActionIdentity.uri(a), GrabActionIdentity.uri(c))
        assertEquals(a, GrabActionIdentity.keyOf(GrabActionIdentity.uri(a)))
        assertEquals(b, GrabActionIdentity.keyOf(GrabActionIdentity.uri(b)))
        assertNull(GrabActionIdentity.keyOf("yjxgrab://alert?k=bad"))
        assertNull(GrabActionIdentity.keyOf("https://example.invalid/?k=" + a.encode()))
        assertNull(GrabActionIdentity.keyOf(null))
        assertFalse(GrabActionIdentity.uri(a).contains("token"))
    }
}
