package cn.hbzjhs.driver.preview

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrabAlertRulesTest {
    private fun alert(account: String, id: String, round: String = "1", kind: String = "STANDARD") =
        GrabAlert(GrabAlertKey(account, id, round), kind, "起点$id", "终点$id", admittedAt = 0L)

    private fun book(account: String = "d1", seen: GrabSeenLog = BoundedSeenLog()) =
        GrabAlertBook(seen = seen).also { it.bind(account) }

    @Test
    fun lifetimeRunsFromFirstDisplayAndPollingDoesNotRestartIt() {
        val book = book()
        val key = GrabAlertKey("d1", "o1", "1")
        book.admit(listOf(alert("d1", "o1")), onlyNewest = false, now = 1_000)
        assertEquals(1_000L, book.current?.shownAt)
        // Polls re-deliver the same AVAILABLE order/round: nothing new, clock untouched.
        assertTrue(book.admit(listOf(alert("d1", "o1")), false, 2_500).isEmpty())
        assertEquals(1_000L, book.current?.shownAt)
        assertEquals(500L, book.remainingMs(3_500))
        assertFalse(book.tick(3_999))
        assertTrue(book.isLive(key, 3_999))
        assertTrue(book.tick(4_000))
        assertNull(book.current)
        assertFalse(book.isActionable(key, 4_000))
        // Expired reminders never come back for the same round, even after more polls.
        assertTrue(book.admit(listOf(alert("d1", "o1")), false, 4_100).isEmpty())
        assertNull(book.current)
        assertEquals(listOf(key), book.drainRetired())
    }

    @Test
    fun newRoundOfSameOrderRemindsAgainAndOldRoundIsNotActionable() {
        val book = book()
        book.admit(listOf(alert("d1", "o1", "1")), false, 0)
        book.tick(3_000)
        val again = book.admit(listOf(alert("d1", "o1", "2")), false, 10_000)
        assertEquals(listOf(GrabAlertKey("d1", "o1", "2")), again.map { it.key })
        assertEquals(10_000L, book.current?.shownAt)
        assertFalse(book.isActionable(GrabAlertKey("d1", "o1", "1"), 10_001))
        assertTrue(book.isActionable(GrabAlertKey("d1", "o1", "2"), 10_001))
    }

    @Test
    fun accountSwitchEndsRemindersAndSwitchingBackDoesNotReplaySameRound() {
        val book = book("d1")
        book.admit(listOf(alert("d1", "o1")), false, 0)
        val epoch = book.epoch
        assertTrue(book.admit(listOf(alert("d2", "o9")), false, 100).isEmpty())
        assertTrue(book.bind("d2"))
        assertNotEquals(epoch, book.epoch)
        assertNull(book.current)
        assertFalse(book.isActionable(GrabAlertKey("d1", "o1", "1"), 200))
        // The same order id reminds the other account (keys carry the account) ...
        assertEquals(1, book.admit(listOf(alert("d2", "o1")), false, 300).size)
        // ... and switching back to d1 never replays d1's already-seen round.
        book.bind("d1")
        assertTrue(book.admit(listOf(alert("d1", "o1")), false, 400).isEmpty())
        assertEquals(1, book.admit(listOf(alert("d1", "o1", "2")), false, 500).size)
    }

    @Test
    fun persistedSeenSurvivesProcessRestartAndStaysBounded() {
        var stored = emptyList<String>()
        val first = book(seen = BoundedSeenLog(3) { stored = it })
        first.admit(listOf(alert("d1", "o1")), false, 0)
        first.tick(3_000)
        // "Process restart": a new book built from what was persisted.
        val restarted = book(seen = BoundedSeenLog(3, stored))
        assertTrue(restarted.admit(listOf(alert("d1", "o1")), false, 0).isEmpty())
        // Bounded: only account|order|round strings, oldest evicted beyond capacity.
        val log = BoundedSeenLog(2, listOf("garbage", GrabAlertKey("d1", "a", "1").encode()))
        log.addAll(listOf(GrabAlertKey("d1", "b", "1"), GrabAlertKey("d1", "c", "1")))
        assertEquals(listOf(GrabAlertKey("d1", "b", "1").encode(), GrabAlertKey("d1", "c", "1").encode()), log.snapshot())
        assertTrue(stored.all { GrabAlertKey.decode(it) != null })
    }

    @Test
    fun clearEndsRemindersButKeepsSeenSoOfflineOnlineDoesNotReplay() {
        val book = book()
        book.admit(listOf(alert("d1", "o1"), alert("d1", "o2")), false, 0)
        val epoch = book.epoch
        book.clear()
        assertNotEquals(epoch, book.epoch)
        assertNull(book.current)
        assertEquals(0, book.queued)
        assertTrue(book.admit(listOf(alert("d1", "o1"), alert("d1", "o2")), false, 500).isEmpty())
    }

    @Test
    fun multipleNewOrdersPresentInOrderOneAtATimeAndQueueIsBounded() {
        val book = GrabAlertBook(maxQueue = 2, maxQueueWaitMs = 9_000).also { it.bind("d1") }
        val admitted = book.admit((1..5).map { alert("d1", "o$it") }, false, 0)
        assertEquals(5, admitted.size)
        assertEquals("o1", book.current?.key?.orderId)
        assertEquals(2, book.queued)
        book.tick(3_000)
        assertEquals("o4", book.current?.key?.orderId)
        assertEquals(3_000L, book.current?.shownAt)
        book.tick(6_000)
        assertEquals("o5", book.current?.key?.orderId)
        book.tick(9_000)
        assertNull(book.current)
    }

    @Test
    fun queuedReminderTooOldIsSkippedInsteadOfShownLate() {
        val book = GrabAlertBook(maxQueueWaitMs = 4_000).also { it.bind("d1") }
        book.admit(listOf(alert("d1", "o1"), alert("d1", "o2")), false, 0)
        book.dismiss(GrabAlertKey("d1", "o1", "1"), 5_000)
        assertNull(book.current)
    }

    @Test
    fun firstOnlineRemindsOnlyNewestAndMarksRestSeen() {
        val book = book()
        val admitted = book.admit(listOf(alert("d1", "old"), alert("d1", "new")), onlyNewest = true, now = 0)
        assertEquals(listOf("new"), admitted.map { it.key.orderId })
        book.tick(3_000)
        assertTrue(book.admit(listOf(alert("d1", "old")), false, 4_000).isEmpty())
    }

    @Test
    fun withdrawnClosedOrNewRoundLeavesImmediately() {
        val book = book()
        book.admit(listOf(alert("d1", "o1"), alert("d1", "o2")), false, 0)
        book.retain(setOf(GrabAlertKey("d1", "o2", "1")), 1_000)
        assertEquals("o2", book.current?.key?.orderId)
        assertEquals(1_000L, book.current?.shownAt)
        assertEquals(listOf(GrabAlertKey("d1", "o1", "1")), book.drainRetired())
    }

    @Test
    fun keyRoundTripsAndRejectsMalformed() {
        val key = GrabAlertKey("d1", "o1", "3")
        assertEquals(key, GrabAlertKey.decode(key.encode()))
        assertNull(GrabAlertKey.decode(null))
        assertNull(GrabAlertKey.decode("d1"))
        assertNull(GrabAlertKey.decode("\u001fo1\u001f"))
    }

    @Test
    fun onlyStandardAndReservedWithRoundOfferDirectGrab() {
        assertTrue(alert("d1", "o", kind = "STANDARD").directGrab)
        assertTrue(alert("d1", "o", kind = "RESERVED").directGrab)
        assertFalse(alert("d1", "o", round = "", kind = "STANDARD").directGrab)
        assertFalse(alert("d1", "o", kind = "BID").directGrab)
        assertTrue(alert("d1", "o", kind = "BID").bid)
        assertFalse(alert("d1", "o", kind = "NEGOTIATED").directGrab)
        val row = JSONObject().put("id", "o1").put("kind", "STANDARD")
            .put("waypoints", org.json.JSONArray().put(JSONObject().put("name", "A")).put("B"))
            .put("competition", JSONObject().put("round", 2).put("status", "READY").put("myOutcome", "NONE"))
        val parsed = grabAlertFor("d1", row, 0)
        assertEquals("2", parsed?.key?.round)
        assertEquals(listOf("A", "B"), parsed?.waypoints)
        assertEquals("", grabAlertFor("d1", JSONObject().put("id", "p1").put("kind", "BID"), 0)?.key?.round)
        assertNull(grabAlertFor("", row, 0))
    }
}
