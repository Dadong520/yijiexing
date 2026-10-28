package cn.hbzjhs.driver.preview

import androidx.compose.ui.graphics.Color

// CCUI-20261029-01 shared visual tokens; presentation only.
val GreenStrong = Color(0xFF005F56)
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

/** Module accent for menu/tile icons: tint to soft background. */
fun moduleAccent(icon: String): Pair<Color, Color> = when (icon) {
    "location", "navigation", "car", "orders", "doc", "scan" -> Sky to SkySoft
    "wallet", "card", "gift", "chart" -> Warm to WarmSoft
    "settings", "support", "voice", "bell" -> Violet to VioletSoft
    else -> Green to Pale
}
