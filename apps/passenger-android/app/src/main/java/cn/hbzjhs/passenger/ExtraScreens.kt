package cn.hbzjhs.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    else -> HelpScreen(state, store)
  }
}

@Composable
fun RidersScreen(state: JSONObject, store: PassengerStore) {
  val busy by store.busy.collectAsState()
  var edit by remember { mutableStateOf<JSONObject?>(null) }
  var remove by remember { mutableStateOf<JSONObject?>(null) }
  PageColumn {
    PassengerSectionHeader("乘车人", "＋ 添加") { edit = JSONObject() }
    Note("为他人叫车可选择乘车人，司机核验所选手机号尾号。")
    val self = state.optJSONObject("passenger") ?: JSONObject()
    Panel {
      Text("${self.text("name", "当前账号")} · 本人", fontWeight = FontWeight.SemiBold, color = Ink)
      Note(self.text("mobile").let { if (it.isNotBlank()) "账号尾号 ${it.takeLast(4)}" else "当前登录账号" })
      if (PassengerDefaults.id(state, "RIDER").isBlank()) Text("新行程由本人乘车", color = Teal)
      else PassengerTextButton(enabled = !busy, onClick = { PassengerDefaults.set(store, "RIDER", null) }) { Text("恢复本人为新行程建议") }
    }
    if (state.rows("riders").isEmpty()) EmptyCard("暂无其他乘车人", "可添加常用乘车人；默认建议只用于新行程草稿。")
    state.rows("riders").forEach { r ->
      val isDefault = PassengerDefaults.id(state, "RIDER") == r.text("id")
      PassengerManagedRecord(r.text("name"), "手机号 ${r.text("mobile")}", isDefault, busy,
        onEdit = { edit = r }, onRemove = { remove = r },
        onDefault = { PassengerDefaults.set(store, "RIDER", if (isDefault) null else r.text("id")) })
    }
  }
  edit?.let { r ->
    var name by remember(r) { mutableStateOf(r.text("name")) }
    var mobile by remember(r) { mutableStateOf(r.text("mobile")) }
    var isDefault by remember(r) { mutableStateOf(r.text("id").isNotBlank() && PassengerDefaults.id(state, "RIDER") == r.text("id")) }
    PassengerDialog(
      onDismissRequest = { edit = null },
      title = { Text("乘车人资料") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Field(name, { name = it.take(30) }, "姓名")
          Field(mobile, { mobile = it.take(11) }, "11 位手机号", KeyboardType.Phone)
          PassengerDefaultCheck(isDefault, "设为新行程默认乘车人") { isDefault = it }
          Note("仅用于独立体验订单，请勿输入真实他人隐私信息。")
        }
      },
      confirmButton = {
        PassengerTextButton(
          onClick = {
            val p = JSONObject().put("name", name.trim()).put("mobile", mobile.trim()).put("isDefault", isDefault)
            if (r.has("id")) p.put("id", r.text("id"))
            store.action("rider.save", p) { edit = null }
          },
          enabled = !busy && name.trim().length >= 2 && Regex("1[0-9]{10}").matches(mobile),
        ) {
          Text("保存")
        }
      },
      dismissButton = { PassengerTextButton(onClick = { edit = null }) { Text("取消") } },
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
    PassengerSectionHeader("发票抬头", "＋ 添加") { edit = JSONObject() }
    Note("用于生成模拟回执，不可用于实际报销。默认抬头只为新申请提供初次建议。")
    if (state.rows("invoiceTitles").isEmpty()) EmptyCard("暂无发票抬头", "可添加个人或企业抬头。")
    state.rows("invoiceTitles").forEach { t ->
      val isDefault = PassengerDefaults.id(state, "INVOICE_TITLE") == t.text("id")
      PassengerManagedRecord(t.text("title"), "${if (t.text("type") == "COMPANY") "企业 · ${t.text("taxNumber")}" else "个人"}\n${t.text("email").ifBlank { "未填写邮箱" }}", isDefault, busy,
        onEdit = { edit = t }, onRemove = { remove = t },
        onDefault = { PassengerDefaults.set(store, "INVOICE_TITLE", if (isDefault) null else t.text("id")) })
    }
  }
  edit?.let { t ->
    var title by remember(t) { mutableStateOf(t.text("title")) }
    var type by remember(t) { mutableStateOf(t.text("type", "PERSONAL")) }
    var tax by remember(t) { mutableStateOf(t.text("taxNumber")) }
    var email by remember(t) { mutableStateOf(t.text("email")) }
    var isDefault by remember(t) { mutableStateOf(t.text("id").isNotBlank() && PassengerDefaults.id(state, "INVOICE_TITLE") == t.text("id")) }
    PassengerDialog(
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
          PassengerDefaultCheck(isDefault, "设为新申请默认抬头") { isDefault = it }
          Note("本功能只生成模拟回执，不连接税务平台，不发送邮件。")
        }
      },
      confirmButton = {
        PassengerTextButton(
          onClick = {
            val p =
              JSONObject()
                .put("isDefault", isDefault)
                .put("type", type)
                .put("title", title.trim())
                .put("taxNumber", if (type == "COMPANY") tax.trim() else "")
                .put("email", email.trim())
            if (t.has("id")) p.put("id", t.text("id"))
            store.action("invoice.title.save", p) { edit = null }
          },
          enabled =
            !busy && title.trim().length >= 2 && (type != "COMPANY" || tax.trim().length >= 15),
        ) {
          Text("保存")
        }
      },
      dismissButton = { PassengerTextButton(onClick = { edit = null }) { Text("取消") } },
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
  val owner = state.optJSONObject("passenger")?.text("id").orEmpty()
  val titles = state.rows("invoiceTitles")
  var orderId by rememberSaveable(owner) { mutableStateOf("") }
  var titleId by rememberSaveable(owner) { mutableStateOf(PassengerDefaults.draftSelection("", false, PassengerDefaults.id(state, "INVOICE_TITLE"), titles.map { it.text("id") }.toSet())) }
  var confirm by remember(owner) { mutableStateOf(false) }
  LaunchedEffect(titles.map { it.text("id") }, owner) {
    if (titleId.isNotBlank() && titles.none { it.text("id") == titleId }) { titleId = ""; confirm = false }
  }
  val busy by store.busy.collectAsState()
  val invoices = state.rows("invoices")
  val available =
    state.rows("orders").filter { o ->
      o.text("stage") == "DONE" &&
        o.optBoolean("paid") &&
        invoices.none { it.text("orderId") == o.text("id") } &&
        PassengerOperations.invoiceEnabled(state, o)
    }
  val selectedEligible = available.any { it.text("id") == orderId }
  LaunchedEffect(available.map { it.text("id") }) {
    if (!selectedEligible) { orderId = ""; confirm = false }
  }
  PageColumn {
    SectionTitle("发票与模拟回执", "模拟回执不可用于报销，不是真实税务发票。")
    if (PassengerOperations.enabled(state,"invoice") || available.isNotEmpty()) Panel("申请模拟回执") {
      if (available.isEmpty()) EmptyCard("暂无可申请回执的订单", "已完成且模拟支付成功的订单可申请，每单仅一次。")
      available.forEach { o ->
        PassengerCompactRecord(
          title = "${o.text("origin")} → ${o.text("destination")}",
          subtitle = "${formatTime(o.text("createdAt"))} · ${kindName(o.text("kind"))}",
          selected = orderId == o.text("id"),
          onClick = { orderId = o.text("id") },
          trailing = { Text("¥${money(o.optLong("fare"))}", color = Teal, fontWeight = FontWeight.SemiBold) },
        )
      }
      Text("选择发票抬头", fontWeight = FontWeight.Bold)
      if (titles.isEmpty()) Note("请先返回“我的 → 发票抬头”添加抬头。")
      titles.forEach { t ->
        PassengerCompactRecord(t.text("title"), "${if (t.text("type") == "COMPANY") "企业" else "个人"}${if (PassengerDefaults.id(state, "INVOICE_TITLE") == t.text("id")) " · 默认" else ""}", titleId == t.text("id"), { titleId = t.text("id") }, trailing = { RadioButton(titleId == t.text("id"), onClick = null) })
      }
      ActionButton("生成模拟回执", selectedEligible && titles.any { it.text("id") == titleId } && !busy) {
        confirm = true
      }
    }
    if (!PassengerOperations.enabled(state,"invoice")) Note("当前地区未开放新的开票服务；历史订单按各自服务地区判断申请资格，历史回执始终可查。")
    Text("申请记录", fontWeight = FontWeight.Bold)
    if (invoices.isEmpty()) EmptyCard("暂无模拟回执", "生成后可查看云端保存的订单与抬头快照。")
    invoices.reversed().forEach { i ->
      ExpandablePanel(i.text("title", "模拟回执"), "¥${money(i.optLong("amount"))} · ${formatTime(i.text("createdAt"))} · 模拟不可报销") {
        KV("抬头", i.text("title"))
        KV("金额", "¥${money(i.optLong("amount"))}")
        KV("生成时间", formatTime(i.text("createdAt")))
        val snapshot = i.optJSONObject("titleSnapshot")
        snapshot?.let { t ->
          KV("类型", if (t.text("type") == "COMPANY") "企业" else "个人")
          if (t.text("taxNumber").isNotBlank()) KV("税号", t.text("taxNumber"))
          if (t.text("email").isNotBlank()) KV("邮箱", t.text("email"))
        }
        IdentifierButton(i.text("id"), "回执编号")
        IdentifierButton(i.text("orderId"))
        Note(i.text("notice", "这是体验模拟回执，不是税务发票，不可报销。"))
      }
    }
  }
  if (confirm && selectedEligible)
    PassengerDialog(
      onDismissRequest = { confirm = false },
      title = { Text("确认生成模拟回执") },
      text = { Text("将使用所选抬头保存云端模拟申请，不会开具真实税务发票，也不会发送邮件。") },
      confirmButton = {
        PassengerTextButton(
          enabled = !busy && selectedEligible && titles.any { it.text("id") == titleId },
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
      dismissButton = { PassengerTextButton(onClick = { confirm = false }) { Text("取消") } },
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
        SecondaryButton("恢复匹配此司机") {
          store.action("order.unblockDriver", JSONObject().put("driverId", d.text("driverId")))
        }
      }
    }
  }
}

@Composable
fun ComplaintImage(attachment: JSONObject, store: PassengerStore) {
  var bitmap by remember(attachment.text("id")) { mutableStateOf<android.graphics.Bitmap?>(null) }
  PassengerTextButton(
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
