package cn.hbzjhs.driver.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

// UIO-20261029-01 presentation helpers; no business rules live here.

/** Spacing scale shared by the optimized screens: 8 / 12 / 16 / 24. */
object Gap {
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
}

/** Empty list placeholder: semantic icon, short explanation, optional real action. */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    message: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (tint, soft) = moduleAccent(icon)
    SheetCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Gap.L),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(soft),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(icon, tint, Modifier.size(32.dp))
            }
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Gap.M),
            )
            if (message.isNotBlank())
                Text(
                    message,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = Gap.L, end = Gap.L),
                )
            if (actionLabel != null && onAction != null)
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = Gap.M).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Green),
                ) {
                    Text(actionLabel, color = Green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
        }
    }
}

/** Stage colour + icon + text, so status never relies on colour alone. */
data class StageLook(val label: String, val icon: String, val tint: Color, val soft: Color)

fun stageLook(order: DriverOrder): StageLook = when (order.stage) {
    Stage.AVAILABLE -> StageLook("待接单", "bell", Sky, SkySoft)
    Stage.BID_SENT, Stage.PREPAY -> StageLook(order.stage.title, "wallet", Warm, WarmSoft)
    Stage.RESERVED -> StageLook("待进行 · 待出发", "doc", Violet, VioletSoft)
    Stage.PICKUP, Stage.ARRIVED, Stage.TRIP -> StageLook("进行中 · ${order.stage.title}", "car", Sky, SkySoft)
    Stage.WAIT_PAY -> StageLook("待支付", "wallet", Warm, WarmSoft)
    Stage.DONE -> StageLook("已完成", "check", Success, SuccessSoft)
    Stage.CANCELLED -> StageLook("已取消", "close", Danger, DangerSoft)
}

@Composable
fun StageBadge(order: DriverOrder) {
    val look = stageLook(order)
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(look.soft).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(look.icon, look.tint, Modifier.size(14.dp))
        Text(
            look.label,
            color = look.tint,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private val plainDistance = Regex("""\d+(\.\d+)?""")
private val distanceUnit = Regex("""\s*(km|公里)$""", RegexOption.IGNORE_CASE)

/**
 * Shows "<n> km" only for a finite, non-negative plain number supplied by the server (0 is a valid
 * known distance). An existing km/公里 suffix is not repeated. Pending text keeps "待确认"; blank,
 * placeholders, NaN/Infinity, negatives and other non-numeric text show "未提供", never a made-up km.
 */
fun kmOrMissing(raw: String?): String {
    val text = raw?.trim().orEmpty()
    if (text.contains("待确认")) return "待确认"
    val value = text.replace(distanceUnit, "").trim()
    if (!plainDistance.matches(value)) return "未提供"
    val number = value.toDoubleOrNull() ?: return "未提供"
    return if (number.isFinite() && number >= 0.0) "$value km" else "未提供"
}

/**
 * Source note for the new-order prompt. Private demo orders and shared sandbox orders stay distinct;
 * both state that the fare is simulated, and shared orders state the pickup distance is a
 * server-side sandbox estimate rather than live measurement.
 */
fun newOrderSourceNote(isPrivateExample: Boolean): String =
    if (isPrivateExample) "演示订单 · 未关联乘客 App · 金额为模拟"
    else "共享沙箱订单 · 距乘客为服务端沙箱估算，非实时测距 · 金额为模拟"

/** Driver membership identity card: shows only server-supplied level, expiry and rights. */
@Composable
fun MembershipIdentityCard(store: ConnectedStore, navigate: (String) -> Unit) {
    val payload by store.payload.collectAsState()
    val catalog = payload.optJSONObject("benefits")
    val subscription = catalog?.optJSONObject("subscription")
    val trial = catalog?.optJSONObject("trial")
    val active = subscription != null && subscription.mText("status", "ACTIVE") == "ACTIVE"
    val rights = (catalog?.let { mRights(it) } ?: emptyList()).ifEmpty {
        subscription?.let { mRights(it) } ?: emptyList()
    }
    val features = payload.optJSONObject("features") ?: JSONObject()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(GreenStrong, Violet)))
                .padding(Gap.L)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph("gift", Color.White, Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f).padding(start = Gap.M)) {
                    Text("会员司机", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    Text(
                        if (active) subscription!!.mText("levelName") else if (trial != null) "体验权益" else "尚未开通会员",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    if (active) "生效中" else if (trial?.mText("status") == "ENDED") "已结束" else if (trial != null) "体验中" else "未开通",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(Gap.M))
            Text(
                when {
                    active -> "${subscription!!.mText("cityName")} · 有效期至 ${mDate(subscription.mText("endAt", ""))}"
                    trial == null -> "有效期：未提供 · 请查看所在城市的有效价格与权益"
                    trial.mText("status") == "ENDED" -> "体验已结束 · 历史订单与钱包保留"
                    else -> "体验剩余 ${trial.mNumber("daysRemaining")} 天 · ${trial.mNumber("ordersRemaining")} 单"
                },
                color = Color.White,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(Gap.S))
            val shown = rights.take(3).map { it.mText("name", it.mText("title", it.mText("label"))) }.filter { it != "—" }
            if (shown.isNotEmpty())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.forEach {
                        Text(
                            it,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            else
                Text(
                    "接单 ${if (features.optBoolean("canAcceptOrders")) "可用" else "不可用"} · 线下议价 ${if (features.optBoolean("canNegotiate")) "可用" else "未开放"} · 热力 ${if (features.optBoolean("canHeatmap")) "可用" else "未开放"}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                )
            Spacer(Modifier.height(Gap.M))
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.S)) {
                Button(
                    onClick = {
                        navigate(if (trial?.mText("status") == "ENDED" && !active) mRoute("expiry") else mRoute())
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = GreenStrong),
                ) {
                    Text(if (active) "管理会员" else "查看权益", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { navigate(mRoute("records")) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                ) {
                    Text("开通记录", color = Color.White)
                }
            }
        }
    }
}
