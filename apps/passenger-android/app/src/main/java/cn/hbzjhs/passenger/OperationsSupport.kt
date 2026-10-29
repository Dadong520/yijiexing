package cn.hbzjhs.passenger

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.json.JSONObject

internal object PassengerOperations {
  fun enabled(state: JSONObject, key: String): Boolean =
    state.optJSONObject("operations")?.optJSONObject("passengerFeatures")?.optJSONObject("features")?.opt(key) as? Boolean ?: true
  fun invoiceEnabled(state: JSONObject, order: JSONObject): Boolean =
    if (order.has("invoiceApplicationEnabled")) order.opt("invoiceApplicationEnabled") as? Boolean ?: false
    else enabled(state, "invoice")
  fun kindEnabled(state: JSONObject, kind: String): Boolean = when(kind) {
    "STANDARD" -> enabled(state, "instant")
    "RESERVED" -> enabled(state, "reservation")
    "BID" -> enabled(state, "bidding")
    else -> false
  }
}

@Composable
fun PassengerCompanyPhone(company: JSONObject?) {
  val context = LocalContext.current
  val status = company?.text("status").orEmpty()
  val phone = company?.text("supportPhone").orEmpty().takeUnless { it == "null" }.orEmpty()
  var confirm by remember(company?.toString()) { mutableStateOf(false) }
  var error by remember { mutableStateOf("") }
  Panel("公司客服电话") {
    KV("公司", company?.text("name").orEmpty().takeUnless { it.isBlank() || it == "null" } ?: "未配置公司")
    KV("电话", if (status == "CONFIGURED" && phone.isNotBlank()) phone else if(status == "DISABLED") "公司已停用" else "未配置")
    Note(when(company?.text("source")) { "ORDER" -> "按当前订单记录的公司提供客服。"; "SELECTED" -> "按您选择的公司提供客服，不改变订单或司机归属。"; else -> "公司由后台维护，未配置时不提供虚构号码。" })
    if (status == "CONFIGURED" && phone.isNotBlank()) ActionButton("联系公司客服") { confirm = true }
    if(error.isNotBlank()) Text(error, color=MaterialTheme.colorScheme.error)
  }
  PassengerModalSlot(confirm)
  if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("打开系统拨号？")},text={Text("$phone\n进入系统拨号界面后，由您决定是否拨出。")},
    confirmButton={TextButton(onClick={confirm=false;runCatching { context.startActivity(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",phone,null))) }.onFailure { error="无法打开拨号应用，请手动拨号。" }}){Text("打开拨号")}},
    dismissButton={TextButton(onClick={confirm=false}){Text("取消")}})
}

@Composable
fun PassengerCompanySelector(state: JSONObject, store: PassengerStore) {
  val ops=state.optJSONObject("operations") ?: JSONObject()
  val busy by store.busy.collectAsState()
  PassengerCompanyPhone(ops.optJSONObject("company"))
  if(ops.rows("companies").isNotEmpty()) Panel("选择客服公司") {
    Note("有活动订单公司时优先使用订单公司；此选择不会改变历史订单公司或派单规则。")
    ops.rows("companies").forEach { c ->
      OutlinedButton(enabled=!busy,onClick={store.action("ops.company.select",JSONObject().put("companyId",c.text("id")))},modifier=Modifier.fillMaxWidth()) { Text(c.text("name")) }
    }
    TextButton(enabled=!busy,onClick={store.action("ops.company.select",JSONObject().put("companyId",JSONObject.NULL))}){Text("清除本人选择")}
  }
}

@Composable
fun PassengerAnnouncements(state: JSONObject, store: PassengerStore, logged: Boolean) {
  val owner=state.optJSONObject("passenger")?.text("id").orEmpty()
  val busy by store.busy.collectAsState()
  val lifecycle=LocalLifecycleOwner.current
  var showing by remember(owner) { mutableStateOf<JSONObject?>(null) }
  var claiming by remember(owner) { mutableStateOf(false) }
  var failed by remember(owner) { mutableStateOf(false) }
  var pausedKey by remember(owner) { mutableStateOf("") }
  val candidates=state.optJSONObject("operations")?.rows("announcements").orEmpty()
  val candidate=candidates.firstOrNull()
  val candidateKey=candidate?.let { it.text("id")+":"+it.optInt("version") }.orEmpty()
  // A claim waits while anything else is open. Once claimed, the announcement is held in `showing`
  // (per account) until the passenger closes it; it is only drawn while no passenger-opened dialog or
  // page is open, so a modal opened while the claim was in flight is never covered, and the held copy
  // is neither dropped nor claimed again. The slot never depends on the coordinator, so no loop.
  val slot=PassengerModalSlot(claiming||showing!=null||(failed&&candidate!=null), PassengerModalCoordinator.RANK_ANNOUNCEMENT)
  val blocked=PassengerModalCoordinator.othersOpen(slot)
  val hidden=PassengerModalCoordinator.outranked(slot, PassengerModalCoordinator.RANK_ANNOUNCEMENT)
  val ready=logged&&owner.isNotBlank()&&candidate!=null&&!busy
  LaunchedEffect(owner,logged,candidateKey,busy,showing,failed,pausedKey,blocked) {
    if(PassengerModalCoordinator.mayClaimAnnouncement(ready,claiming,showing!=null,failed,candidateKey==pausedKey,blocked)) {
      lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        if(PassengerModalCoordinator.mayClaimAnnouncement(true,claiming,showing!=null,failed,candidateKey==pausedKey,PassengerModalCoordinator.othersOpen(slot))) {
          claiming=true
          val captured=JSONObject(candidate!!.toString())
          store.claimAnnouncement(captured, onResult={ first ->
            claiming=false
            if(first) showing=captured
          }, onFailure={claiming=false;failed=true})
        }
      }
    }
  }
  if(!hidden) showing?.let { row ->
    AlertDialog(onDismissRequest={showing=null},title={Text(row.text("title"))},
      text={Text(row.text("content"),modifier=Modifier.verticalScroll(rememberScrollState()))},
      confirmButton={TextButton(onClick={showing=null}){Text("知道了")}})
  }
  if(!hidden&&failed&&candidate!=null) AlertDialog(onDismissRequest={failed=false;pausedKey=candidateKey},title={Text("公告暂未读取完成")},
    text={Text("请检查网络后重试。公告通过服务端回执去重，不会重复消耗展示次数。")},
    confirmButton={TextButton(onClick={failed=false;store.refresh()}){Text("重试")}},dismissButton={TextButton(onClick={failed=false;pausedKey=candidateKey}){Text("稍后")}})
}
