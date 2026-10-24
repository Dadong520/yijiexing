package cn.hbzjhs.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun ExtraScreen(page: String, state: JSONObject, store: PassengerStore) {
  when (page) {
    "riders" -> RidersScreen(state, store)
    "invoiceTitles" -> InvoiceTitlesScreen(state, store)
    "invoices" -> InvoicesScreen(state, store)
    "blocked" -> BlockedScreen(state, store)
    else -> HelpScreen(state)
  }
}

@Composable
fun RidersScreen(state: JSONObject, store: PassengerStore) {
  val busy by store.busy.collectAsState()
  var edit by remember { mutableStateOf<JSONObject?>(null) }
  var remove by remember { mutableStateOf<JSONObject?>(null) }
  PageColumn {
    SectionTitle("乘车人管理", "为他人叫车时可选择乘车人，司机核验所选手机号尾号。")
    ActionButton("添加乘车人") { edit = JSONObject() }
    if (state.rows("riders").isEmpty()) EmptyCard("暂无其他乘车人", "默认由当前账号本人乘车；请使用合成体验资料。")
    state.rows("riders").forEach { r ->
      Panel(r.text("name")) {
        Text(r.text("mobile"))
        Row {
          TextButton(onClick = { edit = r }) { Text("编辑") }
          TextButton(onClick = { remove = r }) { Text("删除") }
        }
      }
    }
  }
  edit?.let { r ->
    var name by remember(r) { mutableStateOf(r.text("name")) }
    var mobile by remember(r) { mutableStateOf(r.text("mobile")) }
    AlertDialog(
      onDismissRequest = { edit = null },
      title = { Text("乘车人资料") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Field(name, { name = it.take(30) }, "姓名")
          Field(mobile, { mobile = it.take(11) }, "11 位手机号", KeyboardType.Phone)
          Note("仅用于独立体验订单，请勿输入真实他人隐私信息。")
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            val p = JSONObject().put("name", name.trim()).put("mobile", mobile.trim())
            if (r.has("id")) p.put("id", r.text("id"))
            store.action("rider.save", p)
            edit = null
          },
          enabled = !busy && name.trim().length >= 2 && Regex("1[0-9]{10}").matches(mobile),
        ) {
          Text("保存")
        }
      },
      dismissButton = { TextButton(onClick = { edit = null }) { Text("取消") } },
    )
  }
  remove?.let { r ->
    DeleteDialog("删除乘车人 ${r.text("name")}？", { remove = null }) {
      store.action("rider.remove", JSONObject().put("id", r.text("id")))
      remove = null
    }
  }
}

@Composable
fun InvoiceTitlesScreen(state: JSONObject, store: PassengerStore) {
  val busy by store.busy.collectAsState()
  var edit by remember { mutableStateOf<JSONObject?>(null) }
  var remove by remember { mutableStateOf<JSONObject?>(null) }
  PageColumn {
    SectionTitle("发票抬头", "保存抬头后可生成模拟回执，不可用于实际报销。")
    ActionButton("添加发票抬头") { edit = JSONObject() }
    state.rows("invoiceTitles").forEach { t ->
      Panel(t.text("title")) {
        KV("类型", if (t.text("type") == "COMPANY") "企业" else "个人")
        if (t.text("taxNumber").isNotBlank()) KV("税号", t.text("taxNumber"))
        KV("邮箱", t.text("email", "未填写"))
        Row {
          TextButton(onClick = { edit = t }) { Text("编辑") }
          TextButton(onClick = { remove = t }) { Text("删除") }
        }
      }
    }
    if (state.rows("invoiceTitles").isEmpty()) EmptyCard("暂无抬头", "添加个人或企业抬头以体验发票申请流程。")
  }
  edit?.let { t ->
    var title by remember(t) { mutableStateOf(t.text("title")) }
    var type by remember(t) { mutableStateOf(t.text("type", "PERSONAL")) }
    var tax by remember(t) { mutableStateOf(t.text("taxNumber")) }
    var email by remember(t) { mutableStateOf(t.text("email")) }
    AlertDialog(
      onDismissRequest = { edit = null },
      title = { Text("编辑发票抬头") },
      text = {
        Column(
          Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row {
            FilterChip(type == "PERSONAL", { type = "PERSONAL" }, label = { Text("个人") })
            Spacer(Modifier.width(8.dp))
            FilterChip(type == "COMPANY", { type = "COMPANY" }, label = { Text("企业") })
          }
          Field(title, { title = it }, "发票抬头")
          if (type == "COMPANY") Field(tax, { tax = it }, "纳税人识别号（体验资料）")
          Field(email, { email = it }, "邮箱（可选，不发送邮件）", KeyboardType.Email)
          Note("本功能只生成模拟回执，不连接税务平台，不发送邮件。")
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            val p =
              JSONObject()
                .put("type", type)
                .put("title", title.trim())
                .put("taxNumber", if (type == "COMPANY") tax.trim() else "")
                .put("email", email.trim())
            if (t.has("id")) p.put("id", t.text("id"))
            store.action("invoice.title.save", p)
            edit = null
          },
          enabled =
            !busy && title.trim().length >= 2 && (type != "COMPANY" || tax.trim().length >= 15),
        ) {
          Text("保存")
        }
      },
      dismissButton = { TextButton(onClick = { edit = null }) { Text("取消") } },
    )
  }
  remove?.let { t ->
    DeleteDialog("删除此发票抬头？", { remove = null }) {
      store.action("invoice.title.remove", JSONObject().put("id", t.text("id")))
      remove = null
    }
  }
}

@Composable
fun InvoicesScreen(state: JSONObject, store: PassengerStore) {
  var orderId by remember { mutableStateOf("") }
  var titleId by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf(false) }
  val busy by store.busy.collectAsState()
  val invoices = state.rows("invoices")
  val available =
    state.rows("orders").filter { o ->
      o.text("stage") == "DONE" &&
        o.optBoolean("paid") &&
        invoices.none { it.text("orderId") == o.text("id") }
    }
  PageColumn {
    SectionTitle("发票与模拟回执", "模拟回执不可用于报销，不是真实税务发票。")
    Panel("申请模拟回执") {
      if (available.isEmpty()) Note("暂无可申请订单。已完成且模拟支付成功的订单可以申请，每单仅一次。")
      available.forEach { o ->
        FilterChip(
          orderId == o.text("id"),
          { orderId = o.text("id") },
          label = {
            Text(
              "${orderSummary(o)} · ¥${money(o.optLong("fare"))}\n${o.text("origin")} → ${o.text("destination")}"
            )
          },
        )
      }
      Text("选择发票抬头", fontWeight = FontWeight.Bold)
      if (state.rows("invoiceTitles").isEmpty()) Note("请先返回“我的 → 发票抬头”添加抬头。")
      state.rows("invoiceTitles").forEach { t ->
        FilterChip(
          titleId == t.text("id"),
          { titleId = t.text("id") },
          label = { Text(t.text("title")) },
        )
      }
      ActionButton("生成模拟回执", orderId.isNotBlank() && titleId.isNotBlank() && !busy) {
        confirm = true
      }
    }
    Text("申请记录", fontWeight = FontWeight.Bold)
    if (invoices.isEmpty()) EmptyCard("暂无模拟回执", "生成后可查看云端保存的订单与抬头快照。")
    invoices.reversed().forEach { i ->
      Panel("模拟回执") {
        IdentifierButton(i.text("id"), "回执编号")
        Text("模拟开具 · 不可报销", color = Amber, fontWeight = FontWeight.Bold)
        KV("抬头", i.text("title"))
        IdentifierButton(i.text("orderId"))
        KV("金额", "¥${money(i.optLong("amount"))}")
        KV("生成时间", formatTime(i.text("createdAt")))
        Note(i.text("notice", "这是体验模拟回执，不是税务发票，不可报销。"))
      }
    }
  }
  if (confirm)
    AlertDialog(
      onDismissRequest = { confirm = false },
      title = { Text("确认生成模拟回执") },
      text = { Text("将使用所选抬头保存云端模拟申请，不会开具真实税务发票，也不会发送邮件。") },
      confirmButton = {
        TextButton(
          onClick = {
            store.action(
              "invoice.apply",
              JSONObject().put("orderId", orderId).put("titleId", titleId),
            ) {
              orderId = ""
            }
            confirm = false
          }
        ) {
          Text("生成模拟回执")
        }
      },
      dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
    )
}

@Composable
fun BlockedScreen(state: JSONObject, store: PassengerStore) {
  PageColumn {
    SectionTitle("已屏蔽司机", "后续新订单将不再匹配列表中的司机。")
    if (state.rows("blockedDrivers").isEmpty()) EmptyCard("暂无屏蔽司机", "可在已结束的订单详情中选择不再匹配。")
    state.rows("blockedDrivers").forEach { d ->
      Panel(d.text("name")) {
        Text(d.text("plate"))
        Note(d.text("reason"))
        OutlinedButton(
          onClick = {
            store.action("order.unblockDriver", JSONObject().put("driverId", d.text("driverId")))
          }
        ) {
          Text("恢复匹配此司机")
        }
      }
    }
  }
}

@Composable
fun ComplaintImage(attachment: JSONObject, store: PassengerStore) {
  var bitmap by remember(attachment.text("id")) { mutableStateOf<android.graphics.Bitmap?>(null) }
  TextButton(
    onClick = {
      store.query(attachment.text("path") + "?encoding=base64") { data ->
        runCatching {
            val bytes =
              android.util.Base64.decode(data.text("imageBase64"), android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
          }
          .onSuccess { bitmap = it }
          .onFailure { store.reportError("投诉图片解码失败，请重试") }
      }
    }
  ) {
    Text("查看投诉图片 · ${attachment.optLong("size")/1024} KB")
  }
  bitmap?.let { b ->
    androidx.compose.foundation.Image(
      bitmap = b.asImageBitmap(),
      contentDescription = "本人上传的投诉图片",
      modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
    )
  }
}
