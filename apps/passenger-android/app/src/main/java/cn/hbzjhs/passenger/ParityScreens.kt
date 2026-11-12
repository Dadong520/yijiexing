package cn.hbzjhs.passenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun PassengerExtrasScreen(page: String, owner: String, state: JSONObject, store: PassengerStore) {
  if (owner.isBlank() || owner == "null") {
    PageColumn { EmptyCard("请先登录", "登录后查看本账号的功能。") }
    return
  }
  key(owner) {
    when (page) {
      "safety" -> SafetyCenter(owner)
      "coupons" -> PassengerCoupons(state, store)
      else -> PassengerReferral(state)
    }
  }
}

@Composable
private fun PassengerCoupons(state: JSONObject, store: PassengerStore) {
  var center by remember { mutableStateOf(false) }
  var claiming by remember { mutableStateOf(false) }
  var claimProblem by remember { mutableStateOf("") }
  val busy by store.busy.collectAsState()
  val error by store.error.collectAsState()
  LaunchedEffect(busy, claiming) {
    if (claiming && !busy) {
      if (error != null) claimProblem = "当前营销余额不足，无法领取\n${error}"
      claiming = false
    }
  }
  PageColumn {
    SectionTitle("我的卡券与领券中心", "优惠券由服务端发放，模拟抵扣运价费，不兑换现金。")
    Row { FilterChip(!center, { center = false }, label = { Text("我的卡券") }); FilterChip(center, { center = true }, label = { Text("领券中心") }) }
    if (state.optBoolean("couponPoolFused") || claimProblem.isNotBlank()) Text(claimProblem.ifBlank { "当前营销余额不足，无法领取" }, color = Danger)
    if (!center) {
      val coupons = state.rows("coupons")
      if (coupons.isEmpty()) EmptyCard("暂无卡券", "前往领券中心查看可领取的券。")
      coupons.forEach { c -> Panel(c.text("label", c.text("name", "优惠券"))) {
        KV("状态", when(c.text("status")) { "UNUSED" -> "可用"; "LOCKED" -> "锁定中"; "USED" -> "已使用"; "EXPIRED" -> "已过期"; else -> "待同步" })
        KV("面额", "¥${money(c.optLong("amount"))}"); KV("使用门槛", "运价费满¥${money(c.optLong("threshold"))}")
        KV("有效期", "${formatTime(c.text("validFrom"))} 至 ${formatTime(c.text("expiresAt"))}")
        if (c.text("orderId").isNotBlank()) IdentifierButton(c.text("orderId"))
      } }
    } else {
      val templates = state.rows("couponCenter")
      if (templates.isEmpty()) EmptyCard("暂无可领取优惠券", "请稍后刷新查看。")
      templates.forEach { c -> Panel(c.text("label", c.text("name", "优惠券"))) {
        Note(c.text("description")); KV("面额", "¥${money(c.optLong("amount"))}"); KV("门槛", "运价费满¥${money(c.optLong("threshold"))}")
        KV("剩余数量", c.optInt("remaining").toString()); KV("领取情况", "${c.optInt("claimedCount")}/${c.optInt("perUserLimit", 1)}")
        Note(couponValidityText(c))
        ActionButton("领取优惠券", !busy && !state.optBoolean("couponPoolFused") && c.optInt("remaining") > 0 && c.optInt("claimedCount") < c.optInt("perUserLimit", 1)) {
          claimProblem = ""; store.clearError(); claiming = true
          store.action("coupon.claim", JSONObject().put("templateId", c.text("id"))) { claiming = false }
        }
      } }
    }
  }
}

@Composable
private fun PassengerReferral(state: JSONObject) {
  val referral = state.optJSONObject("referral") ?: JSONObject()
  val code = referral.text("code", referral.text("inviteCode"))
  val context = LocalContext.current
  var message by remember(code) { mutableStateOf("") }
  PageColumn {
    SectionTitle("邀请好友", "服务端记录模拟积分拉新，不发生真实现金奖励。")
    Panel("我的专属邀请码") {
      Text(code.ifBlank { "邀请码待同步" }, color = Teal, fontWeight = FontWeight.Bold)
      SecondaryButton("复制邀请码", code.isNotBlank()) {
        runCatching { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("宜捷行邀请码", code)) }
          .onSuccess { message = "邀请码已复制" }.onFailure { message = "复制失败，请重试" }
      }
      if (message.isNotBlank()) Note(message)
      KV("已邀请人数", referral.optInt("invited", referral.optInt("count")).toString())
      KV("已达标人数", referral.optInt("completed").toString())
      KV("累计获得积分", referral.optLong("awarded", referral.optLong("reward")).toString())
      Note(referralRuleText(referral.optJSONObject("rule")))
    }
    val records = referral.rows("records")
    if (records.isEmpty()) EmptyCard("暂无邀请明细", "好友首次注册填写邀请码后显示绑定与达标情况。")
    records.forEach { row -> Panel(row.text("mobileMasked", row.text("phoneTail", "受邀用户"))) {
      KV("邀请时间", formatTime(row.text("createdAt"))); KV("达标状态", row.text("status"))
      KV("奖励积分", row.optLong("reward").toString())
    } }
  }
}

@Composable
private fun SafetyCenter(owner: String) {
  val context = LocalContext.current
  val local = remember { PassengerLocalExtras(context) }
  var contacts by remember(owner) { mutableStateOf(local.contacts(owner)) }
  var editing by remember(owner) { mutableStateOf(false) }
  var editId by remember(owner) { mutableStateOf<String?>(null) }
  var name by remember(owner) { mutableStateOf("") }
  var phone by remember(owner) { mutableStateOf("") }
  var error by remember(owner) { mutableStateOf("") }
  var deleting by remember(owner) { mutableStateOf<SafetyContact?>(null) }
  fun dial(number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }
      .onFailure { error = "未找到可用拨号应用，请手动拨号" }
  }
  PageColumn {
    SectionTitle("安全中心", "联系人仅保存在本机当前账号，不跨设备同步。")
    Panel {
      Text("紧急协助", color = Ink, fontWeight = FontWeight.SemiBold)
      Note("紧急情况请本人拨打当地报警电话；应用不会自动报警。")
      DangerButton("打开 110 拨号界面", icon = androidx.compose.material.icons.Icons.Default.Call) { dial("110") }
    }
    PassengerSectionHeader("安全联系人 · ${contacts.size}/3", if (contacts.size < 3) "＋ 添加" else null) { editId=null; name=""; phone=""; error=""; editing=true }
    if (contacts.isEmpty()) {
      EmptyCard("暂无安全联系人", "按需添加，保存后可手动打开拨号。")
      EmptyHint("点按上方“＋ 添加”，最多保存 3 位联系人。")
    }
    contacts.forEach { contact ->
      Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Column(Modifier.weight(1f)) {
            Text(contact.name, color = Ink, fontWeight = FontWeight.SemiBold)
            Note(contact.phone)
          }
          IconActionButton("拨号", androidx.compose.material.icons.Icons.Default.Call, tint = Success, container = SuccessSoft) { dial(contact.phone) }
        }
        Row {
          PassengerTextButton(onClick = { editId=contact.id; name=contact.name; phone=contact.phone; error=""; editing=true }) { Text("编辑") }
          PassengerTextButton(onClick = { deleting=contact }) { Text("删除", color = Muted) }
        }
      }
    }
    if (error.isNotBlank() && !editing) Text(error, color = MaterialTheme.colorScheme.error)
    ExpandablePanel("安全功能说明", "录音、自动短信与警方联动未接入") {
      Note("联系人不会被自动联系或上传；卸载应用会删除本机记录。")
      Note("录音保护、短信自动发送、警方专网及警务定位联动均未接入。拨号界面打开后，仍需本人确认拨出。")
    }
  }
  if (editing) PassengerModal(title="安全联系人", onDismiss={editing=false}, confirmLabel="保存到本机", onConfirm={
    runCatching { local.save(owner, contacts, editId, name, phone); contacts=local.contacts(owner) }
      .onSuccess { editing=false; error="" }.onFailure { error=it.message ?: "保存失败" }
  }) {
    Field(name, {name=it.take(30)}, "姓名")
    Field(phone, {phone=it.take(22)}, "联系电话", KeyboardType.Phone)
    if (error.isNotBlank()) Text(error, color=MaterialTheme.colorScheme.error)
  }
  deleting?.let { contact ->
    PassengerModalSlot()
    PassengerDialog(onDismissRequest={deleting=null}, title={Text("删除安全联系人？")}, text={Text(contact.name)}, confirmButton={PassengerTextButton(onClick={
      runCatching { local.remove(owner, contacts, contact.id); contacts=local.contacts(owner) }
        .onSuccess { deleting=null }.onFailure { error=it.message ?: "删除失败"; deleting=null }
    }) {Text("删除")}}, dismissButton={PassengerTextButton(onClick={deleting=null}) {Text("保留")}})
  }
}

@Composable
fun PassengerDriverCard(driver: JSONObject, close: () -> Unit) {
  fun value(key: String): String = driver.text(key).takeIf { it.isNotBlank() && it != "null" } ?: "未提供"
  PassengerModal(title="司机服务卡", onDismiss=close, confirmLabel="关闭", onConfirm=close) {
    KV("司机姓名", value("name")); KV("车牌", value("plate")); KV("车型", value("vehicleModel"))
    KV("所属企业", value("organization")); KV("联系方式", value("mobileMasked"))
    val rawScore = driver.opt("serviceScore")
    val score = when (rawScore) {
      is JSONObject -> rawScore.text("value").ifBlank { rawScore.text("score") }
      is Number -> rawScore.toString()
      else -> ""
    }
    KV("服务分", score.ifBlank { "未提供" })
    KV("资质标记", when(driver.text("certificationStatus")) { "APPROVED" -> "服务端标记通过（体验资料）"; "PENDING" -> "待审核"; "REJECTED" -> "未通过"; else -> "未提供" })
    Note("仅展示此订单服务端提供的司机字段，缺失项显示未提供；不代表已对接真实身份或警方认证。电话中继未接入。")
  }
}
