package cn.hbzjhs.passenger

import org.junit.Assert.*
import org.junit.Test

class PassengerRulesTest {
  @Test
  fun coordinatesRejectNonFiniteAndOutOfRange() {
    listOf("NaN", "Infinity", "91", "-91", "abc", "").forEach { value ->
      assertTrue(value, runCatching { coordinate(value, true) }.isFailure)
    }
    assertTrue(runCatching { coordinate("181", false) }.isFailure)
    assertEquals(30.6648, coordinate("30.6648", true), 0.00001)
    assertEquals(-180.0, coordinate("-180", false), 0.00001)
  }

  @Test
  fun onlyPassengerIdentitiesAreAccepted() {
    requireMobile("13800000101")
    requireMobile("13800000102")
    requireMobile("13800000103")
    assertTrue(runCatching { requireMobile("13800000001") }.isFailure)
    assertTrue(runCatching { requireMobile("13800000104") }.isFailure)
  }

  @Test
  fun moneyUsesExactIntegerCents() {
    assertEquals("0.01", money(1))
    assertEquals("32.80", money(3280))
    assertEquals("0.00", money(0))
  }

  @Test
  fun importantTerminalAndPaymentStatesAreExplicit() {
    assertEquals("已取消", stageName("CANCELLED"))
    assertEquals("待模拟预付", stageName("PREPAY"))
    assertEquals("已模拟退款", paymentName("REFUNDED"))
    assertTrue(paymentName("SUCCESS").contains("模拟"))
  }
}
