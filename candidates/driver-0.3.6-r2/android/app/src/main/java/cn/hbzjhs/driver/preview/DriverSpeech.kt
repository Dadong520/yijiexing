package cn.hbzjhs.driver.preview

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.ArrayDeque
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Observable status describes the actual output; engine initialization alone is never a spoken
 * result.
 */
object OrderAnnouncementStatus {
    internal val mutable = MutableStateFlow("新订单语音提醒未启动")
    val status: StateFlow<String> = mutable.asStateFlow()
}

/**
 * Called with an authoritative /api/state payload. All audio and queue work runs on main. This is
 * native local TTS, audio and Android notifications; it does not contact a speech API.
 */
class NativeOrderAnnouncer(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val audio = app.getSystemService(AudioManager::class.java)
    private val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    /** New-order notifications by account+order+round, so a new round never reuses an old one. */
    private val posted = mutableMapOf<GrabAlertKey, Int>()
    private val retireNotification: (GrabAlertKey) -> Unit = { key ->
        posted.remove(key)?.let(notifications::cancel)
    }
    private val notifyGrabResult: (GrabAlertKey, String) -> Unit = { key, text ->
        if (!closed && key.account == account) postGrabResult(key, text)
    }
    private val queue = ArrayDeque<Announcement>()
    private var engine: TextToSpeech? = null
    private var engineReady = false
    private var engineInitializing = true
    private var engineProblem = "中文语音正在准备"
    private var player: MediaPlayer? = null
    private var current: Announcement? = null
    private var repeatIndex = 0
    private var spokenCount = 0
    private var toneCount = 0
    private var remindersEnabled = false
    private var utteranceId: String? = null
    private var sequence = 0L
    private var wasOnline = false
    private var account = ""
    private var focus: AudioFocusRequest? = null
    private var watchdog: Runnable? = null
    @Volatile private var closed = false

    private data class Announcement(
        val id: String,
        val origin: String,
        val destination: String,
        val count: Int,
        val speed: Float,
        val notification: String,
    )

    init {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "新订单提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "听单新订单横幅、提示音与振动；可在系统设置中调整"
                setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                enableVibration(true)
            }
        )
        NewOrderAlerts.install(app)
        NewOrderAlerts.onRetired = retireNotification
        GrabActions.onBackgroundResult = notifyGrabResult
        main.post {
            if (!closed) {
                OrderAnnouncementStatus.mutable.value = "正在检查已安装的中文语音"
                runCatching {
                        engine =
                            TextToSpeech(app) { status -> main.post { initializeVoice(status) } }
                        main.postDelayed(
                            {
                                if (!closed && engineInitializing) {
                                    engineInitializing = false
                                    engineProblem = "中文语音初始化超时"
                                    OrderAnnouncementStatus.mutable.value =
                                        "$engineProblem，使用提示音和订单通知"
                                    startNext()
                                }
                            },
                            8000,
                        )
                    }
                    .onFailure {
                        engineInitializing = false
                        engineProblem = "本机没有可用语音引擎"
                        OrderAnnouncementStatus.mutable.value = "$engineProblem，使用提示音和订单通知"
                        startNext()
                    }
            }
        }
    }

    private fun initializeVoice(status: Int) {
        if (closed) return
        engineInitializing = false
        val tts = engine
        val chosen =
            if (status == TextToSpeech.SUCCESS && tts != null)
                runCatching {
                        tts.voices
                            ?.filter {
                                it.locale.language == Locale.CHINESE.language &&
                                    !it.isNetworkConnectionRequired &&
                                    !it.features
                                        .orEmpty()
                                        .contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                            }
                            ?.sortedBy { if (it.locale.country == Locale.CHINA.country) 0 else 1 }
                            ?.firstOrNull()
                    }
                    .getOrNull()
            else null
        engineReady =
            chosen != null &&
                tts != null &&
                runCatching { tts.setVoice(chosen) == TextToSpeech.SUCCESS }.getOrDefault(false)
        if (engineReady && tts != null) {
            tts.setAudioAttributes(attributes)
            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        main.post {
                            if (!closed && id == utteranceId && current != null)
                                OrderAnnouncementStatus.mutable.value =
                                    "正在中文播报新订单（${repeatIndex + 1}/${current!!.count}次）"
                        }
                    }

                    override fun onDone(id: String?) {
                        main.post {
                            if (!closed && id == utteranceId && current != null) {
                                clearWatchdog()
                                spokenCount++
                                repeatIndex++
                                if (repeatIndex < current!!.count) speakCurrent()
                                else
                                    finishCurrent(
                                        "已完成中文订单播报 · ${current!!.count}次 · ${current!!.notification}"
                                    )
                            }
                        }
                    }

                    @Deprecated("Deprecated in Android")
                    override fun onError(id: String?) {
                        main.post { speechFailed(id) }
                    }

                    override fun onError(id: String?, code: Int) {
                        main.post { speechFailed(id) }
                    }
                }
            )
            if (remindersEnabled && current == null && queue.isEmpty())
                OrderAnnouncementStatus.mutable.value = "中文离线语音已就绪，等待新订单"
        } else {
            engineProblem = if (status == TextToSpeech.SUCCESS) "未安装可用的中文离线语音" else "本机中文语音引擎不可用"
            if (remindersEnabled)
                OrderAnnouncementStatus.mutable.value = "$engineProblem，使用提示音和订单通知；可到系统安装中文语音"
        }
        startNext()
    }

    /** Safe from a service IO coroutine; incoming JSON is not changed. */
    fun update(data: JSONObject) {
        if (closed) return
        main.post { if (!closed) consume(data) }
    }

    private fun consume(data: JSONObject) {
        val state = data.optJSONObject("state") ?: return
        val features = data.optJSONObject("features") ?: JSONObject()
        val identity =
            data.optJSONObject("profile")?.optJSONObject("driver")?.optString("id").orEmpty()
        if (identity != account) {
            stopAudio(true)
            cancelNotifications()
            // Rebinding ends the previous account's reminders and candidate waiting; persisted seen
            // keys carry the account, so switching back never replays a round.
            NewOrderAlerts.bind(identity)
            wasOnline = false
            account = identity
        }
        val rows = state.optJSONArray("orders")
        val orders = (0 until (rows?.length() ?: 0)).mapNotNull { rows?.optJSONObject(it) }
        val available =
            orders.filter {
                it.optString("stage") == "AVAILABLE" && it.optString("id").isNotBlank()
            }
        val availableIds = available.map { it.optString("id") }.toSet()
        // Only rounds this driver may still join remind; joined/closed/assigned rounds stay hall-only.
        val grabbable = available.filter { row ->
            grabAlertFor(account, row, 0L)?.key?.let { GrabActions.availability(it) } in
                setOf(GrabAvailability.LEGACY, GrabAvailability.CAN_GRAB)
        }
        val liveKeys = grabbable.mapNotNull { grabAlertFor(account, it, 0L)?.key }.toSet()
        posted.keys
            .filter { it !in liveKeys }
            .toList()
            .forEach { key -> posted.remove(key)?.let(notifications::cancel) }
        queue.removeIf { it.id !in availableIds }
        if (current?.id?.let { it !in availableIds } == true) stopAudio(false)
        val online = state.optBoolean("online")
        // Going offline really ends local candidate waiting (the server no longer awards offline drivers).
        if (!online && wasOnline) GrabActions.endSession()
        val firstOnline = online && !wasOnline
        wasOnline = online
        val active = orders.any {
            it.optString("stage") in ACTIVE_STAGES &&
                !(it.optString("kind") == "RESERVED" && it.optString("stage") == "RESERVED")
        }
        if (!online || !features.optBoolean("canAcceptOrders") || active) {
            remindersEnabled = false
            NewOrderAlerts.clear()
            stopAudio(true)
            if (online) NewOrderAlerts.markSeen(account, available)
            OrderAnnouncementStatus.mutable.value =
                when {
                    !online -> "离线 · 新订单提醒暂停"
                    active -> "正在服务订单 · 其他新单提醒暂停"
                    else -> "当前不可新增接单 · 新单提醒暂停"
                }
            return
        }
        // Text notifications are independent of voice permission, audio preference and TTS
        // parameters.
        fun createdAtMillis(order: JSONObject): Long = runCatching {
            java.time.OffsetDateTime.parse(order.optString("createdAt")).toInstant().toEpochMilli()
        }.getOrDefault(0L)
        // One seen book (account + order + round, bounded by count and TTL) decides both the visual
        // reminder and the voice/notification below, so they can never disagree or repeat.
        NewOrderAlerts.retainAvailable(account, grabbable)
        val newOrders =
            NewOrderAlerts.admit(account, grabbable.sortedBy(::createdAtMillis), onlyNewest = firstOnline)
        val notices = newOrders.map { it to notifyOrder(it, state.optBoolean("voice")) }
        val noticesSummary = notices.map { it.second }.distinct().joinToString("；")
        val wasEnabled = remindersEnabled
        remindersEnabled = state.optBoolean("voice") && features.optBoolean("canVoice")
        if (!remindersEnabled) {
            stopAudio(true)
            if (state.optBoolean("voice") && newOrders.isNotEmpty() &&
                !noticesSummary.contains("系统订单通知已发送") && DriverUiPresence.visible &&
                audio.ringerMode == AudioManager.RINGER_MODE_NORMAL && audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)>0) {
                runCatching {
                    val cue = MediaPlayer.create(app, R.raw.new_order, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(), 0)
                    player = cue
                    cue?.setOnCompletionListener { if (player===it) player=null; it.release() }
                    cue?.start()
                }
            }
            val reason = if (!state.optBoolean("voice")) "声音已关闭，新单保留界面和静默通知" else "语音播报未开放，使用基础新单提示"
            OrderAnnouncementStatus.mutable.value =
                reason + if (noticesSummary.isBlank()) "；新单保留界面及系统通知" else " · $noticesSummary"
            return
        }
        val benefits = data.optJSONObject("benefits")
        val subscription = benefits?.optJSONObject("subscription")
        val snapshot =
            if (subscription?.optString("status") == "ACTIVE")
                subscription.optJSONObject("rightsSnapshot")
            else null
        val levels = benefits?.optJSONArray("levels")
        val trial =
            (0 until (levels?.length() ?: 0))
                .mapNotNull { levels?.optJSONObject(it) }
                .firstOrNull { it.optString("id") == "TRIAL" }
        val parameters = (snapshot ?: trial)?.optJSONObject("params")
        val countValue = parameters?.opt("voiceCount")
        val speedValue = parameters?.opt("voiceSpeed")
        val count = (countValue as? Number)?.toInt()
        val speed = (speedValue as? Number)?.toFloat()
        if (
            count == null ||
                countValue.toString().toDoubleOrNull() != count.toDouble() ||
                count !in 1..20 ||
                speed == null ||
                !speed.isFinite() ||
                speed !in .5f..2f
        ) {
            remindersEnabled = false
            stopAudio(true)
            OrderAnnouncementStatus.mutable.value =
                "服务端语音参数无效，未套用默认值" +
                    if (noticesSummary.isBlank()) "；新单保留界面及系统通知" else " · $noticesSummary"
            return
        }
        notices.forEach { (order, notified) ->
            queue.addLast(
                Announcement(
                    order.optString("id"),
                    order.optString("origin"),
                    order.optString("destination"),
                    count,
                    speed,
                    notified,
                )
            )
        }
        // Bound only the local audio backlog; each new order still gets its own visible
        // notification.
        while (queue.size > 20) queue.removeFirst()
        if (!wasEnabled && current == null && queue.isEmpty()) {
            OrderAnnouncementStatus.mutable.value =
                if (engineReady) "声音提醒已开启，等待新订单" else "$engineProblem；后续使用提示音和订单通知"
        }
        startNext()
    }

    /**
     * Call on the service main thread to stop immediately after a confirmed voice/rights change.
     */
    fun pause(reason: String = "声音提醒已关闭") {
        if (closed) return
        val action = Runnable {
            if (!closed) {
                remindersEnabled = false
                stopAudio(true)
                OrderAnnouncementStatus.mutable.value = reason
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action.run() else main.post(action)
    }

    private fun notifyOrder(order: JSONObject, soundEnabled: Boolean): String {
        val allowed =
            Build.VERSION.SDK_INT < 33 ||
                app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        if (
            !allowed ||
                !notifications.areNotificationsEnabled() ||
                notifications.getNotificationChannel(CHANNEL)?.importance ==
                    NotificationManager.IMPORTANCE_NONE
        )
            return "系统订单通知未授权"
        val key = grabAlertFor(account, order, 0L)?.key ?: return "系统订单通知未能发送"
        val number = GrabActionIdentity.notificationId(key)
        val intent =
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending =
            PendingIntent.getActivity(
                app,
                number,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val directGrab = order.optString("kind") in DIRECT_GRAB_KINDS && key.round.isNotBlank()
        return runCatching {
                val builder =
                    androidx.core.app.NotificationCompat.Builder(app, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_dialog_map)
                        .setContentTitle(if (directGrab) "宜捷行 · 新订单" else "宜捷行 · 云端沙箱新订单")
                        .setContentText(
                            "${order.optString("origin")} → ${order.optString("destination")}"
                        )
                        .setStyle(
                            androidx.core.app.NotificationCompat.BigTextStyle()
                                .bigText(
                                    "${order.optString("origin")} → ${order.optString("destination")}\n" +
                                        if (directGrab) COMPETITION_NOTE
                                        else "点按返回司机端查看，订单可接状态以服务端为准。"
                                )
                        )
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                        .setSilent(!soundEnabled)
                        .setOnlyAlertOnce(true)
                        // A new-order reminder, not a record: it leaves with the 3s reminder (also
                        // cancelled when the owner retires it); the order itself stays in the hall.
                        .setTimeoutAfter(GRAB_ALERT_MS)
                if (directGrab) {
                    // Explicit non-exported receiver, immutable, and a unique data URI per
                    // account+order+round: an old action can never be rewritten to a new round or
                    // another account. No token or extras; the receiver only trusts this identity.
                    val grab =
                        PendingIntent.getBroadcast(
                            app,
                            GrabActionIdentity.requestCode(key),
                            Intent(app, GrabActionReceiver::class.java)
                                .setAction(GrabActionReceiver.ACTION_GRAB)
                                .setData(android.net.Uri.parse(GrabActionIdentity.uri(key))),
                            PendingIntent.FLAG_IMMUTABLE,
                        )
                    builder.addAction(0, "立即抢单", grab)
                }
                notifications.notify(number, builder.build())
                posted[key] = number
                "系统订单通知已发送"
            }
            .getOrElse { "系统订单通知未能发送" }
    }

    /** Background grab result: tells the server's answer; tapping only returns to the app, no navigation. */
    private fun postGrabResult(key: GrabAlertKey, text: String) {
        val allowed =
            Build.VERSION.SDK_INT < 33 ||
                app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        if (!allowed || !notifications.areNotificationsEnabled()) return
        // One result notification per account+order+round, replaced in place (joined → final).
        val number = 1_100_000 + (key.encode().hashCode() and 0x3fffffff) % 1_000_000
        val pending =
            PendingIntent.getActivity(
                app,
                number,
                Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        runCatching {
            notifications.notify(
                number,
                androidx.core.app.NotificationCompat.Builder(app, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_map)
                    .setContentTitle("宜捷行 · 抢单结果")
                    .setContentText(text)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setSilent(true)
                    .build(),
            )
        }
    }

    private fun startNext() {
        if (closed || current != null || queue.isEmpty() || engineInitializing) return
        current = queue.removeFirst()
        repeatIndex = 0
        spokenCount = 0
        toneCount = 0
        val request =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(
                    { change ->
                        if (
                            change == AudioManager.AUDIOFOCUS_LOSS ||
                                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        ) {
                            main.post {
                                if (!closed) {
                                    stopAudio(true)
                                    OrderAnnouncementStatus.mutable.value =
                                        "音频被其他应用占用，语音已暂停；可查看订单通知"
                                }
                            }
                        }
                    },
                    main,
                )
                .build()
        focus = request
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            finishCurrent("当前音频不可用 · ${current!!.notification}")
            return
        }
        if (engineReady) speakCurrent() else playFallback()
    }

    private fun speakCurrent() {
        val item = current ?: return
        val tts =
            engine
                ?: run {
                    engineProblem = "中文语音引擎不可用"
                    playFallback()
                    return
                }
        val id = "order-${++sequence}"
        utteranceId = id
        val result =
            runCatching {
                    val rate = tts.setSpeechRate(item.speed)
                    if (rate != TextToSpeech.SUCCESS) TextToSpeech.ERROR
                    else
                        tts.speak(
                            "收到新订单。上车地点，${item.origin}。目的地，${item.destination}。请返回宜捷行查看。",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            id,
                        )
                }
                .getOrDefault(TextToSpeech.ERROR)
        if (result == TextToSpeech.ERROR) {
            speechFailed(id)
            return
        }
        clearWatchdog()
        watchdog = Runnable {
            if (!closed && id == utteranceId) {
                engineProblem = "中文播报超时"
                engineReady = false
                engine?.stop()
                utteranceId = null
                repeatIndex = 0
                toneCount = 0
                playFallback()
            }
        }
        main.postDelayed(watchdog!!, 120000)
    }

    private fun speechFailed(id: String?) {
        if (closed || id != utteranceId || current == null) return
        clearWatchdog()
        utteranceId = null
        engine?.stop()
        engineReady = false
        engineProblem = "中文语音播放失败"
        repeatIndex = 0
        playFallback()
    }

    private fun playFallback() {
        val item = current ?: return
        clearWatchdog()
        utteranceId = null
        runCatching {
                player?.release()
                player =
                    MediaPlayer().apply {
                        setAudioAttributes(attributes)
                        app.resources.openRawResourceFd(R.raw.new_order).use { file ->
                            setDataSource(file.fileDescriptor, file.startOffset, file.length)
                        }
                        setOnCompletionListener {
                            it.release()
                            if (player === it) player = null
                            if (!closed && current === item) {
                                toneCount++
                                repeatIndex++
                                if (repeatIndex < item.count) playFallback()
                                else
                                    finishCurrent(
                                        "$engineProblem · 已播放订单提示音${toneCount}次 · ${item.notification}；${addressNote()}"
                                    )
                            }
                        }
                        setOnErrorListener { media, _, _ ->
                            media.release()
                            if (player === media) player = null
                            if (!closed && current === item)
                                finishCurrent(
                                    "$engineProblem · 提示音播放失败 · ${item.notification}；${addressNote()}"
                                )
                            true
                        }
                        prepare()
                        start()
                    }
                OrderAnnouncementStatus.mutable.value =
                    "$engineProblem · 正在播放订单提示音 · ${item.notification}；${addressNote()}"
            }
            .onFailure {
                finishCurrent("$engineProblem · 提示音不可用 · ${item.notification}；${addressNote()}")
            }
    }

    private fun addressNote(): String =
        if (spokenCount == 0) "未完成地址播报" else "地址曾完成${spokenCount}次播报，后续使用提示音"

    private fun clearWatchdog() {
        watchdog?.let(main::removeCallbacks)
        watchdog = null
    }

    private fun finishCurrent(status: String) {
        clearWatchdog()
        utteranceId = null
        current = null
        focus?.let(audio::abandonAudioFocusRequest)
        focus = null
        OrderAnnouncementStatus.mutable.value = status
        startNext()
    }

    private fun stopAudio(clearQueue: Boolean) {
        clearWatchdog()
        utteranceId = null
        current = null
        engine?.stop()
        player?.run {
            runCatching { stop() }
            release()
        }
        player = null
        focus?.let(audio::abandonAudioFocusRequest)
        focus = null
        if (clearQueue) queue.clear()
    }

    private fun cancelNotifications() {
        posted.values.forEach(notifications::cancel)
        posted.clear()
    }

    fun close() {
        closed = true
        val action = Runnable {
            stopAudio(true)
            cancelNotifications()
            NewOrderAlerts.clear()
            if (NewOrderAlerts.onRetired === retireNotification) NewOrderAlerts.onRetired = null
            if (GrabActions.onBackgroundResult === notifyGrabResult) GrabActions.onBackgroundResult = null
            engine?.shutdown()
            engine = null
            OrderAnnouncementStatus.mutable.value = "新订单播报已停止"
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action.run() else main.post(action)
    }

    companion object {
        private const val CHANNEL = "new-orders-v2"
        private val ACTIVE_STAGES =
            setOf("BID_SENT", "PREPAY", "RESERVED", "PICKUP", "ARRIVED", "TRIP")
    }
}
