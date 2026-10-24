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
    Panel("尚未接入营销账务") {
      KV(when(page) { "coupons" -> "可用券"; "points" -> "积分余额"; else -> "邀请记录" }, "0")
      Note("此处是功能演示，不是真实资产。不会抵扣订单、改变报价、发放积分或奖励；订单金额以共享服务端为准。")
      if (page == "coupons") Note("暂无可用券。领取、有效期、核销及退款规则待服务端接入后开放。")
      if (page == "points") Note("暂无积分变动。预付成功不视作完单，不在本机发放完单积分。")
      if (page == "invite") {
        Note("尚无服务端邀请码或真实拉新绑定，不承诺注册送券或首单奖励。")
        ActionButton("复制体验说明") {
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
    SectionTitle("安全中心")
    Panel("功能范围") {
      Note("安全联系人仅保存在本机当前乘客账号下，不跨设备同步、不自动联系或上传。卸载应用会删除本机记录。")
      Note("录音保护、短信自动发送、警方专网及警务定位联动均未接入。紧急情况请由本人拨打当地报警电话。")
      OutlinedButton(onClick = { dial("110") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("打开110拨号界面（需本人拨出）") }
    }
    Panel("安全联系人 · ${contacts.size}/3") {
      if (contacts.isEmpty()) Note("暂无联系人，请按需添加。")
      contacts.forEach { contact ->
        Text(contact.name); Note(contact.phone)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { editId=contact.id; name=contact.name; phone=contact.phone; error=""; editing=true }) { Text("编辑") }
          TextButton(onClick = { deleting=contact }) { Text("删除") }
          TextButton(onClick = { dial(contact.phone) }) { Text("打开拨号") }
        }
        HorizontalDivider()
      }
      ActionButton("添加安全联系人", contacts.size < 3) { editId=null; name=""; phone=""; error=""; editing=true }
      if (error.isNotBlank() && !editing) Text(error, color = MaterialTheme.colorScheme.error)
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
    AlertDialog(onDismissRequest={deleting=null}, title={Text("删除安全联系人？")}, text={Text(contact.name)}, confirmButton={TextButton(onClick={
      runCatching { local.remove(owner, contacts, contact.id); contacts=local.contacts(owner) }
        .onSuccess { deleting=null }.onFailure { error=it.message ?: "删除失败"; deleting=null }
    }) {Text("删除")}}, dismissButton={TextButton(onClick={deleting=null}) {Text("保留")}})
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
