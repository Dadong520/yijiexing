package cn.hbzjhs.passenger

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// CCUI-20261029-01 shared visual tokens; presentation only.
val BrandStrong = Color(0xFF005F56)
val BrandSoft = Color(0xFFE3F4F1)
val Line = Color(0xFFDCE5E8)
val ControlOutline = Color(0xFF71818C)
val DividerTone =Color(0xFFE6EDEF)
val Chevron = Color(0xFF71818C)
val Sky = Color(0xFF0369A1)
val SkySoft = Color(0xFFE0F2FE)
val Warm = Color(0xFFB45309)
val WarmSoft = Color(0xFFFFF4E5)
val Violet = Color(0xFF6D28D9)
val VioletSoft = Color(0xFFF1EBFE)
val Success = Color(0xFF1B7A3E)
val SuccessSoft = Color(0xFFE6F4EA)
val Danger = Color(0xFFB42318)
val DangerSoft = Color(0xFFFDECEA)
val DangerLine = Color(0xFFF3C5C0)

/** Module accent for menu/tile icons: tint to soft background. */
fun iconAccent(icon: ImageVector): Pair<Color, Color> = when (icon) {
  Icons.Default.Place, Icons.Default.Person -> Sky to SkySoft
  Icons.AutoMirrored.Filled.List, Icons.Default.Info -> Warm to WarmSoft
  Icons.Default.Email, Icons.Default.Settings -> Violet to VioletSoft
  Icons.Default.Lock -> Success to SuccessSoft
  else -> Teal to BrandSoft
}
