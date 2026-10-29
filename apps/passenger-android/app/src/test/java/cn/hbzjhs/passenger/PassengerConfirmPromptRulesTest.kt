package cn.hbzjhs.passenger

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class PassengerConfirmPromptRulesTest {
  private val now = Instant.parse("2026-10-29T12:00:00Z")
  private fun consent(
    account: String = "p1",
    order: String = "A",
    id: String = "DC1",
    stage: String = "ARRIVED",
    expected: String = "ARRIVED",
    status: String = "PENDING",
    expires: String = "2026-10-29T12:05:00Z",
  ) = DistanceConsentView(account, order, stage, id, status, expected, expires)

  @Test
  fun blankOrMalformedExpiryIsNeverUnexpired() {
    assertFalse(PassengerConfirmPromptRules.unexpired("", now))
    assertFalse(PassengerConfirmPromptRules.unexpired("not-a-date", now))
    assertFalse(PassengerConfirmPromptRules.unexpired("2026-10-29T11:59:59Z", now))
    assertTrue(PassengerConfirmPromptRules.unexpired("2026-10-29T20:05:00+08:00", now))
    assertFalse(PassengerConfirmPromptRules.answerable(consent(expires = ""), now))
  }

  @Test
  fun answerableNeedsPendingSameStage() {
    assertTrue(PassengerConfirmPromptRules.answerable(consent(), now))
    assertFalse(PassengerConfirmPromptRules.answerable(consent(status = "APPROVED"), now))
    assertFalse(PassengerConfirmPromptRules.answerable(consent(stage = "TRIP"), now))
    assertFalse(PassengerConfirmPromptRules.answerable(consent(account = ""), now))
  }

  @Test
  fun tapTimeCheckRefusesChangedOrExpiredRequests() {
    val shown = consent()
    assertNull(PassengerConfirmPromptRules.rejectReason(shown, consent(), now))
    assertNotNull(PassengerConfirmPromptRules.rejectReason(shown, null, now))
    assertNotNull(PassengerConfirmPromptRules.rejectReason(shown, consent(account = "p2"), now))
    assertNotNull(PassengerConfirmPromptRules.rejectReason(shown, consent(id = "DC9"), now))
    assertNotNull(PassengerConfirmPromptRules.rejectReason(shown, consent(status = "REJECTED"), now))
    assertNotNull(PassengerConfirmPromptRules.rejectReason(shown, consent(stage = "TRIP"), now))
    assertNotNull(PassengerConfirmPromptRules.rejectReason(shown, consent(expires = "garbage"), now))
    assertNotNull(PassengerConfirmPromptRules.rejectReason(shown, consent(), Instant.parse("2026-10-29T12:06:00Z")))
  }

  private data class P(val key: String, val order: String, val consent: Boolean)

  @Test
  fun settlingOnePromptKeepsOtherOrdersPending() {
    val a = P(PassengerConfirmPromptRules.key("p1", "A", "distance:DC1"), "A", true)
    val b = P(PassengerConfirmPromptRules.key("p1", "B", "distance:DC2"), "B", true)
    val pay = P(PassengerConfirmPromptRules.key("p1", "C", "pay"), "C", false)
    val all = listOf(a, b, pay)
    fun pick(settled: List<String>, open: String = "") = PassengerConfirmPromptRules.pick(all, { it.key }, { it.order }, { it.consent }, settled.toSet(), open)
    assertEquals(a, pick(emptyList()))
    val afterA = PassengerConfirmPromptRules.settle(emptyList(), a.key)
    assertEquals(listOf(a.key), afterA)
    assertEquals(b, pick(afterA))
    // Distance requests show even on their own open order; financial ones do not.
    assertEquals(a, pick(emptyList(), open = "A"))
    assertNull(pick(listOf(a.key, b.key), open = "C"))
  }

  @Test
  fun idleWithoutSuccessCallbackIsAFailureNotASuccess() {
    val k = PassengerConfirmPromptRules.key("p1", "A", "distance:DC1")
    // Still in flight.
    assertFalse(PassengerConfirmPromptRules.submitFailed(true, k, emptySet()))
    // Network/server error or session switch: idle, never settled -> failure, popup stays for retry.
    assertTrue(PassengerConfirmPromptRules.submitFailed(false, k, emptySet()))
    // Another order's success does not count for this one.
    assertTrue(PassengerConfirmPromptRules.submitFailed(false, k, setOf(PassengerConfirmPromptRules.key("p1", "B", "distance:DC2"))))
    // Success callback settled it.
    assertFalse(PassengerConfirmPromptRules.submitFailed(false, k, setOf(k)))
    // Nothing submitted.
    assertFalse(PassengerConfirmPromptRules.submitFailed(false, "", emptySet()))
  }

  @Test
  fun failedOrDeferredPromptOnlyAffectsItself() {
    val a = PassengerConfirmPromptRules.key("p1", "A", "distance:DC1")
    val b = PassengerConfirmPromptRules.key("p1", "B", "distance:DC2")
    // A failed answer leaves nothing settled; "稍后" settles only the current one.
    val later = PassengerConfirmPromptRules.settle(emptyList(), a)
    assertFalse(b in later)
    // A new consent id on the same order is a new question.
    assertFalse(PassengerConfirmPromptRules.key("p1", "A", "distance:DC3") in later)
  }

  @Test
  fun modalCoordinatorReleasesTokens() {
    val mine = Any()
    val other = Any()
    PassengerModalCoordinator.mark(mine, true)
    assertFalse(PassengerModalCoordinator.othersOpen(mine))
    PassengerModalCoordinator.mark(other, true)
    assertTrue(PassengerModalCoordinator.othersOpen(mine))
    PassengerModalCoordinator.mark(other, false)
    assertFalse(PassengerModalCoordinator.othersOpen(mine))
    PassengerModalCoordinator.mark(mine, false)
    assertFalse(PassengerModalCoordinator.othersOpen(null))
  }

  @Test
  fun keysAreScopedByAccountAndOrder() {
    assertNotEquals(PassengerConfirmPromptRules.key("p1", "A", "pay"), PassengerConfirmPromptRules.key("p2", "A", "pay"))
    assertNotEquals(PassengerConfirmPromptRules.key("p1", "A", "pay"), PassengerConfirmPromptRules.key("p1", "B", "pay"))
  }

  @Test
  fun settledListIsBoundedAndDeduplicated() {
    var list = emptyList<String>()
    repeat(60) { list = PassengerConfirmPromptRules.settle(list, "k$it") }
    list = PassengerConfirmPromptRules.settle(list, "k59")
    assertEquals(50, list.size)
    assertEquals("k59", list.last())
    assertEquals(1, list.count { it == "k59" })
  }
}
