package cn.hbzjhs.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
fun PointsScreen(state: JSONObject, store: PassengerStore) {
  val owner = state.optJSONObject("passenger")?.text("id").orEmpty()
  val busy by store.busy.collectAsState()
  val points = state.optJSONObject("points") ?: JSONObject()
  val balance = points.optLong("balance")
  val mall = state.optJSONObject("pointsMall") ?: JSONObject()
  val products = mall.rows("products")
  val redemptions = mall.rows("redemptions")
  val entries = points.rows("entries")
  var redeem by remember(owner) { mutableStateOf<JSONObject?>(null) }
  var name by rememberSaveable(owner) { mutableStateOf("") }
  var mobile by rememberSaveable(owner) { mutableStateOf("") }
  var address by rememberSaveable(owner) { mutableStateOf("") }
  PageColumn {
    SectionTitle("积分商城", "完成行程获得积分，可兑换平台商品。")
    Panel("我的积分") {
      Text(balance.toString(), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Teal)
      RecordRow("累计获得", points.optLong("total").toString())
      mall.optJSONObject("rule")?.let { rule ->
        if (rule.optBoolean("enabled") && rule.optInt("perOrder") > 0) Note("每完成一笔行程获得 ${rule.optInt("perOrder")} 积分。")
      }
    }
    if (products.isEmpty()) {
      EmptyCard("暂无可兑换商品", "商品上架后会显示在这里。")
    } else Panel("可兑换商品") {
      products.forEach { p ->
        val cost = p.optLong("points")
        val stock = p.optInt("stock")
        Text(p.text("name"), fontWeight = FontWeight.Bold)
        if (p.text("description").isNotBlank()) Note(p.text("description"))
        RecordRow("所需积分", cost.toString())
        RecordRow("剩余可兑", "$stock 份")
        RecordRow("类型", if (p.text("type") == "PHYSICAL") "实物 · 需填写收货信息" else "虚拟权益")
        ActionButton(if (stock <= 0) "已兑完" else if (balance < cost) "积分不足" else "立即兑换", stock > 0 && balance >= cost && !busy) { redeem = p }
        HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      }
    }
    if (redemptions.isNotEmpty()) Panel("兑换记录") {
      redemptions.forEach { r ->
        RecordRow(r.text("productName"), "-${r.optLong("points")} 积分")
        RecordRow("状态", when (r.text("status")) { "PENDING_SHIPMENT" -> "待发货"; "SHIPPED" -> "已发货"; else -> "已完成" })
        if (r.text("shipNote").isNotBlank()) RecordRow("物流", r.text("shipNote"))
        RecordRow("兑换时间", formatTime(r.text("createdAt")))
        HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      }
    }
    if (entries.isNotEmpty()) Panel("积分明细") {
      entries.take(50).forEach { row ->
        val delta = row.optLong("delta")
        RecordRow("${row.text("title")}\n${formatTime(row.text("date"))}", "${if (delta > 0) "+" else ""}$delta")
      }
    }
    Note(mall.text("notice", "积分不可提现或转让。"))
  }
  PassengerModalSlot(redeem != null)
  redeem?.let { p ->
    val physical = p.text("type") == "PHYSICAL"
    val ready = !physical || (name.isNotBlank() && mobile.matches(Regex("1\\d{10}")) && address.trim().length >= 5)
    PassengerDialog(
      onDismissRequest = { redeem = null },
      title = { Text("兑换 ${p.text("name")}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("将扣除 ${p.optLong("points")} 积分，兑换后不可撤销。")
          if (physical) {
            Field(name, { name = it.take(30) }, "收货人")
            Field(mobile, { mobile = it.filter(Char::isDigit).take(11) }, "收货手机号", KeyboardType.Phone)
            Field(address, { address = it.take(200) }, "收货地址", lines = 2)
          }
        }
      },
      confirmButton = {
        PassengerTextButton(enabled = !busy && ready, onClick = {
          val payload = JSONObject().put("productId", p.text("id"))
          if (physical) payload.put("receiverName", name.trim()).put("receiverMobile", mobile).put("receiverAddress", address.trim())
          redeem = null
          store.action("points.redeem", payload)
        }) { Text("确认兑换") }
      },
      dismissButton = { PassengerTextButton(onClick = { redeem = null }) { Text("取消") } },
    )
  }
}
