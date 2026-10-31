package cn.hbzjhs.driver.preview

import java.net.URLEncoder

/** Cloud demo order coordinates, MapLibre selections and Android GPS are WGS84. */
internal data class Wgs84Point(val latitude: Double, val longitude: Double)

internal fun wgs84Point(latitude: Double?, longitude: Double?): Wgs84Point? =
    if (
        latitude != null &&
            longitude != null &&
            latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)
    )
        Wgs84Point(latitude, longitude)
    else null

internal enum class NavigationStop(val title: String) {
    PICKUP("上车点"),
    WAYPOINT("下一途经点"),
    DESTINATION("行程终点"),
}

internal data class NavigationTarget(
    val name: String,
    val point: Wgs84Point?,
    val stop: NavigationStop,
)

internal fun DriverOrder.navigationTarget(): NavigationTarget? {
    if (stage == Stage.CANCELLED) return null
    val nextWaypoint = if (stage == Stage.TRIP) pendingWaypoints.firstOrNull() else null
    val stop: NavigationStop
    val name: String
    val point: Wgs84Point?
    if (nextWaypoint != null) {
        stop = NavigationStop.WAYPOINT
        name = nextWaypoint.name
        point = wgs84Point(nextWaypoint.latitude, nextWaypoint.longitude)
    } else if (stage in setOf(Stage.TRIP, Stage.WAIT_PAY, Stage.DONE)) {
        stop = NavigationStop.DESTINATION
        name = destination
        point = wgs84Point(destinationLat, destinationLng)
    } else {
        stop = NavigationStop.PICKUP
        name = origin
        point = wgs84Point(originLat, originLng)
    }
    if (name.isBlank() && point == null) return null
    return NavigationTarget(name.ifBlank { stop.title }, point, stop)
}

/** Values are raw text. Encoding happens here once, never before calling this function. */
private fun navigationQuery(base: String, vararg values: Pair<String, String>): String =
    base +
        "?" +
        values.joinToString("&") { (key, value) ->
            key + "=" + URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        }

/**
 * Legacy external-navigation URI builders, kept for their pure contract tests only. The app no longer
 * launches external maps, browsers or geo: intents; navigation runs in-app (see InAppNavigation.kt).
 */
internal object NavigationContract {
    const val AMAP_PACKAGE = "com.autonavi.minimap"
    private const val SOURCE = "宜捷行云端体验版"

    fun amapUri(target: NavigationTarget): String {
        val point = target.point
        return if (point != null)
            navigationQuery(
                "androidamap://navi",
                "sourceApplication" to SOURCE,
                "poiname" to target.name,
                "lat" to point.latitude.toString(),
                "lon" to point.longitude.toString(),
                // AMap applies its documented WGS84 -> GCJ02 offset once. No local conversion.
                "dev" to "1",
                "style" to "0",
            )
        else
            navigationQuery(
                "androidamap://poi",
                "sourceApplication" to SOURCE,
                "keywords" to target.name,
                "dev" to "1",
            )
    }

    // A generic geo label is interpreted differently by map apps. Search by raw name instead.
    // 0,0 is the Android geo search URI's placeholder, never the destination coordinate.
    fun systemSearchUri(target: NavigationTarget): String =
        navigationQuery("geo:0,0", "q" to target.name)

    fun webUri(target: NavigationTarget): String {
        val point = target.point
        return if (point != null)
            navigationQuery(
                "https://uri.amap.com/marker",
                "position" to "${point.longitude},${point.latitude}",
                "name" to target.name,
                "coordinate" to "wgs84",
                "src" to SOURCE,
                "callnative" to "0",
            )
        else
            navigationQuery(
                "https://uri.amap.com/search",
                "keyword" to target.name,
                "src" to SOURCE,
                "callnative" to "0",
            )
    }
}
