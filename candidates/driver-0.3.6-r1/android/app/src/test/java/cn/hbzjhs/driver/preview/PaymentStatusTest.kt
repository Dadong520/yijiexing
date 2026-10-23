package cn.hbzjhs.driver.preview

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PaymentStatusTest {
    private fun row() = JSONObject()
        .put("id", "TEST-REFUND")
        .put("kind", "BID")
        .put("origin", "测试上车点")
        .put("destination", "测试终点")
        .put("fare", 3600)
        .put("stage", "CANCELLED")
        .put("shared", true)
        .put("paid", false)

    private fun parse(row: JSONObject) = ConnectedStore.parseDriverState(
        JSONObject().put("orders", JSONArray().put(row))
    ).orders.single()

    @Test
    fun cancelledPrepaidBidDisplaysTheServerRefundAndExactAmount() {
        val order = parse(row().put("paymentStatus", "REFUNDED").put("refundAmount", 3600))
        assertFalse(order.paid)
        assertEquals("已模拟退款", order.paymentStatusLabel)
        assertEquals(3600L, order.refundAmount)
        assertEquals("¥36.00", order.refundAmountText)
    }

    @Test
    fun explicitPaymentStatusTakesPriorityOverTheLegacyPaidFlag() {
        val labels = mapOf(
            "REFUNDED" to "已模拟退款",
            "SUCCESS" to "已模拟支付",
            "FAILED" to "模拟支付失败",
            "PROCESSING" to "模拟支付处理中",
            "CANCELLED" to "已取消模拟支付",
            "UNPAID" to "未模拟支付",
        )
        labels.forEach { (status, label) ->
            listOf(false, true).forEach { paid ->
                assertEquals(label, parse(row().put("paymentStatus", status).put("paid", paid)).paymentStatusLabel)
            }
        }
    }

    @Test
    fun oldPrivateSnapshotsWithoutStatusKeepTheirPaidFlagFallback() {
        assertEquals("已模拟支付", parse(row().put("shared", false).put("paid", true)).paymentStatusLabel)
        assertEquals("未模拟支付", parse(row().put("shared", false)).paymentStatusLabel)
    }

    @Test
    fun unknownPaymentStatusDoesNotInventPaymentOrRefundSuccess() {
        listOf(false, true).forEach { paid ->
            val order = parse(row().put("paymentStatus", "FUTURE_STATUS").put("paid", paid).put("refundAmount", 3600))
            assertEquals("支付状态待确认", order.paymentStatusLabel)
            assertNull(order.refundAmountText)
        }
    }

    @Test
    fun absentOrInvalidRefundAmountIsNotReplacedWithFareOrZero() {
        listOf(row(), row().put("refundAmount", JSONObject.NULL), row().put("refundAmount", -1), row().put("refundAmount", "invalid"))
            .forEach { data ->
                val order = parse(data.put("paymentStatus", "REFUNDED"))
                assertEquals("已模拟退款", order.paymentStatusLabel)
                assertNull(order.refundAmount)
                assertNull(order.refundAmountText)
            }
    }

    @Test
    fun refundAmountUsesServerCentsAndDoesNotAppearForOtherStatuses() {
        assertEquals("¥35.99", parse(row().put("paymentStatus", "REFUNDED").put("refundAmount", 3599)).refundAmountText)
        assertEquals("¥0.00", parse(row().put("paymentStatus", "REFUNDED").put("refundAmount", 0)).refundAmountText)
        listOf("SUCCESS", "FAILED", "PROCESSING", "CANCELLED", "UNPAID", "")
            .forEach { status ->
                assertNull(parse(row().put("paymentStatus", status).put("refundAmount", 3600)).refundAmountText)
            }
    }

    @Test
    fun refundedUncreditedOrderNeverDisplaysTheCalculatedFareAsIncome() {
        val order = parse(row().put("paymentStatus", "REFUNDED").put("refundAmount", 3600).put("credited", false))
        assertEquals("本单未计入收入", order.incomeLabel)
        assertEquals(0L, order.incomeForDisplay(3600))
        assertEquals("¥36.00", order.refundAmountText)
    }

    @Test
    fun unfinishedAndUncreditedCompletedOrdersLabelIncomeAsAnEstimate() {
        listOf("PICKUP", "TRIP", "WAIT_PAY", "DONE").forEach { stage ->
            val order = parse(row().put("stage", stage).put("paymentStatus", "SUCCESS").put("credited", false))
            assertEquals("预计收入（未入账）", order.incomeLabel)
            assertEquals(3600L, order.incomeForDisplay(3600))
        }
    }

    @Test
    fun onlyServerCreditedFlagLabelsIncomeAsRecorded() {
        val order = parse(row().put("stage", "DONE").put("paymentStatus", "SUCCESS").put("credited", true))
        assertEquals("本单已入账收入", order.incomeLabel)
        assertEquals(3600L, order.incomeForDisplay(3600))
        val cancelled = parse(row().put("credited", false))
        assertEquals("本单未计入收入", cancelled.incomeLabel)
        assertEquals(0L, cancelled.incomeForDisplay(3600))
    }
}
