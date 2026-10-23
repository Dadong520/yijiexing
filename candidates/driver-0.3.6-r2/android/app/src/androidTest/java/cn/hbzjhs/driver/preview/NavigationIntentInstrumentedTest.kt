package cn.hbzjhs.driver.preview

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

/** Real Android Uri/Intent parsing; this does not claim the third-party AMap UI was tested. */
@Suppress("DEPRECATION")
class NavigationIntentInstrumentedTest {
    @Test
    fun testAmapIntentKeepsUnicodeAndReservedSymbolsAsOnePoiName() {
        listOf("宜昌东站", "接驾 A&B #1+P", "100% 停车场 🚘 (南)/出口?").forEach { name ->
            val target =
                NavigationTarget(name, Wgs84Point(30.6665, 111.3676), NavigationStop.PICKUP)
            val intent = amapNavigationIntent(target)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("com.autonavi.minimap", intent.`package`)
            assertTrue(intent.categories!!.contains(Intent.CATEGORY_DEFAULT))
            val data = intent.data!!
            assertEquals("androidamap", data.scheme)
            assertEquals("navi", data.host)
            assertEquals(name, data.getQueryParameter("poiname"))
            assertEquals("30.6665", data.getQueryParameter("lat"))
            assertEquals("111.3676", data.getQueryParameter("lon"))
            assertEquals("1", data.getQueryParameter("dev"))
            assertNull(data.fragment)
            assertEquals(6, data.queryParameterNames.size)
            // Verify Android Intent serialization does not introduce a second encoding layer.
            val roundTrip =
                Intent.parseUri(intent.toUri(Intent.URI_INTENT_SCHEME), Intent.URI_INTENT_SCHEME)
            assertEquals(name, roundTrip.data!!.getQueryParameter("poiname"))
        }
    }

    @Test
    fun testUnknownWaypointUsesNameSearchAndDoesNotSkipToDestination() {
        val order =
            DriverOrder(
                id = "ANDROID-NAV",
                kind = OrderKind.STANDARD,
                origin = "上车点",
                destination = "最终终点",
                fare = 100L,
                distance = "",
                mileage = "",
                time = "",
                stage = Stage.TRIP,
                destinationLat = 30.7,
                destinationLng = 111.4,
                waypoints = listOf(DriverWaypoint(0, "已到"), DriverWaypoint(1, "未到 A&B #1+P")),
                arrivedWaypoints = setOf(0),
            )
        val target = order.navigationTarget()!!
        val data = amapNavigationIntent(target).data!!
        assertEquals("poi", data.host)
        assertEquals("未到 A&B #1+P", data.getQueryParameter("keywords"))
        assertNull(data.getQueryParameter("lat"))
        assertNull(data.getQueryParameter("lon"))
        assertFalse(data.toString().contains("最终终点"))
    }

    @Test
    fun testWebFallbackDeclaresCoordinateSystemAndKeepsNameSeparate() {
        val name = "行程终点 A&B #1+P"
        val target = NavigationTarget(name, Wgs84Point(30.7, 111.4), NavigationStop.DESTINATION)
        val intent = webNavigationIntent(target)
        val uri = intent.data!!
        assertEquals("https", uri.scheme)
        assertEquals("uri.amap.com", uri.host)
        assertEquals("/marker", uri.path)
        assertEquals("111.4,30.7", uri.getQueryParameter("position"))
        assertEquals(name, uri.getQueryParameter("name"))
        assertEquals("wgs84", uri.getQueryParameter("coordinate"))
        assertEquals("0", uri.getQueryParameter("callnative"))
        assertTrue(intent.categories!!.contains(Intent.CATEGORY_BROWSABLE))
        assertNull(uri.fragment)
        assertNull(intent.`package`)
    }

    @Test
    fun testGenericFallbackIsOnlyANameSearchWithoutCoordinateLabel() {
        val name = "无坐标 A&B #1+P"
        val target = NavigationTarget(name, null, NavigationStop.WAYPOINT)
        val intent = systemNavigationSearchIntent(target)
        val uri = intent.data!!
        assertEquals("geo", uri.scheme)
        assertEquals(name, Uri.decode(uri.encodedSchemeSpecificPart.substringAfter("?q=")))
        assertFalse(uri.toString().contains("("))
        assertFalse(uri.toString().contains(")"))
        assertNull(intent.`package`)
        val web = webNavigationIntent(target).data!!
        assertEquals("/search", web.path)
        assertEquals(name, web.getQueryParameter("keyword"))
        assertNull(web.getQueryParameter("position"))
    }
}
