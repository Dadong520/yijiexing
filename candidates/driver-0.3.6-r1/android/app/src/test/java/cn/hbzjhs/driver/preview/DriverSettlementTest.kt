package cn.hbzjhs.driver.preview

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DriverSettlementTest {
    private fun order(id: String, stage: Stage, shared: Boolean = true) = DriverOrder(
        id, OrderKind.STANDARD, "宜昌东站", "夷陵广场", 2850,
        "1.2", "8.0", "今天", stage = stage, shared = shared,
        paymentStatus = "UNPAID",
    )

    @Test fun endedUnpaidTripsDoNotOccupyDriverForPrivateOrSharedOrders() {
        for (shared in listOf(false, true)) {
            val unpaid = order("A", Stage.WAIT_PAY, shared)
            val state = DriverState(online = true, orders = listOf(unpaid))
            assertFalse(unpaid.isActive)
            assertNull(state.activeOrder)
            assertEquals(listOf(unpaid), state.pendingCollections)
            assertFalse(unpaid.paid)
            assertFalse(unpaid.credited)
            assertEquals(Stage.WAIT_PAY, unpaid.stage)
        }
    }

    @Test fun everyTransportStageStillOccupiesDriverWhileSettlementDoesNot() {
        val transport = setOf(Stage.BID_SENT, Stage.PREPAY, Stage.RESERVED, Stage.PICKUP, Stage.ARRIVED, Stage.TRIP)
        Stage.entries.forEach { stage ->
            assertEquals("stage $stage", stage in transport, order("A", stage).isActive)
        }
    }

    @Test fun waitingPrepaymentIsNotConfusedWithEndedTripCollection() {
        val prepay = order("PRE", Stage.PREPAY)
        val state = DriverState(orders = listOf(prepay, order("OLD", Stage.WAIT_PAY)))
        assertEquals("PRE", state.activeOrder?.id)
        assertEquals(listOf("OLD"), state.pendingCollections.map { it.id })
    }

    @Test fun olderUnpaidOrderNeverHidesCurrentNewTripRegardlessOfListOrder() {
        val old = order("A", Stage.WAIT_PAY, false)
        val current = order("B", Stage.PICKUP)
        for (rows in listOf(listOf(old, current), listOf(current, old))) {
            val state = DriverState(orders = rows)
            assertEquals("B", state.activeOrder?.id)
            assertEquals(listOf("A"), state.pendingCollections.map { it.id })
        }
    }

    @Test fun latePaymentOfOldTripPreservesNewTripAndOnlyUsesServerBalance() {
        fun snapshot(oldStage: String, paid: Boolean, balance: Long) = ConnectedStore.parseDriverState(
            JSONObject().put("balance", balance).put("orders", JSONArray()
                .put(JSONObject().put("id", "A").put("kind", "STANDARD").put("stage", oldStage)
                    .put("origin", "宜昌东站").put("destination", "夷陵广场")
                    .put("fare", 2850).put("paid", paid).put("credited", paid)
                    .put("paymentStatus", if (paid) "SUCCESS" else "UNPAID"))
                .put(JSONObject().put("id", "B").put("kind", "STANDARD").put("stage", "TRIP")
                    .put("origin", "万达广场").put("destination", "三峡游客中心")
                    .put("fare", 3600).put("paid", false).put("credited", false)))
        )
        val before = snapshot("WAIT_PAY", false, 10000)
        val after = snapshot("DONE", true, 12850)
        assertEquals("B", before.activeOrder?.id)
        assertEquals("B", after.activeOrder?.id)
        assertEquals(Stage.TRIP, after.activeOrder?.stage)
        assertEquals(before.orders[1], after.orders[1])
        assertEquals(2850L, after.balance - before.balance)
        assertTrue(after.pendingCollections.isEmpty())
    }

    @Test fun failedOrProcessingPaymentsRemainCollectibleWithoutOccupyingDriver() {
        for (status in listOf("UNPAID", "FAILED", "PROCESSING", "CANCELLED")) {
            val unpaid = order("A", Stage.WAIT_PAY).copy(paymentStatus = status)
            val state = DriverState(orders = listOf(unpaid))
            assertNull(state.activeOrder)
            assertEquals(1, state.pendingCollections.size)
            assertFalse(unpaid.credited)
        }
    }

    @Test fun cancelledParticipationCannotBecomeACollectionOrCurrentTrip() {
        val exited = order("A", Stage.WAIT_PAY).copy(driverParticipationStage = "CANCELLED")
        val state = DriverState(orders = listOf(exited))
        assertNull(state.activeOrder)
        assertTrue(state.pendingCollections.isEmpty())
    }

    @Test fun staleTripActionsCannotAdvanceChangedOrCancelledOrders() {
        val arrived = order("A", Stage.ARRIVED)
        assertTrue(arrived.mayAdvanceFrom(Stage.ARRIVED))
        assertFalse(arrived.copy(stage = Stage.TRIP).mayAdvanceFrom(Stage.ARRIVED))
        assertFalse(arrived.copy(driverParticipationStage = "CANCELLED").mayAdvanceFrom(Stage.ARRIVED))
        assertFalse(order("A", Stage.WAIT_PAY).mayAdvanceFrom(Stage.WAIT_PAY))
        assertFalse(order("A", Stage.PREPAY).mayAdvanceFrom(Stage.PREPAY))
    }

    @Test fun exampleLabelUsesServerMarkerAndNeverLabelsSharedPassengerOrders() {
        fun parsed(synthetic: Boolean, shared: Boolean): DriverOrder = ConnectedStore.parseDriverState(
            JSONObject().put("orders", JSONArray().put(JSONObject()
                .put("id", "A").put("kind", "STANDARD").put("stage", "WAIT_PAY")
                .put("origin", "宜昌东站").put("destination", "夷陵广场").put("fare", 2850)
                .put("synthetic", synthetic).put("shared", shared)))
        ).orders.single()
        assertTrue(parsed(synthetic = true, shared = false).isPrivateExample)
        assertFalse(parsed(synthetic = true, shared = true).isPrivateExample)
        assertFalse(parsed(synthetic = false, shared = false).isPrivateExample)
    }
}
