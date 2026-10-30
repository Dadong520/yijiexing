package cn.hbzjhs.passenger

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.Instant
import java.time.OffsetDateTime
import org.json.JSONObject

/** One thing an order is waiting for the passenger to decide. [key] changes whenever the question does. */
private data class ConfirmPrompt(
  val key: String,
  val orderId: String,
  val title: String,
  val message: String,
  val primary: String,
  // Non-null only for the driver's distance request, which is answered right inside the popup.
  val consentId: String? = null,
)

private fun expired(iso: String): Boolean {
  if (iso.isBlank()) return false
  val at = runCatching { OffsetDateTime.parse(iso).toInstant() }.recoverCatching { Instant.parse(iso) }.getOrNull() ?: return false
  return at.isBefore(Instant.now())
}

private fun JSONObject.confirmPrompt(): ConfirmPrompt? {
  val id = text("id")
  val stage = text("stage")
  val consent = optJSONObject("distanceConsent")
  if (
    consent != null && consent.text("status") == "PENDING" && consent.text("expectedStage") == stage &&
      !expired(consent.text("expiresAt"))
  ) {
    val dropoff = consent.text("type") == "DROPOFF"
    val distance = if (consent.has("distanceMeters") && !consent.isNull("distanceMeters")) "${consent.optInt("distanceMeters")} 米" else "未知"
    return ConfirmPrompt(
      "distance:" + consent.text("id"),
      id,
      if (dropoff) "司机申请确认已送达" else "司机申请确认已接到您",
      (if (dropoff) "您确认已到达目的地吗？" else "您确认已上车、与司机实际碰面了吗？") +
        "\n距离核验：$distance\n同意后订单进入下一阶段；拒绝则保持当前阶段，司机可重新申请。",
      if (dropoff) "同意已送达" else "同意已上车",
      consent.text("id"),
    )
  }
  return when {
    stage == "AVAILABLE" && text("kind") == "BID" && rows("bids").isNotEmpty() ->
      ConfirmPrompt("bids:$id:${rows("bids").size}", id, "有司机报价了", "已有 ${rows("bids").size} 位司机报价，请选择司机。", "去选择司机")
    stage == "PREPAY" ->
      ConfirmPrompt("prepay:$id:${optInt("quoteVersion")}:${optLong("fare")}", id, "请确认订单金额", "司机已接单，请核对金额并确认模拟预付（不会真实扣款）。", "去确认")
    stage == "WAIT_CONTINUE" ->
      ConfirmPrompt("continue:$id", id, "司机已取消接驾", "您可以继续呼叫其他司机，或取消此次行程。", "去处理")
    optBoolean("destinationPending") && stage in listOf("PICKUP", "ARRIVED") ->
      ConfirmPrompt("destination:$id", id, "请补充目的地", "司机需要目的地才能开始行程，请补充并确认体验估价。", "去补充")
    stage == "WAIT_PAY" ->
      ConfirmPrompt("pay:$id", id, "行程已结束", "请核对费用并确认模拟支付（不会真实扣款）。", "去支付")
    else -> null
  }
}

/**
 * App-wide popup for orders that wait on the passenger. Each question is shown once; "稍后" keeps it
 * reachable on the order page. Questions about the order already open are not repeated, except the
 * driver's distance request, which needs an answer before the trip can move on.
 */
@Composable
fun PassengerConfirmPrompts(
  state: JSONObject,
  store: PassengerStore,
  logged: Boolean,
  busy: Boolean,
  openOrderId: String,
  openOrder: (String) -> Unit,
) {
  // Newline-joined keys keep the saved state a plain String.
  var handledText by rememberSaveable { mutableStateOf("") }
  if (!logged) return
  val handled = handledText.split('\n').filter { it.isNotBlank() }
  val orders = state.rows("orders")
  // An unpaid finished trip blocks drivers from accepting or starting another trip of this passenger.
  val unpaid = orders.firstOrNull { it.text("stage") == "WAIT_PAY" && !it.optBoolean("paid") }
  val blocked = unpaid?.let { u ->
    orders.firstOrNull { it.text("id") != u.text("id") && it.text("stage") in listOf("AVAILABLE", "RESERVED", "PICKUP") }
  }
  val arrears =
    if (unpaid != null && blocked != null)
      ConfirmPrompt(
        "arrears:${unpaid.text("id")}:${blocked.text("id")}",
        unpaid.text("id"),
        "请先支付上一笔行程",
        "您有一笔已结束的行程尚未支付（¥${money(unpaid.optLong("fare"))}），支付前司机无法接单或出发接驾。",
        "去支付",
      )
    else null
  val prompts = listOfNotNull(arrears) + orders.mapNotNull { it.confirmPrompt() }
  val prompt =
    prompts.firstOrNull { it.key !in handled && (it.consentId != null || it.orderId != openOrderId) } ?: return
  // Closing one prompt also settles the same kind of question on other orders, so several old unpaid
  // orders never pop up one after another.
  val category = prompt.key.substringBefore(':')
  val close = {
    handledText = (handled + prompts.map { it.key }.filter { it.substringBefore(':') == category })
      .distinct().takeLast(50).joinToString("\n")
  }
  fun answer(approve: Boolean) {
    close()
    store.action(
      "order.distanceConsent",
      JSONObject().put("id", prompt.orderId).put("consentId", prompt.consentId).put("approve", approve),
    )
  }
  PassengerModal(
    title = prompt.title,
    onDismiss = close,
    confirmLabel = prompt.primary,
    confirmEnabled = !busy,
    onConfirm = {
      if (prompt.consentId != null) answer(true)
      else {
        close()
        openOrder(prompt.orderId)
      }
    },
    dismissLabel = "稍后",
    extraLabel = if (prompt.consentId != null) "拒绝" else null,
    onExtra = { if (!busy) answer(false) },
  ) {
    Text(prompt.message, color = Muted)
  }
}
