package cn.hbzjhs.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
internal fun BookingPricePreview(state: JSONObject, fare: BookingFare, choice: String, choose: (String) -> Unit, enabled: Boolean) {
  var menu by remember { mutableStateOf(false) }
  val allUnused = state.rows("coupons").filter { it.text("status") == "UNUSED" }
  val coupons = BookingPricing.usableCoupons(state, fare.tripFare)
  val selected = if (choice == "AUTO") coupons.firstOrNull() else coupons.firstOrNull { it.text("id") == choice }
  Panel("计价预估与优惠券") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("运价费 ¥${money(fare.tripFare)}${if (fare.serviceFee > 0) " · 服务费 ¥${money(fare.serviceFee)}" else ""}", color = Ink, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold)
        Text("约${"%.2f".format(java.util.Locale.ROOT, fare.meters / 1000)}公里${if (fare.night) " · 夜间" else ""}${if (fare.returning) " · 含回空" else ""}", color = Muted, fontSize = TypeScale.Caption)
      }
      Box(
        Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(BrandSoft)
          .padding(horizontal = 10.dp, vertical = 6.dp)
      ) {
        Text("预计 ¥${money(fare.payable(selected))}", color = BrandStrong, fontWeight = FontWeight.Bold, fontSize = TypeScale.Title)
      }
    }
    Box(Modifier.fillMaxWidth()) {
      if (coupons.isEmpty()) {
        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceMuted)
            .padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("优惠券", color = Muted, fontSize = TypeScale.Body)
          Text(
            if (allUnused.isEmpty()) "暂无优惠券" else "暂无满足当前运价门槛的优惠券",
            color = DisabledText,
            fontSize = TypeScale.Caption,
            fontWeight = FontWeight.Medium,
          )
        }
      } else {
        PassengerTextButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
          val label = when {
            selected != null && choice == "AUTO" -> "已选最大优惠：${selected.text("label", selected.text("name"))} (−¥${money(fare.discount(selected))}) ▾"
            selected != null -> "优惠券：${selected.text("label", selected.text("name"))} (−¥${money(fare.discount(selected))}) ▾"
            else -> "优惠券：不使用优惠券（有 ${coupons.size} 张可用）▾"
          }
          Text(label)
        }
        DropdownMenu(menu, { menu = false }) {
          DropdownMenuItem(
            text = { Text("自动选择最大优惠（推荐 · 省¥${money(fare.discount(coupons.first()))}）", color = BrandStrong, fontWeight = FontWeight.SemiBold) },
            onClick = { choose("AUTO"); menu = false },
          )
          coupons.forEach { c ->
            DropdownMenuItem(
              text = { Text("${c.text("label", c.text("name"))} · 满¥${money(c.optLong("threshold"))}减¥${money(c.optLong("amount"))}") },
              onClick = { choose(c.text("id")); menu = false },
            )
          }
          DropdownMenuItem(text = { Text("不使用优惠券", color = Muted) }, onClick = { choose(""); menu = false })
        }
      }
    }
    if (selected != null) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("优惠券抵扣", color = Muted, fontSize = TypeScale.Caption)
        Text("−¥${money(fare.discount(selected))}", color = Warm, fontWeight = FontWeight.SemiBold, fontSize = TypeScale.Caption)
      }
    }
    Note("按各段直线距离×1.3预估，不含实时等候费；优惠仅抵运价费，最终以服务端结算为准。")
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
