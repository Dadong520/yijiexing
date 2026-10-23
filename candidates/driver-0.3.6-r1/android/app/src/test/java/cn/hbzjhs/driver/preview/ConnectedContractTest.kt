package cn.hbzjhs.driver.preview

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConnectedContractTest {
    @Test
    fun waypointIndexesAndArrivalsRemainAuthoritative() {
        val row =
            JSONObject()
                .put("id", "LOCAL-STOPS")
                .put("kind", "STANDARD")
                .put("origin", "A")
                .put("destination", "B")
                .put("fare", 100)
                .put("stage", "TRIP")
                .put(
                    "waypoints",
                    JSONArray()
                        .put("第一途经点")
                        .put(
                            JSONObject()
                                .put("name", "第二途经点")
                                .put("latitude", 30.67)
                                .put("longitude", 111.34)
                        ),
                )
                .put("arrivedWaypoints", JSONArray().put(0).put(0).put(99).put(-1))
        val state =
            ConnectedStore.parseDriverState(JSONObject().put("orders", JSONArray().put(row)))
        val order = state.orders.single()
        assertEquals(setOf(0), order.arrivedWaypoints)
        assertEquals(1, order.pendingWaypoints.single().index)
        assertEquals("第二途经点", order.pendingWaypoints.single().name)
        assertEquals(30.67, order.pendingWaypoints.single().latitude!!, 0.00001)
    }

    @Test
    fun moneyUsesExactCents() {
        assertEquals(9550L, DriverRules.cents("95.50"))
        assertEquals(100L, DriverRules.cents("1"))
        assertEquals("95.50", money(9550L))
        assertEquals(500000L, DriverRules.cents("5000.00"))
        listOf("0", "0.99", "5000.01", "1.001", "-2", "NaN", "1e2").forEach {
            assertTrue("Reject $it", runCatching { DriverRules.cents(it) }.isFailure)
        }
    }

    @Test
    fun emptyStateDoesNotInventTripsOrMoney() {
        val state = ConnectedStore.emptyState()
        assertFalse(state.loggedIn)
        assertTrue(state.orders.isEmpty())
        assertTrue(state.entries.isEmpty())
        assertEquals(0L, state.balance)
        assertEquals(0L, state.todayIncome)
    }

    @Test
    fun serverMoneyAndCoordinatesAreRetained() {
        val order =
            JSONObject()
                .put("id", "LOCAL-1")
                .put("kind", "STANDARD")
                .put("origin", "A")
                .put("destination", "B")
                .put("fare", 9550)
                .put("stage", "WAIT_PAY")
                .put("paid", false)
                .put("credited", false)
                .put("originLat", 30.1)
                .put("originLng", 111.2)
                .put("destinationLat", 30.3)
                .put("destinationLng", 111.4)
        val json =
            JSONObject()
                .put("orders", JSONArray().put(order))
                .put("balance", 123456)
                .put("todayIncome", 9550)
                .put("todayCompleted", 3)
                .put("loggedIn", true)
                .put(
                    "entries",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("title", "行程收入")
                                .put("amount", 9550)
                                .put("note", "LOCAL-1")
                        ),
                )
        val state = ConnectedStore.parseDriverState(json)
        assertEquals(123456L, state.balance)
        assertEquals(9550L, state.todayIncome)
        assertEquals(9550L, state.entries.single().amount)
        assertEquals(9550L, state.orders.single().fare)
        assertEquals(Stage.WAIT_PAY, state.orders.single().stage)
        assertFalse(state.orders.single().paid)
        assertEquals(30.1, state.orders.single().originLat, 0.00001)
    }

    @Test
    fun malformedStagesFailInsteadOfBecomingCompleted() {
        val row =
            JSONObject()
                .put("id", "LOCAL-2")
                .put("kind", "STANDARD")
                .put("origin", "A")
                .put("destination", "B")
                .put("fare", 100)
                .put("stage", "UNRECOGNIZED")
        assertTrue(
            runCatching {
                    ConnectedStore.parseDriverState(
                        JSONObject().put("orders", JSONArray().put(row))
                    )
                }
                .isFailure
        )
    }

    @Test
    fun endpointIsConfinedToExactCloudBuildOrigin() {
        val expected = "https://isolated-yij-demo.example.test"
        assertEquals(expected, validateCloudEndpoint("$expected/", expected))
        listOf(
                "http://isolated-yij-demo.example.test",
                "https://other-service.run.app",
                "https://isolated-yij-demo.example.test.evil.test",
                "https://evil.test@isolated-yij-demo.example.test",
                "$expected:443",
                "$expected/api",
                "$expected?next=evil",
                "$expected#x",
                "http://127.0.0.1:18765",
                "https://127.0.0.1",
                "https://192.168.1.2",
                "https://ISOLATED-YIJ-DEMO.EXAMPLE.TEST",
            )
            .forEach {
                assertTrue(
                    "Reject $it",
                    runCatching { validateCloudEndpoint(it, expected) }.isFailure,
                )
            }
        assertTrue(runCatching { validateCloudEndpoint(expected, "") }.isFailure)
    }

    @Test
    fun accessCodeMustBeCompleteAndCannotContainWhitespaceOrHeaderControls() {
        assertEquals("sample-code-12345", validateDemoAccess("  sample-code-12345  "))
        listOf(
                "",
                "too-short",
                "contains space-123",
                "code-123456789\r\nInjected: yes",
                "x".repeat(257),
                "中文访问码测试123456789",
            )
            .forEach { assertTrue(runCatching { validateDemoAccess(it) }.isFailure) }
    }
}
