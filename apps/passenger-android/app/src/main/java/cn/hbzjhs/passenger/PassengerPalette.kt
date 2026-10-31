package cn.hbzjhs.passenger

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// CCUI-20261029-01 shared visual tokens; presentation only.
// RIDEC-20261031-01: white / near-white surfaces, ink text and a restrained teal accent. Soft brand
// tint is only for small selected states; neutral grey fills carry secondary actions.
val BrandStrong = Color(0xFF005F56)
val BrandSoft = Color(0xFFEAF5F3)
val Line = Color(0xFFE2E6EA)
val ControlOutline = Color(0xFF71818C)
val DividerTone = Color(0xFFEDF0F2)
val Chevron = Color(0xFF8A96A1)
/** Neutral fill for secondary buttons, segmented tracks and icon wells. */
val SurfaceMuted = Color(0xFFF2F4F6)
/** Monochrome list icons, so colour is left for state and the one primary action. */
val IconTone = Color(0xFF5B6773)
/** Disabled text that stays readable on [SurfaceMuted]. */
val DisabledText = Color(0xFF6B7682)
val Sky = Color(0xFF0369A1)
val SkySoft = Color(0xFFE0F2FE)
val Warm = Color(0xFFB45309)
val WarmSoft = Color(0xFFFFF4E5)
val Violet = Color(0xFF6D28D9)
val VioletSoft = Color(0xFFF1EBFE)
val Success = Color(0xFF1B7A3E)
val SuccessSoft = Color(0xFFEBFAF1)
val Danger = Color(0xFFB42318)
val DangerSoft = Color(0xFFFDECEA)
val DangerLine = Color(0xFFF3C5C0)

/** Module accent for menu/tile icons: tint to soft background. */
fun iconAccent(icon: ImageVector): Pair<Color, Color> = when (icon) {
  Icons.Default.Place, Icons.Default.Person -> Sky to SkySoft
  Icons.AutoMirrored.Filled.List, Icons.Default.Info -> Warm to WarmSoft
  Icons.Default.Email, Icons.Default.Settings -> Violet to VioletSoft
  Icons.Default.Lock, Icons.Default.Star -> Success to SuccessSoft
  Icons.Default.Phone, Icons.Default.Notifications -> Sky to SkySoft
  Icons.Default.Favorite, Icons.Default.Warning -> Danger to DangerSoft
  Icons.Default.ShoppingCart, Icons.Default.Share -> Violet to VioletSoft
  else -> Teal to BrandSoft
}
