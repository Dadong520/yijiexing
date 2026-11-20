package cn.hbzjhs.driver.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

// UIO-20261029-01 presentation helpers; no business rules live here.

/** Spacing scale shared by the optimized screens: 6 / 10 / 12 / 16. */
object Gap {
    val S = 6.dp
    val M = 10.dp
    val L = 12.dp
    val XL = 16.dp
}

/**
 * One type scale for every driver page: page title largest, card titles next, body normal, helper
 * text smallest. Screens use these instead of ad-hoc sizes.
 */
object DriverType {
    val Page = 20.sp
    val Title = 16.sp
    val Body = 14.sp
    val Caption = 12.sp
}

// UIFC-20261102-01: member card retoned from a stand-alone gold to the app's teal family; gold is
// kept only as a small badge accent so it reads as "membership" without clashing.
private val MemberInk = GreenStrong
private val MemberAccent = Gold

/** Brand fill: one flat teal (RIDEC dropped the lit gradients and coloured shadows). */
val BrandGradient: Brush = SolidColor(Green)
/** Kept for source compatibility; heroes are now plain white surfaces. */
val HeroGradient: Brush = SolidColor(Green)

/** 流线型新能源轿车简笔插画。 */
@Composable
fun CarIllustration(modifier: Modifier = Modifier, body: Color = Green) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawLine(
            body.copy(alpha = 0.28f),
            Offset(w * 0.04f, h * 0.84f),
            Offset(w * 0.96f, h * 0.84f),
            strokeWidth = h * 0.05f,
            cap = StrokeCap.Round,
        )
        val shell = Path().apply {
            moveTo(w * 0.08f, h * 0.72f)
            cubicTo(w * 0.06f, h * 0.56f, w * 0.12f, h * 0.44f, w * 0.22f, h * 0.42f)
            cubicTo(w * 0.31f, h * 0.20f, w * 0.45f, h * 0.15f, w * 0.62f, h * 0.17f)
            cubicTo(w * 0.73f, h * 0.19f, w * 0.80f, h * 0.32f, w * 0.86f, h * 0.40f)
            cubicTo(w * 0.94f, h * 0.43f, w * 0.95f, h * 0.58f, w * 0.92f, h * 0.72f)
            close()
        }
        drawPath(shell, body.copy(alpha = 0.24f))
        drawPath(shell, body.copy(alpha = 0.92f), style = Stroke(width = h * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val cabin = Path().apply {
            moveTo(w * 0.28f, h * 0.41f)
            cubicTo(w * 0.35f, h * 0.24f, w * 0.46f, h * 0.22f, w * 0.60f, h * 0.23f)
            cubicTo(w * 0.68f, h * 0.24f, w * 0.74f, h * 0.32f, w * 0.78f, h * 0.41f)
            close()
        }
        drawPath(cabin, body.copy(alpha = 0.42f))
        drawLine(body.copy(alpha = 0.85f), Offset(w * 0.53f, h * 0.23f), Offset(w * 0.50f, h * 0.41f), strokeWidth = h * 0.055f, cap = StrokeCap.Round)
        listOf(0.26f, 0.74f).forEach { cx ->
            drawCircle(body.copy(alpha = 0.96f), radius = h * 0.15f, center = Offset(w * cx, h * 0.74f))
            drawCircle(Color(0xFF0A4D46).copy(alpha = 0.38f), radius = h * 0.08f, center = Offset(w * cx, h * 0.74f))
            drawCircle(body.copy(alpha = 0.96f), radius = h * 0.032f, center = Offset(w * cx, h * 0.74f))
        }
    }
}

private enum class DialogButtonRole { None, Confirm, Dismiss }

private val LocalDialogButtonRole = staticCompositionLocalOf { DialogButtonRole.None }

/**
 * Text-level action shown as a light button: a neutral grey pill with brand text, so it never
 * competes with the page's one primary action. Inside [DriverDialog] the same call renders as the
 * dialog's full-width confirm or cancel button.
 */
@Composable
fun DriverTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val role = LocalDialogButtonRole.current
    Button(
        onClick = onClick,
        // Dialog buttons ignore caller sizing so every popup gets the same equal-height button row.
        // Every variant keeps a 48dp touch height (UIFC-20261102-01).
        modifier = if (role == DialogButtonRole.None) modifier.heightIn(min = DriverButtonSize.Min) else Modifier.fillMaxWidth().heightIn(min = DriverButtonSize.Min),
        enabled = enabled,
        shape = RoundedCornerShape(if (role == DialogButtonRole.None) 12.dp else DriverButtonSize.Corner),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (role == DialogButtonRole.Dismiss) SurfaceMuted else Pale,
            contentColor = if (role == DialogButtonRole.Dismiss) Ink else GreenStrong,
            disabledContainerColor = SurfaceMuted,
            disabledContentColor = DisabledText,
        ),
        border = when (role) {
            DialogButtonRole.Confirm -> BorderStroke(1.dp, Green.copy(alpha = 0.45f))
            DialogButtonRole.None -> if (enabled) BorderStroke(1.dp, Green.copy(alpha = 0.18f)) else null
            else -> null
        },
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = if (role == DialogButtonRole.None) contentPadding else PaddingValues(horizontal = 6.dp),
    ) {
        ProvideTextStyle(
            TextStyle(
                fontSize = if (role == DialogButtonRole.None) 14.sp else DriverType.Body,
                fontWeight = if (role == DialogButtonRole.Confirm) FontWeight.SemiBold else FontWeight.Medium,
            )
        ) { content() }
    }
}

/** Drop-in for Material's AlertDialog in the one popup style shared by every driver dialog. */
@Composable
fun DriverDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    title?.let {
                        ProvideTextStyle(TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Ink)) { it() }
                    }
                    text?.let {
                        // Long bodies bring their own scroll; nesting another vertical scroll here would crash.
                        Box(Modifier.weight(1f, fill = false)) {
                            ProvideTextStyle(TextStyle(fontSize = DriverType.Body, lineHeight = 22.sp, color = Ink)) { it() }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        dismissButton?.let {
                            Box(Modifier.weight(1f)) {
                                CompositionLocalProvider(LocalDialogButtonRole provides DialogButtonRole.Dismiss) { it() }
                            }
                        }
                        Box(Modifier.weight(1.2f)) {
                            CompositionLocalProvider(LocalDialogButtonRole provides DialogButtonRole.Confirm) { confirmButton() }
                        }
                    }
                }
            }
        }
    }
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
    SheetCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Gap.L),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyIllustration(icon)
            Text(
                title,
                fontSize = DriverType.Title,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Gap.S),
            )
            if (message.isNotBlank())
                Text(
                    message,
                    fontSize = DriverType.Caption,
                    lineHeight = 18.sp,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = Gap.L, end = Gap.L),
                )
            // The one clear next step of this empty list (only actions that really exist are passed).
            if (actionLabel != null && onAction != null)
                BusinessOutlinedButton(onClick = onAction, modifier = Modifier.padding(top = Gap.M).widthIn(min = 160.dp)) {
                    Text(actionLabel)
                }
        }
    }
}

/**
 * Empty-state art drawn in code (no bitmaps), same composition as the passenger app: a soft
 * module-tinted backdrop, a blank record card with placeholder lines, accent sparks and the module
 * glyph in a white badge. Decorative only.
 */
@Composable
fun EmptyIllustration(icon: String) {
    val (tint, soft) = moduleAccent(icon)
    Box(Modifier.size(width = 148.dp, height = 112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawOval(soft, topLeft = Offset(w * 0.12f, h * 0.06f), size = Size(w * 0.76f, h * 0.84f))
            drawOval(Color(0x140F1B2A), topLeft = Offset(w * 0.22f, h * 0.88f), size = Size(w * 0.56f, h * 0.08f))
            drawRoundRect(tint.copy(alpha = 0.16f), topLeft = Offset(w * 0.36f, h * 0.2f), size = Size(w * 0.42f, h * 0.56f), cornerRadius = CornerRadius(h * 0.06f))
            drawRoundRect(Color.White, topLeft = Offset(w * 0.28f, h * 0.26f), size = Size(w * 0.42f, h * 0.58f), cornerRadius = CornerRadius(h * 0.06f))
            drawRoundRect(Line, topLeft = Offset(w * 0.28f, h * 0.26f), size = Size(w * 0.42f, h * 0.58f), cornerRadius = CornerRadius(h * 0.06f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f * density))
            listOf(0.62f, 0.7f, 0.78f).forEachIndexed { i, y ->
                drawRoundRect(if (i == 0) tint.copy(alpha = 0.35f) else Hairline, topLeft = Offset(w * 0.34f, h * y - h * 0.02f), size = Size(w * (if (i == 2) 0.18f else 0.3f), h * 0.04f), cornerRadius = CornerRadius(h * 0.02f))
            }
            drawCircle(tint.copy(alpha = 0.45f), radius = h * 0.03f, center = Offset(w * 0.18f, h * 0.3f))
            drawCircle(Gold.copy(alpha = 0.5f), radius = h * 0.022f, center = Offset(w * 0.84f, h * 0.2f))
            drawCircle(tint.copy(alpha = 0.3f), radius = h * 0.018f, center = Offset(w * 0.86f, h * 0.7f))
        }
        Box(
            Modifier.offset(y = (-14).dp).size(44.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape).padding(4.dp).background(soft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon, tint, Modifier.size(22.dp))
        }
    }
}

/** Short next-step hint under an empty state; only describes flows that exist. */
@Composable
fun EmptyHint(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BrandMist).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Glyph("info", Green, Modifier.size(18.dp))
        Text(text, color = Muted, fontSize = DriverType.Caption, lineHeight = 18.sp, modifier = Modifier.weight(1f))
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
    val cardGold = Color(0xFFF3D08A)
    val cardGoldStrong = Color(0xFFD6A84E)
    val cardDark = Color(0xFF172D2B)
    val cardDarkEnd = Color(0xFF23423E)
    val cardInk = Color(0xFF1B2B29)
    // Prestige dark-teal & champagne-gold VIP membership card.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardDark,
        border = BorderStroke(1.dp, cardGoldStrong.copy(alpha = 0.55f)),
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(cardDark, cardDarkEnd, Color(0xFF132220))))
                .drawBehind {
                    drawCircle(cardGold.copy(alpha = 0.08f), radius = size.height * 0.85f, center = Offset(size.width * 0.94f, 0f))
                    drawCircle(cardGold.copy(alpha = 0.05f), radius = size.height * 0.45f, center = Offset(size.width * 0.75f, size.height))
                }
                .padding(Gap.L)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(cardGold.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph("gift", cardGold, Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f).padding(start = Gap.M)) {
                    Text("宜捷行 · 司机会员卡", color = cardGold.copy(alpha = 0.85f), fontSize = DriverType.Caption, fontWeight = FontWeight.Medium)
                    Text(
                        if (active) subscription!!.mText("levelName") else if (trial != null) "体验权益卡" else "尚未开通会员",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    if (active) "生效中" else if (trial?.mText("status") == "ENDED") "已结束" else if (trial != null) "体验中" else "未开通",
                    color = cardInk,
                    fontSize = DriverType.Caption,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cardGold).padding(horizontal = 8.dp, vertical = 4.dp),
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
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(Gap.S))
            val shown = rights.take(3).map { it.mText("name", it.mText("title", it.mText("label"))) }.filter { it != "—" }
            if (shown.isNotEmpty())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.forEach {
                        Text(
                            it,
                            color = cardGold,
                            fontSize = DriverType.Caption,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            else
                Text(
                    "接单 ${if (features.optBoolean("canAcceptOrders")) "可用" else "不可用"} · 线下议价 ${if (features.optBoolean("canNegotiate")) "可用" else "未开放"} · 热力 ${if (features.optBoolean("canHeatmap")) "可用" else "未开放"}",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = DriverType.Caption,
                )
            Spacer(Modifier.height(Gap.M))
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.S)) {
                Button(
                    onClick = {
                        navigate(if (trial?.mText("status") == "ENDED" && !active) mRoute("expiry") else mRoute())
                    },
                    modifier = Modifier.weight(1f).heightIn(min = DriverButtonSize.Min),
                    shape = RoundedCornerShape(DriverButtonSize.Corner),
                    colors = ButtonDefaults.buttonColors(containerColor = cardGold, contentColor = cardInk),
                ) {
                    Text(if (active) "管理会员" else "查看权益", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { navigate(mRoute("records")) },
                    modifier = Modifier.weight(1f).heightIn(min = DriverButtonSize.Min),
                    shape = RoundedCornerShape(DriverButtonSize.Corner),
                    border = BorderStroke(1.dp, cardGold.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                ) {
                    Text("开通记录", color = Color.White)
                }
            }
        }
    }
}
