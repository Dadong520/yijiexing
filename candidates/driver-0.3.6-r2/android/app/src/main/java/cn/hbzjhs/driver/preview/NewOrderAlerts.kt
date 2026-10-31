package cn.hbzjhs.driver.preview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Process-wide owner of new-order reminders: the foreground dialog and the background overlay both
 * render [current] and nothing else, so a reminder has exactly one lifetime (3s from first display,
 * monotonic) whichever surface shows it. Candidate waiting is [GrabActions] and outlives it.
 * Main thread only.
 */
object NewOrderAlerts {
    private const val PREFS = "driver_grab_seen_v1"
    private const val PREF_KEYS = "keys"
    private val main = Handler(Looper.getMainLooper())
    private var book = GrabAlertBook()
    private var installed = false
    private val mutableCurrent = MutableStateFlow<GrabAlert?>(null)
    val current: StateFlow<GrabAlert?> = mutableCurrent.asStateFlow()

    private val mutableOpenRequested = MutableStateFlow<GrabAlertKey?>(null)
    /** A BID/detail open the user explicitly asked for from the overlay; consumed once per account. */
    val openRequested: StateFlow<GrabAlertKey?> = mutableOpenRequested.asStateFlow()

    /** Removes the order notification of a retired reminder (set by the announcer). */
    internal var onRetired: ((GrabAlertKey) -> Unit)? = null
    /** Re-renders the background overlay (set by the location service). */
    internal var onChanged: (() -> Unit)? = null

    private val expiry = Runnable { book.tick(now()); sync() }

    internal fun now(): Long = SystemClock.elapsedRealtime()

    val account: String get() = book.account
    val epoch: Long get() = book.epoch

    /**
     * Seen/dismissed keys (account|order|round only, ≤512) persist in private prefs so a restart or
     * switching account and back never replays the same round. Installed once, before any reminder.
     */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(PREF_KEYS, "").orEmpty().split('\n').filter { it.isNotBlank() }
        val log = BoundedSeenLog(512, stored) { keys -> prefs.edit().putString(PREF_KEYS, keys.joinToString("\n")).apply() }
        val previousAccount = book.account
        book = GrabAlertBook(seen = log)
        if (previousAccount.isNotBlank()) book.bind(previousAccount)
        sync()
    }

    fun bind(account: String) {
        if (book.bind(account)) mutableOpenRequested.value = null
        GrabActions.bindAccount(account)
        sync()
    }

    /** Admits this account's not-yet-seen grabbable orders; returns those that will remind, in order. */
    fun admit(account: String, orders: List<JSONObject>, onlyNewest: Boolean): List<JSONObject> {
        bind(account)
        val now = now()
        val byKey = orders.mapNotNull { order -> grabAlertFor(account, order, now)?.let { it to order } }
        // Reminders dropped by the bounded queue get no voice/notification either; the hall keeps them.
        val admitted = book.admit(byKey.map { it.first }, onlyNewest, now).map { it.key }
            .filter { book.isActionable(it, now) }.toSet()
        sync()
        return byKey.filter { it.first.key in admitted }.map { it.second }
    }

    fun markSeen(account: String, orders: List<JSONObject>) {
        bind(account)
        val now = now()
        book.markSeen(orders.mapNotNull { grabAlertFor(account, it, now)?.key })
    }

    fun retainAvailable(account: String, orders: List<JSONObject>) {
        if (account != book.account) return
        val now = now()
        book.retain(orders.mapNotNull { grabAlertFor(account, it, now)?.key }.toSet(), now)
        sync()
    }

    fun dismiss(key: GrabAlertKey) {
        book.dismiss(key, now())
        sync()
    }

    /** Offline, active trip, logout or service stop: every reminder ends. Candidate waiting is separate. */
    fun clear() {
        book.clear()
        mutableOpenRequested.value = null
        sync()
    }

    fun isLive(key: GrabAlertKey): Boolean = book.isLive(key, now())
    fun isActionable(key: GrabAlertKey): Boolean = book.isActionable(key, now())
    fun remainingMs(): Long = book.remainingMs(now())

    /** Foreground/background switch: hand the same reminder to the other surface, no restart. */
    fun notifyPresence() = main.post { onChanged?.invoke() }

    internal fun notifyChanged() = onChanged?.invoke()

    fun requestOpen(key: GrabAlertKey) {
        mutableOpenRequested.value = key
        dismiss(key)
    }

    fun consumeOpen(account: String): GrabAlertKey? {
        val requested = mutableOpenRequested.value
        mutableOpenRequested.value = null
        return requested?.takeIf { it.account == account && it.account == book.account }
    }

    private fun sync() {
        mutableCurrent.value = book.current
        book.drainRetired().forEach { key -> onRetired?.invoke(key) }
        main.removeCallbacks(expiry)
        if (book.current != null) main.postDelayed(expiry, book.remainingMs(now()).coerceAtLeast(1L))
        onChanged?.invoke()
    }
}

/** A final (or retryable) candidate result, scoped to the session generation that produced it. */
data class GrabResult(val key: GrabAlertKey, val outcome: GrabOutcome, val foreground: Boolean, val generation: Long)

/**
 * The single competition entry for every surface (hall, detail, confirm, dialog, overlay,
 * notification): one explicit click → one real order.accept {id, competitionRound} with a requestId
 * kept until an authoritative outcome. While an attempt waits it polls /api/state about once a second
 * then backs off (bounded), feeding the same state to the UI and the service so nothing polls twice.
 * Main thread only.
 */
object GrabActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val watch = CompetitionWatch()
    /** orderId#round → local monotonic deadline. */
    private val anchors = mutableMapOf<String, Long>()
    /** One AVAILABLE/owned row as last seen in authoritative state. */
    private data class KnownRow(val kind: String, val stage: String, val competition: OrderCompetition?, val required: Boolean)
    private val known = mutableMapOf<String, KnownRow>()
    private val stateListeners = mutableListOf<(LocalApi.Credentials, JSONObject) -> Unit>()
    private var pollJob: Job? = null
    private var oneShot: Job? = null
    private var lastStateAt = Long.MIN_VALUE / 2
    /** /api/state reads currently on the wire from any base poller or the candidate poll. */
    private var inFlight = 0
    private val mutableVersion = MutableStateFlow(0L)
    /** Changes whenever availability or an attempt changes; UI reads it to recompose. */
    val version: StateFlow<Long> = mutableVersion.asStateFlow()
    private val mutableResults = MutableStateFlow<List<GrabResult>>(emptyList())
    val results: StateFlow<List<GrabResult>> = mutableResults.asStateFlow()

    /** Background result presenter (set by the announcer; one notification per key, replaced in place). */
    internal var onBackgroundResult: ((GrabAlertKey, String) -> Unit)? = null

    private fun now() = SystemClock.elapsedRealtime()
    private fun anchorKey(orderId: String, round: Int) = "$orderId#$round"

    private fun bump() {
        mutableVersion.value = mutableVersion.value + 1
        NewOrderAlerts.notifyChanged()
    }

    fun addStateListener(listener: (LocalApi.Credentials, JSONObject) -> Unit) {
        if (stateListeners.none { it === listener }) stateListeners += listener
    }

    fun removeStateListener(listener: (LocalApi.Credentials, JSONObject) -> Unit) {
        stateListeners.removeAll { it === listener }
    }

    /**
     * Base cadence for the surface that owns polling now (foreground store or background service, by
     * DriverUiPresence): ≤2s while really listening, ~1s with an open round or own candidacy, the
     * original long intervals otherwise; null = don't poll (not listening and nothing in progress).
     */
    fun baseInterval(data: JSONObject, foreground: Boolean): Long? =
        StateCadence.interval(data, foreground, localHot = watch.awaiting().isNotEmpty())

    /**
     * Single-flight gate for periodic reads: refused while any read is on the wire, while the
     * candidate poll runs (it delivers state to everyone), or when a state is younger than the
     * interval. Pair every true with [endFetch].
     */
    fun tryBeginBaseFetch(intervalMs: Long): Boolean {
        if (inFlight > 0 || pollJob?.isActive == true) return false
        if (now() - lastStateAt < intervalMs - 200L) return false
        inFlight++
        return true
    }

    fun endFetch() {
        if (inFlight > 0) inFlight--
    }

    fun bindAccount(account: String) {
        if (!watch.bind(account)) return
        resetLocal()
    }

    /** Logout, account switch or going offline: candidate waiting really ends; late results are dropped. */
    fun endSession() {
        watch.end()
        resetLocal()
    }

    private fun resetLocal() {
        anchors.clear()
        known.clear()
        pollJob?.cancel()
        pollJob = null
        oneShot?.cancel()
        oneShot = null
        mutableResults.value = emptyList()
        bump()
    }

    fun availability(account: String, order: DriverOrder): GrabAvailability {
        val c = order.competition
        return CompetitionRules.availability(
            order.kind.name, order.stage.name, c,
            c?.let { anchors[anchorKey(order.id, it.round)] }, now(),
            watch.isPending(competitionKey(account, order.id, c)),
            required = order.competitionRequired,
        )
    }

    fun availability(key: GrabAlertKey): GrabAvailability {
        val row = known[key.orderId] ?: return GrabAvailability.UNAVAILABLE
        val c = row.competition
        if (c != null && c.round.toString() != key.round) return GrabAvailability.UNAVAILABLE
        return CompetitionRules.availability(
            row.kind, row.stage, c, c?.let { anchors[anchorKey(key.orderId, it.round)] }, now(),
            watch.isPending(key), required = row.required,
        )
    }

    fun phase(key: GrabAlertKey): CompetitionWatch.Phase? = watch.attempt(key)?.phase

    /** From a 3s reminder (dialog/overlay/notification): only while that exact reminder is actionable. */
    fun grabAlert(key: GrabAlertKey, foreground: Boolean, onDone: () -> Unit = {}): String? {
        if (!NewOrderAlerts.isActionable(key)) {
            onDone()
            return "该新单提醒已结束，请在订单大厅查看"
        }
        return submit(key, foreground, onDone)
    }

    /**
     * Returns a message to show, or null when the request started or one is already running.
     * [onDone] runs exactly once: immediately when nothing is sent, else when the accept round trip
     * (and its state read) has finished — the notification receiver uses it to end goAsync.
     */
    fun submit(key: GrabAlertKey, foreground: Boolean, onDone: () -> Unit = {}): String? {
        val (message, job) = start(key, foreground)
        if (job == null) onDone() else job.invokeOnCompletion { onDone() }
        return message
    }

    private fun start(key: GrabAlertKey, foreground: Boolean): Pair<String?, Job?> {
        val credentials = LocalApi.credentials()
        if (credentials.token.isBlank()) return "请先登录后再抢单" to null
        if (key.account.isBlank() || key.account != watch.account) return "账号状态同步中，请稍后再试" to null
        val round = key.round.toIntOrNull() ?: return "该订单不参与竞抢" to null
        when (availability(key)) {
            GrabAvailability.CAN_GRAB -> Unit
            GrabAvailability.COMPETING -> return null to null
            GrabAvailability.CLOSED_WAITING -> {
                // Includes "no measured anchor yet": conservative, one timed read, never a resubmit.
                refreshOnce(credentials)
                return "本轮已截止或剩余时间未知，等待服务端最新状态" to null
            }
            GrabAvailability.INVALID -> {
                refreshOnce(credentials)
                return "订单竞抢信息异常，已刷新，请稍后再试" to null
            }
            else -> return "该订单本轮不可抢，请刷新订单大厅" to null
        }
        val attempt = watch.begin(key, now(), foreground) { UUID.randomUUID().toString() } ?: return null to null
        val generation = watch.generation
        bump()
        val job = scope.launch {
            fun owns() = generation == watch.generation && LocalApi.isCurrent(credentials)
            val body = JSONObject()
                .put("action", "order.accept")
                .put("payload", JSONObject().put("id", key.orderId).put("competitionRound", round))
                .put("requestId", attempt.requestId)
            try {
                val postStarted = now()
                val data = LocalApi.request("POST", "/api/action", body, credentials = credentials)
                if (!owns()) return@launch
                // 200 only means "joined/pending"; never success. The outcome comes from state/results.
                watch.sent(key, attempt.token, CompetitionWatch.Phase.PENDING)
                if (!DriverUiPresence.visible) onBackgroundResult?.invoke(key, "已加入竞抢，等待派单结果")
                if (data.has("state")) observeState(credentials, data, timing = RequestTiming(postStarted, now()))
                else {
                    val getStarted = now()
                    val state = LocalApi.request("GET", "/api/state", credentials = credentials)
                    if (!owns()) return@launch
                    observeState(credentials, state, timing = RequestTiming(getStarted, now()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LocalApiException) {
                if (!owns()) return@launch
                val outcome = CompetitionVerdicts.fromFailure(key.orderId, e.status, e.details.optString("reason"), e.message.orEmpty())
                if (outcome is GrabOutcome.Failed && outcome.retryable)
                    watch.sent(key, attempt.token, CompetitionWatch.Phase.UNCONFIRMED)
                else if (watch.finish(key, attempt.token) != null) publish(attempt, outcome)
            } catch (_: Exception) {
                // May or may not have reached the server: keep the requestId and learn from state.
                if (owns()) watch.sent(key, attempt.token, CompetitionWatch.Phase.UNCONFIRMED)
            }
            if (owns()) {
                bump()
                ensurePolling(credentials)
            }
        }
        return null to job
    }

    /**
     * Every authoritative driver payload passes here once (store, service, candidate poll). Anchors
     * windows from the measured [timing] of the request that fetched it (null = unmeasured: no new
     * anchor), resolves waiting attempts, then forwards to the other listeners ([from] excluded).
     */
    fun observeState(credentials: LocalApi.Credentials, data: JSONObject, from: Any? = null, timing: RequestTiming? = null) {
        if (!LocalApi.isCurrent(credentials)) return
        val account = driverAccount(data)
        val rows = data.optJSONObject("state")?.optJSONArray("orders") ?: return
        if (account.isBlank()) return
        bindAccount(account)
        val t = now()
        lastStateAt = t
        known.clear()
        val liveAnchors = mutableSetOf<String>()
        (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.forEach { row ->
            val id = row.optString("id")
            if (id.isBlank()) return@forEach
            val c = parseCompetition(row)
            val stage = row.optString("stage")
            known[id] = KnownRow(row.optString("kind"), stage, c, CompetitionRules.required(row))
            if (c != null && c.status == "OPEN" && stage == "AVAILABLE") {
                val k = anchorKey(id, c.round)
                liveAnchors += k
                // Keep the old anchor if this payload was unmeasured; never extend an existing one.
                CompetitionWindow.anchor(anchors[k], timing, c.remainingMs)?.let { anchors[k] = it }
            }
        }
        anchors.keys.retainAll(liveAnchors)
        watch.awaiting().forEach { a ->
            val outcome = CompetitionVerdicts.resolve(a.key, data, sentConfirmed = a.phase == CompetitionWatch.Phase.PENDING)
            when {
                outcome is GrabOutcome.Pending ->
                    if (a.phase == CompetitionWatch.Phase.UNCONFIRMED) watch.sent(a.key, a.token, CompetitionWatch.Phase.PENDING)
                outcome is GrabOutcome.Failed && outcome.retryable -> {
                    if (watch.sent(a.key, a.token, CompetitionWatch.Phase.RETRYABLE)) publish(a, outcome)
                }
                else -> if (watch.finish(a.key, a.token) != null) publish(a, outcome)
            }
        }
        sweepTimeouts()
        stateListeners.filter { it !== from }.toList().forEach { it(credentials, data) }
        bump()
        if (watch.nextPollDelay(t) != null) ensurePolling(credentials)
    }

    private fun sweepTimeouts() {
        watch.timedOut(now()).forEach { a ->
            if (watch.finish(a.key, a.token) != null)
                publish(a, GrabOutcome.Failed(a.key.orderId, "暂未收到派单结果，请在订单列表查看最新状态", retryable = false))
        }
    }

    private fun publish(attempt: CompetitionWatch.Attempt, outcome: GrabOutcome) {
        if (GrabOutcomes.isFinal(outcome)) NewOrderAlerts.dismiss(attempt.key)
        mutableResults.value = (mutableResults.value + GrabResult(attempt.key, outcome, attempt.foreground, watch.generation)).takeLast(10)
        if (!DriverUiPresence.visible) onBackgroundResult?.invoke(attempt.key, GrabOutcomes.message(outcome))
    }

    private fun ensurePolling(credentials: LocalApi.Credentials) {
        if (pollJob?.isActive == true) return
        val generation = watch.generation
        pollJob = scope.launch {
            while (isActive) {
                val wait = watch.nextPollDelay(now()) ?: break
                delay(wait)
                if (generation != watch.generation || !LocalApi.isCurrent(credentials)) break
                // Shares the wire with base polls: skip while another read is out or one just landed.
                if (inFlight == 0 && now() - lastStateAt >= wait / 2) {
                    inFlight++
                    try {
                        val started = now()
                        val data = LocalApi.request("GET", "/api/state", credentials = credentials)
                        if (generation != watch.generation) break
                        observeState(credentials, data, timing = RequestTiming(started, now()))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Bounded: the attempt times out below instead of polling forever.
                    } finally {
                        endFetch()
                    }
                }
                sweepTimeouts()
                bump()
            }
        }
    }

    /** One conservative read when the local window has run out (no resubmission). */
    private fun refreshOnce(credentials: LocalApi.Credentials) {
        if (oneShot?.isActive == true || !tryBeginBaseFetch(1_000L)) return
        val generation = watch.generation
        oneShot = scope.launch {
            try {
                val started = now()
                val data = LocalApi.request("GET", "/api/state", credentials = credentials)
                if (generation == watch.generation) observeState(credentials, data, timing = RequestTiming(started, now()))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                endFetch()
            }
        }
    }

    /** Hands results to the activity once, only for the same account and session generation. */
    fun consumeResults(account: String): List<GrabResult> {
        val list = mutableResults.value
        if (list.isEmpty()) return emptyList()
        mutableResults.value = emptyList()
        return list.filter { it.key.account == account && it.generation == watch.generation }
    }
}

/** Target of the new-order notification's explicit "立即抢单" action; never launches an activity. */
class GrabActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GRAB) return
        // The identity is the PendingIntent's own data (account+order+round); no extras, no token.
        val key = GrabActionIdentity.keyOf(intent.dataString) ?: return
        // Keeps the broadcast alive only for the short explicit submit: finished exactly once when the
        // accept round trip ends or after a bound, whichever comes first. No activity is started.
        val pending = goAsync()
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val finish = { if (finished.compareAndSet(false, true)) pending.finish() }
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, SUBMIT_BOUND_MS)
        GrabActions.grabAlert(key, foreground = false, onDone = { finish() })?.let { message ->
            GrabActions.onBackgroundResult?.invoke(key, message)
        }
    }

    companion object {
        const val ACTION_GRAB = "cn.hbzjhs.driver.preview.GRAB_ORDER"
        /** Well under the system's background-broadcast limit; the request itself may continue after. */
        private const val SUBMIT_BOUND_MS = 9_000L
    }
}

/** Label and whether the primary button may be pressed, from the shared owner. */
internal fun alertPrimary(alert: GrabAlert): Pair<String, Boolean> {
    if (alert.bid) return "去报价" to true
    if (!alert.directGrab) return "查看订单" to true
    val availability = GrabActions.availability(alert.key)
    return when (GrabActions.phase(alert.key)) {
        CompetitionWatch.Phase.SENDING,
        CompetitionWatch.Phase.PENDING,
        CompetitionWatch.Phase.UNCONFIRMED -> GrabAvailability.COMPETING.label to false
        CompetitionWatch.Phase.RETRYABLE -> "重试抢单" to (availability == GrabAvailability.CAN_GRAB)
        null -> availability.label.ifBlank { "立即抢单" } to (availability == GrabAvailability.CAN_GRAB)
    }
}

@Composable
fun NewOrderDialog(
    orders: List<DriverOrder> = emptyList(),
    onOpen: (GrabAlert) -> Unit,
    onRejected: (String) -> Unit,
) {
    val alert by NewOrderAlerts.current.collectAsState()
    val version by GrabActions.version.collectAsState()
    val first = alert ?: return
    // Re-evaluates the local window inside the 3s; it never extends the reminder.
    var tick by remember(first.key) { mutableIntStateOf(0) }
    LaunchedEffect(first.key) {
        while (true) {
            delay(250)
            tick++
        }
    }
    // Display-only lookup of the already-synced order; the reminder lifetime is owned elsewhere.
    val detail = orders.firstOrNull { it.id == first.key.orderId }
    val (label, enabled) = remember(first, version, tick) { alertPrimary(first) }

    val primaryGreen = Color(0xFF00796B)
    val textDark = Color(0xFF212121)
    val textGrey = Color(0xFF757575)

    Dialog(
        onDismissRequest = { NewOrderAlerts.dismiss(first.key) },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (first.directGrab) "新订单" else "发现新订单",
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryGreen
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Route and figures scroll inside a bounded area so the buttons below stay on screen
                // with large text on small phones.
                Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                if (detail != null) {
                    // The shared ordered list: origin, every waypoint in order, destination.
                    RouteStops(detail, compact = true)
                } else {
                    (listOf("起" to first.origin) + first.waypoints.mapIndexed { i, name -> "途${i + 1}" to name } + listOf("终" to first.destination))
                        .forEach { (tag, place) ->
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 6.dp)) {
                                Text("$tag：", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (tag == "终") Color(0xFFE65100) else primaryGreen)
                                Text(text = place, fontSize = 15.sp, color = textDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Pale, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val fareText = when {
                        detail == null -> "未提供"
                        detail.destinationPending -> "费用待定"
                        else -> "¥${money(detail.fare)}"
                    }
                    listOf(
                        "订单金额" to fareText,
                        "预估行程" to (detail?.let { kmOrMissing(it.mileage) } ?: "未提供"),
                        "距乘客" to (detail?.let { kmOrMissing(it.distance) } ?: "未提供"),
                    ).forEach { (label, value) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (label == "订单金额") Warm else textDark)
                            Text(label, fontSize = 12.sp, color = textGrey)
                        }
                    }
                }
                if (detail != null)
                    Text(newOrderSourceNote(detail.isPrivateExample), fontSize = 12.sp, lineHeight = 16.sp, color = Warm, modifier = Modifier.padding(top = 6.dp))
                if (first.directGrab)
                    Text(COMPETITION_NOTE, fontSize = 12.sp, lineHeight = 16.sp, color = textGrey, modifier = Modifier.padding(top = 6.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { NewOrderAlerts.dismiss(first.key) },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("关闭", fontSize = 14.sp, color = textGrey)
                    }

                    Button(
                        onClick = {
                            if (first.directGrab)
                                GrabActions.grabAlert(first.key, foreground = true)?.let(onRejected)
                            else {
                                NewOrderAlerts.dismiss(first.key)
                                onOpen(first)
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1.4f).heightIn(min = 44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(label, fontSize = 14.sp, color = Color.White, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Hall/detail button text for an AVAILABLE order; [version]/[tick] only drive recomposition. */
@Suppress("UNUSED_PARAMETER")
fun competitionLabel(account: String, order: DriverOrder, version: Long, tick: Int): String {
    val availability = GrabActions.availability(account, order)
    if (availability == GrabAvailability.LEGACY) return "立即抢单"
    val retry = GrabActions.phase(competitionKey(account, order.id, order.competition)) == CompetitionWatch.Phase.RETRYABLE
    return if (retry && availability == GrabAvailability.CAN_GRAB) "重试抢单" else availability.label
}

/** Detail page card of an AVAILABLE competition order: status, the same grab entry, own withdraw. */
@Composable
fun CompetitionCard(order: DriverOrder, store: ConnectedStore, onGrab: (DriverOrder) -> Unit, onWithdraw: () -> Unit) {
    val payload by store.payload.collectAsState()
    val version by GrabActions.version.collectAsState()
    val account = driverAccount(payload)
    var tick by remember(order.id) { mutableIntStateOf(0) }
    LaunchedEffect(order.id, order.competition?.status) {
        while (order.competition?.status == "OPEN") {
            delay(500)
            tick++
        }
    }
    val availability = GrabActions.availability(account, order)
    val label = competitionLabel(account, order, version, tick)
    val serverPending = order.competition?.myOutcome == "PENDING"
    SheetCard {
        Text("5秒竞抢 · 第${order.competition?.round ?: 1}轮", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(
            when (availability) {
                GrabAvailability.CAN_GRAB ->
                    if (order.competition?.status == "OPEN") "本轮竞抢已开启，截止前仍可抢单。" else "首位司机抢单后开启5秒竞抢。"
                GrabAvailability.COMPETING -> "你已加入本轮竞抢，等待服务端派单结果；未获派前不会导航或显示乘客联系方式。"
                GrabAvailability.CLOSED_WAITING -> "本轮已截止，等待服务端派单结果。"
                else -> "本轮你已不可参与，订单状态以服务端为准。"
            } + "\n" + COMPETITION_NOTE,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Muted,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            label,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            enabled = availability == GrabAvailability.CAN_GRAB,
            onClick = { onGrab(order) },
        )
        // Only a server-confirmed candidacy can be withdrawn; it never cancels the passenger's order.
        if (serverPending)
            DriverTextButton(onClick = onWithdraw, modifier = Modifier.padding(top = 4.dp)) {
                Text("退出本轮竞抢", color = Danger)
            }
    }
}

// The 3s is only the reminder's life; the 5s window opens once on the server at the first valid grab.
internal const val COMPETITION_NOTE = "首位司机抢单后开启5秒竞抢，派单结果以服务器为准。提示3秒后收起，仍可在订单大厅查看。"

/**
 * Background surface for the current reminder. Shown only with the user's system overlay grant
 * (TYPE_APPLICATION_OVERLAY, never focusable, no full-screen launch); its lifetime is the owner's.
 */
class NewOrderOverlay(private val appContext: Context) {
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var currentKey: GrabAlertKey? = null
    private var primary: Button? = null
    private var shownAlert: GrabAlert? = null

    val shownKey: GrabAlertKey? get() = currentKey

    fun show(alert: GrabAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
            hide()
            return
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(alert) }
            return
        }

        hide()

        val wm = windowManager ?: return
        val dm = appContext.resources.displayMetrics
        val density = dm.density
        val screenWidth = dm.widthPixels

        val cardWidth = Math.min((340 * density).toInt(), screenWidth - (32 * density).toInt())
        // Not named topMargin: inside LayoutParams.apply below that name must mean the params' field.
        val overlayTop = (96 * density).toInt()

        val rootLayout = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            val padding16 = (16 * density).toInt()
            setPadding(padding16, padding16, padding16, padding16)
            background = GradientDrawable().apply {
                setColor(AndroidColor.WHITE)
                cornerRadius = 16 * density
                setStroke((2 * density).toInt(), AndroidColor.parseColor("#00796B"))
            }
            // Touches on the card never fall through to the app underneath.
            isClickable = true
        }

        val titleView = TextView(appContext).apply {
            text = if (alert.directGrab) "宜捷行 · 新订单" else "宜捷行 · 发现新订单"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(AndroidColor.parseColor("#00796B"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        rootLayout.addView(titleView)

        // Full ordered route (origin, every waypoint, destination) scrolls in a bounded area so the
        // buttons stay fixed and pressable at 360dp / 1.3x font.
        val stops = LinearLayout(appContext).apply { orientation = LinearLayout.VERTICAL }
        (listOf("起" to alert.origin) + alert.waypoints.mapIndexed { i, name -> "途${i + 1}" to name } + listOf("终" to alert.destination))
            .forEach { (tag, place) ->
                stops.addView(TextView(appContext).apply {
                    text = "$tag：$place"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(AndroidColor.parseColor("#212121"))
                    maxLines = 2
                    setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
                })
            }
        val scroller = ScrollView(appContext).apply { addView(stops) }
        val routeHeight =
            if (alert.waypoints.isEmpty()) LinearLayout.LayoutParams.WRAP_CONTENT
            else (dm.heightPixels * 0.28f).toInt()
        rootLayout.addView(scroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, routeHeight).apply {
            topMargin = (6 * density).toInt()
            bottomMargin = (10 * density).toInt()
        })
        if (alert.directGrab)
            rootLayout.addView(TextView(appContext).apply {
                text = COMPETITION_NOTE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(AndroidColor.parseColor("#757575"))
                maxLines = 3
                setPadding(0, 0, 0, (8 * density).toInt())
            })

        // Equal-weight buttons wrap their text instead of being pushed off a 360dp/1.3x screen.
        val buttonBar = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val buttonHeight = (44 * density).toInt()

        val btnClose = Button(appContext).apply {
            text = "关闭"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(AndroidColor.parseColor("#757575"))
            background = null
            minHeight = buttonHeight
            setOnClickListener {
                NewOrderAlerts.dismiss(alert.key)
                hide()
            }
        }
        buttonBar.addView(btnClose, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val (label, enabled) = alertPrimary(alert)
        val btnOpen = Button(appContext).apply {
            text = label
            isEnabled = enabled
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(AndroidColor.WHITE)
            minHeight = buttonHeight
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#00796B"))
                cornerRadius = 8 * density
            }
            setOnClickListener {
                if (alert.directGrab) {
                    // One explicit click → one real order.accept; the owner disables it while competing.
                    GrabActions.grabAlert(alert.key, foreground = false)?.let { message ->
                        GrabActions.onBackgroundResult?.invoke(alert.key, message)
                        hide()
                    }
                } else {
                    // BID quoting and detail stay in the app: the user's tap on a visible overlay opens it.
                    NewOrderAlerts.requestOpen(alert.key)
                    hide()
                    try {
                        val intent = Intent(appContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        }
                        appContext.startActivity(intent)
                    } catch (e: Exception) {}
                }
            }
        }
        buttonBar.addView(btnOpen, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f).apply {
            leftMargin = (8 * density).toInt()
        })
        rootLayout.addView(buttonBar)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = overlayTop
        }

        try {
            wm.addView(rootLayout, params)
            currentView = rootLayout
            currentKey = alert.key
            shownAlert = alert
            primary = btnOpen
            android.util.Log.i("YJXOrderOverlay", "shown")
        } catch (e: Exception) {
            android.util.Log.w("YJXOrderOverlay", "not-shown:"+e.javaClass.simpleName)
            currentKey = null
            currentView = null
            primary = null
            shownAlert = null
        }
    }

    /** Refreshes the shown reminder's button from the shared owner without restarting anything. */
    fun render() {
        val alert = shownAlert ?: return
        val (label, enabled) = alertPrimary(alert)
        primary?.let {
            it.isEnabled = enabled
            it.text = label
        }
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        currentKey = null
        primary = null
        shownAlert = null
        val view = currentView
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {}
            currentView = null
        }
    }

    fun close() {
        hide()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
