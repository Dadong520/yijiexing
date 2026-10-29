package cn.hbzjhs.driver.preview

import org.junit.Assert.*
import org.junit.Test

class UIODistancePresentationTest {
    @Test fun missingValuesAreNotProvided() {
        listOf(null, "", "   ", "null", "—", "-").forEach {
            assertEquals("input=$it", "未提供", kmOrMissing(it))
        }
    }

    @Test fun pendingConfirmationKeepsPendingTextWithoutKm() {
        assertEquals("待确认", kmOrMissing("待确认"))
        assertEquals("待确认", kmOrMissing(" 待确认 "))
    }

    @Test fun nonFiniteNegativeAndNonNumericNeverGetKm() {
        listOf("NaN", "Infinity", "-Infinity", "-1", "-0.5", "abc", "1e3", "1.2.3", "km", "约3").forEach {
            assertEquals("input=$it", "未提供", kmOrMissing(it))
        }
    }

    @Test fun zeroIsAValidKnownDistance() {
        assertEquals("0 km", kmOrMissing("0"))
        assertEquals("0.0 km", kmOrMissing("0.0"))
    }

    @Test fun plainNumbersAndDecimalsGetSingleKm() {
        assertEquals("3 km", kmOrMissing("3"))
        assertEquals("12.5 km", kmOrMissing("12.5"))
        assertEquals("1.0 km", kmOrMissing(" 1.0 "))
    }

    @Test fun existingUnitIsNotRepeated() {
        assertEquals("3.2 km", kmOrMissing("3.2km"))
        assertEquals("3.2 km", kmOrMissing("3.2 km"))
        assertEquals("3.2 km", kmOrMissing("3.2 KM"))
        assertEquals("8 km", kmOrMissing("8公里"))
        assertEquals("未提供", kmOrMissing("NaN km"))
        assertEquals("未提供", kmOrMissing("-2 km"))
    }

    @Test fun newOrderNoteSeparatesPrivateDemoFromSharedSandbox() {
        val demo = newOrderSourceNote(isPrivateExample = true)
        val shared = newOrderSourceNote(isPrivateExample = false)
        assertNotEquals(demo, shared)
        assertTrue(demo.contains("演示订单"))
        assertTrue(demo.contains("模拟"))
        assertTrue(shared.contains("沙箱估算"))
        assertTrue(shared.contains("非实时测距"))
        assertTrue(shared.contains("模拟"))
    }
}
