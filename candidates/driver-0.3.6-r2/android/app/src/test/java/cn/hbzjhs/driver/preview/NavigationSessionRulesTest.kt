package cn.hbzjhs.driver.preview

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSessionRulesTest {
    private val a = 30.6921 to 111.2865
    private val b = 30.7100 to 111.3000
    private val c = 30.7300 to 111.3200

    private fun order(
        stage: Stage,
        waypoints: List<DriverWaypoint> = emptyList(),
        arrived: Set<Int> = emptySet(),
        destination: Pair<Double, Double> = c,
        destinationPending: Boolean = false,
        participation: String = "",
    ) = DriverOrder(
        id = "o-1", kind = OrderKind.STANDARD, origin = "A 站", destination = "终点", fare = 1000,
        distance = "1", mileage = "5", time = "", stage = stage,
        originLat = a.first, originLng = a.second,
        destinationLat = destination.first, destinationLng = destination.second,
        waypoints = waypoints, arrivedWaypoints = arrived,
        destinationPending = destinationPending, driverParticipationStage = participation,
    )

    private fun wp(index: Int, name: String, point: Pair<Double, Double>?) =
        DriverWaypoint(index, name, latitude = point?.first, longitude = point?.second)

    private fun go(decision: LegDecision): NavigationLeg = (decision as LegDecision.Go).leg

    @Test
    fun pickupStagesHeadToOriginIncludingWaitingReservation() {
        for (stage in listOf(Stage.RESERVED, Stage.PICKUP, Stage.ARRIVED)) {
            val leg = go(NavigationSessionRules.legFor(order(stage, listOf(wp(0, "B", b)))))
            assertEquals(NavigationStop.PICKUP, leg.stop)
            assertEquals(a.first, leg.point.latitude, 0.0)
        }
    }

    @Test
    fun tripFollowsServerAcknowledgedWaypointsByOriginalIndexThenDestination() {
        val stops = listOf(wp(0, "B", b), wp(1, "C2", c))
        val first = go(NavigationSessionRules.legFor(order(Stage.TRIP, stops)))
        assertEquals(NavigationStop.WAYPOINT, first.stop)
        assertEquals(0, first.waypointIndex)
        // An SDK arrival changes nothing; only the server's arrivedWaypoints moves the leg on.
        val second = go(NavigationSessionRules.legFor(order(Stage.TRIP, stops, arrived = setOf(0))))
        assertEquals(1, second.waypointIndex)
        assertNotEquals(first.key, second.key)
        val last = go(NavigationSessionRules.legFor(order(Stage.TRIP, stops, arrived = setOf(0, 1))))
        assertEquals(NavigationStop.DESTINATION, last.stop)
        assertNull(last.waypointIndex)
    }

    @Test
    fun outOfOrderAcknowledgementStillTargetsFirstPendingOriginalIndex() {
        val stops = listOf(wp(0, "B", b), wp(1, "C2", c))
        val leg = go(NavigationSessionRules.legFor(order(Stage.TRIP, stops, arrived = setOf(1))))
        assertEquals(0, leg.waypointIndex)
        assertEquals("B", leg.name)
    }

    @Test
    fun roundTripToTheStartIsTwoSeparateLegs() {
        val stops = listOf(wp(0, "B", b))
        val out = go(NavigationSessionRules.legFor(order(Stage.TRIP, stops, destination = a)))
        val back = go(NavigationSessionRules.legFor(order(Stage.TRIP, stops, arrived = setOf(0), destination = a)))
        assertEquals(b.first, out.point.latitude, 0.0)
        assertEquals(NavigationStop.DESTINATION, back.stop)
        assertEquals(a.first, back.point.latitude, 0.0)
        assertNotEquals(out.key, back.key)
    }

    @Test
    fun missingPointsAreUnavailableWithoutSkippingAhead() {
        val noCoordinates = NavigationSessionRules.legFor(order(Stage.TRIP, listOf(wp(0, "B", null))))
        assertTrue(noCoordinates is LegDecision.Unavailable)
        assertFalse((noCoordinates as LegDecision.Unavailable).exit)
        val pending = NavigationSessionRules.legFor(order(Stage.TRIP, destinationPending = true))
        assertTrue(pending is LegDecision.Unavailable && !pending.exit)
        val zero = NavigationSessionRules.legFor(order(Stage.TRIP, destination = 0.0 to 0.0))
        assertTrue(zero is LegDecision.Unavailable)
    }

    @Test
    fun endedOrdersExitAndLeavingThePageEndsSilently() {
        val owner = NavigationOwner("d-1", "o-1")
        for (stage in listOf(Stage.WAIT_PAY, Stage.DONE, Stage.CANCELLED, Stage.AVAILABLE)) {
            val reason = NavigationSessionRules.exitReason(owner, "d-1", true, order(stage), "detail:o-1")
            assertTrue(stage.name, !reason.isNullOrBlank())
        }
        assertNotNull(NavigationSessionRules.exitReason(owner, "d-1", true, order(Stage.TRIP, participation = "CANCELLED"), "detail:o-1"))
        assertNotNull(NavigationSessionRules.exitReason(owner, "d-1", true, null, "detail:o-1"))
        assertNotNull(NavigationSessionRules.exitReason(owner, "d-2", true, order(Stage.TRIP), "detail:o-1"))
        assertNotNull(NavigationSessionRules.exitReason(owner, "d-1", false, order(Stage.TRIP), "detail:o-1"))
        assertEquals("", NavigationSessionRules.exitReason(owner, "d-1", true, order(Stage.TRIP), "root"))
        // A missing next point keeps the session (and its business area) open with a visible message.
        assertNull(NavigationSessionRules.exitReason(owner, "d-1", true, order(Stage.TRIP, destinationPending = true), "detail:o-1"))
        assertNull(NavigationSessionRules.exitReason(owner, "d-1", true, order(Stage.ARRIVED), "detail:o-1"))
    }

    @Test
    fun stopRowsKeepEveryWaypointInArrayOrderWithProgress() {
        val rows = NavigationSessionRules.stopRows(
            order(Stage.TRIP, listOf(wp(0, "B", b), wp(1, "C2", c), wp(2, "D", null)), arrived = setOf(1))
        )
        assertEquals(listOf("起", "途1", "途2", "途3", "终"), rows.map { it.label })
        assertEquals(listOf(null, 0, 1, 2, null), rows.map { it.waypointIndex })
        assertEquals(
            listOf(StopProgress.DONE, StopProgress.NEXT, StopProgress.DONE, StopProgress.PENDING, StopProgress.PENDING),
            rows.map { it.progress },
        )
        val pickup = NavigationSessionRules.stopRows(order(Stage.PICKUP, listOf(wp(0, "B", b))))
        assertEquals(StopProgress.NEXT, pickup.first().progress)
        assertTrue(pickup.drop(1).all { it.progress == StopProgress.PENDING })
    }

    /**
     * One leg session as the controller wires it: its own gate and its own listener object. The
     * listener is the real SDK adapter class; only its deliver sink is replaced by a recorder.
     */
    private class LegSession {
        val gate = LegRouteGate()
        val events = mutableListOf<LegEvent>()
        val listener = LegRouteListener(gate) { events += it }
        val decisions get() = events.filterIsInstance<LegEvent.Decided>().map { it.decision }
    }

    private val modernResult: com.amap.api.navi.model.AMapCalcRouteResult? = null

    @Test
    fun switchingToANearbyLegBeforeTheOldRequestFinishesNeverStartsTheOldRoute() {
        // Two real waypoints ~100 m apart: distance can not tell their routes apart.
        val legA = go(NavigationSessionRules.legFor(order(Stage.TRIP, listOf(wp(0, "B", b), wp(1, "B2", b.first + 0.0009 to b.second)))))
        val legB = go(NavigationSessionRules.legFor(order(Stage.TRIP, listOf(wp(0, "B", b), wp(1, "B2", b.first + 0.0009 to b.second)), arrived = setOf(0))))
        assertTrue(NavigationSessionRules.distanceMeters(legA.point.latitude, legA.point.longitude, legB.point.latitude, legB.point.longitude) < 150)
        val sessionA = LegSession()
        assertTrue(sessionA.gate.claimRequest())
        sessionA.gate.release() // leg switch: A's listener removed, engine destroyed, gate released
        val sessionB = LegSession()
        assertTrue(sessionB.gate.claimRequest())
        // A's late results (both styles, success and failure, any order) reach only A's released gate.
        sessionA.listener.onCalculateRouteSuccess(intArrayOf(1))
        sessionA.listener.onCalculateRouteSuccess(modernResult)
        sessionA.listener.onCalculateRouteFailure(modernResult)
        sessionA.listener.onCalculateRouteFailure(3)
        sessionA.listener.onInitNaviSuccess()
        assertTrue(sessionA.events.isEmpty())
        assertTrue(sessionB.events.isEmpty())
        assertEquals(LegRouteGate.Phase.REQUESTED, sessionB.gate.phase)
        sessionB.listener.onCalculateRouteSuccess(modernResult)
        assertEquals(listOf(LegRouteGate.Decision.Start(null)), sessionB.decisions)
    }

    @Test
    fun eachRequestDecidesOnceWhicheverCallbackStyleArrivesFirst() {
        val legacyFirst = LegSession()
        legacyFirst.gate.claimRequest()
        legacyFirst.listener.onCalculateRouteSuccess(intArrayOf(5))
        assertTrue(legacyFirst.events.isEmpty())
        legacyFirst.listener.onCalculateRouteSuccess(modernResult)
        assertEquals(1, legacyFirst.decisions.size)

        val modernFirst = LegSession()
        modernFirst.gate.claimRequest()
        modernFirst.listener.onCalculateRouteSuccess(modernResult)
        modernFirst.listener.onCalculateRouteSuccess(intArrayOf(5))
        modernFirst.listener.onCalculateRouteFailure(modernResult)
        modernFirst.listener.onCalculateRouteFailure(9)
        assertEquals(listOf(LegRouteGate.Decision.Start(null)), modernFirst.decisions)
        assertEquals(LegRouteGate.Phase.NAVIGATING, modernFirst.gate.phase)
    }

    @Test
    fun lateResultsNeverOverrideTheFirstDecision() {
        val failedFirst = LegSession()
        failedFirst.gate.claimRequest()
        failedFirst.listener.onCalculateRouteFailure(modernResult)
        failedFirst.listener.onCalculateRouteSuccess(modernResult)
        assertEquals(1, failedFirst.decisions.size)
        assertTrue(failedFirst.decisions.single() is LegRouteGate.Decision.Fail)

        val timedOut = LegSession()
        timedOut.gate.claimRequest()
        assertTrue(timedOut.gate.timedOut() is LegRouteGate.Decision.Fail)
        timedOut.listener.onCalculateRouteSuccess(modernResult)
        assertTrue(timedOut.events.isEmpty())

        // An old failure can not turn a started leg into a failure either.
        val started = LegSession()
        started.gate.claimRequest()
        started.listener.onCalculateRouteSuccess(modernResult)
        started.listener.onCalculateRouteFailure(modernResult)
        assertEquals(LegRouteGate.Phase.NAVIGATING, started.gate.phase)
    }

    @Test
    fun sdkReroutesWhileNavigatingAreNotNewRequests() {
        val s = LegSession()
        s.listener.onCalculateRouteSuccess(modernResult) // nothing claimed yet: ignored
        assertTrue(s.events.isEmpty())
        assertTrue(s.gate.claimRequest())
        assertFalse(s.gate.claimRequest()) // engine-ready twice or a fallback timer: still one request
        s.listener.onCalculateRouteSuccess(modernResult)
        s.listener.onReCalculateRouteForYaw()
        s.listener.onCalculateRouteSuccess(modernResult)
        s.listener.onReCalculateRouteForTrafficJam()
        s.listener.onCalculateRouteFailure(modernResult)
        assertEquals(1, s.decisions.size)
    }

    @Test
    fun repeatedRetriesAndReleaseOnAccountSwitchKeepOnlyTheNewestSession() {
        val sessions = List(3) { LegSession() }
        sessions.forEach { it.gate.claimRequest() }
        sessions[0].gate.release()
        sessions[1].gate.release()
        sessions.forEach { it.listener.onCalculateRouteSuccess(modernResult) }
        assertTrue(sessions[0].events.isEmpty())
        assertTrue(sessions[1].events.isEmpty())
        assertEquals(1, sessions[2].decisions.size)
        // Account switch / leaving: the live session is released and goes silent too.
        sessions[2].gate.release()
        sessions[2].listener.onArriveDestination()
        sessions[2].listener.onInitNaviFailure()
        assertEquals(1, sessions[2].events.size)
        assertFalse(sessions[2].gate.claimRequest())
        assertFalse(sessions[2].gate.startFailed())
    }

    @Test
    fun arrivalIsOnlyAHintWhileNavigating() {
        val s = LegSession()
        s.listener.onArriveDestination()
        s.listener.onArrivedWayPoint(0)
        assertTrue(s.events.isEmpty())
        s.gate.claimRequest()
        s.listener.onCalculateRouteSuccess(modernResult)
        s.listener.onArrivedWayPoint(0)
        s.listener.onArriveDestination()
        assertEquals(LegEvent.NearTarget, s.events.last())
        assertEquals(2, s.events.size)
    }

    @Test
    fun routeStartUsesOnlyFreshAccuratePhoneFixes() {
        val now = 1_000_000_000L
        assertNotNull(NavigationSessionRules.reliableStart(DriverPosition(a.first, a.second, 30f, now - 60_000), now))
        assertNotNull(NavigationSessionRules.reliableStart(DriverPosition(a.first, a.second, 100f, now), now))
        assertNull(NavigationSessionRules.reliableStart(DriverPosition(a.first, a.second, 30f, now - 121_000), now))
        assertNull(NavigationSessionRules.reliableStart(DriverPosition(a.first, a.second, 150f, now), now))
        assertNull(NavigationSessionRules.reliableStart(DriverPosition(a.first, a.second, 0f, now), now))
        assertNull(NavigationSessionRules.reliableStart(DriverPosition(a.first, a.second, -5f, now), now))
        assertNull(NavigationSessionRules.reliableStart(DriverPosition(a.first, a.second, Float.NaN, now), now))
        assertNull(NavigationSessionRules.reliableStart(DriverPosition(a.first, a.second, 30f, now + 5_000), now))
        assertNull(NavigationSessionRules.reliableStart(DriverPosition(Double.NaN, a.second, 30f, now), now))
        assertNull(NavigationSessionRules.reliableStart(DriverPosition(0.0, 0.0, 5f, now), now))
        assertNull(NavigationSessionRules.reliableStart(null, now))
    }

    @Test
    fun plannedRouteMustEndAtThisLeg() {
        assertTrue(NavigationSessionRules.endpointMatches(b.first, b.second, b.first + 0.002, b.second))
        assertFalse(NavigationSessionRules.endpointMatches(a.first, a.second, c.first, c.second))
        assertFalse(NavigationSessionRules.endpointMatches(Double.NaN, a.second, a.first, a.second))
    }

    @Test
    fun ownerRoundTripsAndRejectsMalformedValues() {
        val owner = NavigationOwner("d-1", "o-1")
        assertEquals(owner, NavigationOwner.decode(owner.encode()))
        assertNull(NavigationOwner.decode("only-one"))
        assertNull(NavigationOwner.decode("\no-1"))
        assertNull(NavigationOwner.decode(null))
    }

    @Test
    fun passengerContactIsBoundToOrderStageAndNeverPrinted() {
        val data = JSONObject().put("orderId", "o-1").put("mobile", "13900001111").put("name", "王")
            .put("contactMode", "DIRECT_DIAL").put("relayAvailable", false)
        val contact = PassengerContactRules.parse("o-1", data)
        assertEquals("13900001111", contact?.mobile)
        assertFalse(contact.toString().contains("13900001111"))
        assertNull(PassengerContactRules.parse("o-2", data))
        assertNull(PassengerContactRules.parse("o-1", JSONObject(data.toString()).put("contactMode", "RELAY")))
        for (bad in listOf("", "139****1111", "1390000111", "139000011112", "+8613900001111", "139 0000 1111", "23900001111", "１３９００００１１１１"))
            assertNull(bad, PassengerContactRules.parse("o-1", JSONObject(data.toString()).put("mobile", bad)))
        assertNotEquals(
            PassengerContactRules.scope("d-1", order(Stage.PICKUP)),
            PassengerContactRules.scope("d-1", order(Stage.ARRIVED)),
        )
        assertNull(PassengerContactRules.scope("d-1", order(Stage.WAIT_PAY)))
        assertNull(PassengerContactRules.scope("", order(Stage.TRIP)))
        assertNotEquals(
            PassengerContactRules.failureMessage(409, "PASSENGER_CONTACT_UNAVAILABLE"),
            PassengerContactRules.failureMessage(409, "PASSENGER_CONTACT_INACTIVE"),
        )
    }
}
