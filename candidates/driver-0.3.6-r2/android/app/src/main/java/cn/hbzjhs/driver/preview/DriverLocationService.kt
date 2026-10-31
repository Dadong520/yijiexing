package cn.hbzjhs.driver.preview

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class DriverPosition(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val time: Long,
)

object PositionBus {
    internal val latest = MutableStateFlow<DriverPosition?>(null)
    internal val mutableStatus = MutableStateFlow("未启动定位")
    val position = latest.asStateFlow()
    val status = mutableStatus.asStateFlow()
}

object DriverUiPresence {
    @Volatile var visible = false
}

class DriverLocationService : Service(), LocationListener {
    private lateinit var manager: LocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var serviceGeneration = 0L
    private var lastSent = 0L
    private var floatingEnabled = false
    private var floatingAllowed = false
    private var listening = true
    private var linkAvailable = true
    private var bubble: TextView? = null
    private var windows: WindowManager? = null
    private var announcer: NativeOrderAnnouncer? = null
    private var orderOverlay: NewOrderOverlay? = null
    private val renderAlerts: () -> Unit = { updateBubble() }
    /** State fetched by the app or the candidate poll also reaches the announcer: one poll, two readers. */
    private val sharedState: (LocalApi.Credentials, JSONObject) -> Unit = { _, data -> applyState(data) }
    /** Latest authoritative payload (main thread); decides this service's background cadence. */
    private var latestState = JSONObject()

    private fun updateBubble() {
        // The 3s new-order reminder is a core listening capability: it needs only the listening
        // eligibility (enforced where reminders are admitted) and the user's own system overlay grant,
        // never the paid floating-bubble benefit below. The foreground dialog owns it while visible.
        val alert = NewOrderAlerts.current.value
        val overlayAllowed = !DriverUiPresence.visible && Settings.canDrawOverlays(this)
        if (overlayAllowed && alert != null) {
            if (orderOverlay?.shownKey != alert.key) orderOverlay?.show(alert)
            else orderOverlay?.render()
        } else orderOverlay?.hide()
        val shouldShow =
            floatingEnabled &&
                floatingAllowed &&
                !DriverUiPresence.visible &&
                Settings.canDrawOverlays(this)
        if (!shouldShow) {
            bubble?.let { runCatching { windows?.removeView(it) } }
            bubble = null
            return
        }
        val label = "宜捷行\n" + if (!linkAvailable) "连接中断" else if (listening) "听单中" else "行程中"
        bubble?.let {
            it.text = label
            return
        }
        val density = resources.displayMetrics.density
        val size = (68 * density).toInt()
        val params =
            WindowManager.LayoutParams(
                    size,
                    size,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                )
                .apply {
                    gravity = Gravity.TOP or Gravity.LEFT
                    x = (16 * density).toInt()
                    y = (180 * density).toInt()
                }
        val view =
            TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background =
                    GradientDrawable().apply {
                        setColor(android.graphics.Color.rgb(0, 148, 137))
                        cornerRadius = 34 * density
                    }
                contentDescription = "返回宜捷行"
                setOnClickListener {
                    startActivity(
                        Intent(this@DriverLocationService, MainActivity::class.java)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            )
                    )
                }
            }
        windows = getSystemService(WindowManager::class.java)
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 8 * density) moved = true
                    params.x =
                        (startX + dx)
                            .toInt()
                            .coerceIn(
                                0,
                                (resources.displayMetrics.widthPixels - size).coerceAtLeast(0),
                            )
                    params.y =
                        (startY + dy)
                            .toInt()
                            .coerceIn(
                                0,
                                (resources.displayMetrics.heightPixels - size).coerceAtLeast(0),
                            )
                    runCatching { windows?.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) view.performClick()
                    true
                }
                else -> true
            }
        }
        runCatching {
                windows?.addView(view, params)
                bubble = view
            }
            .onFailure { PositionBus.mutableStatus.value = "已定位 · 悬浮显示未开启" }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        announcer = NativeOrderAnnouncer(this)
        orderOverlay = NewOrderOverlay(this)
        NewOrderAlerts.onChanged = renderAlerts
        GrabActions.addStateListener(sharedState)
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel("driver-location", "接单期间的位置服务", NotificationManager.IMPORTANCE_LOW)
        )
        val pending =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        startForeground(
            2688,
            Notification.Builder(this, "driver-location")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("宜捷行位置服务已开启")
                .setContentText("听单和行程期间同步位置；休息后停止，减少云端请求")
                .setContentIntent(pending)
                .setOngoing(true)
                .build(),
        )
        manager = getSystemService(LocationManager::class.java)
        if (LocalApi.token.isBlank()) LocalApi.token = SessionVault(this).load()
        if (
            LocalApi.token.isBlank() ||  LocalApi.baseUrl.isBlank()
        ) {
            PositionBus.mutableStatus.value = "请先完成云端连接设置并登录"
            stopSelf()
            return
        }
        if (
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            PositionBus.mutableStatus.value = "尚未授予定位权限"
            stopSelf()
            return
        }
        try {
            val providers =
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
                    it in manager.allProviders
                }
            // Keep listeners registered while the system location switch is off, so
            // enabling it resumes fixes without restarting the listening service.
            if (providers.none { manager.isProviderEnabled(it) })
                PositionBus.mutableStatus.value = "请开启系统定位服务"
            if (providers.isNotEmpty()) {
                if (providers.any { manager.isProviderEnabled(it) })
                    PositionBus.mutableStatus.value = "正在获取位置"
                providers.forEach { provider ->
                    manager.requestLocationUpdates(
                        provider,
                        15000L,
                        0f,
                        this,
                        Looper.getMainLooper(),
                    )
                    manager
                        .getLastKnownLocation(provider)
                        ?.takeIf { System.currentTimeMillis() - it.time < 120000 }
                        ?.let(::onLocationChanged)
                }
            }
        } catch (e: SecurityException) {
            PositionBus.mutableStatus.value = "定位授权已失效"
            stopSelf()
        }
        scope.launch {
            // Error backoff only; 0 = healthy. The normal cadence comes from the shared gate.
            var retryDelay = 0L
            var nextAllowedAt = 0L
            while (isActive) {
                delay(500)
                withContext(Dispatchers.Main) { updateBubble() }
                if (SystemClock.elapsedRealtime() < nextAllowedAt) continue
                // While the app is visible its store polls and this service only reads the shared
                // state (sharedState listener). In the background this loop polls through the same
                // single-flight gate as the store and the candidate poll: ≤2s/1s while listening,
                // the original 15s while only a trip is in progress.
                val began = withContext(Dispatchers.Main) {
                    if (DriverUiPresence.visible) false
                    else {
                        val interval =
                            if (latestState.optJSONObject("state") == null) StateCadence.LISTENING_MS
                            else GrabActions.baseInterval(latestState, foreground = false) ?: StateCadence.TRIP_MS
                        GrabActions.tryBeginBaseFetch(interval)
                    }
                }
                if (!began) continue
                run {
                    val generation = serviceGeneration
                    val credentials = LocalApi.credentials()
                    try {
                        val started = SystemClock.elapsedRealtime()
                        val data = LocalApi.request("GET", "/api/state", credentials = credentials)
                        val timing = RequestTiming(started, SystemClock.elapsedRealtime())
                        withContext(Dispatchers.Main) {
                        if (generation != serviceGeneration || !LocalApi.isCurrent(credentials)) return@withContext
                        retryDelay = 0L
                        // Anchors windows/resolves candidates, then forwards to the app store.
                        GrabActions.observeState(credentials, data, from = sharedState, timing = timing)
                        applyState(data)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: LocalApiException) {
                        withContext(Dispatchers.Main) {
                        if (generation != serviceGeneration || !LocalApi.isCurrent(credentials)) return@withContext
                        linkAvailable = false
                        announcer?.pause("云端服务连接中断，新单语音暂停")
                        if (
                            e.status in setOf(401, 503) ||
                                e.details.optString("reason") in
                                    setOf("DEMO_ACCESS_REQUIRED", "DEMO_ACCESS_INVALID")
                        ) {
                            PositionBus.mutableStatus.value =
                                if (e.status == 503) "云端服务已暂停，恢复后请重新开启听单" else "请返回应用查看连接或登录提示"
                            stopSelf()
                            return@withContext
                        }
                        retryDelay = (retryDelay * 2).coerceIn(4000L, 60000L)
                        nextAllowedAt = SystemClock.elapsedRealtime() + retryDelay
                        }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                        if (generation != serviceGeneration || !LocalApi.isCurrent(credentials)) return@withContext
                        linkAvailable = false
                        announcer?.pause("云端服务连接中断，新单语音暂停")
                        retryDelay = (retryDelay * 2).coerceIn(4000L, 60000L)
                        nextAllowedAt = SystemClock.elapsedRealtime() + retryDelay
                        // Back off while the phone's connection recovers.
                        }
                    } finally {
                        withContext(NonCancellable + Dispatchers.Main) { GrabActions.endFetch() }
                    }
                }
            }
        }
    }

    /**
     * One place that turns any authoritative state (own poll, app refresh or candidate poll) into the
     * announcer update and this service's flags, so none of them lag behind a skipped poll.
     */
    private fun applyState(data: JSONObject) {
        val state = data.optJSONObject("state") ?: return
        latestState = data
        announcer?.update(data)
        linkAvailable = true
        listening = state.optBoolean("online")
        if (!state.optBoolean("online") && !StateCadence.inProgress(data)) {
            GrabActions.endSession()
            stopSelf()
            return
        }
        floatingEnabled = state.optBoolean("floating")
        floatingAllowed = data.optJSONObject("features")?.optBoolean("canFloating") == true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceGeneration++
        floatingEnabled = intent?.getBooleanExtra("floatingEnabled", false) ?: false
        floatingAllowed = intent?.getBooleanExtra("floatingAllowed", false) ?: false
        listening = intent?.getBooleanExtra("listening", true) ?: true
        if (intent?.getBooleanExtra("voiceAllowed", false) != true)
            announcer?.pause("声音提醒或当前语音权益已关闭")
        updateBubble()
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
        PositionBus.latest.value =
            DriverPosition(location.latitude, location.longitude, location.accuracy, location.time)
        PositionBus.mutableStatus.value = "已定位 · 精度约 ${location.accuracy.toInt()} 米"
        if (System.currentTimeMillis() - lastSent < 14500 || LocalApi.token.isBlank()) return
        lastSent = System.currentTimeMillis()
        val generation = serviceGeneration
        val credentials = LocalApi.credentials()
        scope.launch {
            try {
                LocalApi.request(
                    "POST", "/api/action",
                    JSONObject().put("action", "location.report")
                        .put("payload", JSONObject()
                            .put("latitude", location.latitude)
                            .put("longitude", location.longitude)
                            .put("accuracy", location.accuracy)
                            .put("time", location.time))
                        .put("requestId", UUID.randomUUID().toString()),
                    credentials = credentials,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (generation != serviceGeneration || !LocalApi.isCurrent(credentials)) return@withContext
                    PositionBus.mutableStatus.value = "已定位 · 位置同步暂不可用"
                    if (e is LocalApiException &&
                        (e.status in setOf(401, 503) || e.details.optString("reason") in
                            setOf("DEMO_ACCESS_REQUIRED", "DEMO_ACCESS_INVALID"))) stopSelf()
                }
            }
        }
    }

    override fun onProviderDisabled(provider: String) {
        PositionBus.mutableStatus.value = "请开启系统定位服务"
    }

    override fun onProviderEnabled(provider: String) {
        PositionBus.mutableStatus.value = "正在获取位置"
    }

    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onDestroy() {
        serviceGeneration++
        // Unhook first so clearing reminders below cannot re-add the bubble or overlay.
        if (NewOrderAlerts.onChanged === renderAlerts) NewOrderAlerts.onChanged = null
        GrabActions.removeStateListener(sharedState)
        if (::manager.isInitialized) runCatching { manager.removeUpdates(this) }
        bubble?.let { runCatching { windows?.removeView(it) } }
        bubble = null
        announcer?.close()
        announcer = null
        orderOverlay?.close()
        orderOverlay = null
        NewOrderAlerts.clear()
        scope.cancel()
        PositionBus.mutableStatus.value = "未启动定位"
        super.onDestroy()
    }
}
