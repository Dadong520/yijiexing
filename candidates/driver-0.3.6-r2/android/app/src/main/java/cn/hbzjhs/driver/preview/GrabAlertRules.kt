package cn.hbzjhs.driver.preview

import org.json.JSONObject

/** A new-order reminder lives this long from the moment it is first presented, on a monotonic clock. */
internal const val GRAB_ALERT_MS = 3000L

/** Kinds whose reminder offers a direct grab; BID keeps its quote entry and other kinds only open detail. */
internal val DIRECT_GRAB_KINDS = setOf("STANDARD", "RESERVED")

/**
 * One reminder/candidate identity: account + order + competition round (contract GRAB V1; empty for
 * kinds without competition). A new round of the same order is a different key and reminds again.
 */
data class GrabAlertKey(val account: String, val orderId: String, val round: String) {
    fun encode(): String = listOf(account, orderId, round).joinToString("\u001f")

    companion object {
        fun decode(value: String?): GrabAlertKey? {
            val parts = value?.split('\u001f') ?: return null
            if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank()) return null
            return GrabAlertKey(parts[0], parts[1], parts[2])
        }
    }
}

data class GrabAlert(
    val key: GrabAlertKey,
    val kind: String,
    val origin: String,
    val destination: String,
    val admittedAt: Long,
    /** Monotonic time of first presentation; null while still queued. */
    val shownAt: Long? = null,
    /** Ordered waypoint names (origin → these → destination). */
    val waypoints: List<String> = emptyList(),
) {
    /** Direct grab only for STANDARD/RESERVED that carry a competition round. */
    val directGrab: Boolean get() = kind in DIRECT_GRAB_KINDS && key.round.isNotBlank()
    val bid: Boolean get() = kind == "BID"
}

internal fun waypointNames(order: JSONObject): List<String> {
    val raw = order.optJSONArray("waypoints") ?: return emptyList()
    return (0 until raw.length()).map { index ->
        val item = raw.opt(index)
        val point = item as? JSONObject
        listOf("waypointName", "name", "address", "title")
            .firstNotNullOfOrNull { k -> point?.optString(k, "")?.takeIf { it.isNotBlank() && it != "null" } }
            ?: (item as? String)?.takeIf { it.isNotBlank() }
            ?: "途经点 ${index + 1}"
    }
}

internal fun grabAlertFor(account: String, order: JSONObject, now: Long): GrabAlert? {
    val id = order.optString("id").ifEmpty { order.optString("orderId") }
    if (account.isBlank() || id.isBlank()) return null
    return GrabAlert(
        competitionKey(account, id, parseCompetition(order)),
        order.optString("kind"),
        order.optString("pickupAddress").ifEmpty { order.optString("origin", "上车点") },
        order.optString("destAddress").ifEmpty { order.optString("destination", "目的地") },
        admittedAt = now,
        waypoints = waypointNames(order),
    )
}

/**
 * The single owner of reminder lifetime for the whole process. Foreground dialog and background
 * overlay only render [current]; neither starts, extends or restarts it. Pure: all time comes from
 * the caller's monotonic clock, so polling, rotation and foreground/background switches cannot reset
 * a reminder. Seen keys live in [seen] (persisted and bounded on Android). This book only owns the
 * 3s reminder; candidate waiting is [CompetitionWatch] and survives the reminder.
 * Not thread-safe; the Android wrapper calls it on the main thread only.
 */
class GrabAlertBook(
    private val seen: GrabSeenLog = BoundedSeenLog(),
    private val lifetimeMs: Long = GRAB_ALERT_MS,
    private val maxQueue: Int = 3,
    private val maxQueueWaitMs: Long = 9000L,
) {
    var account: String = ""
        private set
    /** Bumped whenever outstanding reminders stop being valid. */
    var epoch: Long = 0L
        private set
    var current: GrabAlert? = null
        private set
    private val queue = ArrayDeque<GrabAlert>()
    private val retired = mutableListOf<GrabAlertKey>()

    val queued: Int get() = queue.size

    /** Switches account: reminders end; seen keys stay (they carry the account, so switching back never replays). */
    fun bind(account: String): Boolean {
        if (account == this.account) return false
        clear()
        this.account = account
        return true
    }

    /**
     * Admits reminders not seen yet. With [onlyNewest] only the last candidate is reminded and the rest
     * are marked seen, matching the original first-online behaviour. Returns the admitted alerts in order.
     */
    fun admit(candidates: List<GrabAlert>, onlyNewest: Boolean, now: Long): List<GrabAlert> {
        val own = candidates.filter { it.key.account == account }
        val fresh = own.filter { !seen.contains(it.key) }
        seen.addAll(own.map { it.key })
        val admitted = if (onlyNewest) listOfNotNull(fresh.lastOrNull()) else fresh
        admitted.forEach { queue.addLast(it.copy(admittedAt = now, shownAt = null)) }
        promote(now)
        // Bounded: the oldest waiting reminders give way; their orders stay in the order hall.
        while (queue.size > maxQueue) retired += queue.removeFirst().key
        return admitted
    }

    fun markSeen(keys: Collection<GrabAlertKey>) {
        seen.addAll(keys.filter { it.account == account })
    }

    /** Drops reminders whose order/round is no longer grabbable (withdrawn, assigned, new round, closed). */
    fun retain(live: Set<GrabAlertKey>, now: Long) {
        queue.filter { it.key !in live }.forEach { retired += it.key }
        queue.removeAll { it.key !in live }
        current?.takeIf { it.key !in live }?.let {
            retired += it.key
            current = null
        }
        promote(now)
    }

    /** Advances the clock; returns true when the presented reminder changed. */
    fun tick(now: Long): Boolean {
        val before = current
        val shown = before?.shownAt
        if (before != null && shown != null && now - shown >= lifetimeMs) {
            retired += before.key
            current = null
        }
        promote(now)
        return before != current
    }

    fun dismiss(key: GrabAlertKey, now: Long) {
        if (current?.key == key) {
            retired += key
            current = null
        } else if (queue.removeAll { it.key == key }) retired += key
        promote(now)
    }

    /** Ends all reminders (offline, active trip, logout). Candidate attempts are not touched. */
    fun clear() {
        current?.let { retired += it.key }
        queue.forEach { retired += it.key }
        queue.clear()
        current = null
        epoch++
    }

    fun remainingMs(now: Long): Long {
        val shown = current?.shownAt ?: return 0L
        return (lifetimeMs - (now - shown)).coerceIn(0L, lifetimeMs)
    }

    /** True only while [key] is the presented reminder of the bound account and still within its life. */
    fun isLive(key: GrabAlertKey, now: Long): Boolean {
        val alert = current ?: return false
        val shown = alert.shownAt ?: return false
        return alert.key == key && key.account == account && now - shown < lifetimeMs
    }

    /**
     * An explicit click may act on the presented reminder, or on a queued one whose notification is
     * already on screen; never on a retired, dismissed, other-account or other-round key.
     */
    fun isActionable(key: GrabAlertKey, now: Long): Boolean =
        isLive(key, now) ||
            (key.account == account && queue.any { it.key == key && now - it.admittedAt <= lifetimeMs })

    /** Keys whose reminder ended since the last call; the wrapper removes their notifications. */
    fun drainRetired(): List<GrabAlertKey> = retired.toList().also { retired.clear() }

    private fun promote(now: Long) {
        while (current == null && queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (now - next.admittedAt > maxQueueWaitMs) {
                retired += next.key
                continue
            }
            current = next.copy(shownAt = now)
        }
    }
}

sealed class GrabOutcome {
    abstract val orderId: String

    /** This driver's own order row is in an owned stage for the attempted round. Only this may navigate. */
    data class Assigned(override val orderId: String, val stage: String) : GrabOutcome()

    /** Candidate (or not yet known): never navigate, wait for authoritative state/results. */
    data class Pending(override val orderId: String) : GrabOutcome()

    /** History says WON but no matching owned row: feedback only, no navigation or contact. */
    data class WonFeedback(override val orderId: String) : GrabOutcome()

    /** Not assigned to this driver; report the server's words, no success. */
    data class Lost(override val orderId: String, val message: String) : GrabOutcome()

    data class Failed(override val orderId: String, val message: String, val retryable: Boolean) : GrabOutcome()
}

object GrabOutcomes {
    /** Only a server-confirmed own assignment may enter the existing navigation entry. */
    fun mayNavigate(outcome: GrabOutcome): Boolean = outcome is GrabOutcome.Assigned

    fun isFinal(outcome: GrabOutcome): Boolean =
        outcome !is GrabOutcome.Pending && !(outcome is GrabOutcome.Failed && outcome.retryable)

    /**
     * HTTP failures: 4xx are definitive server answers (shown as-is); 401 means the session ended.
     * Transport errors (status <= 0) and 5xx are retryable with the same requestId.
     */
    fun fromFailure(orderId: String, status: Int, message: String): GrabOutcome = when {
        status == 401 -> GrabOutcome.Failed(orderId, "登录已失效，请返回应用重新登录", retryable = false)
        status in 400..499 -> GrabOutcome.Lost(orderId, message.ifBlank { "订单当前不可接" })
        else -> GrabOutcome.Failed(orderId, "网络异常，抢单未确认；可在窗口截止前点击重试", retryable = true)
    }

    fun message(outcome: GrabOutcome): String = when (outcome) {
        is GrabOutcome.Assigned -> "抢单成功，订单已派给你"
        is GrabOutcome.Pending -> "已加入竞抢，等待派单结果"
        is GrabOutcome.WonFeedback -> "服务端记录本轮获派，但订单当前状态已变化，请以订单列表为准"
        is GrabOutcome.Lost -> outcome.message
        is GrabOutcome.Failed -> outcome.message
    }
}
