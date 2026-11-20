package cn.hbzjhs.passenger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// UIO-20261029-01 presentation helpers; no business rules live here.

/** Spacing scale shared by the optimized screens: 6 / 10 / 12 / 16. */
object Gap {
  val S = 6.dp
  val M = 10.dp
  val L = 12.dp
  val XL = 16.dp
}

/**
 * One type scale for every page: page title is the largest, card titles next, body text normal and
 * helper text the smallest. Screens use these instead of ad-hoc sizes.
 */
object TypeScale {
  val Page = 20.sp
  val Title = 16.sp
  val Body = 14.sp
  val Caption = 12.sp
}

/** Brand fill: one flat teal (RIDEC dropped the lit gradients and coloured shadows). */
val BrandGradient: Brush = SolidColor(Teal)
/** Kept for source compatibility; heroes are now plain white surfaces. */
val HeroGradient: Brush = SolidColor(Teal)

/** Coordinate as text, or "" when absent/NaN/0 so a missing point is never filled as 0 or NaN. */
fun org.json.JSONObject.coordText(key: String): String {
  if (!has(key) || isNull(key)) return ""
  val value = optDouble(key)
  return if (value.isFinite() && value != 0.0) value.toString() else ""
}

/** Semantic icon for an empty state, picked from its existing title text. */
fun emptyIcon(title: String): ImageVector = when {
  "卡券" in title -> Icons.Default.Star
  "积分" in title -> Icons.Default.Favorite
  "邀请" in title -> Icons.Default.Share
  "投诉" in title -> Icons.Default.Warning
  "订单" in title -> Icons.AutoMirrored.Filled.List
  "地址" in title || "地点" in title -> Icons.Default.Place
  "乘车人" in title || "联系人" in title -> Icons.Default.Person
  "发票" in title || "回执" in title -> Icons.AutoMirrored.Filled.List
  "反馈" in title || "投诉" in title -> Icons.Default.Email
  "屏蔽" in title || "登录" in title -> Icons.Default.Lock
  "同步" in title -> Icons.Default.Refresh
  else -> Icons.Default.Info
}

/**
 * Empty-state art drawn in code (no bitmaps): a soft module-tinted backdrop, a blank "record" card
 * with placeholder lines, a few accent sparks, and the module icon in a tinted badge. Decorative only.
 */
@Composable
fun EmptyIllustration(icon: ImageVector) {
  val (tint, soft) = iconAccent(icon)
  Box(Modifier.size(width = 148.dp, height = 112.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
      val w = size.width
      val h = size.height
      drawOval(soft, topLeft = Offset(w * 0.12f, h * 0.06f), size = Size(w * 0.76f, h * 0.84f))
      drawOval(Color(0x140F1B2A), topLeft = Offset(w * 0.22f, h * 0.88f), size = Size(w * 0.56f, h * 0.08f))
      // Blank record card, slightly tilted by offsetting a second sheet behind it.
      drawRoundRect(tint.copy(alpha = 0.16f), topLeft = Offset(w * 0.36f, h * 0.2f), size = Size(w * 0.42f, h * 0.56f), cornerRadius = CornerRadius(h * 0.06f))
      drawRoundRect(Color.White, topLeft = Offset(w * 0.28f, h * 0.26f), size = Size(w * 0.42f, h * 0.58f), cornerRadius = CornerRadius(h * 0.06f))
      drawRoundRect(Line, topLeft = Offset(w * 0.28f, h * 0.26f), size = Size(w * 0.42f, h * 0.58f), cornerRadius = CornerRadius(h * 0.06f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f * density))
      listOf(0.62f, 0.7f, 0.78f).forEachIndexed { i, y ->
        drawRoundRect(if (i == 0) tint.copy(alpha = 0.35f) else DividerTone, topLeft = Offset(w * 0.34f, h * y - h * 0.02f), size = Size(w * (if (i == 2) 0.18f else 0.3f), h * 0.04f), cornerRadius = CornerRadius(h * 0.02f))
      }
      drawCircle(tint.copy(alpha = 0.45f), radius = h * 0.03f, center = Offset(w * 0.18f, h * 0.3f))
      drawCircle(Amber.copy(alpha = 0.55f), radius = h * 0.022f, center = Offset(w * 0.84f, h * 0.2f))
      drawCircle(tint.copy(alpha = 0.3f), radius = h * 0.018f, center = Offset(w * 0.86f, h * 0.7f))
    }
    Box(
      Modifier.offset(y = (-14).dp).size(44.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape).padding(4.dp).background(soft, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
  }
}

/** Short next-step hint under an empty state, e.g. where the records come from. */
@Composable
fun EmptyHint(text: String, icon: ImageVector = Icons.Default.Info) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BrandMist).padding(horizontal = 12.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Icon(icon, contentDescription = null, tint = Teal, modifier = Modifier.size(18.dp))
    Text(text, color = Muted, fontSize = TypeScale.Caption, lineHeight = 18.sp, modifier = Modifier.weight(1f))
  }
}

/**
 * Icon + visible label action (call, complaint, contact…): at least 48dp tall, the label is always
 * shown so the icon never has to be guessed, and screen readers read the label as a button.
 */
@Composable
fun IconActionButton(
  label: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  tint: Color = BrandStrong,
  container: Color = BrandSoft,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier
      .heightIn(min = 48.dp)
      .clip(shape)
      .background(if (enabled) container else SurfaceMuted)
      .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val content = if (enabled) tint else DisabledText
    Box(Modifier.size(28.dp).background(content.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
    }
    Text(label, color = content, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, textAlign = TextAlign.Center)
  }
}

/** Left label / right-aligned value row for record details, matching the other edge-aligned forms. */
@Composable
fun RecordRow(label: String, value: String) {
  Row(
    Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Text(label, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
    Text(value, color = Ink, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
  }
}

/** 路线起终途经徽标类型。 */
enum class RoutePinKind { ORIGIN, WAYPOINT, DESTINATION }

/** 统一双层圆环起终途经图标，单字严格几何居中。 */
@Composable
fun RouteBadgeIcon(kind: RoutePinKind, index: Int? = null, modifier: Modifier = Modifier) {
  val (dot, halo, label) = when (kind) {
    RoutePinKind.ORIGIN -> Triple(Teal, BrandSoft, "起")
    RoutePinKind.WAYPOINT -> Triple(Sky, SkySoft, index?.toString() ?: "经")
    RoutePinKind.DESTINATION -> Triple(Amber, WarmSoft, "终")
  }
  Box(
    modifier.size(22.dp).clip(CircleShape).background(halo),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier.size(18.dp).clip(CircleShape).background(dot),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        color = Color.White,
        fontSize = 10.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
      )
    }
  }
}

/** 选址地图展开/收起全屏图标（四角外扩或内收箭头）。 */
@Composable
fun FullscreenIcon(expanded: Boolean, tint: Color, modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    val stroke = 2.dp.toPx()
    val arm = w * 0.22f
    listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
      val tipX = if (expanded) (if (sx < 0) w * 0.36f else w * 0.64f) else (if (sx < 0) w * 0.16f else w * 0.84f)
      val tipY = if (expanded) (if (sy < 0) h * 0.36f else h * 0.64f) else (if (sy < 0) h * 0.16f else h * 0.84f)
      val dirX = if (expanded) sx else -sx
      val dirY = if (expanded) sy else -sy
      drawLine(tint, Offset(tipX, tipY), Offset(tipX + dirX * arm, tipY), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
      drawLine(tint, Offset(tipX, tipY), Offset(tipX, tipY + dirY * arm), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
    }
  }
}

/** 流线型新能源轿车简笔插画，贴合青绿卡片与整体简洁风。 */
@Composable
fun CarIllustration(modifier: Modifier = Modifier, body: Color = Teal) {
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    // 地面轻影与速度引导线
    drawLine(
      body.copy(alpha = 0.28f),
      Offset(w * 0.04f, h * 0.84f),
      Offset(w * 0.96f, h * 0.84f),
      strokeWidth = h * 0.05f,
      cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
    val shell = androidx.compose.ui.graphics.Path().apply {
      moveTo(w * 0.08f, h * 0.72f)
      cubicTo(w * 0.06f, h * 0.56f, w * 0.12f, h * 0.44f, w * 0.22f, h * 0.42f)
      cubicTo(w * 0.31f, h * 0.20f, w * 0.45f, h * 0.15f, w * 0.62f, h * 0.17f)
      cubicTo(w * 0.73f, h * 0.19f, w * 0.80f, h * 0.32f, w * 0.86f, h * 0.40f)
      cubicTo(w * 0.94f, h * 0.43f, w * 0.95f, h * 0.58f, w * 0.92f, h * 0.72f)
      close()
    }
    drawPath(shell, body.copy(alpha = 0.24f))
    drawPath(
      shell,
      body.copy(alpha = 0.92f),
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.075f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )
    // 溜背车窗与B柱分隔
    val cabin = androidx.compose.ui.graphics.Path().apply {
      moveTo(w * 0.28f, h * 0.41f)
      cubicTo(w * 0.35f, h * 0.24f, w * 0.46f, h * 0.22f, w * 0.60f, h * 0.23f)
      cubicTo(w * 0.68f, h * 0.24f, w * 0.74f, h * 0.32f, w * 0.78f, h * 0.41f)
      close()
    }
    drawPath(cabin, body.copy(alpha = 0.42f))
    drawLine(
      body.copy(alpha = 0.85f),
      Offset(w * 0.53f, h * 0.23f),
      Offset(w * 0.50f, h * 0.41f),
      strokeWidth = h * 0.055f,
      cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
    // 轮拱镂空与双环轮毂
    listOf(0.26f, 0.74f).forEach { cx ->
      drawCircle(body.copy(alpha = 0.96f), radius = h * 0.15f, center = Offset(w * cx, h * 0.74f))
      drawCircle(Color(0xFF0A4D46).copy(alpha = 0.38f), radius = h * 0.08f, center = Offset(w * cx, h * 0.74f))
      drawCircle(body.copy(alpha = 0.96f), radius = h * 0.032f, center = Offset(w * cx, h * 0.74f))
    }
  }
}

/** Unified button sizing: every full-width action is at least 52dp, smaller ones at least 48dp. */
object ButtonSize {
  val Primary = 52.dp
  val Min = 48.dp
  val Corner = 14.dp
}

/** Primary fill: a quiet teal-to-deep-teal sweep, so the one main action has depth without glare. */
private val PrimaryBrush = Brush.horizontalGradient(listOf(Teal, BrandStrong))

/**
 * The page's one primary action: teal sweep, white text, soft shadow. Disabled turns neutral grey
 * with text that stays readable. Wrapped labels grow the button instead of clipping.
 */
@Composable
fun GradientButton(
  label: String,
  enabled: Boolean = true,
  modifier: Modifier = Modifier.fillMaxWidth(),
  icon: ImageVector? = null,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(ButtonSize.Corner)
  Row(
    modifier
      .heightIn(min = ButtonSize.Primary)
      .then(if (enabled) Modifier.shadow(3.dp, shape, ambientColor = Teal, spotColor = Teal) else Modifier)
      .clip(shape)
      .background(if (enabled) PrimaryBrush else SolidColor(SurfaceMuted))
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val content = if (enabled) Color.White else DisabledText
    icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(20.dp)) }
    Text(label, color = content, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, textAlign = TextAlign.Center)
  }
}

/** Secondary action: soft brand tint with a hairline edge and brand text, lighter than the primary. */
@Composable
fun SecondaryButton(label: String, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonSize.Min),
    shape = RoundedCornerShape(ButtonSize.Corner),
    colors = ButtonDefaults.buttonColors(
      containerColor = BrandSoft,
      contentColor = BrandStrong,
      disabledContainerColor = SurfaceMuted,
      disabledContentColor = DisabledText,
    ),
    border = if (enabled) BorderStroke(1.dp, Teal.copy(alpha = 0.22f)) else null,
    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
  ) {
    icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
    Text(label, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, textAlign = TextAlign.Center)
  }
}

/** Destructive action: red text on a faint red wash with a red edge, never the primary teal block. */
@Composable
fun DangerButton(label: String, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonSize.Min),
    shape = RoundedCornerShape(ButtonSize.Corner),
    border = BorderStroke(1.dp, if (enabled) DangerLine else Line),
    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (enabled) DangerSoft.copy(alpha = 0.5f) else Color.White, contentColor = Danger, disabledContentColor = DisabledText),
  ) {
    icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
    Text(label, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, textAlign = TextAlign.Center)
  }
}
