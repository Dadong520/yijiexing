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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// UIO-20261029-01 presentation helpers; no business rules live here.

/** Spacing scale shared by the optimized screens: 8 / 12 / 16 / 24. */
object Gap {
  val S = 8.dp
  val M = 12.dp
  val L = 16.dp
  val XL = 24.dp
}

/**
 * One type scale for every page: page title is the largest, card titles next, body text normal and
 * helper text the smallest. Screens use these instead of ad-hoc sizes.
 */
object TypeScale {
  val Page = 20.sp
  val Title = 16.sp
  val Body = 15.sp
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

/** Empty-state mark: the module icon in a plain neutral circle. */
@Composable
fun EmptyIllustration(icon: ImageVector) {
  Box(Modifier.size(64.dp).background(SurfaceMuted, CircleShape), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = IconTone, modifier = Modifier.size(28.dp))
  }
}

/** Small light-3D car on a road, used to decorate the booking and profile headers. */
@Composable
fun CarIllustration(modifier: Modifier = Modifier, body: Color = Teal) {
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    drawOval(Color(0x220F1B2A), topLeft = Offset(w * 0.08f, h * 0.84f), size = Size(w * 0.84f, h * 0.12f))
    // Body with a top highlight so it reads as a rounded, lit shape.
    drawRoundRect(
      Brush.verticalGradient(listOf(body.copy(alpha = 0.75f), body), startY = h * 0.42f, endY = h * 0.82f),
      topLeft = Offset(w * 0.06f, h * 0.44f), size = Size(w * 0.88f, h * 0.36f), cornerRadius = CornerRadius(h * 0.16f),
    )
    drawRoundRect(
      Brush.verticalGradient(listOf(body.copy(alpha = 0.55f), body.copy(alpha = 0.9f)), startY = h * 0.16f, endY = h * 0.5f),
      topLeft = Offset(w * 0.22f, h * 0.18f), size = Size(w * 0.52f, h * 0.32f), cornerRadius = CornerRadius(h * 0.14f),
    )
    drawRoundRect(Color(0xCCE8F7FF), topLeft = Offset(w * 0.28f, h * 0.24f), size = Size(w * 0.19f, h * 0.2f), cornerRadius = CornerRadius(h * 0.06f))
    drawRoundRect(Color(0xCCE8F7FF), topLeft = Offset(w * 0.5f, h * 0.24f), size = Size(w * 0.18f, h * 0.2f), cornerRadius = CornerRadius(h * 0.06f))
    drawRoundRect(Color(0x55FFFFFF), topLeft = Offset(w * 0.12f, h * 0.48f), size = Size(w * 0.7f, h * 0.05f), cornerRadius = CornerRadius(h * 0.03f))
    drawCircle(Color(0xFFFFD27A), radius = h * 0.05f, center = Offset(w * 0.9f, h * 0.58f))
    listOf(0.27f, 0.73f).forEach { x ->
      drawCircle(Color(0xFF243447), radius = h * 0.13f, center = Offset(w * x, h * 0.8f))
      drawCircle(Color(0xFFCBD5DF), radius = h * 0.055f, center = Offset(w * x, h * 0.8f))
    }
  }
}

/**
 * The page's one primary action: solid teal, white text, no shadow. Disabled turns neutral grey with
 * text that stays readable. Wrapped labels grow the button instead of clipping.
 */
@Composable
fun GradientButton(
  label: String,
  enabled: Boolean = true,
  modifier: Modifier = Modifier.fillMaxWidth(),
  icon: ImageVector? = null,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(12.dp)
  Row(
    modifier
      .heightIn(min = 50.dp)
      .clip(shape)
      .background(if (enabled) Teal else SurfaceMuted)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val content = if (enabled) Color.White else DisabledText
    icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(18.dp).padding(end = 2.dp)) }
    Text(label, color = content, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, textAlign = TextAlign.Center)
  }
}

/** Secondary action: neutral grey fill with brand text, clearly lighter than the primary. */
@Composable
fun SecondaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
    shape = RoundedCornerShape(12.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = SurfaceMuted,
      contentColor = BrandStrong,
      disabledContainerColor = SurfaceMuted,
      disabledContentColor = DisabledText,
    ),
    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
  ) {
    Text(label, fontSize = TypeScale.Body, fontWeight = FontWeight.Medium, lineHeight = 20.sp, textAlign = TextAlign.Center)
  }
}

/** Destructive action: red text on white with a red edge, never the primary teal block. */
@Composable
fun DangerButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, if (enabled) DangerLine else Line),
    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Danger, disabledContentColor = DisabledText),
  ) {
    Text(label, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, textAlign = TextAlign.Center)
  }
}
