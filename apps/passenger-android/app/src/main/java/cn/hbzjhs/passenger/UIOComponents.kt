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

/** Light-3D brand fill: a top-lit vertical gradient used on primary buttons and hero cards. */
val BrandGradient = Brush.verticalGradient(listOf(Color(0xFF14A394), Teal, BrandStrong))
val HeroGradient = Brush.linearGradient(listOf(Color(0xFF0E9C8E), Color(0xFF0B7F86), Color(0xFF175E8C)))

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

/** Light-3D empty-state art: a lit sphere with a soft ground shadow behind the module icon. */
@Composable
fun EmptyIllustration(icon: ImageVector) {
  val (tint, soft) = iconAccent(icon)
  Box(Modifier.size(width = 120.dp, height = 96.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
      drawOval(Color(0x1A0F1B2A), topLeft = Offset(size.width * 0.22f, size.height * 0.80f), size = Size(size.width * 0.56f, size.height * 0.12f))
      drawCircle(soft, radius = size.height * 0.40f, center = Offset(size.width / 2, size.height * 0.44f))
      drawCircle(
        Brush.radialGradient(listOf(Color.White, soft), center = Offset(size.width * 0.42f, size.height * 0.30f), radius = size.height * 0.42f),
        radius = size.height * 0.34f,
        center = Offset(size.width / 2, size.height * 0.44f),
      )
      drawCircle(tint.copy(alpha = 0.16f), radius = 7.dp.toPx(), center = Offset(size.width * 0.16f, size.height * 0.24f))
      drawCircle(tint.copy(alpha = 0.12f), radius = 4.dp.toPx(), center = Offset(size.width * 0.86f, size.height * 0.62f))
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp).padding(bottom = 4.dp))
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

/** Primary action with a lit gradient and soft shadow, so it reads as a real button rather than coloured text. */
@Composable
fun GradientButton(
  label: String,
  enabled: Boolean = true,
  modifier: Modifier = Modifier.fillMaxWidth(),
  icon: ImageVector? = null,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier
      .heightIn(min = 50.dp)
      .shadow(if (enabled) 6.dp else 0.dp, shape, ambientColor = Teal, spotColor = Teal)
      .clip(shape)
      .background(if (enabled) BrandGradient else SolidColor(Color(0xFFE5E7EB)))
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val content = if (enabled) Color.White else Color(0xFF9CA3AF)
    icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(18.dp).padding(end = 2.dp)) }
    Text(label, color = content, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, textAlign = TextAlign.Center)
  }
}

/** Secondary action: soft brand fill with a thin edge, lighter than the gradient primary. */
@Composable
fun SecondaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, if (enabled) Teal.copy(alpha = 0.35f) else Line),
    colors = ButtonDefaults.buttonColors(
      containerColor = BrandSoft,
      contentColor = BrandStrong,
      disabledContainerColor = Color(0xFFF3F4F6),
      disabledContentColor = Color(0xFF9CA3AF),
    ),
    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
  ) {
    Text(label, fontSize = TypeScale.Body, fontWeight = FontWeight.Medium, lineHeight = 20.sp, textAlign = TextAlign.Center)
  }
}

/** Destructive action: red outline on soft red, never the primary green block. */
@Composable
fun DangerButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, if (enabled) Danger else Line),
    colors = ButtonDefaults.outlinedButtonColors(containerColor = DangerSoft, contentColor = Danger, disabledContentColor = Muted.copy(alpha = 0.6f)),
  ) {
    Text(label, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, textAlign = TextAlign.Center)
  }
}
