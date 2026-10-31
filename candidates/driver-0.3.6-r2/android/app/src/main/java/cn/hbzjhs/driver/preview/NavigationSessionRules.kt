package cn.hbzjhs.driver.preview

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One navigation leg the driver may follow right now, derived only from the server-confirmed order.
 * Waypoint legs carry the original zero-based array index (never the 1-based display number).
 */
internal data class NavigationLeg(
    val orderId: String,
    val stop: NavigationStop,
    val waypointIndex: Int?,
    val name: String,
    val point: Wgs84Point,
) {
    /** Changes whenever the driver must head somewhere else; a new key means a new route request. */
    val key: String
        get() = "$orderId|${stop.name}|${waypointIndex ?: -1}|${point.latitude},${point.longitude}"
}

internal sealed class LegDecision {
    data class Go(val leg: NavigationLeg) : LegDecision()

    /** [exit] is true when the order itself no longer allows navigation (not just a missing point). */
    data class Unavailable(val reason: String, val exit: Boolean) : LegDecision()
}

/** The single in-app navigation owner: the signed-in driver and one order. */
internal data class NavigationOwner(val account: String, val orderId: String) {
    fun encode(): String = "$account\n$orderId"

    companion object {
        fun decode(value: String?): NavigationOwner? {
            val parts = value?.split('\n') ?: return null
            if (parts.size != 2 || parts.any { it.isBlank() }) return null
            return NavigationOwner(parts[0], parts[1])
        }
    }
}

internal enum class StopProgress { DONE, NEXT, PENDING }

internal data class StopRow(
    val label: String,
    val name: String,
    /** Original zero-based waypoint index; null for pickup and destination. */
    val waypointIndex: Int?,
    val progress: StopProgress,
)

internal object NavigationSessionRules {
    private val NAVIGABLE = setOf(Stage.RESERVED, Stage.PICKUP, Stage.ARRIVED, Stage.TRIP)

    fun navigable(order: DriverOrder?): Boolean =
        order != null && !order.participationCancelled && order.stage in NAVIGABLE

    /**
     * Pickup stages head to the origin; TRIP heads to the first waypoint the server has not yet
     * acknowledged (original array order), then the destination. Same-place legs (A→B→A) are
     * therefore separate segments and never need a closed-loop SDK plan.
     */
    fun legFor(order: DriverOrder?): LegDecision {
        if (order == null) return LegDecision.Unavailable("订单已不在当前账号，导航已结束", true)
        if (!navigable(order)) return LegDecision.Unavailable(endedReason(order), true)
        if (order.stage == Stage.TRIP) {
            val next = order.pendingWaypoints.firstOrNull()
            if (next != null) {
                val point = wgs84Point(next.latitude, next.longitude)
                    ?: return LegDecision.Unavailable("途经点「${next.name}」缺少坐标，暂不能导航。请按订单信息前往并确认到达。", false)
                return LegDecision.Go(NavigationLeg(order.id, NavigationStop.WAYPOINT, next.index, next.name, point))
            }
            if (order.destinationPending) return LegDecision.Unavailable("目的地待乘客补充，暂不能导航", false)
            val point = wgs84Point(order.destinationLat, order.destinationLng)
                ?: return LegDecision.Unavailable("行程终点缺少坐标，暂不能导航", false)
            return LegDecision.Go(
                NavigationLeg(order.id, NavigationStop.DESTINATION, null, order.destination.ifBlank { NavigationStop.DESTINATION.title }, point)
            )
        }
        val point = wgs84Point(order.originLat, order.originLng)
            ?: return LegDecision.Unavailable("上车点缺少坐标，暂不能导航", false)
        return LegDecision.Go(NavigationLeg(order.id, NavigationStop.PICKUP, null, order.origin.ifBlank { NavigationStop.PICKUP.title }, point))
    }

    private fun endedReason(order: DriverOrder): String =
        when {
            order.participationCancelled || order.stage == Stage.CANCELLED -> "订单已取消，导航已结束"
            order.stage == Stage.WAIT_PAY -> "行程已结束，导航已结束"
            order.stage == Stage.DONE -> "订单已完成，导航已结束"
            else -> "当前订单阶段不能导航"
        }

    /**
     * Why an open session must end now, or null while it may continue. An empty string ends the
     * session silently (the driver left the trip page themself).
     */
    fun exitReason(owner: NavigationOwner, account: String, loggedIn: Boolean, order: DriverOrder?, route: String): String? =
        when {
            !loggedIn || account != owner.account -> "账号已变化，导航已结束"
            route != "detail:${owner.orderId}" -> ""
            else -> (legFor(order) as? LegDecision.Unavailable)?.takeIf { it.exit }?.reason
        }

    /** Ordered stops for display: origin, every waypoint in original array order, destination. */
    fun stopRows(order: DriverOrder): List<StopRow> {
        val pickupDone = order.stage in setOf(Stage.TRIP, Stage.WAIT_PAY, Stage.DONE)
        val pickupNext = !order.participationCancelled && order.stage in setOf(Stage.RESERVED, Stage.PICKUP, Stage.ARRIVED)
        val nextWaypoint =
            if (order.stage == Stage.TRIP && !order.participationCancelled) order.pendingWaypoints.firstOrNull()?.index else null
        val rows = mutableListOf(
            StopRow("起", order.origin, null, if (pickupDone) StopProgress.DONE else if (pickupNext) StopProgress.NEXT else StopProgress.PENDING)
        )
        order.waypoints.forEachIndexed { position, point ->
            val progress = when {
                point.index in order.arrivedWaypoints -> StopProgress.DONE
                point.index == nextWaypoint -> StopProgress.NEXT
                else -> StopProgress.PENDING
            }
            rows += StopRow("途${position + 1}", point.name, point.index, progress)
        }
        val destinationProgress = when {
            order.stage in setOf(Stage.WAIT_PAY, Stage.DONE) -> StopProgress.DONE
            order.stage == Stage.TRIP && !order.participationCancelled && nextWaypoint == null -> StopProgress.NEXT
            else -> StopProgress.PENDING
        }
        rows += StopRow("终", if (order.destinationPending) "目的地待补充" else order.destination, null, destinationProgress)
        return rows
    }

    /** Short ordered route text for confirmations: one stop per line, waypoints in array order. */
    fun routeSummary(order: DriverOrder): String =
        stopRows(order).joinToString("\n") { "${it.label}：${it.name}" }

    /** A fresh, accurate phone fix may serve as the route start; anything else lets the SDK use its own GPS. */
    fun reliableStart(position: DriverPosition?, nowMillis: Long): Wgs84Point? {
        position ?: return null
        val age = nowMillis - position.time
        // Accuracy must be a real positive radius; 0, negative and NaN are "unknown", never "perfect".
        if (age < 0 || age > 120_000 || !(position.accuracy > 0f && position.accuracy <= 100f)) return null
        return wgs84Point(position.latitude, position.longitude)
    }

    /**
     * Road-snapping sanity check on this leg's own route (a POI off the road network ends nearby).
     * It is not request ownership evidence: ownership comes from the per-leg engine and [LegRouteGate].
     */
    fun endpointMatches(endLat: Double, endLng: Double, targetLat: Double, targetLng: Double, toleranceMeters: Double = 1000.0): Boolean {
        if (!endLat.isFinite() || !endLng.isFinite() || !targetLat.isFinite() || !targetLng.isFinite()) return false
        return distanceMeters(endLat, endLng, targetLat, targetLng) <= toleranceMeters
    }

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

/**
 * Route ownership for exactly one leg. Each leg (and each retry) gets its own gate, its own SDK
 * listener object and its own AMapNavi engine instance; the previous leg's listener is removed and
 * its engine destroyed before the next is created. A gate therefore never sees more than one
 * request of its own, so no request is ever overwritten while unfinished:
 *  - exactly one request may be claimed (CREATED -> REQUESTED);
 *  - only the first modern result while REQUESTED decides (start or fail); later results are the
 *    SDK's own reroutes while navigating, or late arrivals after a timeout/failure, and are ignored;
 *  - a released gate decides nothing, so a late callback of an old leg can never start anything.
 * Deprecated callback styles (int[] / int) are not routed here at all.
 */
internal class LegRouteGate {
    enum class Phase { CREATED, REQUESTED, NAVIGATING, FAILED, RELEASED }

    sealed class Decision {
        object Ignore : Decision()
        data class Start(val routeId: Int?) : Decision()
        data class Fail(val message: String) : Decision()
    }

    private var current = Phase.CREATED

    val phase: Phase
        @Synchronized get() = current

    val live: Boolean
        @Synchronized get() = current != Phase.RELEASED

    @Synchronized
    fun claimRequest(): Boolean = (current == Phase.CREATED).also { if (it) current = Phase.REQUESTED }

    /** The SDK refused to start the claimed request (or the target could not be converted). */
    @Synchronized
    fun requestRejected(): Boolean = (current == Phase.REQUESTED).also { if (it) current = Phase.FAILED }

    @Synchronized
    fun routeSucceeded(routeIds: IntArray?): Decision =
        if (current != Phase.REQUESTED) Decision.Ignore
        else {
            current = Phase.NAVIGATING
            Decision.Start(routeIds?.firstOrNull())
        }

    @Synchronized
    fun routeFailed(detail: String): Decision =
        if (current != Phase.REQUESTED) Decision.Ignore
        else {
            current = Phase.FAILED
            Decision.Fail("路线规划失败（$detail），请检查网络和定位后重试")
        }

    /** Liveness only: a result arriving after this is ignored, it never overrides the failure. */
    @Synchronized
    fun timedOut(): Decision =
        if (current != Phase.REQUESTED) Decision.Ignore
        else {
            current = Phase.FAILED
            Decision.Fail("路线规划超时，请检查网络和定位后重试")
        }

    @Synchronized
    fun engineFailed(): Decision =
        if (current != Phase.CREATED && current != Phase.REQUESTED) Decision.Ignore
        else {
            current = Phase.FAILED
            Decision.Fail("导航引擎初始化失败，请重试")
        }

    /** The route was accepted but guidance could not start (endpoint check or startNavi false). */
    @Synchronized
    fun startFailed(): Boolean = (current == Phase.NAVIGATING).also { if (it) current = Phase.FAILED }

    @Synchronized
    fun release() {
        current = Phase.RELEASED
    }
}
