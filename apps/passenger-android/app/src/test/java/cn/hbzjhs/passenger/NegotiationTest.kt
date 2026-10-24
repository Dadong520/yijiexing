package cn.hbzjhs.passenger

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NegotiationTest {
  private val example = "A".repeat(32)

  private fun offered(version: Int = 1, amount: Long = 2800) =
    JSONObject()
      .put("quoteVersion", version)
      .put("fare", amount)
      .put("negotiationStatus", "OFFERED")
      .put("stage", "PREPAY")

  @Test
  fun acceptsOnlyExactInvitationCodeOrOwnDeepLink() {
    assertEquals(example, parseNegotiationCode(" $example "))
    assertEquals(example, parseNegotiationCode("yijiexing://negotiation?code=$example"))
    listOf(
        "A".repeat(31),
        "A".repeat(33),
        "https://example.com/?code=$example",
        "yijiexing://negotiation/other?code=$example",
        "yijiexing://negotiation?code=$example&code=$example",
        "yijiexing://negotiation?code=$example#fragment",
        "yijiexing://user@negotiation?code=$example",
      )
      .forEach { invalid -> assertTrue(runCatching { parseNegotiationCode(invalid) }.isFailure) }
  }

  @Test
  fun confirmationLocksBothAmountAndVersion() {
    val snapshot = QuoteSnapshot.from(offered())
    val payload = negotiatedPaymentPayload("NEG-EXAMPLE", snapshot, offered(), "SUCCESS")
    assertEquals(1, payload.getInt("quoteVersion"))
    assertEquals(2800, payload.getLong("amount"))
    assertTrue(
      runCatching { negotiatedPaymentPayload("NEG-EXAMPLE", snapshot, offered(2, 2800), "SUCCESS") }
        .isFailure
    )
    assertTrue(
      runCatching { negotiatedPaymentPayload("NEG-EXAMPLE", snapshot, offered(1, 2900), "SUCCESS") }
        .isFailure
    )
  }

  @Test
  fun disputedOrStartedOrderCannotBeConfirmedFromOldDialog() {
    val snapshot = QuoteSnapshot.from(offered())
    assertFalse(quotationMatches(snapshot, offered().put("negotiationStatus", "DISPUTED")))
    assertFalse(quotationMatches(snapshot, offered().put("stage", "TRIP")))
    assertFalse(quotationMatches(null, offered()))
  }

  @Test
  fun everySimulationOutcomeCarriesTheConfirmedQuote() {
    val latest = offered(3, 3250)
    listOf("SUCCESS", "FAILED", "PENDING", "CANCEL").forEach { outcome ->
      val payload =
        negotiatedPaymentPayload("NEG-EXAMPLE", QuoteSnapshot.from(latest), latest, outcome)
      assertEquals(3, payload.getInt("quoteVersion"))
      assertEquals(3250, payload.getLong("amount"))
      assertEquals(outcome, payload.getString("outcome"))
    }
  }
}
