package cn.hbzjhs.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.json.JSONObject

@Composable
internal fun BookingPricePreview(state: JSONObject, fare: BookingFare, choice: String, choose: (String) -> Unit, enabled: Boolean) {
  var menu by remember { mutableStateOf(false) }
  val coupons = BookingPricing.usableCoupons(state, fare.tripFare)
  val selected = if (choice == "AUTO") coupons.firstOrNull() else coupons.firstOrNull { it.text("id") == choice }
  Panel("计价预估") {
    KV("运价费", "¥${money(fare.tripFare)}")
    if (fare.serviceFee > 0) KV("区域服务费", "¥${money(fare.serviceFee)}")
    Box {
      PassengerTextButton(onClick = { menu = true }, enabled = enabled) {
        Text("优惠券：${selected?.let { it.text("label", it.text("name")) } ?: "不使用优惠券"} ▾")
      }
      DropdownMenu(menu, { menu = false }) {
        DropdownMenuItem(text = { Text("自动选择最大优惠") }, onClick = { choose("AUTO"); menu = false })
        DropdownMenuItem(text = { Text("不使用优惠券") }, onClick = { choose(""); menu = false })
        coupons.forEach { c -> DropdownMenuItem(text = { Text("${c.text("label", c.text("name"))} · 满¥${money(c.optLong("threshold"))}减¥${money(c.optLong("amount"))}") }, onClick = { choose(c.text("id")); menu = false }) }
      }
    }
    KV("优惠券抵扣", "−¥${money(fare.discount(selected))}")
    KV("预计实付", "¥${money(fare.payable(selected))}")
    Note("按各段直线距离×1.3预估，约${"%.2f".format(java.util.Locale.ROOT, fare.meters/1000)}公里，不含实时等候费；优惠仅抵运价费。最终按服务端计价及券状态结算。${if(fare.night) " 夜间计价" else ""}${if(fare.returning) " 含回空加收" else ""}")
  }
}

@Composable
internal fun CancelFeeNotice(order: JSONObject, open: (String) -> Unit) {
  Panel("待付空车费") {
    Text("您有待付空车费 ¥${money(order.optJSONObject("cancelFee")?.optLong("amount") ?: 0)}，结清前无法创建新行程。", color = Danger)
    SecondaryButton("查看订单并模拟结清") { open(order.text("id")) }
  }
}

@Composable
internal fun CancelFeePayment(order: JSONObject, store: PassengerStore) {
  val account = store.state.value.optJSONObject("passenger")?.text("id").orEmpty()
  val id = order.text("id"); val fee = order.optJSONObject("cancelFee") ?: return
  if (fee.text("status") != "UNPAID") return
  val amount = fee.optLong("amount")
  var shown by remember(account, id, amount) { mutableStateOf(false) }
  val busy by store.busy.collectAsState()
  ActionButton("模拟支付空车费 ¥${money(amount)}", !busy) { shown = true }
  if (shown) PassengerModal(title = "确认模拟支付空车费", onDismiss = { if (!busy) shown = false }, confirmLabel = "确认模拟结清", confirmEnabled = !busy, dismissEnabled = !busy, onConfirm = {
    val latest = store.state.value
    val current = latest.rows("orders").firstOrNull { it.text("id") == id }?.optJSONObject("cancelFee")
    if (latest.optJSONObject("passenger")?.text("id") != account || current?.text("status") != "UNPAID" || current.optLong("amount") != amount) {
      shown = false; store.reportError("空车费状态已变化，请刷新后重新核对"); store.refresh(true)
    } else store.action("order.payCancelFee", JSONObject().put("id", id).put("expectedAmount", amount)) { shown = false }
  }) { Text("将模拟结清 ¥${money(amount)}，不会真实扣款。金额及欠款状态由服务端再次校验。") }
}

@Composable
internal fun FareDetailRows(detail: JSONObject) {
  KV("计费里程", "${detail.optLong("meters")}米 · ${when(detail.text("source")) { "METER" -> "实际定位计费"; "ESTIMATE" -> "预估计费"; else -> "预估里程回退" }}")
  KV("等候费", "¥${money(detail.optLong("waitFee"))} · ${detail.optLong("waitMinutes")}分钟")
  KV("夜间计价", if (detail.optBoolean("night")) "是" else "否")
  val tariff = detail.optJSONObject("tariff")
  KV("回空加收", if(if(detail.has("returnApplied")) detail.optBoolean("returnApplied") else detail.optLong("meters") > (tariff?.optLong("returnAfterMeters", 9000) ?: 9000)) "超过回空里程门槛" else "未达到回空门槛")
}
