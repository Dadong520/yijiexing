package cn.hbzjhs.driver.preview

import org.junit.Assert.*
import org.junit.Test

class ReservationAndIncomeTest {
    private fun reservation() = DriverOrder("PM-RES", OrderKind.RESERVED, "起点", "终点", 2000,
        "1.0", "5.0", "", Stage.RESERVED, scheduledAt = "2026-10-27T08:00:00Z")

    @Test fun waitingReservationRemainsAccessibleWithoutOccupyingCurrentTrip() {
        val waiting = reservation()
        val running = waiting.copy(id = "PM-NOW", kind = OrderKind.STANDARD, stage = Stage.PICKUP)
        val state = DriverState(orders = listOf(waiting, running))
        assertFalse(waiting.isActive)
        assertEquals(running, state.activeOrder)
        assertEquals(listOf(waiting), state.waitingReservations)
        assertTrue(waiting.mayAdvanceFrom(Stage.RESERVED))
        assertTrue(waiting.canCancel)
        assertTrue(waiting.copy(stage = Stage.PICKUP).isActive)
    }

    @Test fun planTimeIsShownInBeijingTime() {
        assertEquals("10-27 16:00", reservation().scheduledTimeText)
    }

    @Test fun refundOverridesHistoricalCreditedFlag() {
        val refunded = reservation().copy(stage = Stage.DONE, credited = true, paymentStatus = "REFUNDED")
        assertEquals("已退款冲回（模拟）", refunded.incomeLabel)
        assertEquals(0L, refunded.incomeForDisplay(2000))
    }

    @Test fun currentServerNetIncomeOverridesAnOldCalculatedEstimate() {
        val credited = reservation().copy(stage = Stage.DONE, credited = true, settlementStatus = "CREDITED",
            settlementLabel = "已入账（模拟）", netIncome = 1800)
        assertEquals(1800L, credited.incomeForDisplay(2000))
        assertEquals("已入账（模拟）", credited.incomeLabel)
        assertEquals(0L, credited.copy(settlementStatus = "REVERSED", netIncome = 0).incomeForDisplay(2000))
    }
}
