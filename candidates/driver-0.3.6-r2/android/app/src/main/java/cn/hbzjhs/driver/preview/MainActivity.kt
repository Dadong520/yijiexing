package cn.hbzjhs.driver.preview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        DriverUiPresence.visible = true
        NewOrderAlerts.notifyPresence()
    }

    override fun onStop() {
        DriverUiPresence.visible = false
        // The background overlay takes over the same reminder with its remaining time.
        NewOrderAlerts.notifyPresence()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val store: ConnectedStore = viewModel()
            val state by store.state.collectAsState()
            NativeTheme(state.largeText) { ConnectedApp(store, state) }
        }
    }
}

private data class CoreConfirm(
    val title: String,
    val message: String,
    val label: String = "确定",
    val action: () -> Unit,
)

@Composable
private fun ConnectedApp(store: ConnectedStore, state: DriverState) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val loading by store.loading.collectAsState()
    val error by store.error.collectAsState()
    var waitingForCloud by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        waitingForCloud = false
        if (loading) {
            delay(8000)
            waitingForCloud = true
        }
    }
    val initialized by store.initialized.collectAsState()
    val connected by store.connected.collectAsState()
    val payload by store.payload.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var orderFilter by rememberSaveable { mutableIntStateOf(0) }
    var stack by rememberSaveable { mutableStateOf(listOf("root")) }
    var confirm by remember { mutableStateOf<CoreConfirm?>(null) }
    var cancelOrder by rememberSaveable { mutableStateOf<String?>(null) }
    var cancelReason by rememberSaveable { mutableStateOf("") }
    var cancelOther by rememberSaveable { mutableStateOf("") }
    var wantsOnline by remember { mutableStateOf(false) }
    var showPermissionGuide by rememberSaveable { mutableStateOf(false) }
    var pendingFloatingEnable by rememberSaveable { mutableStateOf(false) }
    val requestedAlertOrder by NewOrderAlerts.openRequested.collectAsState()
    val holder = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }
    fun audio(res: Int) {
        if (state.voice && payload.optJSONObject("features")?.optBoolean("canVoice", false) == true)
            runCatching {
                MediaPlayer.create(context, res)?.apply {
                    setOnCompletionListener { it.release() }
                    start()
                }
            }
    }
    fun navigate(value: String) {
        when (value) {
            "permissions" -> showPermissionGuide = true
            "home" -> {
                tab = 0
                stack = listOf("root")
            }
            "orders" -> {
                tab = 1
                orderFilter = 0
                stack = listOf("root")
            }
            "orders/unpaid" -> {
                tab = 1
                orderFilter = 2
                stack = listOf("root")
            }
            "wallet" -> {
                tab = 2
                stack = listOf("root")
            }
            "certificates" -> stack = stack + "qualification"
            "support" -> stack = stack + "help"
            else -> if (stack.last() != value) stack = stack + value
        }
    }
    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                if (wantsOnline)
                    store.perform("driver.online", JSONObject().put("online", true)) {
                        audio(R.raw.new_order)
                    }
            } else message("未获得精确定位权限，无法开启接单定位。可在系统设置中授权后重试。")
            wantsOnline = false
        }
    fun startListening() {
        if (state.online) {
            // Offline really ends local candidate waiting; late results of the old session are dropped.
            store.perform("driver.online", JSONObject().put("online", false)) { GrabActions.endSession() }
            return
        }
        if (
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            store.perform("driver.online", JSONObject().put("online", true)) {
                audio(R.raw.new_order)
            }
        } else {
            wantsOnline = true
            permissions.launch(
                if (Build.VERSION.SDK_INT >= 33)
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                else
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
            )
        }
    }
    LaunchedEffect(error) { error?.let { message(it) } }
    LaunchedEffect(state.loggedIn) {
        if (!state.loggedIn) {
            stack = listOf("root")
            tab = 0
        } else
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(10000)
                    store.refresh(silent = true)
                }
            }
    }
    LaunchedEffect(
        state.loggedIn,
        connected,
        state.online,
        state.activeOrder?.id,
        state.voice,
        payload.optJSONObject("features")?.optBoolean("canVoice"),
        state.floating,
        payload.optJSONObject("features")?.optBoolean("canFloating"),
    ) {
        val intent =
            Intent(context, DriverLocationService::class.java)
                .putExtra("listening", state.online)
                .putExtra(
                    "voiceAllowed",
                    state.voice &&
                        state.online &&
                        state.activeOrder == null &&
                        payload.optJSONObject("features")?.optBoolean("canVoice") == true,
                )
                .putExtra("floatingEnabled", state.floating)
                .putExtra(
                    "floatingAllowed",
                    payload.optJSONObject("features")?.optBoolean("canFloating", false) == true,
                )
        if (
            state.loggedIn &&
                connected &&
                (state.online || state.activeOrder != null) &&
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { context.startForegroundService(intent) }
                .onFailure { message("定位服务暂未启动，请回到首页重新开启听单") }
        } else context.stopService(intent)
    }
    BackHandler(state.loggedIn && stack.size > 1) { back() }
    val guideShowing = DriverPermissions(showPermissionGuide, { showPermissionGuide = false }) {
        if (!state.loggedIn || !connected || loading) {
            pendingFloatingEnable = true
            message("已记录开启意愿，登录后会核对权益并开启应用浮窗提醒")
        } else if (payload.optJSONObject("features")?.optBoolean("canFloating") == true)
            store.perform("settings.update", JSONObject().put("floating", true))
        else message("当前城市或等级未开放浮窗提醒，已保留系统通知和应用内新单提示")
    }
    LaunchedEffect(pendingFloatingEnable, state.loggedIn, connected, loading) {
        if (pendingFloatingEnable && state.loggedIn && connected && !loading) {
            pendingFloatingEnable = false
            if (payload.optJSONObject("features")?.optBoolean("canFloating") == true && android.provider.Settings.canDrawOverlays(context))
                store.perform("settings.update", JSONObject().put("floating", true))
            else message("浮窗尚未启用，请在权限与提醒检查中核对系统授权和当前权益")
        }
    }
    val route = stack.last()
    // The one in-app navigation owner (account + order). Saveable so rotation keeps navigation mode;
    // it holds only ids. Any account/order/stage change or leaving the trip page ends it.
    var navOwnerValue by rememberSaveable { mutableStateOf<String?>(null) }
    val navOwner = NavigationOwner.decode(navOwnerValue)
    val account = driverAccount(payload)
    fun openAlertOrder(orderId: String, bid: Boolean) {
        store.refresh()
        navigate(if (bid) "bid:$orderId" else "detail:$orderId")
    }
    // An overlay tap on a BID/detail reminder; consumed once and only for the account it came from.
    LaunchedEffect(requestedAlertOrder, state.loggedIn, account) {
        if (requestedAlertOrder == null || !state.loggedIn || account.isBlank()) return@LaunchedEffect
        val key = NewOrderAlerts.consumeOpen(account) ?: return@LaunchedEffect
        val kind = state.orders.firstOrNull { it.id == key.orderId }?.kind
        openAlertOrder(key.orderId, kind == OrderKind.BID)
    }
    if (!guideShowing && state.loggedIn)
        NewOrderDialog(state.orders, onOpen = { alert -> openAlertOrder(alert.key.orderId, alert.bid) }, onRejected = ::message)
    fun openNavigation(orderId: String) {
        val currentAccount = driverAccount(store.payload.value)
        val latest = store.state.value.orders.firstOrNull { it.id == orderId }
        val decision = NavigationSessionRules.legFor(latest)
        if (decision is LegDecision.Unavailable) {
            message(decision.reason)
            return
        }
        if (currentAccount.isBlank()) {
            message("账号状态同步中，请稍后再试")
            return
        }
        navOwnerValue = NavigationOwner(currentAccount, orderId).encode()
        if (stack.last() != "detail:$orderId") navigate("detail:$orderId")
    }
    LaunchedEffect(navOwner, account, state, route, initialized) {
        val owner = navOwner ?: return@LaunchedEffect
        if (!initialized) return@LaunchedEffect
        val reason =
            NavigationSessionRules.exitReason(owner, account, state.loggedIn, state.orders.firstOrNull { it.id == owner.orderId }, route)
                ?: return@LaunchedEffect
        navOwnerValue = null
        if (reason.isNotBlank()) message(reason)
    }
    AutoNavigationObserver(store, ::openNavigation)
    // Grab results land once, for the same account and session generation only (consumeResults).
    // Only a server-confirmed own assignment from a foreground click, re-checked against the store's
    // current row (same account, owned stage, same round, a navigable leg), follows the existing
    // post-accept entry. WON history, pending, lost, failed and background results only report.
    val grabResults by GrabActions.results.collectAsState()
    LaunchedEffect(grabResults, account) {
        if (grabResults.isEmpty() || account.isBlank()) return@LaunchedEffect
        GrabActions.consumeResults(account).forEach { result ->
            val outcome = result.outcome
            message(GrabOutcomes.message(outcome))
            val row = store.state.value.orders.firstOrNull { it.id == outcome.orderId }
            val confirmed =
                result.foreground && DriverUiPresence.visible && GrabOutcomes.mayNavigate(outcome) &&
                    driverAccount(store.payload.value) == result.key.account && row != null &&
                    row.stage in setOf(Stage.PICKUP, Stage.RESERVED, Stage.ARRIVED, Stage.TRIP) &&
                    (row.competition == null || row.competition.round.toString() == result.key.round) &&
                    NavigationSessionRules.legFor(row) !is LegDecision.Unavailable
            if (confirmed && row != null) {
                audio(R.raw.success_order)
                if (stack.last() == "root" && tab == 0) navigate("detail:${row.id}")
                navigateForNewLeg(context, store, row.id, Stage.AVAILABLE, ::openNavigation)
            } else if (outcome !is GrabOutcome.Assigned) store.refresh(silent = true)
        }
    }
    /** The one grab entry for hall, detail and confirm: competition orders join with their round. */
    fun grabOrder(order: DriverOrder) {
        if (order.kind == OrderKind.BID) {
            navigate("bid:${order.id}")
            return
        }
        val competition = order.competition
        if (competition == null) {
            // No competition object (private examples, other kinds): the original confirm + accept.
            confirm =
                CoreConfirm("确认接单", NavigationSessionRules.routeSummary(order), "确认接单") {
                    store.perform("order.accept", JSONObject().put("id", order.id)) {
                        if (stack.last() == "root" && tab == 0) navigate("detail:${order.id}")
                        audio(R.raw.success_order)
                        navigateForNewLeg(context, store, order.id, order.stage, ::openNavigation)
                    }
                }
            return
        }
        when (GrabActions.availability(account, order)) {
            GrabAvailability.CAN_GRAB ->
                confirm =
                    CoreConfirm("确认抢单", NavigationSessionRules.routeSummary(order) + "\n\n" + COMPETITION_NOTE, "确认抢单") {
                        // Re-checked at click time: an expired window or another round is refused.
                        GrabActions.submit(competitionKey(account, order.id, competition), foreground = true)?.let(::message)
                    }
            GrabAvailability.COMPETING -> message("已在竞抢中，等待派单结果")
            GrabAvailability.CLOSED_WAITING -> {
                message("本轮已截止，等待服务端派单结果")
                store.refresh()
            }
            else -> message("该订单本轮不可抢，请刷新订单大厅")
        }
    }
    // A collapsed map panel on the page shown right now hides the tabs (full-screen map). Only a claim
    // for this exact page (this account's listening home, or this order's trip) counts, so leaving the
    // page, signing out or switching account always brings the tabs back.
    val mapChrome = remember { MapChromeHost() }
    val mapScope =
        if (!state.loggedIn) null
        else if (route == "root" && tab == 0) driverHomeMapScope(driverAccount(payload))
        else if (route.startsWith("detail:")) driverTripMapScope(route.substringAfter(':'))
        else null
    val chromeHidden = mapChrome.hiddenFor(mapScope)
    Box(Modifier.fillMaxSize().background(Page), contentAlignment = Alignment.TopCenter) {
        CompositionLocalProvider(LocalMapChromeHost provides mapChrome) {
        Scaffold(
            Modifier.fillMaxSize().widthIn(max = 650.dp),
            containerColor = Page,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (state.loggedIn && route == "root" && !chromeHidden)
                    NavigationBar(
                        modifier = Modifier.heightIn(min = 72.dp),
                        containerColor = Color.White,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        listOf(
                                "听单" to "home",
                                "订单" to "orders",
                                "钱包" to "wallet",
                                "我的" to "profile",
                            )
                            .forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    tab == i,
                                    { tab = i },
                                    icon = { Glyph(icon, if (tab == i) Green else Muted, Modifier.size(24.dp)) },
                                    label = { Text(label, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium) },
                                    colors =
                                        NavigationBarItemDefaults.colors(
                                            indicatorColor = Pale,
                                            selectedTextColor = Green,
                                            unselectedTextColor = Muted,
                                        ),
                                )
                            }
                    }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (!initialized)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                else if (!state.loggedIn) LocalLogin(store)
                else
                    holder.SaveableStateProvider(if (route == "root") "root-$tab" else route) {
                        when {
                            route == "root" ->
                                when (tab) {
                                    0 ->
                                        ConnectedHome(store, state, ::navigate, ::startListening, ::grabOrder)
                                    1 -> ConnectedOrders(state, ::navigate, orderFilter) { orderFilter = it }
                                    2 -> ConnectedWallet(store, state, ::navigate)
                                    else ->
                                        ConnectedProfile(
                                            store,
                                            state,
                                            ::navigate,
                                            { tab = 1 },
                                            { tab = 2 },
                                        )
                                }
                            route.startsWith("benefits") ->
                                MembershipEntry(route, store, ::back, ::navigate, ::message)
                            route.startsWith("detail:") -> {
                                val id = route.substringAfter(':')
                                val order = state.orders.find { it.id == id }
                                if (order == null) InfoPage("订单不存在", ::back, "请返回订单列表重新选择。")
                                else
                                    ConnectedTrip(
                                        order,
                                        store,
                                        ::back,
                                        ::navigate,
                                        onCancel = {
                                            cancelReason = ""
                                            cancelOther = ""
                                            cancelOrder = id
                                        },
                                        onMessage = ::message,
                                        onHome = { navigate("home") },
                                        navigating = navOwner != null && navOwner.orderId == id && navOwner.account == account,
                                        onNavigate = ::openNavigation,
                                        onExitNavigation = { navOwnerValue = null },
                                        onGrab = ::grabOrder,
                                        onWithdraw = {
                                            cancelReason = ""
                                            cancelOther = ""
                                            cancelOrder = id
                                        },
                                    )
                            }
                            route.startsWith("bid:") -> {
                                val id = route.substringAfter(':')
                                val order = state.orders.find { it.id == id }
                                if (order != null)
                                    ConnectedBid(order, store, ::back) {
                                        if (stack.last() == "bid:$id")
                                            stack = stack.dropLast(1) + "detail:$id"
                                    }
                                else InfoPage("订单不存在", ::back, "请刷新订单列表。")
                            }
                            route == "negotiate" ->
                                ConnectedNegotiation(store, ::back) { id ->
                                    if (stack.last() == "negotiate")
                                        stack = stack.dropLast(1) + "detail:$id"
                                }
                            route == "settings" ->
                                ConnectedSettings(store, state, ::back, ::message, ::navigate)
                            route == "heat" -> ConnectedHeat(store, ::back)
                            route == "notifications" ->
                                ConnectedNotifications(store, ::back, ::navigate)
                            else -> BusinessEntry(route, store, ::back, ::navigate, ::message)
                        }
                    }
            }
        }
        }
        if (loading)
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .08f)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(23.dp), strokeWidth = 2.dp)
                        Column(Modifier.padding(start = 12.dp).widthIn(max = 240.dp)) {
                            Text(if (waitingForCloud) "云端服务正在响应…" else "正在处理…")
                            if (waitingForCloud)
                                Text(
                                    "首次使用可能需要启动，请稍候；请勿重复提交支付或提现。",
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp,
                                    color = Muted,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                        }
                    }
                }
            }
    }
    confirm?.let { c ->
        DriverDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.message, modifier = Modifier.verticalScroll(rememberScrollState()), color = Muted) },
            confirmButton = {
                DriverTextButton(
                    onClick = {
                        confirm = null
                        c.action()
                    }
                ) {
                    Text(c.label)
                }
            },
            dismissButton = { DriverTextButton(onClick = { confirm = null }) { Text("返回") } },
        )
    }
    cancelOrder?.let { id ->
        val cancelTarget = state.orders.firstOrNull { it.id == id }
        // An AVAILABLE competition row here is this driver's own candidacy: withdrawing leaves only
        // the current round and never cancels the passenger's order.
        val withdrawing = cancelTarget?.stage == Stage.AVAILABLE && cancelTarget.competition != null
        DriverDialog(
            onDismissRequest = { cancelOrder = null },
            title = { Text(if (withdrawing) "退出本轮竞抢" else "取消订单") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (withdrawing) "仅退出你本人在本轮的竞抢，不会取消乘客订单。请选择原因。"
                        else "请选择实际取消原因，提交后将保存到订单记录。"
                    )
                    listOf("乘客要求取消", "无法联系乘客", "车辆临时故障", "其他原因").forEach { reason ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected = cancelReason == reason, role = Role.RadioButton, onClick = { cancelReason = reason }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(cancelReason == reason, onClick = null)
                            Text(reason, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                    if (cancelReason == "其他原因")
                        OutlinedTextField(
                            cancelOther,
                            { cancelOther = it.take(160) },
                            label = { Text("请说明原因") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge,
                        )
                }
            },
            confirmButton = {
                DriverTextButton(
                    modifier = Modifier.heightIn(min = 52.dp),
                    enabled =
                        cancelReason.isNotBlank() &&
                            (cancelReason != "其他原因" || cancelOther.isNotBlank()),
                    onClick = {
                        val reason =
                            if (cancelReason == "其他原因") cancelOther.trim() else cancelReason
                        cancelOrder = null
                        // GRAB V1: competition orders carry the round read from state, so a cancel or
                        // withdraw can never land on a newer round of the same order id.
                        val cancelBody = JSONObject().put("id", id).put("reason", reason)
                        cancelTarget?.competition?.let { cancelBody.put("competitionRound", it.round) }
                        store.perform("order.cancel", cancelBody)
                    },
                ) {
                    Text("确认取消", fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { DriverTextButton(onClick = { cancelOrder = null }) { Text("继续行程") } },
        )
    }
}
