package cn.hbzjhs.passenger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** Coordinate as text, or "" when absent/NaN/0 so a missing point is never filled as 0 or NaN. */
fun org.json.JSONObject.coordText(key: String): String {
  if (!has(key) || isNull(key)) return ""
  val value = optDouble(key)
  return if (value.isFinite() && value != 0.0) value.toString() else ""
}

/** Semantic icon for an empty state, picked from its existing title text. */
fun emptyIcon(title: String): ImageVector = when {
  "订单" in title -> Icons.AutoMirrored.Filled.List
  "地址" in title || "地点" in title -> Icons.Default.Place
  "乘车人" in title || "联系人" in title -> Icons.Default.Person
  "发票" in title || "回执" in title -> Icons.AutoMirrored.Filled.List
  "反馈" in title || "投诉" in title -> Icons.Default.Email
  "屏蔽" in title || "登录" in title -> Icons.Default.Lock
  "同步" in title -> Icons.Default.Refresh
  else -> Icons.Default.Info
}

/** Round tinted badge behind an empty-state icon. */
@Composable
fun EmptyIllustration(icon: ImageVector) {
  val (tint, soft) = iconAccent(icon)
  Box(
    Modifier.size(64.dp).clip(CircleShape).background(soft),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
    }
  }
}

/** Secondary action: outlined Teal, lighter than ActionButton. */
@Composable
fun SecondaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, if (enabled) Teal else Line),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal),
  ) {
    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, textAlign = TextAlign.Center)
  }
}

/** Destructive action: red outline on soft red, never the primary green block. */
@Composable
fun DangerButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.5.dp, if (enabled) Danger else Line),
    colors = ButtonDefaults.outlinedButtonColors(containerColor = DangerSoft, contentColor = Danger, disabledContentColor = Muted.copy(alpha = 0.6f)),
  ) {
    Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, textAlign = TextAlign.Center)
  }
}
