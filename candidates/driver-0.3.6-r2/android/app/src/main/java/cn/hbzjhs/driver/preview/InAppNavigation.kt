package cn.hbzjhs.driver.preview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.AMapNaviView
import com.amap.api.navi.AMapNaviViewListener
import com.amap.api.navi.AmapPageType
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.model.*

/** AMapNavi is a process singleton: exactly one leg session may hold it at a time. */
internal object NaviOwnership {
    private var owner: Any? = null

    @Synchronized
    fun acquire(token: Any): Boolean {
        if (owner != null && owner !== token) return false
        owner = token
        return true
    }

    @Synchronized
    fun release(token: Any): Boolean {
        if (owner !== token) return false
        owner = null
        return true
    }
}

internal sealed class LegEvent {
    object EngineReady : LegEvent()
    data class Decided(val decision: LegRouteGate.Decision) : LegEvent()
    object NearTarget : LegEvent()
}

/**
 * The SDK listener of one leg. It is registered only on that leg's engine instance and routes only
 * the modern AMapCalcRouteResult callbacks through the leg's [LegRouteGate]; the deprecated int[] /
 * int twins are ignored, so each request yields one decision at most. Arrival is a hint only.
 * [deliver] may be called on any thread; the controller moves it to the main thread.
 */
internal class LegRouteListener(
    private val gate: LegRouteGate,
    private val deliver: (LegEvent) -> Unit,
) : AMapNaviListener {
    private fun decide(decision: LegRouteGate.Decision) {
        if (decision !is LegRouteGate.Decision.Ignore) deliver(LegEvent.Decided(decision))
    }

    override fun onInitNaviSuccess() {
        if (gate.live) deliver(LegEvent.EngineReady)
    }
    override fun onInitNaviFailure() = decide(gate.engineFailed())
    override fun onCalculateRouteSuccess(result: AMapCalcRouteResult?) = decide(gate.routeSucceeded(result?.routeid))
    override fun onCalculateRouteFailure(result: AMapCalcRouteResult?) =
        decide(gate.routeFailed(result?.errorDescription?.takeIf { it.isNotBlank() } ?: "错误码 ${result?.errorCode}"))

    // Deprecated twins of the two callbacks above: never routed, so one request cannot decide twice.
    override fun onCalculateRouteSuccess(ids: IntArray?) = Unit
    override fun onCalculateRouteFailure(code: Int) = Unit

    // Hints only. Business confirmation stays with the driver's buttons and the server.
    override fun onArriveDestination() {
        if (gate.phase == LegRouteGate.Phase.NAVIGATING) deliver(LegEvent.NearTarget)
    }
    override fun onArrivedWayPoint(index: Int) = Unit

    override fun onStartNavi(type: Int) = Unit
    override fun onTrafficStatusUpdate() = Unit
    override fun onLocationChange(location: AMapNaviLocation?) = Unit
    override fun onGetNavigationText(type: Int, text: String?) = Unit
    override fun onGetNavigationText(text: String?) = Unit
    override fun onEndEmulatorNavi() = Unit
    override fun onReCalculateRouteForYaw() = Unit
    override fun onReCalculateRouteForTrafficJam() = Unit
    override fun onGpsOpenStatus(enabled: Boolean) = Unit
    override fun onNaviInfoUpdate(info: NaviInfo?) = Unit
    override fun updateCameraInfo(infos: Array<out AMapNaviCameraInfo>?) = Unit
    override fun updateIntervalCameraInfo(start: AMapNaviCameraInfo?, end: AMapNaviCameraInfo?, status: Int) = Unit
    override fun onServiceAreaUpdate(infos: Array<out AMapServiceAreaInfo>?) = Unit
    override fun showCross(cross: AMapNaviCross?) = Unit
    override fun hideCross() = Unit
    override fun showModeCross(cross: AMapModelCross?) = Unit
    override fun hideModeCross() = Unit
    override fun showLaneInfo(infos: Array<out AMapLaneInfo>?, background: ByteArray?, recommend: ByteArray?) = Unit
    override fun showLaneInfo(info: AMapLaneInfo?) = Unit
    override fun hideLaneInfo() = Unit
    override fun notifyParallelRoad(type: Int) = Unit
    override fun OnUpdateTrafficFacility(infos: Array<out AMapNaviTrafficFacilityInfo>?) = Unit
    override fun OnUpdateTrafficFacility(info: AMapNaviTrafficFacilityInfo?) = Unit
    override fun updateAimlessModeStatistics(stat: AimLessModeStat?) = Unit
    override fun updateAimlessModeCongestionInfo(info: AimLessModeCongestionInfo?) = Unit
    override fun onPlayRing(type: Int) = Unit
    override fun onNaviRouteNotify(data: AMapNaviRouteNotifyData?) = Unit
    override fun onGpsSignalWeak(weak: Boolean) = Unit
}

internal sealed class NaviStatus {
    data class Calculating(val leg: NavigationLeg) : NaviStatus()
    data class Navigating(val leg: NavigationLeg) : NaviStatus()
    data class Failed(val message: String) : NaviStatus()

    /** The navigation engine cannot load on this device; retrying would fail the same way. */
    data class Unsupported(val message: String) : NaviStatus()
}

/**
 * One leg = one controller = one engine instance + view + listener + one route request.
 * Switching leg, retrying, leaving, rotating or switching account releases it completely (listener
 * removed, stopNavi, view destroyed, AMapNavi destroyed) before the next controller opens its own
 * engine, so an old leg's callbacks only ever reach its own released gate.
 */
internal class InAppNaviController(private val appContext: Context, val leg: NavigationLeg) : AMapNaviViewListener {
    var status by mutableStateOf<NaviStatus>(NaviStatus.Calculating(leg))
        private set
    var arrivalHint by mutableStateOf<String?>(null)
    var onExitRequested: (() -> Unit)? = null

    private val token = Any()
    private val main = Handler(Looper.getMainLooper())
    private val gate = LegRouteGate()
    private val listener = LegRouteListener(gate) { event -> onMain { handle(event) } }
    private var navi: AMapNavi? = null
    private var view: AMapNaviView? = null
    private var targetGcj: com.amap.api.maps.model.LatLng? = null

    /** Opens this leg's engine and view; returns null with a visible failure when it cannot. */
    fun open(context: Context): AMapNaviView? {
        if (!gate.live) return null
        if (!NaviOwnership.acquire(token)) {
            gate.engineFailed()
            status = NaviStatus.Failed("导航正在其他页面使用，请返回后重试")
            return null
        }
        try {
            AmapRuntime.prepareNavigation(appContext)
            val engine = AMapNavi.getInstance(appContext)
            navi = engine
            engine.addAMapNaviListener(listener)
            engine.setUseInnerVoice(true, true)
            view = AMapNaviView(context).apply {
                onCreate(null)
                setAMapNaviViewListener(this@InAppNaviController)
                viewOptions = viewOptions.apply {
                    setSettingMenuEnabled(false)
                    setRouteListButtonShow(false)
                    setLayoutVisible(true)
                }
            }
        } catch (e: Exception) {
            gate.engineFailed()
            status = NaviStatus.Failed(
                e.message?.takeIf { it.isNotBlank() && e is IllegalStateException } ?: "导航服务初始化失败，请检查网络后重试"
            )
            release()
            return null
        } catch (_: LinkageError) {
            // The SDK ships ARM navigation libraries only; on other ABIs (or a broken install) loading
            // fails here. Navigation is unavailable, the order controls below keep working.
            gate.engineFailed()
            status = NaviStatus.Unsupported("此设备暂不支持应用内导航（导航组件无法在本机加载）。请退出导航，订单操作不受影响。")
            release()
            return null
        }
        // The request normally starts on onInitNaviSuccess. These timers only keep the screen from
        // waiting forever; the gate still allows one request and one decision, whatever the timing.
        main.postDelayed({ requestRoute() }, 3_000)
        main.postDelayed({ handle(LegEvent.Decided(gate.timedOut())) }, 30_000)
        return view
    }

    private fun handle(event: LegEvent) {
        if (!gate.live) return
        when (event) {
            LegEvent.EngineReady -> requestRoute()
            LegEvent.NearTarget -> arrivalHint = when (leg.stop) {
                NavigationStop.PICKUP -> "导航提示已接近上车点，请在下方按实际情况操作"
                NavigationStop.WAYPOINT -> "导航提示已接近途经点「${leg.name}」，确认到达后才会前往下一站"
                NavigationStop.DESTINATION -> "导航提示已接近终点，请在下方按实际情况确认"
            }
            is LegEvent.Decided -> when (val d = event.decision) {
                is LegRouteGate.Decision.Start -> startGuidance(d.routeId)
                is LegRouteGate.Decision.Fail -> status = NaviStatus.Failed(d.message)
                LegRouteGate.Decision.Ignore -> Unit
            }
        }
    }

    private fun requestRoute() {
        val n = navi ?: return
        if (!gate.claimRequest()) return
        // Order points are WGS84; the SDK takes GCJ-02. Convert exactly once, here.
        val to = runCatching { amapForward(appContext, leg.point.latitude, leg.point.longitude) }.getOrNull()
        if (to == null) {
            if (gate.requestRejected()) status = NaviStatus.Failed("目的地坐标转换失败，请重试")
            return
        }
        targetGcj = to
        val start = NavigationSessionRules.reliableStart(PositionBus.position.value, System.currentTimeMillis())
            ?.let { runCatching { amapForward(appContext, it.latitude, it.longitude) }.getOrNull() }
        val strategy = runCatching { n.strategyConvert(true, false, false, false, false) }.getOrDefault(0)
        val end = listOf(NaviLatLng(to.latitude, to.longitude))
        val requested = runCatching {
            // Without a fresh accurate phone fix the SDK plans from its own GPS position; never 0,0.
            if (start != null) n.calculateDriveRoute(listOf(NaviLatLng(start.latitude, start.longitude)), end, emptyList(), strategy)
            else n.calculateDriveRoute(end, emptyList(), strategy)
        }.getOrDefault(false)
        if (!requested && gate.requestRejected()) status = NaviStatus.Failed("路线规划未能发起，请确认定位与网络后重试")
    }

    private fun startGuidance(routeId: Int?) {
        val n = navi ?: return
        if (gate.phase != LegRouteGate.Phase.NAVIGATING) return
        val end = runCatching { n.naviPath?.endPoint }.getOrNull()
        val target = targetGcj
        if (end == null || target == null ||
            !NavigationSessionRules.endpointMatches(end.latitude, end.longitude, target.latitude, target.longitude)
        ) {
            if (gate.startFailed()) status = NaviStatus.Failed("规划的路线终点与本段目的地不一致，请重试")
            return
        }
        routeId?.let { runCatching { n.selectRouteId(it) } }
        if (runCatching { n.startNavi(NaviType.GPS) }.getOrDefault(false)) status = NaviStatus.Navigating(leg)
        else if (gate.startFailed()) status = NaviStatus.Failed("导航未能启动，请确认已开启定位后重试")
    }

    fun release() {
        if (!gate.live) return
        gate.release()
        onExitRequested = null
        main.removeCallbacksAndMessages(null)
        navi?.let {
            runCatching { it.removeAMapNaviListener(listener) }
            runCatching { it.stopNavi() }
        }
        navi = null
        view?.let {
            runCatching { it.setAMapNaviViewListener(null) }
            runCatching { it.onDestroy() }
        }
        view = null
        if (NaviOwnership.release(token)) runCatching { AMapNavi.destroy() }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    override fun onNaviCancel() = onMain { onExitRequested?.invoke() }
    override fun onNaviBackClick(): Boolean {
        onMain { onExitRequested?.invoke() }
        return true
    }
    override fun onAMapNaviViewExit() = onMain { onExitRequested?.invoke() }
    override fun onNaviSetting() = Unit
    override fun onNaviMapMode(mode: Int) = Unit
    override fun onNaviTurnClick() = Unit
    override fun onNextRoadClick() = Unit
    override fun onScanViewButtonClick() = Unit
    override fun onLockMap(locked: Boolean) = Unit
    override fun onNaviViewLoaded() = Unit
    override fun onMapTypeChanged(type: Int) = Unit
    override fun onNaviViewShowMode(mode: Int) = Unit
    override fun onStopSpeaking() = Unit
    override fun onViewTypeChanged(type: AmapPageType?) = Unit
    override fun onStrategyChanged(strategy: Int) = Unit
    override fun onBroadcastModeChanged(mode: Int) = Unit
    override fun onDayAndNightModeChanged(mode: Int) = Unit
    override fun onScaleAutoChanged(auto: Boolean) = Unit
    override fun onListenToVoiceDuringCallChanged(listen: Boolean) = Unit
    override fun onControlMusicVolumeModeChanged(mode: Int) = Unit
    override fun onEagleChanged(eagle: Boolean) = Unit
    override fun onNaviRouteHighlightChange(pathId: Long, index: Int) = Unit
}

/**
 * Full SDK navigation (turn guidance, lanes, voice) for the current leg. The order's business
 * controls are composed by the caller outside this panel, so the SDK never covers them.
 */
@Composable
internal fun InAppNavigationPanel(
    decision: LegDecision,
    sessionKey: String,
    modifier: Modifier = Modifier,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val apiKey by AmapRuntime.key.collectAsState()
    val navigationConsent by AmapRuntime.navigationConsent.collectAsState()
    LaunchedEffect(context) { AmapRuntime.loadConsent(context) }
    if (!navigationConsent) {
        NavigationNotice(modifier, onAgree = { AmapRuntime.agreeNavigation(context) }, onExit = onExit)
        return
    }
    if (apiKey.isBlank()) {
        NavigationMessage(modifier, "地图配置暂不可用，请刷新连接后重试", null, onExit)
        return
    }
    if (decision is LegDecision.Unavailable) {
        // No engine at all while there is no valid target; the business area below stays usable.
        NavigationMessage(modifier, decision.reason, null, onExit)
        return
    }
    val leg = (decision as LegDecision.Go).leg
    var attempt by remember(sessionKey, leg.key) { mutableIntStateOf(0) }
    // A new leg or a retry is a brand-new controller; the old one is fully released first.
    val controller = remember(sessionKey, apiKey, leg.key, attempt) { InAppNaviController(context.applicationContext, leg) }
    var naviView by remember(controller) { mutableStateOf<AMapNaviView?>(null) }
    val exit by rememberUpdatedState(onExit)
    val owner = LocalLifecycleOwner.current
    // Keyed on the controller alone: create/resume/pause/destroy stay paired for exactly one view.
    DisposableEffect(controller) {
        controller.onExitRequested = { exit() }
        val view = controller.open(context)
        naviView = view
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!resumed) { view?.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { view?.onPause(); resumed = false }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { view?.onResume(); resumed = true }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (resumed) view?.onPause()
            controller.release()
        }
    }
    Box(modifier) {
        naviView?.let { view -> key(view) { AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) } }
        when (val s = controller.status) {
            is NaviStatus.Calculating -> StatusChip("正在规划去「${s.leg.name}」的路线…", Modifier.align(Alignment.TopCenter))
            is NaviStatus.Failed -> FailureCard(s.message, onRetry = { attempt++ }, onExit = onExit, Modifier.align(Alignment.Center))
            is NaviStatus.Unsupported -> FailureCard(s.message, onRetry = null, onExit = onExit, Modifier.align(Alignment.Center))
            is NaviStatus.Navigating -> Unit
        }
        controller.arrivalHint?.let { hint ->
            Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp), color = Color.White, shadowElevation = 2.dp) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(hint, fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
                    DriverTextButton(onClick = { controller.arrivalHint = null }) { Text("知道了") }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, modifier: Modifier) {
    Surface(modifier.padding(8.dp), color = Color.White.copy(alpha = .95f), shadowElevation = 2.dp) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, color = Ink)
    }
}

@Composable
private fun FailureCard(message: String, onRetry: (() -> Unit)?, onExit: () -> Unit, modifier: Modifier) {
    Surface(modifier.padding(16.dp), color = Color.White, shadowElevation = 3.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, fontSize = 15.sp, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onRetry?.let { DriverTextButton(onClick = it) { Text("重试导航") } }
                DriverTextButton(onClick = onExit) { Text("退出导航") }
            }
        }
    }
}

@Composable
private fun NavigationMessage(modifier: Modifier, message: String, onRetry: (() -> Unit)?, onExit: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) { FailureCard(message, onRetry, onExit, Modifier) }
}

@Composable
private fun NavigationNotice(modifier: Modifier, onAgree: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    Surface(modifier, color = Color.White) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("启用应用内导航", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(AmapRuntime.NAVIGATION_NOTICE, fontSize = 14.sp, lineHeight = 21.sp, color = Ink)
            Text("同时启用高德地图服务：处理设备信息、IP地址、搜索词和地图操作位置，用于地图展示与地点搜索。", fontSize = 13.sp, lineHeight = 20.sp, color = Muted)
            DriverTextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lbs.amap.com/pages/privacy/"))) }
            }) { Text("阅读高德隐私政策") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAgree) { Text("同意并开始导航") }
                DriverTextButton(onClick = onExit) { Text("暂不导航") }
            }
            Text("不同意不影响接驾、核对尾号、确认到达等订单操作。", fontSize = 12.sp, color = Muted)
        }
    }
}
