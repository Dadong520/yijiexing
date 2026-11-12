package cn.hbzjhs.passenger

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CouponValidityTest {
    private val start = "2026-11-12T00:00:00Z"
    private val end = "2026-11-19T23:59:00Z"
    @Test fun rangeUsesBothDatesInsteadOfMissingDayCount() {
        val c = JSONObject().put("validity", "RANGE").put("validStartAt", start).put("validEndAt", end)
        assertEquals("有效期 ${formatTime(start)} 至 ${formatTime(end)}", couponValidityText(c))
        assertFalse(couponValidityText(c).contains("0天"))
    }
    @Test fun rangeWithOnlyEndShowsDeadline() {
        assertEquals("有效期至 ${formatTime(end)}", couponValidityText(JSONObject().put("validity", "RANGE").put("validEndAt", end)))
    }
    @Test fun legacyFixedUsesSameDateContract() {
        assertEquals("有效期至 ${formatTime(end)}", couponValidityText(JSONObject().put("validity", "FIXED").put("validStartAt", JSONObject.NULL).put("validEndAt", end)))
    }
    @Test fun daysUsesPositiveClaimDuration() {
        assertEquals("领取后7天有效", couponValidityText(JSONObject().put("validity", "DAYS").put("validDays", 7)))
    }
    @Test fun missingValidityDataDoesNotInventZeroDayExpiry() {
        assertEquals("有效期待同步", couponValidityText(JSONObject().put("validity", "RANGE")))
        assertEquals("有效期待同步", couponValidityText(JSONObject().put("validity", "DAYS")))
    }
}
