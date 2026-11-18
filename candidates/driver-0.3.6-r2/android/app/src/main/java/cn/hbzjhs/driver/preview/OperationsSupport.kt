package cn.hbzjhs.driver.preview

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** A replacement (or the material itself) is under review; nothing more can be submitted meanwhile. */
internal fun certificateInReview(cert: JSONObject): Boolean =
    cert.optJSONObject("pending") != null || cert.optString("status") == "PENDING"

/** Only approved current material with a definite in-date validity counts as valid. */
internal fun certificateCurrentlyValid(cert: JSONObject): Boolean =
    cert.optString("status") == "APPROVED" && cert.optString("validity") in setOf("VALID", "EXPIRING", "LONG_TERM")

/** Expired or not yet effective: the existing rule pauses new orders (in-trip orders can finish). */
internal fun certificateOutOfDate(cert: JSONObject): Boolean =
    cert.optString("validity") in setOf("EXPIRED", "NOT_EFFECTIVE")

/** Effective badge text + tint + background taking real expiry and pending state into account. */
internal fun certificateEffectiveBadge(cert: JSONObject): Triple<String, Color, Color> {
    val reviewing = certificateInReview(cert)
    val validity = cert.optString("validity")
    val status = cert.optString("status")
    return when {
        validity == "EXPIRED" -> Triple(if (reviewing) "已过期 · 审核中" else "已过期", Danger, DangerSoft)
        validity == "NOT_EFFECTIVE" -> Triple("未生效", Warm, WarmSoft)
        status == "REJECTED" -> Triple("已驳回", Danger, DangerSoft)
        reviewing -> Triple("新材料待审核", Sky, SkySoft)
        validity == "EXPIRING" -> Triple("${cert.optInt("daysRemaining")}天后到期", Warm, WarmSoft)
        validity == "UNKNOWN" -> Triple("有效期未确认", Warm, WarmSoft)
        status == "APPROVED" -> Triple("已通过", Success, SuccessSoft)
        else -> Triple(bStatus(status), IconTone, SurfaceMuted)
    }
}

/** Overall qualification summary across all certificates; never masks expired/invalid certs as healthy. */
internal fun qualificationSummary(certificates: List<JSONObject>): Triple<String, Color, Boolean> {
    val expired = certificates.count { it.optString("validity") == "EXPIRED" }
    val notEffective = certificates.count { it.optString("validity") == "NOT_EFFECTIVE" }
    val rejected = certificates.count { it.optString("status") == "REJECTED" && !certificateInReview(it) }
    val reviewing = certificates.count { certificateInReview(it) }
    val expiring = certificates.count { it.optString("validity") == "EXPIRING" }
    val unknown = certificates.count { it.optString("validity") == "UNKNOWN" }
    val invalid = certificates.count { !certificateCurrentlyValid(it) }
    return when {
        expired > 0 -> Triple("${expired}项证件已过期（暂停新接单）", Danger, true)
        notEffective > 0 -> Triple("${notEffective}项证件尚未生效（暂停新接单）", Warm, true)
        rejected > 0 -> Triple("${rejected}项证件被驳回，请重新提交", Danger, true)
        reviewing > 0 -> Triple("${reviewing}项证件审核中", Sky, false)
        expiring > 0 -> Triple("${expiring}项证件即将到期，请及时更新", Warm, true)
        unknown > 0 -> Triple("${unknown}项证件有效期待补全", Warm, true)
        certificates.isEmpty() -> Triple("资质资料待同步", Muted, false)
        invalid > 0 -> Triple("${invalid}项证件待核验或更新", Warm, true)
        else -> Triple("全部证件有效", Success, false)
    }
}

@Composable
internal fun DriverCertificateStatus(cert: JSONObject) {
    val approved = cert.optString("status") == "APPROVED"
    // Under review: keep the current material's facts, but never ask to update what is already submitted.
    val reviewing = certificateInReview(cert)
    val text = when (cert.optString("validity")) {
        "EXPIRING" ->
            if (reviewing) "当前材料将在 ${cert.optInt("daysRemaining")} 天后到期；新材料审核中，通过后替换。"
            else "证件将在 ${cert.optInt("daysRemaining")} 天后到期，请提前更新。"
        "EXPIRED" ->
            if (reviewing) "当前材料已过期，暂停新增接单；在途订单可完成。新材料审核中，审核通过前不解除。"
            else "当前材料已过期，暂停新增接单；在途订单可完成。请更新并等待审核通过。"
        "NOT_EFFECTIVE" -> "当前材料尚未生效，不能新增接单。"
        "UNKNOWN" ->
            if (reviewing) "当前有效期未确认，历史标记不代表真实资质已核验；新材料审核中，请等待审核结果。"
            else "当前有效期未确认，请补全资料；历史标记不代表真实资质已核验。"
        "LONG_TERM" -> if (approved) "当前材料长期有效。" else "当前材料标注长期，但尚未审核通过。"
        else -> ""
    }
    if (text.isNotBlank()) BusinessHint(text)
    if (reviewing && !approved && cert.optString("validity") in setOf("VALID", "EXPIRING"))
        BusinessHint("当前材料尚未审核通过，不能视为有效证件；请等待审核结果。")
    if (cert.optString("reviewStatus") == "REJECTED")
        BusinessHint(
            when {
                reviewing -> "上次提交的材料曾被驳回；本次新材料审核中，请等待审核结果。"
                certificateCurrentlyValid(cert) -> "新材料已驳回，当前有效材料保留。请查看审核说明后重新提交。"
                else -> "新材料已驳回，当前材料状态不变（见上方说明）。请查看审核说明后重新提交。"
            },
        )
}

@Composable
internal fun DriverQualificationBanner(payload: JSONObject, navigate: (String) -> Unit) {
    val certificates = payload.optJSONObject("profile")?.bList("certificates").orEmpty()
    // Routine update nags drop out once a replacement is submitted, but only while the current material
    // is approved and in date; otherwise the current fact stays visible next to "审核中".
    val alerts = certificates.filter {
        (it.optBoolean("reminder") || it.optString("validity") == "UNKNOWN") && !certificateInReview(it)
    }
    val reviewing = certificates.filter { certificateInReview(it) && !certificateCurrentlyValid(it) }
    val outOfDate = reviewing.filter { certificateOutOfDate(it) }
    val unconfirmed = reviewing.filterNot { certificateOutOfDate(it) }
    if (alerts.isNotEmpty() || reviewing.isNotEmpty()) {
        val expiredAlerts = alerts.count { it.optString("validity") == "EXPIRED" }
        val otherAlerts = alerts.size - expiredAlerts
        val urgent = expiredAlerts > 0 || outOfDate.isNotEmpty()
        Surface(color = if (urgent) DangerSoft else WarmSoft, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                val message = listOfNotNull(
                    "${expiredAlerts}项证件已过期（暂停新接单）".takeIf { expiredAlerts > 0 },
                    "${otherAlerts}项证件需更新".takeIf { otherAlerts > 0 },
                    "${outOfDate.size}项证件不在有效期、审核中，暂停新增接单".takeIf { outOfDate.isNotEmpty() },
                    "${unconfirmed.size}项证件当前材料有效期未确认或未通过、审核中，请以审核结果为准".takeIf { unconfirmed.isNotEmpty() },
                ).joinToString("；")
                Text(message, modifier = Modifier.weight(1f), color = if (urgent) Danger else Warm, fontSize = 13.sp)
                DriverTextButton(onClick = { navigate("qualification") }) { Text(if (alerts.isNotEmpty()) "去更新" else "查看") }
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
    if (confirm) DriverDialog(onDismissRequest = { confirm = false }, title = { Text("打开系统拨号？") },
        text = { Text("${company.optString("name")}\n$phone\n进入系统拨号界面后由您决定是否拨出。") },
        confirmButton = { DriverTextButton(onClick = {
            confirm = false
            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) }
                .onFailure { error = "无法打开拨号应用，请手动拨号。" }
        }) { Text("打开拨号") } }, dismissButton = { DriverTextButton(onClick = { confirm = false }) { Text("取消") } })
}
