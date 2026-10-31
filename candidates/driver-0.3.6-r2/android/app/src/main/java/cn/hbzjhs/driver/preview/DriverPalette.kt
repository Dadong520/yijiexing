package cn.hbzjhs.driver.preview

import androidx.compose.ui.graphics.Color

// CCUI-20261029-01 shared visual tokens; presentation only.
val GreenStrong = Color(0xFF005F56)
val ControlOutline = Color(0xFF71818C)
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
// RIDEC-20261031-01, shared with the passenger app: white surfaces, hairlines instead of shadows,
// neutral fills for secondary actions and monochrome list icons.
/** Card edges and list dividers. */
val Hairline = Color(0xFFEDF0F2)
/** Neutral fill for secondary buttons, segmented tracks and icon wells. */
val SurfaceMuted = Color(0xFFF2F4F6)
/** Monochrome list icons, so colour is left for state and the one primary action. */
val IconTone = Color(0xFF5B6773)
/** Disabled text that stays readable on [SurfaceMuted]. */
val DisabledText = Color(0xFF6B7682)

/** Module accent for menu/tile icons: tint to soft background. */
fun moduleAccent(icon: String): Pair<Color, Color> = when (icon) {
    "location", "navigation", "car", "orders", "doc", "scan" -> Sky to SkySoft
    "wallet", "card", "gift", "chart" -> Warm to WarmSoft
    "settings", "support", "voice", "bell", "share", "mail" -> Violet to VioletSoft
    "face", "tool", "calendar", "info" -> Sky to SkySoft
    "star" -> Warm to WarmSoft
    "lock", "thumb" -> Success to SuccessSoft
    "warning", "exit" -> Danger to DangerSoft
    else -> Green to Pale
}
