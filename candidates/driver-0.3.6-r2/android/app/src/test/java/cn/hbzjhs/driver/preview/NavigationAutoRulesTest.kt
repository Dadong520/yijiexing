package cn.hbzjhs.driver.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAutoRulesTest {
    @Test
    fun onlyRealLegStartsCount() {
        assertTrue(NavigationAutoRules.isNewLeg(Stage.AVAILABLE, Stage.PICKUP))
        assertTrue(NavigationAutoRules.isNewLeg(Stage.RESERVED, Stage.PICKUP))
        assertTrue(NavigationAutoRules.isNewLeg(Stage.ARRIVED, Stage.TRIP))
        assertFalse(NavigationAutoRules.isNewLeg(Stage.PICKUP, Stage.ARRIVED))
        assertFalse(NavigationAutoRules.isNewLeg(Stage.AVAILABLE, Stage.RESERVED))
        assertFalse(NavigationAutoRules.isNewLeg(null, Stage.PICKUP))
    }

    @Test
    fun tripDistanceRequestThatKeepsTripNeverNavigates() {
        assertNull(NavigationAutoRules.claim(emptyList(), "d1", "o1", Stage.TRIP, Stage.TRIP))
        assertNull(NavigationAutoRules.claim(emptyList(), "d1", "o1", Stage.TRIP, Stage.WAIT_PAY))
        assertNull(NavigationAutoRules.claim(emptyList(), "d1", "o1", Stage.ARRIVED, Stage.ARRIVED))
    }

    @Test
    fun eachAccountOrderLegOpensOnceEvenAfterRestart() {
        var memory = NavigationAutoRules.claim(emptyList(), "d1", "o1", Stage.AVAILABLE, Stage.PICKUP)!!
        // Retry, replayed response, or the same leg seen again after a restart (memory is persisted).
        assertNull(NavigationAutoRules.claim(memory, "d1", "o1", Stage.AVAILABLE, Stage.PICKUP))
        memory = NavigationAutoRules.claim(memory, "d1", "o1", Stage.ARRIVED, Stage.TRIP)!!
        assertNull(NavigationAutoRules.claim(memory, "d1", "o1", Stage.ARRIVED, Stage.TRIP))
        assertNotNull(NavigationAutoRules.claim(memory, "d1", "o2", Stage.ARRIVED, Stage.TRIP))
        assertNotNull(NavigationAutoRules.claim(memory, "d2", "o1", Stage.ARRIVED, Stage.TRIP))
    }

    @Test
    fun memoryIsBounded() {
        var memory = emptyList<String>()
        repeat(NavigationAutoRules.MAX_REMEMBERED + 20) { memory = NavigationAutoRules.claim(memory, "d1", "o$it", Stage.ARRIVED, Stage.TRIP)!! }
        assertEquals(NavigationAutoRules.MAX_REMEMBERED, memory.size)
        assertEquals(NavigationAutoRules.legKey("d1", "o${NavigationAutoRules.MAX_REMEMBERED + 19}", Stage.TRIP), memory.last())
    }

    @Test
    fun missingIdentityNeverNavigates() {
        assertNull(NavigationAutoRules.claim(emptyList(), "", "o1", Stage.ARRIVED, Stage.TRIP))
        assertNull(NavigationAutoRules.claim(emptyList(), "d1", "", Stage.ARRIVED, Stage.TRIP))
        assertNull(NavigationAutoRules.claim(emptyList(), "d1", "o1", Stage.ARRIVED, null))
    }

    @Test
    fun observerNeedsCompletedConnectedAccountSnapshot() {
        assertTrue(NavigationAutoRules.snapshotUsable(true, true, true, "d1"))
        assertFalse(NavigationAutoRules.snapshotUsable(false, true, true, "d1"))
        assertFalse(NavigationAutoRules.snapshotUsable(true, false, true, "d1"))
        assertFalse(NavigationAutoRules.snapshotUsable(true, true, false, "d1"))
        assertFalse(NavigationAutoRules.snapshotUsable(true, true, true, ""))
    }

    @Test
    fun firstSnapshotIsOnlyABaseline() {
        // After a restart the first synced snapshot already shows TRIP: no event without a baseline.
        assertTrue(NavigationAutoRules.boardedSince(null, mapOf("o1" to Stage.TRIP)).isEmpty())
        val baseline = mapOf("o1" to Stage.ARRIVED, "o2" to Stage.TRIP, "o3" to Stage.PICKUP)
        val now = mapOf("o1" to Stage.TRIP, "o2" to Stage.TRIP, "o3" to Stage.TRIP, "o4" to Stage.TRIP)
        assertEquals(listOf("o1"), NavigationAutoRules.boardedSince(baseline, now))
        assertTrue(NavigationAutoRules.boardedSince(now, now).isEmpty())
    }
}
