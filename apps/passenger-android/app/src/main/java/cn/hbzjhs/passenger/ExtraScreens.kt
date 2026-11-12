package cn.hbzjhs.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
    PassengerModalSlot()
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
    Note("常用抬头可在开票时直接选择；不保存也可在开票时填写。模拟回执不可用于实际报销。")
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
    PassengerModalSlot()
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
  var typing by rememberSaveable(owner) { mutableStateOf(false) }
  var inlineType by rememberSaveable(owner) { mutableStateOf("PERSONAL") }
  var inlineTitle by rememberSaveable(owner) { mutableStateOf("") }
  var inlineTax by rememberSaveable(owner) { mutableStateOf("") }
  var inlineEmail by rememberSaveable(owner) { mutableStateOf("") }
  var saveTitle by rememberSaveable(owner) { mutableStateOf(false) }
  // No saved or default title is required: with none saved the form is shown directly.
  val manual = typing || titles.isEmpty()
  val inlineValid = inlineTitle.isNotBlank() && (inlineType != "COMPANY" || inlineTax.trim().length in setOf(15, 18, 20) && inlineTax.trim().all { it.isLetterOrDigit() && it.code < 128 })
  val titleReady = if (manual) inlineValid else titles.any { it.text("id") == titleId }
  LaunchedEffect(titles.map { it.text("id") }, owner) {
    if (titleId.isNotBlank() && titles.none { it.text("id") == titleId }) { titleId = ""; confirm = false }
  }
  val busy by store.busy.collectAsState()
  var reissueInvoice by remember(owner) { mutableStateOf<JSONObject?>(null) }
  var reissueReason by remember(owner) { mutableStateOf("") }
  var reissueType by remember(owner) { mutableStateOf("PERSONAL") }
  var reissueTitle by remember(owner) { mutableStateOf("") }
  var reissueTax by remember(owner) { mutableStateOf("") }
  var reissueEmail by remember(owner) { mutableStateOf("") }
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
      if (titles.isNotEmpty()) PassengerCompactRecord("填写新抬头", "本次直接填写，无需提前保存", typing, { typing = true; titleId = "" })
      titles.forEach { t ->
        PassengerCompactRecord(t.text("title"), "${if (t.text("type") == "COMPANY") "企业" else "个人"}${if (PassengerDefaults.id(state, "INVOICE_TITLE") == t.text("id")) " · 默认" else ""}", titleId == t.text("id"), { typing = false; titleId = t.text("id") }, trailing = { RadioButton(titleId == t.text("id"), onClick = null) })
      }
      if (manual) {
        Row {
          FilterChip(inlineType == "PERSONAL", { inlineType = "PERSONAL" }, label = { Text("个人") })
          Spacer(Modifier.width(8.dp))
          FilterChip(inlineType == "COMPANY", { inlineType = "COMPANY" }, label = { Text("企业") })
        }
        Field(inlineTitle, { inlineTitle = it.take(100) }, "发票抬头")
        if (inlineType == "COMPANY") Field(inlineTax, { inlineTax = it.take(20) }, "纳税人识别号（15、18或20位）")
        Field(inlineEmail, { inlineEmail = it.take(254) }, "邮箱（可选，不发送邮件）", KeyboardType.Email)
        PassengerDefaultCheck(saveTitle, "保存为常用抬头") { saveTitle = it }
      }
      ActionButton("生成模拟回执", selectedEligible && titleReady && !busy) {
        confirm = true
      }
    }
    if (!PassengerOperations.enabled(state,"invoice")) Note("当前地区未开放新的开票服务；历史订单按各自服务地区判断申请资格，历史回执始终可查。")
    Text("申请记录", fontWeight = FontWeight.Bold)
    if (invoices.isEmpty()) {
      EmptyCard("暂无模拟回执", "生成后可查看云端保存的订单与抬头快照。")
      EmptyHint("在上方选择已完成且模拟支付成功的订单和抬头，即可生成一条记录。")
    }
    invoices.reversed().forEach { i ->
      ExpandablePanel(i.text("title", "模拟回执"), "¥${money(i.optLong("amount"))} · ${formatTime(i.text("createdAt"))} · ${if(i.text("status") == "RED_FLUSHED") "已冲红" else "模拟已开具"}${when(i.text("reissueStatus")) { "PENDING" -> " · 重开待审"; "APPROVED" -> " · 重开已通过"; "REJECTED" -> " · 重开已驳回"; else -> "" }} · 模拟不可报销") {
        // Label on the left edge, value on the right edge, like the other record forms.
        RecordRow("状态", if(i.text("status") == "RED_FLUSHED") "已冲红" else "已开具模拟回执")
        if(i.text("redFlushReason").isNotBlank()) RecordRow("冲红原因", i.text("redFlushReason"))
        if(i.text("reissueStatus").isNotBlank()) RecordRow("重开状态", when(i.text("reissueStatus")) { "PENDING" -> "重开待审"; "APPROVED" -> "重开已通过"; "REJECTED" -> "重开已驳回"; else -> i.text("reissueStatus") })
        if(i.text("reissuedAs").isNotBlank()) RecordRow("重开为", i.text("reissuedAs"))
        if(i.text("reissueOf").isNotBlank()) RecordRow("原回执", i.text("reissueOf"))
        if(i.text("status") == "SIMULATED_ISSUED" && i.text("reissueStatus") != "PENDING") SecondaryButton("申请重开", !busy) {
          reissueInvoice = i; reissueReason = ""
          val t = i.optJSONObject("titleSnapshot") ?: JSONObject()
          reissueType = t.text("type", "PERSONAL"); reissueTitle = t.text("title", i.text("title"))
          reissueTax = t.text("taxNumber"); reissueEmail = t.text("email")
        }
        state.rows("invoiceReissues").filter { it.text("invoiceId") == i.text("id") }.forEach { request ->
          RecordRow("重开申请", "${request.text("status")} · ${request.text("reason")}")
          if(request.text("decision").isNotBlank()) RecordRow("审核意见", request.text("decision"))
        }
        RecordRow("抬头", i.text("title"))
        RecordRow("金额", "¥${money(i.optLong("amount"))}")
        RecordRow("生成时间", formatTime(i.text("createdAt")))
        val snapshot = i.optJSONObject("titleSnapshot")
        snapshot?.let { t ->
          RecordRow("类型", if (t.text("type") == "COMPANY") "企业" else "个人")
          if (t.text("taxNumber").isNotBlank()) RecordRow("税号", t.text("taxNumber"))
          if (t.text("email").isNotBlank()) RecordRow("邮箱", t.text("email"))
        }
        IdentifierButton(i.text("id"), "回执编号")
        IdentifierButton(i.text("orderId"))
        Note(i.text("notice", "这是体验模拟回执，不是税务发票，不可报销。"))
      }
    }
  }
  reissueInvoice?.let { shown ->
    val current = state.rows("invoices").firstOrNull { it.text("id") == shown.text("id") }
    val eligible = current?.text("status") == "SIMULATED_ISSUED" && current.text("reissueStatus") != "PENDING"
    val valid = reissueReason.trim().length in 2..200 && reissueTitle.trim().isNotEmpty() &&
      (reissueType != "COMPANY" || reissueTax.trim().length in setOf(15,18,20) && reissueTax.trim().all { it.isLetterOrDigit() && it.code < 128 })
    PassengerDialog(onDismissRequest = { if (!busy) reissueInvoice = null }, title = { Text("申请重开模拟回执") }, text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Note("提交后由后台审核；不产生真实税务发票。")
        Field(reissueReason, { reissueReason = it.take(200) }, "重开原因（2-200字）")
        Row { FilterChip(reissueType == "PERSONAL", { reissueType = "PERSONAL" }, label = { Text("个人") }); FilterChip(reissueType == "COMPANY", { reissueType = "COMPANY" }, label = { Text("企业") }) }
        Field(reissueTitle, { reissueTitle = it.take(100) }, "重开抬头")
        if (reissueType == "COMPANY") Field(reissueTax, { reissueTax = it.take(20) }, "企业税号（必填）")
        Field(reissueEmail, { reissueEmail = it.take(254) }, "邮箱（选填）", KeyboardType.Email)
        if (!eligible) Text("回执状态已变化，请关闭后刷新", color = Danger)
      }
    }, confirmButton = { PassengerTextButton(enabled = !busy && eligible && valid, onClick = {
      val latest = store.state.value.rows("invoices").firstOrNull { it.text("id") == shown.text("id") }
      if (store.state.value.optJSONObject("passenger")?.text("id") != owner || latest?.text("status") != "SIMULATED_ISSUED" || latest.text("reissueStatus") == "PENDING") {
        store.reportError("回执状态已变化，请刷新后重试")
      } else store.action("invoice.reissue.apply", JSONObject().put("invoiceId", shown.text("id")).put("reason", reissueReason.trim()).put("type", reissueType).put("title", reissueTitle.trim()).put("taxNumber", if(reissueType == "COMPANY") reissueTax.trim() else "").put("email", reissueEmail.trim())) { reissueInvoice = null }
    }) { Text("提交重开申请") } }, dismissButton = { PassengerTextButton(onClick = { reissueInvoice = null }, enabled = !busy) { Text("取消") } })
  }
  PassengerModalSlot(confirm && selectedEligible)
  if (confirm && selectedEligible)
    PassengerDialog(
      onDismissRequest = { confirm = false },
      title = { Text("确认生成模拟回执") },
      text = { Text("将使用${if (manual) "填写的" else "所选"}抬头保存云端模拟申请，不会开具真实税务发票，也不会发送邮件。") },
      confirmButton = {
        PassengerTextButton(
          enabled = !busy && selectedEligible && titleReady,
          onClick = {
            val payload = JSONObject().put("orderId", orderId)
            if (manual) {
              payload.put("type", inlineType).put("title", inlineTitle.trim()).put("email", inlineEmail.trim()).put("saveTitle", saveTitle)
              if (inlineType == "COMPANY") payload.put("taxNumber", inlineTax.trim())
            } else payload.put("titleId", titleId)
            store.action("invoice.apply", payload) {
              orderId = ""
              inlineTitle = ""; inlineTax = ""; inlineEmail = ""; saveTitle = false; typing = false
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
    if (state.rows("blockedDrivers").isEmpty()) {
      EmptyCard("暂无屏蔽司机", "可在已结束的订单详情中选择不再匹配。")
      EmptyHint("在已完成、待支付或已取消订单的“司机与车辆”中点按“以后不再匹配此司机”。")
    }
    state.rows("blockedDrivers").forEach { d ->
      Panel(d.text("name")) {
        Text(d.text("plate"))
        Note(d.text("reason"))
        SecondaryButton("恢复匹配此司机", icon = Icons.Default.Refresh) {
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
