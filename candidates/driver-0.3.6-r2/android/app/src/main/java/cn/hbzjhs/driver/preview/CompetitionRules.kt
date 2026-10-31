package cn.hbzjhs.driver.preview

import org.json.JSONObject

/**
 * Client side of contracts/GRAB-20261101-01 V1. Pure: no Android, all time is the caller's
 * monotonic clock (elapsedRealtime). The server is the only authority; nothing here invents a winner.
 */
data class OrderCompetition(
    val round: Int,
    /** READY | OPEN | ASSIGNED | CLOSED */
    val status: String,
    val deadline: String?,
    val remainingMs: Long,
    /** NONE | PENDING | WON | LOST | WITHDRAWN | INELIGIBLE | CANCELLED | SUPERSEDED */
    val myOutcome: String,
)

data class CompetitionResult(val orderId: String, val round: Int, val outcome: String)

/** Reads `competition` of one order row; null when absent (BID/NEGOTIATED/private examples) or malformed. */
fun parseCompetition(order: JSONObject): OrderCompetition? {
    val c = order.optJSONObject("competition") ?: return null
    val round = c.optInt("round", 0)
    val status = c.optString("status")
    if (round < 1 || status !in setOf("READY", "OPEN", "ASSIGNED", "CLOSED")) return null
    return OrderCompetition(
        round,
        status,
        c.optString("deadline").takeIf { !c.isNull("deadline") && it.isNotBlank() && it != "null" },
        c.optLong("remainingMs", 0L).coerceAtLeast(0L),
        c.optString("myOutcome").ifBlank { c.optString("selfStatus") }.ifBlank { "NONE" },
    )
}

/** Top-level `competitionResults` (own latest 20) of a driver data payload. */
fun parseCompetitionResults(data: JSONObject): List<CompetitionResult> {
    val rows = data.optJSONArray("competitionResults") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.mapNotNull { r ->
        val id = r.optString("orderId")
        val round = r.optInt("round", 0)
        if (id.isBlank() || round < 1) null else CompetitionResult(id, round, r.optString("outcome"))
    }
}

/** A grab reminder/candidate identity always carries the competition round (empty for non-competition kinds). */
fun competitionKey(account: String, orderId: String, competition: OrderCompetition?) =
    GrabAlertKey(account, orderId, competition?.round?.toString().orEmpty())

object CompetitionWindow {
    /** Network delay is unknown; close locally a little early rather than submit into a closed window. */
    const val SAFETY_MS = 300L

    /**
     * Local deadline from the server's remainingMs anchored at receipt on the monotonic clock. Never
     * extended by a later poll (the server window is fixed); wall-clock jumps cannot move it.
     */
    fun anchor(existing: Long?, receivedAt: Long, remainingMs: Long): Long {
        val fresh = receivedAt + remainingMs - SAFETY_MS
        return if (existing == null) fresh else minOf(existing, fresh)
    }
}

enum class GrabAvailability(val label: String) {
    /** No competition object: original flow (BID quote, negotiated, private examples). */
    LEGACY(""),
    CAN_GRAB("立即抢单"),
    COMPETING("竞抢中·等待结果"),
    CLOSED_WAITING("已截止·等待派单"),
    UNAVAILABLE("本轮不可抢"),
}

object CompetitionRules {
    private val DIRECT_KINDS = setOf("STANDARD", "RESERVED")

    /**
     * What an AVAILABLE row allows right now. [localDeadline] comes from [CompetitionWindow.anchor];
     * [localPending] is this device's in-flight/pending attempt for the same round.
     */
    fun availability(
        kind: String,
        stage: String,
        competition: OrderCompetition?,
        localDeadline: Long?,
        now: Long,
        localPending: Boolean,
    ): GrabAvailability {
        if (competition == null || kind !in DIRECT_KINDS) return GrabAvailability.LEGACY
        if (stage != "AVAILABLE") return GrabAvailability.UNAVAILABLE
        if (localPending || competition.myOutcome == "PENDING") return GrabAvailability.COMPETING
        // Withdrawn/lost in this round stay out of it; a new round resets myOutcome on the server.
        // INELIGIBLE (e.g. no reliable location) may try again while the window is still open.
        if (competition.myOutcome in setOf("WITHDRAWN", "LOST", "CANCELLED", "SUPERSEDED", "WON"))
            return GrabAvailability.UNAVAILABLE
        return when (competition.status) {
            "READY" -> GrabAvailability.CAN_GRAB
            "OPEN" ->
                if (localDeadline != null && now < localDeadline) GrabAvailability.CAN_GRAB
                else GrabAvailability.CLOSED_WAITING
            else -> GrabAvailability.CLOSED_WAITING
        }
    }
}

/** Server-confirmed assignment stages (contract: STANDARD→PICKUP, RESERVED→RESERVED; later legs too). */
private val OWNED_STAGES = setOf("PICKUP", "RESERVED", "ARRIVED", "TRIP")

object CompetitionVerdicts {
    /**
     * Decides one attempt from an authoritative driver payload. Assigned only when the payload is the
     * same authenticated driver, the order row is in an owned stage, and its round matches (a row
     * without competition after assignment is accepted on stage+account — see SELFCHECK question).
     * WON from history without such a row is feedback only and can never navigate.
     */
    fun resolve(key: GrabAlertKey, data: JSONObject, sentConfirmed: Boolean): GrabOutcome {
        val payloadAccount = data.optJSONObject("profile")?.optJSONObject("driver")?.optString("id").orEmpty()
        if (payloadAccount != key.account) return GrabOutcome.Pending(key.orderId)
        val round = key.round.toIntOrNull() ?: return GrabOutcome.Pending(key.orderId)
        val rows = data.optJSONObject("state")?.optJSONArray("orders")
        val row = (0 until (rows?.length() ?: 0)).mapNotNull { rows?.optJSONObject(it) }
            .firstOrNull { it.optString("id") == key.orderId }
        val competition = row?.let(::parseCompetition)
        val result = parseCompetitionResults(data).firstOrNull { it.orderId == key.orderId && it.round == round }
        if (row != null && row.optString("stage") in OWNED_STAGES && (competition == null || competition.round == round))
            return GrabOutcome.Assigned(key.orderId, row.optString("stage"))
        val outcome =
            result?.outcome?.takeIf { it != "PENDING" && it != "NONE" && it.isNotBlank() }
                ?: competition?.takeIf { it.round == round }?.myOutcome?.takeIf { it != "PENDING" && it != "NONE" }
        if (outcome != null) return finalFor(key.orderId, outcome)
        if (competition != null && competition.round > round)
            return GrabOutcome.Lost(key.orderId, "本轮未获派，订单已进入新一轮")
        val pending = result?.outcome == "PENDING" || (competition?.round == round && competition.myOutcome == "PENDING")
        if (pending) return GrabOutcome.Pending(key.orderId)
        // Still NONE for this round: a request that never reached the server did not join.
        if (!sentConfirmed && competition?.round == round && row?.optString("stage") == "AVAILABLE")
            return GrabOutcome.Failed(key.orderId, "抢单未提交成功，可在窗口截止前重试", retryable = true)
        return GrabOutcome.Pending(key.orderId)
    }

    fun finalFor(orderId: String, outcome: String): GrabOutcome = when (outcome) {
        "WON" -> GrabOutcome.WonFeedback(orderId)
        "LOST" -> GrabOutcome.Lost(orderId, "本轮未获派，离上车点更近的司机已获派")
        "WITHDRAWN" -> GrabOutcome.Lost(orderId, "已退出本轮竞抢")
        "INELIGIBLE" -> GrabOutcome.Lost(orderId, "当前不符合接单条件，未参与本轮派单")
        "CANCELLED" -> GrabOutcome.Lost(orderId, "乘客已取消订单")
        "SUPERSEDED" -> GrabOutcome.Lost(orderId, "订单已变更，本轮竞抢作废")
        else -> GrabOutcome.Lost(orderId, "本轮未获派")
    }

    /** accept/cancel failures: contract reasons first, then generic HTTP classes. */
    fun fromFailure(orderId: String, status: Int, reason: String, message: String): GrabOutcome = when {
        reason == "COMPETITION_LOCATION_REQUIRED" ->
            GrabOutcome.Failed(orderId, "需要可靠定位才能参与竞抢：请开启系统定位并保持听单，定位成功后重试", retryable = false)
        reason == "COMPETITION_ROUND_STALE" -> GrabOutcome.Lost(orderId, "本轮已结束，请在订单大厅查看最新状态")
        else -> GrabOutcomes.fromFailure(orderId, status, message)
    }
}

/**
 * Ownership of in-flight and pending candidate attempts, separate from the 3s reminder lifetime.
 * Ends only with the session (logout, account switch, going offline) or an authoritative outcome.
 * The requestId of an attempt is kept until then, so an explicit retry resends the same body.
 */
class CompetitionWatch(private val maxWaitMs: Long = 25_000L) {
    enum class Phase { SENDING, PENDING, UNCONFIRMED, RETRYABLE }

    data class Attempt(
        val key: GrabAlertKey,
        val requestId: String,
        val token: Long,
        val phase: Phase,
        val foreground: Boolean,
        val startedAt: Long,
    )

    var account: String = ""
        private set
    var generation: Long = 0L
        private set
    private var nextToken = 0L
    private val attempts = LinkedHashMap<GrabAlertKey, Attempt>()

    fun bind(account: String): Boolean {
        if (account == this.account) return false
        end()
        this.account = account
        return true
    }

    /** Session over: forget every attempt; late callbacks of the old generation are ignored. */
    fun end() {
        attempts.clear()
        generation++
    }

    fun attempt(key: GrabAlertKey): Attempt? = attempts[key]

    fun isPending(key: GrabAlertKey): Boolean =
        attempts[key]?.phase in setOf(Phase.SENDING, Phase.PENDING, Phase.UNCONFIRMED)

    /**
     * Starts or retries an attempt. Returns null while one is already sending/pending (repeat click),
     * or for another account. A retry keeps the earlier requestId (same body and round).
     */
    fun begin(key: GrabAlertKey, now: Long, foreground: Boolean, newRequestId: () -> String): Attempt? {
        if (key.account != account || key.round.isBlank()) return null
        val previous = attempts[key]
        if (previous != null && previous.phase != Phase.RETRYABLE) return null
        val attempt = Attempt(key, previous?.requestId ?: newRequestId(), ++nextToken, Phase.SENDING, foreground, previous?.startedAt ?: now)
        attempts[key] = attempt
        return attempt
    }

    /** Applies the HTTP result of [token]; a stale token (cleared, retried or other generation) changes nothing. */
    fun sent(key: GrabAlertKey, token: Long, phase: Phase): Boolean {
        val current = attempts[key] ?: return false
        if (current.token != token) return false
        attempts[key] = current.copy(phase = phase)
        return true
    }

    /** Ends an attempt with a final outcome (also drops its requestId). */
    fun finish(key: GrabAlertKey, token: Long? = null): Attempt? {
        val current = attempts[key] ?: return null
        if (token != null && current.token != token) return null
        return attempts.remove(key)
    }

    /** Attempts waiting for an authoritative answer from a refreshed payload. */
    fun awaiting(): List<Attempt> = attempts.values.filter { it.phase == Phase.PENDING || it.phase == Phase.UNCONFIRMED }

    fun timedOut(now: Long): List<Attempt> = awaiting().filter { now - it.startedAt > maxWaitMs }

    /** ~1s while the 5s window may still be deciding, then bounded backoff; null = nothing to poll. */
    fun nextPollDelay(now: Long): Long? {
        val oldest = awaiting().minOfOrNull { it.startedAt } ?: return null
        val age = now - oldest
        return when {
            age < 8_000 -> 1_000L
            age < 14_000 -> 2_000L
            else -> 4_000L
        }
    }
}

/** Bounded record of reminded/dismissed keys (account+order+round only; no token/address/phone/GPS). */
interface GrabSeenLog {
    fun contains(key: GrabAlertKey): Boolean
    fun addAll(keys: Collection<GrabAlertKey>)
}

class BoundedSeenLog(
    private val capacity: Int = 512,
    initial: List<String> = emptyList(),
    private val onChange: (List<String>) -> Unit = {},
) : GrabSeenLog {
    private val keys = LinkedHashSet<String>()

    init {
        initial.filter { GrabAlertKey.decode(it) != null }.takeLast(capacity).forEach { keys.add(it) }
    }

    override fun contains(key: GrabAlertKey): Boolean = key.encode() in keys

    override fun addAll(keys: Collection<GrabAlertKey>) {
        var changed = false
        keys.forEach { key ->
            val value = key.encode()
            if (this.keys.add(value)) changed = true
        }
        val iterator = this.keys.iterator()
        while (this.keys.size > capacity && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
        if (changed) onChange(this.keys.toList())
    }

    fun snapshot(): List<String> = keys.toList()
}

/** Explicit, unique PendingIntent identity for a notification action: one per account+order+round. */
object GrabActionIdentity {
    private const val PREFIX = "yjxgrab://alert?k="

    fun uri(key: GrabAlertKey): String = PREFIX + java.net.URLEncoder.encode(key.encode(), "UTF-8")

    fun keyOf(uri: String?): GrabAlertKey? {
        if (uri == null || !uri.startsWith(PREFIX)) return null
        return runCatching { GrabAlertKey.decode(java.net.URLDecoder.decode(uri.removePrefix(PREFIX), "UTF-8")) }.getOrNull()
    }

    fun requestCode(key: GrabAlertKey): Int = key.encode().hashCode()

    fun notificationId(key: GrabAlertKey): Int = 20000 + (key.encode().hashCode() and 0x3fffffff) % 1_000_000
}
