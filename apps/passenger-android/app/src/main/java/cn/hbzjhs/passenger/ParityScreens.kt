package cn.hbzjhs.passenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun PassengerExtrasScreen(page: String, owner: String) {
  if (owner.isBlank() || owner == "null") {
    PageColumn { EmptyCard("请先登录", "登录后查看本账号的体验功能。") }
    return
  }
  if (page == "safety") { SafetyCenter(owner); return }
  val context = LocalContext.current
  var message by remember(owner, page) { mutableStateOf("") }
  PageColumn {
    SectionTitle(when(page) { "coupons" -> "我的卡券 · 演示"; "points" -> "积分 · 演示"; else -> "邀请体验 · 演示" })
    when (page) {
      "coupons" -> EmptyCard("暂无可用卡券", "领取、有效期、核销及退款规则待服务端接入后开放。")
      "points" -> EmptyCard("暂无积分记录", "预付成功不视作完单，不在本机发放完单积分。")
      else -> EmptyCard("暂无邀请记录", "尚无服务端邀请码或真实拉新绑定，不承诺注册送券或首单奖励。")
    }
    Panel("尚未接入营销账务") {
      Note("此处是功能演示，不是真实资产。不会抵扣订单、改变报价、发放积分或奖励；订单金额以共享服务端为准。")
      if (page == "invite") {
        Note("尚无服务端邀请码或真实拉新绑定，不承诺注册送券或首单奖励。")
        SecondaryButton("复制体验说明") {
          runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("宜捷行体验说明", "邀请了解宜捷行独立体验版：邀请、积分和卡券目前仅作功能演示，无注册奖励、无实际抵扣或拉新绑定。"))
          }.onSuccess { message = "体验说明已复制" }.onFailure { message = "复制失败，请重试" }
        }
      }
      if (message.isNotBlank()) Text(message, color = Teal)
    }
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
      DangerButton("打开 110 拨号界面") { dial("110") }
    }
    PassengerSectionHeader("安全联系人 · ${contacts.size}/3", if (contacts.size < 3) "＋ 添加" else null) { editId=null; name=""; phone=""; error=""; editing=true }
    if (contacts.isEmpty()) EmptyCard("暂无安全联系人", "按需添加，保存后可手动打开拨号。")
    contacts.forEach { contact ->
      Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Column(Modifier.weight(1f)) {
            Text(contact.name, color = Ink, fontWeight = FontWeight.SemiBold)
            Note(contact.phone)
          }
          PassengerTextButton(onClick = { dial(contact.phone) }) { Text("拨号") }
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
