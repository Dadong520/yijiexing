package cn.hbzjhs.passenger

import org.json.JSONObject

/** The exact ordinary-order price and identity visible when the payment dialog opens. */
data class PaymentSnapshot(
  val orderId: String,
  val fare: Long,
  val fareVersion: Long,
  val stage: String,
) {
  companion object {
    fun from(order: JSONObject) =
      PaymentSnapshot(
        order.text("id"),
        order.optLong("fare"),
        order.optLong("fareVersion", 0),
        order.text("stage"),
      )
  }
}

fun paymentMatches(shown: PaymentSnapshot?, latest: JSONObject): Boolean =
  shown != null &&
    shown.orderId.isNotBlank() &&
    shown.fare > 0 &&
    shown.fareVersion >= 0 &&
    latest.text("kind") in setOf("STANDARD", "RESERVED", "BID") &&
    latest.text("stage") in setOf("PREPAY", "WAIT_PAY") &&
    !latest.optBoolean("paid") &&
    shown == PaymentSnapshot.from(latest)

fun confirmedPaymentPayload(
  shown: PaymentSnapshot,
  latest: JSONObject,
  outcome: String,
): JSONObject {
  require(paymentMatches(shown, latest)) { "订单金额、版本或支付状态已变化，请关闭后重新核对并确认" }
  require(outcome in setOf("SUCCESS", "FAILED", "PENDING", "CANCEL"))
  return JSONObject()
    .put("id", shown.orderId)
    .put("expectedFare", shown.fare)
    .put("fareVersion", shown.fareVersion)
    .put("outcome", outcome)
}
