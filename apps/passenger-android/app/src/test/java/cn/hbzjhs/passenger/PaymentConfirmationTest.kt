package cn.hbzjhs.passenger

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PaymentConfirmationTest {
  private fun unpaid(id: String = "PORD-OLD", kind: String = "STANDARD") =
    JSONObject()
      .put("id", id)
      .put("kind", kind)
      .put("stage", if (kind == "BID") "PREPAY" else "WAIT_PAY")
      .put("fare", 2926)
      .put("paid", false)

  @Test
  fun everyOrdinaryOutcomeCarriesFrozenOrderAmountAndDefaultVersion() {
    listOf("STANDARD", "RESERVED", "BID").forEach { kind ->
      val order = unpaid(kind = kind)
      val snapshot = PaymentSnapshot.from(order)
      listOf("SUCCESS", "FAILED", "PENDING", "CANCEL").forEach { outcome ->
        val payload = confirmedPaymentPayload(snapshot, order, outcome)
        assertEquals(setOf("id", "expectedFare", "fareVersion", "outcome"), payload.keySet())
        assertEquals("PORD-OLD", payload.getString("id"))
        assertEquals(2926L, payload.getLong("expectedFare"))
        assertEquals(0L, payload.getLong("fareVersion"))
        assertEquals(outcome, payload.getString("outcome"))
      }
    }
  }

  @Test
  fun oldDialogCannotPayAChangedAmountEvenBeforeTheVersionChanges() {
    val order = unpaid()
    val snapshot = PaymentSnapshot.from(order)
    order.put("fare", 3600)
    assertEquals(2926L, snapshot.fare)
    assertFalse(paymentMatches(snapshot, order))
    assertTrue(runCatching { confirmedPaymentPayload(snapshot, order, "SUCCESS") }.isFailure)
    order.put("fareVersion", 1)
    val newlyConfirmed = confirmedPaymentPayload(PaymentSnapshot.from(order), order, "SUCCESS")
    assertEquals(3600L, newlyConfirmed.getLong("expectedFare"))
    assertEquals(1L, newlyConfirmed.getLong("fareVersion"))
  }

  @Test
  fun restoredOldAmountStillInvalidatesTheOldDialogThroughItsVersion() {
    val order = unpaid().put("fareVersion", 4)
    val snapshot = PaymentSnapshot.from(order)
    order.put("fare", 3600).put("fareVersion", 5)
    order.put("fare", 2926).put("fareVersion", 6)
    assertEquals(snapshot.fare, order.getLong("fare"))
    assertFalse(paymentMatches(snapshot, order))
    assertTrue(runCatching { confirmedPaymentPayload(snapshot, order, "SUCCESS") }.isFailure)
    assertEquals(
      6L,
      confirmedPaymentPayload(PaymentSnapshot.from(order), order, "SUCCESS").getLong("fareVersion"),
    )
  }

  @Test
  fun oldOrderConfirmationNeverTargetsAnotherOrderOrANonPayableState() {
    val oldOrder = unpaid()
    val snapshot = PaymentSnapshot.from(oldOrder)
    val otherOrder = unpaid("PORD-NEW")
    assertFalse(paymentMatches(snapshot, otherOrder))
    assertTrue(runCatching { confirmedPaymentPayload(snapshot, otherOrder, "SUCCESS") }.isFailure)
    assertEquals("PORD-OLD", confirmedPaymentPayload(snapshot, oldOrder, "SUCCESS").getString("id"))
    assertEquals(
      "PORD-NEW",
      confirmedPaymentPayload(PaymentSnapshot.from(otherOrder), otherOrder, "SUCCESS")
        .getString("id"),
    )
    listOf("TRIP", "DONE", "CANCELLED", "PREPAY").forEach { stage ->
      assertFalse(paymentMatches(snapshot, unpaid().put("stage", stage)))
    }
    assertFalse(paymentMatches(snapshot, unpaid().put("paid", true)))
    assertFalse(paymentMatches(snapshot, unpaid(kind = "NEGOTIATED")))
    assertFalse(paymentMatches(null, oldOrder))
  }
}
