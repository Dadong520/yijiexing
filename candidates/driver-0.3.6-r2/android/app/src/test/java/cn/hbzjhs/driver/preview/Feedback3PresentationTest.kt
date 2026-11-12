package cn.hbzjhs.driver.preview

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class Feedback3PresentationTest {
    private fun order() = DriverOrder("REVIEW", OrderKind.STANDARD, "起点", "终点", 1000, "", "", "", Stage.DONE)
    @Test fun finalFeeRowHonorsRefundAndEveryClosedSettlementState() {
        val refunded = order().copy(credited=true, paymentStatus="REFUNDED")
        assertEquals(0L, driverFeeIncome(refunded, 942).amount)
        assertEquals(refunded.incomeLabel, driverFeeIncome(refunded, 942).label)
        assertFalse(driverFeeIncome(refunded, 942).credited)
        for (status in listOf("REVERSED", "REFUNDED", "CLOSED_NO_INCOME", "UNKNOWN")) {
            val closed = refunded.copy(paymentStatus="SUCCESS", settlementStatus=status, netIncome=0)
            assertEquals(0L, driverFeeIncome(closed, 942).amount)
            assertFalse(driverFeeIncome(closed, 942).credited)
        }
    }
    @Test fun cancelledUncreditedNeverShowsOriginalEstimateAsIncome() {
        val cancelled = order().copy(stage=Stage.CANCELLED)
        assertEquals(0L, driverFeeIncome(cancelled, 942).amount)
        assertEquals("本单未计入收入", driverFeeIncome(cancelled, 942).label)
        assertFalse(driverFeeIncome(cancelled, 942).credited)
    }
    @Test fun creditedUsesAuthoritativeNetIncomeAndPendingRemainsEstimate() {
        val credited = order().copy(credited=true, settlementStatus="CREDITED", settlementLabel="已入账（模拟）", netIncome=800)
        assertEquals(800L, driverFeeIncome(credited, 942).amount)
        assertTrue(driverFeeIncome(credited, 942).credited)
        val pending = order().copy(stage=Stage.WAIT_PAY)
        assertEquals(942L, driverFeeIncome(pending, 942).amount)
        assertFalse(driverFeeIncome(pending, 942).credited)
        assertTrue(driverFeeIncome(pending, 942).label.contains("未入账"))
    }
    @Test fun cancelFeesParseFromSharedOrdersAndStaySeparateFromFareIncome() {
        for (status in listOf("UNPAID", "PAID")) {
            val row = JSONObject().put("id", "REVIEW").put("kind", "STANDARD").put("origin", "起点").put("destination", "终点")
                .put("fare", 1000).put("stage", "CANCELLED").put("settlementStatus", "CLOSED_NO_INCOME")
                .put("cancelFee", JSONObject().put("amount", 300).put("status", status).put("responsible", "PASSENGER").put("credited", status == "PAID"))
            val parsed = ConnectedStore.parseDriverState(JSONObject().put("orders", JSONArray().put(row))).orders.single()
            assertEquals(300L, parsed.cancelFeeAmount)
            assertEquals(status, parsed.cancelFeeStatus)
            assertEquals("PASSENGER", parsed.cancelFeeResponsible)
            assertEquals(0L, driverFeeIncome(parsed, 942).amount)
            val text = parsed.cancelFeeDisplay().text!!
            assertTrue(text.contains("¥3.00"))
            assertTrue(text.contains(if (status == "PAID") "已模拟补偿入账" else "待乘客模拟支付"))
            assertTrue(text.contains("不计入正常完单分账"))
        }
    }
    @Test fun feeEndpointUsesSameCancelFeeParserAndLegacyOrdersRemainEmpty() {
        val fee = JSONObject("""{"cancelFee":{"amount":300,"status":"PAID","responsible":"ADMIN","credited":true}}""")
        assertTrue(parseDriverCancelFee(fee.optJSONObject("cancelFee")).text!!.contains("已模拟补偿入账"))
        assertNull(parseDriverCancelFee(null).text)
        assertNull(order().cancelFeeDisplay().text)
    }
    @Test fun openServiceScoreBoundsUseNullInsteadOfNaN() {
        assertEquals("≥110分", serviceScoreRangeText(JSONObject("""{"min":110,"max":null}""")))
        assertEquals("<80分", serviceScoreRangeText(JSONObject("""{"min":null,"max":80}""")))
        assertEquals("100–110分", serviceScoreRangeText(JSONObject("""{"min":100.0,"max":110.0}""")))
        assertEquals("80.5–100分", serviceScoreRangeText(JSONObject("""{"min":80.5,"max":100}""")))
        assertEquals("区间待同步", serviceScoreRangeText(JSONObject()))
        assertFalse(serviceScoreRangeText(JSONObject("""{"min":"bad","max":null}""")).contains("NaN"))
    }
}
