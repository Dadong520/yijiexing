package cn.hbzjhs.driver.preview

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Held only in memory for one confirmation dialog. toString never prints the number. */
internal class PassengerContact(val orderId: String, val name: String, val mobile: String) {
    override fun toString(): String = "PassengerContact(orderId=$orderId)"
}

internal object PassengerContactRules {
    private val STAGES = setOf(Stage.RESERVED, Stage.PICKUP, Stage.ARRIVED, Stage.TRIP)
    // Current backend contract: a full mainland mobile, 11 ASCII digits starting with 1. Masked,
    // short, spaced or non-ASCII digits are rejected and never reach the dialer.
    private val MOBILE = Regex("1[0-9]{10}")

    fun eligible(order: DriverOrder?): Boolean =
        order != null && !order.participationCancelled && order.stage in STAGES

    /** Identity a fetched number belongs to; any change (account, order, stage) discards it. */
    fun scope(account: String, order: DriverOrder?): String? =
        if (account.isBlank() || !eligible(order)) null else "$account|${order!!.id}|${order.stage.name}"

    fun parse(orderId: String, data: JSONObject): PassengerContact? {
        if (data.optString("orderId") != orderId) return null
        if (data.optString("contactMode") != "DIRECT_DIAL") return null
        val mobile = data.optString("mobile")
        if (!MOBILE.matches(mobile)) return null
        val name = data.optString("name").trim().takeUnless { it.isBlank() || it == "null" } ?: "乘客"
        return PassengerContact(orderId, name, mobile)
    }

    fun failureMessage(status: Int, reason: String): String =
        when {
            status == 401 -> "登录状态已失效，请重新登录后再联系乘客"
            status == 404 -> "订单不存在或已不属于你，无法联系乘客"
            status == 409 && reason == "PASSENGER_CONTACT_UNAVAILABLE" -> "此订单没有可用的乘客电话（较早的订单未保存联系方式）"
            status == 409 -> "当前订单阶段不能联系乘客"
            else -> "获取乘客电话失败，请稍后重试"
        }
}

/**
 * Explicit "联系乘客": fetches the rider's number only on tap, shows it in a confirmation dialog and
 * hands it to the system dialer (ACTION_DIAL) only after the driver confirms. Nothing is persisted,
 * logged or copied; an order, stage or account change drops the number.
 */
@Composable
internal fun PassengerContactAction(order: DriverOrder, store: ConnectedStore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val payload by store.payload.collectAsState()
    val scope = PassengerContactRules.scope(driverAccount(payload), order) ?: return
    if (order.isPrivateExample) {
        // A private demo order has no real passenger: no number is requested, nothing pretends to dial.
        Text("演示订单未关联乘客，无可联系电话", color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = modifier.padding(top = 10.dp))
        return
    }
    var generation by remember(scope) { mutableIntStateOf(0) }
    var loading by remember(scope) { mutableStateOf(false) }
    var contact by remember(scope) { mutableStateOf<PassengerContact?>(null) }
    var failure by remember(scope) { mutableStateOf<String?>(null) }
    // Leaving the screen or any scope change (order, stage, account) drops the number and makes a
    // late response unable to open a dialog.
    val alive = remember(scope) { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(scope) {
        onDispose {
            alive.set(false)
            contact = null
        }
    }
    fun currentScope(): String? =
        PassengerContactRules.scope(driverAccount(store.payload.value), store.state.value.orders.firstOrNull { it.id == order.id })

    Column(modifier) {
        DriverTextButton(
            enabled = !loading,
            onClick = {
                if (loading) return@DriverTextButton
                val request = ++generation
                loading = true
                failure = null
                contact = null
                store.fetchPassengerContact(order.id) { result ->
                    if (!alive.get() || request != generation) return@fetchPassengerContact
                    loading = false
                    if (currentScope() != scope) {
                        failure = "订单状态已变化，请重新获取乘客电话"
                        return@fetchPassengerContact
                    }
                    result.onSuccess { contact = it }.onFailure { failure = it.message ?: "获取乘客电话失败，请稍后重试" }
                }
            },
        ) { Text(if (loading) "正在获取电话…" else "联系乘客") }
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 17.sp) }
    }

    contact?.let { c ->
        DriverDialog(
            onDismissRequest = { contact = null },
            title = { Text("联系乘客") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(c.mobile, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        "将打开系统拨号页面并填入号码，是否拨出由你在拨号页决定。这是直接拨打乘客号码，不是隐私中继；本应用不会自动接通，也不会保存号码。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Muted,
                    )
                }
            },
            confirmButton = {
                DriverTextButton(onClick = {
                    contact = null
                    // Re-check the latest order owner and stage right before handing over the number.
                    if (!alive.get() || currentScope() != scope || c.orderId != order.id) {
                        failure = "订单状态已变化，未打开拨号"
                        return@DriverTextButton
                    }
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", c.mobile, null)))
                    } catch (_: ActivityNotFoundException) {
                        failure = "未找到系统拨号应用"
                    } catch (_: SecurityException) {
                        failure = "无法打开系统拨号页面"
                    }
                }) { Text("打开拨号") }
            },
            dismissButton = { DriverTextButton(onClick = { contact = null }) { Text("取消") } },
        )
    }
}
