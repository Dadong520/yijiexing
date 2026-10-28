package cn.hbzjhs.driver.preview

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Green = Color(0xFF00796E)
val Ink = Color(0xFF0F1B2A)
val Muted = Color(0xFF4B5B6B)
val Pale = Color(0xFFE3F4F1)
val Page = Color(0xFFF2F6F7)
val Line = Color(0xFFDCE5E8)
val Orange = Color(0xFFB45309)

@Composable
fun NativeTheme(large: Boolean = false, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * if (large) 1.08f else 1f)
    ) {
        MaterialTheme(
            colorScheme =
                lightColorScheme(
                    primary = Green,
                    onPrimary = Color.White,
                    background = Page,
                    surface = Color.White,
                    onSurface = Ink,
                    primaryContainer = Pale,
                    onPrimaryContainer = GreenStrong,
                    secondary = Sky,
                    secondaryContainer = Pale,
                    onSecondaryContainer = GreenStrong,
                    tertiary = Warm,
                    tertiaryContainer = WarmSoft,
                    error = Danger,
                    errorContainer = DangerSoft,
                    onBackground = Ink,
                    outline = ControlOutline,
                    outlineVariant = Line,
                    surfaceVariant = Page,
                    onSurfaceVariant = Muted,
                ),
            typography =
                Typography(
                    bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                    labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
                    labelSmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
                    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
                    labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
                ),
            content = content,
        )
    }
}

@Composable
fun Glyph(name: String, color: Color = Green, modifier: Modifier = Modifier.size(24.dp)) {
    val standard = when (name) {
        "home" -> Icons.Outlined.Home
        "orders", "doc" -> Icons.Outlined.List
        "profile" -> Icons.Outlined.Person
        "back" -> Icons.Outlined.ArrowBack
        "right" -> Icons.Outlined.KeyboardArrowRight
        "check" -> Icons.Outlined.Check
        "location" -> Icons.Outlined.LocationOn
        "phone" -> Icons.Outlined.Phone
        "bell" -> Icons.Outlined.Notifications
        "settings" -> Icons.Outlined.Settings
        "plus" -> Icons.Outlined.Add
        "close" -> Icons.Outlined.Close
        else -> null
    }
    if (standard != null) {
        Icon(standard, contentDescription = null, modifier = modifier, tint = color)
        return
    }
    Canvas(modifier) {
        val s = size.width / 24f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun ln(x: Float, y: Float, a: Float, b: Float) =
            drawLine(color, p(x, y), p(a, b), 1.7f * s, StrokeCap.Round)
        fun circ(x: Float, y: Float, r: Float) =
            drawCircle(color, r * s, p(x, y), style = Stroke(1.7f * s))
        fun path(vararg pts: Pair<Float, Float>, close: Boolean = false) {
            val pp = Path()
            pp.moveTo(pts[0].first * s, pts[0].second * s)
            pts.drop(1).forEach { pp.lineTo(it.first * s, it.second * s) }
            if (close) pp.close()
            drawPath(pp, color, style = Stroke(1.7f * s, cap = StrokeCap.Round))
        }
        when (name) {
            "home" -> {
                path(3f to 11f, 12f to 3f, 21f to 11f)
                path(6f to 10f, 6f to 21f, 18f to 21f, 18f to 10f)
                path(10f to 21f, 10f to 15f, 14f to 15f, 14f to 21f)
            }
            "orders",
            "doc" -> {
                path(6f to 3f, 18f to 3f, 18f to 21f, 6f to 21f, close = true)
                ln(9f, 8f, 15f, 8f)
                ln(9f, 12f, 15f, 12f)
                ln(9f, 16f, 13f, 16f)
            }
            "wallet",
            "card" -> {
                drawRoundRect(
                    color,
                    p(3f, 6f),
                    Size(18 * s, 14 * s),
                    androidx.compose.ui.geometry.CornerRadius(2 * s),
                    style = Stroke(1.7f * s),
                )
                ln(3f, 10f, 21f, 10f)
                ln(15f, 15f, 18f, 15f)
            }
            "profile" -> {
                circ(12f, 7f, 4f)
                path(
                    4f to 21f,
                    4f to 19f,
                    6f to 15f,
                    12f to 14f,
                    18f to 15f,
                    20f to 19f,
                    20f to 21f,
                )
            }
            "back" -> {
                path(15f to 5f, 8f to 12f, 15f to 19f)
            }
            "right" -> {
                path(9f to 6f, 15f to 12f, 9f to 18f)
            }
            "check",
            "shield" -> {
                if (name == "shield")
                    path(
                        12f to 2f,
                        21f to 6f,
                        19f to 16f,
                        12f to 22f,
                        5f to 16f,
                        3f to 6f,
                        close = true,
                    )
                path(7f to 12f, 10f to 15f, 17f to 8f)
            }
            "location" -> {
                circ(12f, 10f, 4f)
                path(
                    5f to 12f,
                    4f to 9f,
                    6f to 4f,
                    12f to 2f,
                    18f to 4f,
                    20f to 9f,
                    19f to 12f,
                    12f to 22f,
                    5f to 12f,
                )
            }
            "navigation" -> {
                path(4f to 11f, 21f to 3f, 13f to 21f, 11f to 13f, 4f to 11f)
            }
            "phone" -> {
                path(
                    5f to 3f,
                    9f to 3f,
                    10f to 8f,
                    7f to 10f,
                    10f to 14f,
                    14f to 17f,
                    16f to 14f,
                    21f to 15f,
                    21f to 19f,
                    18f to 22f,
                    12f to 19f,
                    5f to 12f,
                    2f to 6f,
                    5f to 3f,
                )
            }
            "bell" -> {
                path(
                    5f to 17f,
                    7f to 14f,
                    7f to 7f,
                    9f to 4f,
                    15f to 4f,
                    17f to 7f,
                    17f to 14f,
                    19f to 17f,
                    5f to 17f,
                )
                ln(10f, 21f, 14f, 21f)
            }
            "settings" -> {
                circ(12f, 12f, 7f)
                circ(12f, 12f, 2.5f)
                ln(12f, 2f, 12f, 5f)
                ln(12f, 19f, 12f, 22f)
                ln(2f, 12f, 5f, 12f)
                ln(19f, 12f, 22f, 12f)
            }
            "gift" -> {
                path(4f to 10f, 20f to 10f, 20f to 21f, 4f to 21f, close = true)
                ln(12f, 7f, 12f, 21f)
                ln(3f, 7f, 21f, 7f)
                path(
                    12f to 7f,
                    7f to 7f,
                    6f to 4f,
                    8f to 2f,
                    12f to 7f,
                    16f to 2f,
                    18f to 4f,
                    17f to 7f,
                )
            }
            "chart" -> {
                ln(5f, 19f, 5f, 12f)
                ln(12f, 19f, 12f, 4f)
                ln(19f, 19f, 19f, 9f)
                ln(2f, 22f, 22f, 22f)
            }
            "voice" -> {
                path(3f to 9f, 7f to 9f, 12f to 4f, 12f to 20f, 7f to 15f, 3f to 15f, close = true)
                path(16f to 8f, 18f to 12f, 16f to 16f)
                path(19f to 4f, 22f to 12f, 19f to 20f)
            }
            "scan" -> {
                path(3f to 8f, 3f to 3f, 8f to 3f)
                path(16f to 3f, 21f to 3f, 21f to 8f)
                path(21f to 16f, 21f to 21f, 16f to 21f)
                path(8f to 21f, 3f to 21f, 3f to 16f)
                ln(4f, 12f, 20f, 12f)
            }
            "support" -> {
                path(
                    3f to 14f,
                    3f to 10f,
                    5f to 5f,
                    9f to 2f,
                    15f to 2f,
                    19f to 5f,
                    21f to 10f,
                    21f to 17f,
                    18f to 21f,
                    13f to 21f,
                )
                path(3f to 11f, 7f to 11f, 7f to 17f, 3f to 17f, close = true)
                path(17f to 11f, 21f to 11f, 21f to 17f, 17f to 17f, close = true)
            }
            "car" -> {
                path(
                    3f to 11f,
                    6f to 5f,
                    18f to 5f,
                    21f to 11f,
                    21f to 19f,
                    3f to 19f,
                    close = true,
                )
                ln(4f, 11f, 20f, 11f)
                circ(7f, 15f, 1f)
                circ(17f, 15f, 1f)
                ln(5f, 19f, 5f, 22f)
                ln(19f, 19f, 19f, 22f)
            }
            "plus" -> {
                ln(12f, 4f, 12f, 20f)
                ln(4f, 12f, 20f, 12f)
            }
            "close" -> {
                ln(6f, 6f, 18f, 18f)
                ln(6f, 18f, 18f, 6f)
            }
            else -> {
                circ(12f, 12f, 8f)
                ln(12f, 7f, 12f, 13f)
                drawCircle(color, s, p(12f, 17f))
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color = Green, bg: Color = Pale) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun SheetCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Line),
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Green,
            contentColor = Color.White,
            disabledContainerColor = Line,
            disabledContentColor = Muted
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PageHeader(title: String, onBack: (() -> Unit)? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "返回" }
            ) {
                Glyph("back", Ink, Modifier.size(22.dp))
            }
        }
        Text(
            text = title,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
        )
        if (action != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp)
            ) {
                Text(
                    text = action,
                    color = Green,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String = "", action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (action != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(action, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Green)
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, color: Color = Ink) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        Text(value, color = color, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MenuRow(icon: String, title: String, description: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (tint, soft) = moduleAccent(icon)
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(soft),
            contentAlignment = Alignment.Center
        ) {
            Glyph(icon, tint, Modifier.size(18.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        )
        {
            Text(title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = Ink)
            if (description.isNotEmpty()) {
                Text(description, color = Muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 1.dp))
            }
        }
        Glyph("right", Muted, Modifier.size(16.dp))
    }
}

@Composable
fun RouteStops(order: DriverOrder, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple("起", order.origin, Green),
            Triple("终", order.destination, Orange)
        ).forEach { (label, address, color) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp, end = 8.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = address,
                    modifier = Modifier.weight(1f),
                    fontSize = if (compact) 16.sp else 18.sp,
                    lineHeight = if (compact) 22.sp else 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
            }
        }
    }
}

@Composable
fun OrderCard(order: DriverOrder, action: String? = null, onClick: () -> Unit) {
    SheetCard {
        if (order.isPrivateExample) {
            Text(
                "演示订单 · 未关联乘客 App",
                color = Orange,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(
                    order.kind.title,
                    if (order.kind == OrderKind.BID) Orange else Green,
                    if (order.kind == OrderKind.BID) WarmSoft else Pale
                )
                Text(
                    if (order.stage == Stage.AVAILABLE) "距您 ${order.distance} km" else order.stage.title,
                    color = Green,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                if (order.destinationPending) "费用待定" else "¥${money(order.fare)}",
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
        }
        Spacer(Modifier.height(8.dp))
        RouteStops(order, compact = true)
        if (order.kind == OrderKind.RESERVED)
            Text("计划用车 ${order.scheduledTimeText}", color = Green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (order.destinationPending) "目的地待补充"
            else if (order.kind == OrderKind.BID && order.stage == Stage.AVAILABLE) "参考报价 · 约 ${order.mileage} km"
            else "预估里程 · 约 ${order.mileage} km",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(action ?: "查看详情", onClick = onClick)
    }
}

@Composable
fun StatsRow(state: DriverState) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text("累计收入 (含演示数据)", color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                Text(
                    "¥${money(state.todayIncome)}",
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("累计接单" to state.todayAccepted.toString(), "累计完单" to state.todayCompleted.toString()).forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(value, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(label, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun DemoNote(text: String = "体验版 · 当前展示演示数据") {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Glyph("shield", Muted, Modifier.size(16.dp))
        Text(
            text = text,
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f).padding(start = 6.dp)
        )
    }
}

