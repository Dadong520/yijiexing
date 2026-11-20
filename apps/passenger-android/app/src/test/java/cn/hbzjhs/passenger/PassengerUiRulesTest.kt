package cn.hbzjhs.passenger

import org.junit.Assert.*
import org.junit.Test

class PassengerUiRulesTest {
  @Test fun cancellationRequiresAnExplicitSupportedChoiceOrBoundedOtherText() {
    assertEquals("等待时间较长",PassengerUiRules.cancellationReason("等待时间较长","忽略的旧输入"))
    assertEquals("临时改乘公交",PassengerUiRules.cancellationReason("其他"," 临时改乘公交 "))
    listOf("", " ", "字", "字".repeat(201)).forEach { assertNull(PassengerUiRules.cancellationReason("其他",it)) }
    assertNull(PassengerUiRules.cancellationReason("未知理由","随便输入"))
    assertNotNull(PassengerUiRules.cancellationReason("其他","字".repeat(200)))
  }
  @Test fun allFirstFiltersKeepDebtAndReservationsInProgress() {
    assertEquals(listOf("全部","进行中","已完成","已取消"),PassengerUiRules.orderFilters)
    listOf("WAIT_PAY","WAIT_CONTINUE","AVAILABLE","RESERVED","PREPAY","PICKUP","ARRIVED","TRIP").forEach { assertTrue(PassengerUiRules.matchesOrderFilter(it,"进行中")) }
    assertFalse(PassengerUiRules.matchesOrderFilter("DONE","进行中"))
    assertFalse(PassengerUiRules.matchesOrderFilter("CANCELLED","进行中"))
    assertTrue(PassengerUiRules.matchesOrderFilter("DONE","已完成"))
  }
  @Test fun orderFilterCountAlwaysShowsZeroAndPositiveCounts() {
    assertEquals("0", PassengerUiRules.orderFilterCountText(0))
    assertEquals("0", PassengerUiRules.orderFilterCountText(-2))
    assertEquals("5", PassengerUiRules.orderFilterCountText(5))
  }
  @Test fun complaintStatusToneSeparatesPendingPositiveNegativeAndNeutral() {
    assertEquals(PassengerUiRules.ComplaintTone.PENDING, PassengerUiRules.complaintStatusTone("PENDING"))
    assertEquals(PassengerUiRules.ComplaintTone.PENDING, PassengerUiRules.complaintStatusTone("processing"))
    assertEquals(PassengerUiRules.ComplaintTone.POSITIVE, PassengerUiRules.complaintStatusTone("REPLIED"))
    assertEquals(PassengerUiRules.ComplaintTone.POSITIVE, PassengerUiRules.complaintStatusTone("RESOLVED"))
    assertEquals(PassengerUiRules.ComplaintTone.NEGATIVE, PassengerUiRules.complaintStatusTone("REJECTED"))
    assertEquals(PassengerUiRules.ComplaintTone.NEUTRAL, PassengerUiRules.complaintStatusTone("CLOSED"))
  }
}
