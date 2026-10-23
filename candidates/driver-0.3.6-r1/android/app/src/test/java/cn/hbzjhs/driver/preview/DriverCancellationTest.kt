package cn.hbzjhs.driver.preview

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DriverCancellationTest {
    private fun order(
        stage: Stage,
        shared: Boolean = true,
        paid: Boolean = true,
        kind: OrderKind = OrderKind.BID,
    ) = DriverOrder(
        id = "TEST-PREPAID-BID",
        kind = kind,
        origin = "测试上车点",
        destination = "测试终点",
        fare = 3600,
        distance = "1.0",
        mileage = "2.0",
        time = "测试",
        stage = stage,
        shared = shared,
        paid = paid,
        canonicalStage = if (shared) stage.name else "",
        driverParticipationStage = if (shared) stage.name else "",
    )

    @Test
    fun selectedPrepaidBidRetainsCancellationAfterParsingServerSnapshot() {
        // A real v7 flow: bid selected -> passenger prepays -> PICKUP.
        val row = JSONObject()
            .put("id", "TEST-PREPAID-BID")
            .put("kind", "BID")
            .put("origin", "测试上车点")
            .put("destination", "测试终点")
            .put("fare", 3600)
            .put("stage", "PICKUP")
            .put("shared", true)
            .put("paid", true)
            .put("paymentStatus", "SUCCESS")
            .put("canonicalStage", "PICKUP")
            .put("driverParticipationStage", "PICKUP")
        val parsed = ConnectedStore.parseDriverState(
            JSONObject().put("orders", JSONArray().put(row))
        ).orders.single()
        assertTrue(parsed.paid)
        assertTrue(parsed.isActive)
        assertTrue("Prepayment must not hide cancellation before the trip", parsed.canCancel)
    }

    @Test
    fun selectedSharedOrdersUseTheServerPreTripCancellationStages() {
        listOf(Stage.PREPAY, Stage.RESERVED, Stage.PICKUP, Stage.ARRIVED).forEach { stage ->
            listOf(false, true).forEach { paid ->
                assertTrue("Shared $stage paid=$paid", order(stage, paid = paid).canCancel)
            }
        }
    }

    @Test
    fun startedFinishedAndUnassignedOrdersCannotCancelEvenWhenShared() {
        listOf(Stage.AVAILABLE, Stage.TRIP, Stage.WAIT_PAY, Stage.DONE, Stage.CANCELLED)
            .forEach { stage ->
                listOf(false, true).forEach { paid ->
                    assertFalse("Cannot cancel $stage paid=$paid", order(stage, paid = paid).canCancel)
                }
            }
    }

    @Test
    fun privatePaidOrdersRemainBlockedAtEveryStage() {
        OrderKind.values().forEach { kind ->
            Stage.values().forEach { stage ->
                assertFalse("Private paid $kind/$stage", order(stage, shared = false, kind = kind).canCancel)
            }
        }
    }

    @Test
    fun unpaidPrivateOrdersPreserveTheExistingCancellationFlow() {
        listOf(Stage.BID_SENT, Stage.PREPAY, Stage.RESERVED, Stage.PICKUP, Stage.ARRIVED)
            .forEach { stage ->
                assertTrue(order(stage, shared = false, paid = false).canCancel)
            }
    }

    @Test
    fun onlyUnpaidBidsCanBeWithdrawnWhileAwaitingSelection() {
        assertTrue(order(Stage.BID_SENT, paid = false).canCancel)
        assertFalse(order(Stage.BID_SENT, paid = true).canCancel)
    }

    @Test
    fun cancelledDriverParticipationNeverRegainsTheCancellationAction() {
        Stage.values().forEach { stage ->
            listOf(false, true).forEach { paid ->
                assertFalse(order(stage, paid = paid).copy(driverParticipationStage = "CANCELLED").canCancel)
            }
        }
    }

    @Test
    fun refundedContinuationSnapshotIsInactiveAndCannotBeCancelledAgain() {
        val row = JSONObject()
            .put("id", "TEST-PREPAID-BID")
            .put("kind", "BID")
            .put("origin", "测试上车点")
            .put("destination", "测试终点")
            .put("fare", 3600)
            .put("stage", "CANCELLED")
            .put("shared", true)
            .put("paid", false)
            .put("paymentStatus", "REFUNDED")
            .put("canonicalStage", "WAIT_CONTINUE")
            .put("driverParticipationStage", "CANCELLED")
        val parsed = ConnectedStore.parseDriverState(
            JSONObject().put("orders", JSONArray().put(row))
        ).orders.single()
        assertEquals("WAIT_CONTINUE", parsed.canonicalStage)
        assertEquals("REFUNDED", parsed.paymentStatus)
        assertFalse(parsed.isActive)
        assertFalse(parsed.canCancel)
    }
}
