package cn.hbzjhs.driver.preview

import org.junit.Assert.*
import org.junit.Test

class CountdownPresentationTest {
    private val p = CountdownPresentation

    @Test fun ceilRoundsUpAndClampsAtZero() {
        assertEquals(5, p.ceilSeconds(5_000L))
        assertEquals(5, p.ceilSeconds(4_001L))
        assertEquals(4, p.ceilSeconds(4_000L))
        assertEquals(1, p.ceilSeconds(1L))
        assertEquals(0, p.ceilSeconds(0L))
        assertEquals(0, p.ceilSeconds(-250L))
        assertNull(p.ceilSeconds(null))
    }

    @Test fun fractionIsBoundedAndUnknownIsEmpty() {
        assertEquals(1f, p.fraction(9_000L, 3_000L), 0f)
        assertEquals(0.5f, p.fraction(1_500L, 3_000L), 0.0001f)
        assertEquals(0f, p.fraction(-1L, 3_000L), 0f)
        assertEquals(0f, p.fraction(null, 3_000L), 0f)
        assertEquals(0f, p.fraction(1_000L, 0L), 0f)
    }

    @Test fun competitionOnlyForDirectKinds() {
        assertEquals(CountdownPresentation.Competition.Hidden, p.competition("BID", "OPEN", 3_000L))
        assertEquals(CountdownPresentation.Competition.Hidden, p.competition("NEGOTIATED", "OPEN", 3_000L))
        assertEquals(CountdownPresentation.Competition.Hidden, p.competition("STANDARD", null, 3_000L))
    }

    @Test fun competitionStates() {
        assertEquals(CountdownPresentation.Competition.NotStarted, p.competition("STANDARD", "READY", null))
        val running = p.competition("RESERVED", "OPEN", 2_100L)
        assertTrue(running is CountdownPresentation.Competition.Running)
        assertEquals(3, (running as CountdownPresentation.Competition.Running).seconds)
        assertEquals(CountdownPresentation.Competition.Closed, p.competition("STANDARD", "OPEN", 0L))
        assertEquals(CountdownPresentation.Competition.Closed, p.competition("STANDARD", "OPEN", -5L))
        // No measured anchor: never a made-up number.
        assertEquals(CountdownPresentation.Competition.Unknown, p.competition("STANDARD", "OPEN", null))
        assertEquals(CountdownPresentation.Competition.Closed, p.competition("STANDARD", "ASSIGNED", 4_000L))
    }

    @Test fun texts() {
        assertEquals("竞抢剩余 3 秒", p.competitionText(CountdownPresentation.Competition.Running(3, 0.6f)))
        assertEquals("", p.competitionText(CountdownPresentation.Competition.Hidden))
        assertEquals("2 秒后收起", p.reminderText(1_200L))
        assertEquals("提醒即将收起", p.reminderText(0L))
        assertEquals("", p.reminderText(null))
    }
}
