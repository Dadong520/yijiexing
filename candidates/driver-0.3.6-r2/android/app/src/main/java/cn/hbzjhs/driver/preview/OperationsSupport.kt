package cn.hbzjhs.driver.preview

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun DriverCertificateStatus(cert: JSONObject) {
    val text = when (cert.optString("validity")) {
        "EXPIRING" -> "证件将在 ${cert.optInt("daysRemaining")} 天后到期，请提前更新。"
        "EXPIRED" -> "当前材料已过期，暂停新增接单；在途订单可完成。请更新并等待审核通过。"
        "NOT_EFFECTIVE" -> "当前材料尚未生效，不能新增接单。"
        "UNKNOWN" -> "当前有效期未确认，请补全资料；历史标记不代表真实资质已核验。"
        "LONG_TERM" -> "当前材料长期有效。"
        else -> ""
    }
    if (text.isNotBlank()) BusinessHint(text)
    if (cert.optString("reviewStatus") == "REJECTED")
        BusinessHint("新材料已驳回，当前有效材料保留。请查看审核说明后重新提交。")
}

@Composable
internal fun DriverQualificationBanner(payload: JSONObject, navigate: (String) -> Unit) {
    val certificates = payload.optJSONObject("profile")?.bList("certificates").orEmpty()
    val alerts = certificates.filter { it.optBoolean("reminder") || it.optString("validity") == "UNKNOWN" }
    if (alerts.isNotEmpty()) {
        Surface(color = Orange.copy(alpha = .12f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("${alerts.size}项证件需关注；更新审核通过后解除。", modifier = Modifier.weight(1f))
                TextButton(onClick = { navigate("qualification") }) { Text("查看证件") }
            }
        }
    }
}

@Composable
internal fun DriverCompanyContact(store: ConnectedStore) {
    val payload by store.payload.collectAsState()
    val company = payload.optJSONObject("operations")?.optJSONObject("company") ?: JSONObject()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val phone = company.optString("supportPhone").takeUnless { it == "null" }.orEmpty()
    val configured = company.optString("status") == "CONFIGURED"
    DetailRow("所属公司", company.optString("name").takeUnless { it.isBlank() || it == "null" } ?: "未绑定公司")
    DetailRow("公司客服电话", if (configured && phone.isNotBlank()) phone else if (company.optString("status") == "DISABLED") "公司已停用" else "未配置")
    if (configured && phone.isNotBlank()) {
        PrimaryButton("联系公司客服") { confirm = true }
    }
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("打开系统拨号？") },
        text = { Text("${company.optString("name")}\n$phone\n进入系统拨号界面后由您决定是否拨出。") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) }
                .onFailure { error = "无法打开拨号应用，请手动拨号。" }
        }) { Text("打开拨号") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}
