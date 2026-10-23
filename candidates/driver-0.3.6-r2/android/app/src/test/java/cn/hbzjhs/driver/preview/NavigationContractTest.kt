package cn.hbzjhs.driver.preview

import java.net.URI
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NavigationContractTest {
    private fun order(stage: Stage = Stage.TRIP) =
        DriverOrder(
            id = "NAV-TEST",
            kind = OrderKind.STANDARD,
            origin = "宜昌东站 上车点 A&B",
            destination = "万达广场 #1入口+P",
            fare = 100L,
            distance = "",
            mileage = "",
            time = "",
            stage = stage,
            originLat = 30.6665,
            originLng = 111.3676,
            destinationLat = 30.6967,
            destinationLng = 111.2858,
        )

    private fun query(uri: String): Map<String, String> =
        URI(uri).rawQuery.split("&").associate {
            val pair = it.split("=", limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }

    @Test
    fun amapUsesSeparateNameAndWgs84WithoutClientOffset() {
        val target = order().navigationTarget()!!
        val uri = NavigationContract.amapUri(target)
        val parameters = query(uri)
        assertEquals("androidamap", URI(uri).scheme)
        assertEquals("navi", URI(uri).host)
        assertEquals(target.name, parameters["poiname"])
        assertEquals("30.6967", parameters["lat"])
        assertEquals("111.2858", parameters["lon"])
        assertEquals("1", parameters["dev"])
        assertFalse(parameters.containsKey("q"))
        assertFalse(uri.contains("%25E"))
    }

    @Test
    fun namesRoundTripChineseSpacesSymbolsAndLiteralPercentExactlyOnce() {
        listOf("宜昌东站", "上车点 A&B #1+P", "上海虹桥(南) / 出口?", "100% 停车场 🚘").forEach { name ->
            val target = NavigationTarget(name, Wgs84Point(30.1, 111.2), NavigationStop.DESTINATION)
            val uri = NavigationContract.amapUri(target)
            assertEquals(name, query(uri)["poiname"])
            assertNull(URI(uri).fragment)
            assertFalse(uri.contains(" "))
            assertEquals(name, query(NavigationContract.webUri(target))["name"])
        }
    }

    @Test
    fun preTripStagesNavigateToPickupInsteadOfFinalDestination() {
        listOf(
                Stage.AVAILABLE,
                Stage.BID_SENT,
                Stage.PREPAY,
                Stage.RESERVED,
                Stage.PICKUP,
                Stage.ARRIVED,
            )
            .forEach { stage ->
                val target = order(stage).navigationTarget()!!
                assertEquals("Stage $stage", NavigationStop.PICKUP, target.stop)
                assertEquals(order().origin, target.name)
                assertEquals(Wgs84Point(30.6665, 111.3676), target.point)
            }
    }

    @Test
    fun tripNeverSkipsNextUnvisitedWaypointEvenIfItHasNoCoordinates() {
        val withStops =
            order()
                .copy(
                    waypoints =
                        listOf(
                            DriverWaypoint(0, "已到的途经点", latitude = 30.5, longitude = 111.3),
                            DriverWaypoint(1, "未到的途经点 A&B #1+P"),
                            DriverWaypoint(2, "后面的途经点", latitude = 30.7, longitude = 111.4),
                        ),
                    arrivedWaypoints = setOf(0),
                )
        val next = withStops.navigationTarget()!!
        assertEquals(NavigationStop.WAYPOINT, next.stop)
        assertEquals("未到的途经点 A&B #1+P", next.name)
        assertNull(next.point)
        val search = NavigationContract.amapUri(next)
        assertEquals("poi", URI(search).host)
        assertEquals(next.name, query(search)["keywords"])
        assertFalse(query(search).containsKey("lat"))
        assertFalse(query(search).containsKey("lon"))
        assertFalse(search.contains("0.0"))
        assertEquals(
            "后面的途经点",
            withStops.copy(arrivedWaypoints = setOf(0, 1)).navigationTarget()!!.name,
        )
        assertEquals(
            NavigationStop.DESTINATION,
            withStops.copy(arrivedWaypoints = setOf(0, 1, 2)).navigationTarget()!!.stop,
        )
    }

    @Test
    fun invalidOrMissingCoordinatesSearchByNameAndNeverInventDestination() {
        listOf(
                Double.NaN to 111.2,
                30.1 to Double.NaN,
                91.0 to 111.2,
                30.1 to 181.0,
                Double.POSITIVE_INFINITY to 111.2,
                0.0 to 0.0,
            )
            .forEach { (lat, lng) ->
                val target =
                    order().copy(destinationLat = lat, destinationLng = lng).navigationTarget()!!
                assertNull(target.point)
                assertEquals("poi", URI(NavigationContract.amapUri(target)).host)
                assertEquals(target.name, query(NavigationContract.amapUri(target))["keywords"])
                assertEquals(target.name, query(NavigationContract.webUri(target))["keyword"])
            }
        val row =
            JSONObject()
                .put("id", "MISSING")
                .put("kind", "STANDARD")
                .put("origin", "无坐标上车点")
                .put("destination", "无坐标终点")
                .put("fare", 100)
                .put("stage", "TRIP")
        val parsed =
            ConnectedStore.parseDriverState(JSONObject().put("orders", JSONArray().put(row)))
                .orders
                .single()
        assertTrue(parsed.originLat.isNaN())
        assertTrue(parsed.destinationLng.isNaN())
        assertNull(parsed.navigationTarget()!!.point)
        assertNull(order().copy(destination = "", destinationLat = Double.NaN).navigationTarget())
    }

    @Test
    fun cancelledOrdersCannotNavigateAndFinishedOrdersUseDestination() {
        assertNull(order(Stage.CANCELLED).navigationTarget())
        listOf(Stage.WAIT_PAY, Stage.DONE).forEach { stage ->
            assertEquals(NavigationStop.DESTINATION, order(stage).navigationTarget()!!.stop)
        }
    }

    @Test
    fun webFallbackDeclaresWgs84AndCannotReopenNativeApp() {
        val target = order().navigationTarget()!!
        val uri = NavigationContract.webUri(target)
        val parameters = query(uri)
        assertEquals("https", URI(uri).scheme)
        assertEquals("uri.amap.com", URI(uri).host)
        assertEquals("/marker", URI(uri).path)
        assertEquals("111.2858,30.6967", parameters["position"])
        assertEquals("wgs84", parameters["coordinate"])
        assertEquals("0", parameters["callnative"])
        val system = NavigationContract.systemSearchUri(target)
        assertEquals("geo", URI(system).scheme)
        assertEquals(target.name, URLDecoder.decode(system.substringAfter("?q="), "UTF-8"))
        assertFalse(system.contains("(") || system.contains(")"))
    }
}
