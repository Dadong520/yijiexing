package cn.hbzjhs.passenger

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.Instant
import kotlinx.coroutines.delay
import org.json.JSONObject

/** One thing an order is waiting for the passenger to decide. [key] changes whenever the question does. */
private data class ConfirmPrompt(
  val key: String,
  val orderId: String,
  val title: String,
  val message: String,
  val primary: String,
  // Non-null only for the driver's distance request, which is answered right inside the popup.
  val consent: DistanceConsentView? = null,
)

private fun JSONObject.consentView(account: String): DistanceConsentView? {
  val consent = optJSONObject("distanceConsent") ?: return null
  return DistanceConsentView(
    account, text("id"), text("stage"), consent.text("id"), consent.text("status"),
    consent.text("expectedStage"), consent.text("expiresAt"),
  )
}

private fun JSONObject.confirmPrompt(account: String, now: Instant): ConfirmPrompt? {
  val id = text("id")
  val stage = text("stage")
  val view = consentView(account)
  if (view != null && PassengerConfirmPromptRules.answerable(view, now)) {
    val consent = optJSONObject("distanceConsent")!!
    val dropoff = consent.text("type") == "DROPOFF"
    val distance = if (consent.has("distanceMeters") && !consent.isNull("distanceMeters")) "${consent.optInt("distanceMeters")} 米" else "未知"
    return ConfirmPrompt(
      PassengerConfirmPromptRules.key(account, id, "distance:" + view.consentId),
      id,
      if (dropoff) "司机申请确认已送达" else "司机申请确认已接到您",
      (if (dropoff) "您确认已到达目的地吗？" else "您确认已上车、与司机实际碰面了吗？") +
        "\n距离核验：$distance\n同意后订单进入下一阶段；拒绝则保持当前阶段，司机可重新申请。",
      if (dropoff) "同意已送达" else "同意已上车",
      view,
    )
  }
  fun prompt(question: String, title: String, message: String, primary: String) =
    ConfirmPrompt(PassengerConfirmPromptRules.key(account, id, question), id, title, message, primary)
  // Financial prompts only open the existing order page; nothing is paid or confirmed from here.
  return when {
    stage == "AVAILABLE" && text("kind") == "BID" && rows("bids").isNotEmpty() ->
      prompt("bids:${rows("bids").size}", "有司机报价了", "已有 ${rows("bids").size} 位司机报价，请选择司机。", "去选择司机")
    stage == "PREPAY" ->
      prompt("prepay:${optInt("quoteVersion")}:${optLong("fare")}", "请确认订单金额", "司机已接单，请核对金额并确认模拟预付（不会真实扣款）。", "去确认")
    stage == "WAIT_CONTINUE" ->
      prompt("continue", "司机已取消接驾", "您可以继续呼叫其他司机，或取消此次行程。", "去处理")
    optBoolean("destinationPending") && stage in listOf("PICKUP", "ARRIVED") ->
      prompt("destination", "请补充目的地", "司机需要目的地才能开始行程，请补充并确认体验估价。", "去补充")
    stage == "WAIT_PAY" ->
      prompt("pay", "行程已结束", "请核对费用并确认模拟支付（不会真实扣款）。", "去支付")
    else -> null
  }
}

/** An unpaid finished trip blocks drivers from accepting or starting another trip of this passenger. */
private fun List<JSONObject>.arrearsPrompt(account: String): ConfirmPrompt? {
  val unpaid = firstOrNull { it.text("stage") == "WAIT_PAY" && !it.optBoolean("paid") } ?: return null
  val blocked = firstOrNull { it.text("id") != unpaid.text("id") && it.text("stage") in listOf("AVAILABLE", "RESERVED", "PICKUP") } ?: return null
  return ConfirmPrompt(
    // Same identity as the WAIT_PAY "pay" question of this order.
    PassengerConfirmPromptRules.key(account, unpaid.text("id"), "pay"),
    unpaid.text("id"),
    "请先支付上一笔行程",
    "您有一笔已结束的行程尚未支付（¥${money(unpaid.optLong("fare"))}），支付前司机无法接单或出发接驾。",
    "去支付",
  )
}

/**
 * App-wide popup for orders that wait on the passenger. Each account/order/question is shown once;
 * "稍后" settles only the current one and keeps it reachable on the order page. Questions about the
 * order already open are not repeated, except the driver's distance request. A distance answer is
 * settled only after the server accepted it; failures keep the popup open for a retry. The popup
 * stays hidden while another dialog, full page or announcement is open.
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
  val account = state.optJSONObject("passenger")?.text("id").orEmpty()
  // Keyed by account: logout or switching passengers never reuses another account's memory.
  key(if (logged) account else "") {
    if (logged && account.isNotBlank()) AccountConfirmPrompts(account, state, store, busy, openOrderId, openOrder)
  }
}

@Composable
private fun AccountConfirmPrompts(
  account: String,
  state: JSONObject,
  store: PassengerStore,
  busy: Boolean,
  openOrderId: String,
  openOrder: (String) -> Unit,
) {
  // Newline-joined keys keep the saved state a plain String.
  var settledText by rememberSaveable(account) { mutableStateOf("") }
  var submitting by remember { mutableStateOf("") }
  var problem by remember { mutableStateOf("") }
  var now by remember { mutableStateOf(Instant.now()) }
  val settled = settledText.split('\n').filter { it.isNotBlank() }
  fun settle(key: String) {
    settledText = PassengerConfirmPromptRules.settle(settled, key).joinToString("\n")
  }
  val orders = state.rows("orders")
  val own = orders.mapNotNull { it.confirmPrompt(account, now) }
  // The arrears reminder is the unpaid order's own "pay" question with more context: it takes over that
  // prompt's text in place (same key, same position), so "稍后" settles the bill once and nothing moves
  // ahead of other questions. Without such a prompt it is appended last. It only opens the order.
  val arrears = orders.arrearsPrompt(account)
  val prompts = when {
    arrears == null -> own
    own.any { it.key == arrears.key } -> own.map { if (it.key == arrears.key) arrears else it }
    else -> own + arrears
  }
  val prompt = PassengerConfirmPromptRules.pick(prompts, { it.key }, { it.orderId }, { it.consent != null }, settled.toSet(), openOrderId)
  // A pending prompt holds its slot so an announcement does not claim underneath it.
  val slot = PassengerModalSlot(prompt != null, PassengerModalCoordinator.RANK_PROMPT)
  // PassengerStore.run sets busy synchronously before launching and clears it in finally, after
  // done(). So once busy is false again, a request that never reached done() (network/server
  // error, session switch) failed; keep the popup and show why so it can be retried.
  LaunchedEffect(busy, submitting) {
    if (PassengerConfirmPromptRules.submitFailed(busy, submitting, settled.toSet())) {
      submitting = ""
      problem = store.error.value ?: "提交未完成，请重试"
    }
  }
  if (prompt == null) return
  // Expiry is re-evaluated every second, independently of refreshes.
  LaunchedEffect(prompt.key) {
    problem = ""
    while (prompt.consent != null) {
      delay(1_000)
      now = Instant.now()
    }
  }
  if (PassengerModalCoordinator.othersOpen(slot)) return
  fun answer(approve: Boolean) {
    val shown = prompt.consent ?: return
    val latest = store.state.value
    val currentAccount = latest.optJSONObject("passenger")?.text("id").orEmpty()
    val current = latest.rows("orders").firstOrNull { it.text("id") == shown.orderId }?.consentView(currentAccount)
    val reason = PassengerConfirmPromptRules.rejectReason(shown, current, Instant.now())
    if (reason != null) {
      problem = reason
      now = Instant.now()
      store.refresh(true)
      return
    }
    if (store.busy.value) {
      // run() would refuse synchronously; nothing is sent.
      problem = "上一项操作仍在处理中，请稍候再试；本次尚未提交"
      return
    }
    problem = ""
    submitting = prompt.key
    store.action(
      "order.distanceConsent",
      JSONObject().put("id", shown.orderId).put("consentId", shown.consentId).put("approve", approve),
    ) {
      settle(prompt.key)
      submitting = ""
    }
    if (!store.busy.value) {
      // Refused synchronously (e.g. passenger data not synced): no request was launched.
      submitting = ""
      problem = store.error.value ?: "提交未完成，请重试"
    }
  }
  val inFlight = submitting == prompt.key
  // Unified popup look; the RANK_PROMPT slot above already holds the coordinator, so no page slot.
  PassengerModal(
    title = prompt.title,
    // "稍后" only puts this one question aside and never answers it.
    onDismiss = { if (!inFlight) settle(prompt.key) },
    confirmLabel = if (inFlight) "提交中…" else prompt.primary,
    confirmEnabled = !busy && !inFlight,
    onConfirm = {
      if (prompt.consent != null) answer(true)
      else {
        settle(prompt.key)
        openOrder(prompt.orderId)
      }
    },
    dismissLabel = "稍后",
    extraLabel = if (prompt.consent != null) "拒绝" else null,
    onExtra = { if (!busy && !inFlight) answer(false) },
    holdPageSlot = false,
    dismissEnabled = !inFlight,
    extraEnabled = !busy && !inFlight,
  ) {
    Text(prompt.message, color = Muted)
    if (problem.isNotBlank()) Text(problem, color = Danger)
  }
}
