package cn.hbzjhs.driver.preview

import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.AnimatedVisibility
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
fun LocalLogin(store: ConnectedStore) {
    var inviteCode by rememberSaveable { mutableStateOf("") }
    var selectedPhone by rememberSaveable { mutableStateOf("13800000003") }
    var connectionDialog by remember { mutableStateOf(false) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    val loading by store.loading.collectAsState()
    val sessionAvailable by store.sessionAvailable.collectAsState()
    val connectionError by store.error.collectAsState()
    val driverAccounts = listOf(
        "13800000001" to "体验司机01 (13800000001)",
        "13800000002" to "体验司机02 (13800000002)",
        "13800000003" to "体验司机03 (13800000003)",
    )

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            CoreTextButton(
                onClick = { connectionDialog = true }
            ) {
                Text("连接状态", fontSize = 15.sp)
            }
        }
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.yijiexing_welcome_hero),
            contentDescription = "宜捷行出租车与司机品牌插画",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.height(18.dp))
        Text("宜捷行 · 司机", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green)
        Text("欢迎回来，师傅", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Text("选择体验账号，一键进入", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))

        if (!sessionAvailable && connectionError != null) {
            Spacer(Modifier.height(12.dp))
            SheetCard {
                Text(
                    "连接未完成",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    connectionError!!,
                    fontSize = 15.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                CoreTextButton(
                    onClick = { connectionDialog = true }
                ) {
                    Text("查看连接状态")
                }
            }
        }
        if (sessionAvailable) {
            Spacer(Modifier.height(14.dp))
            SheetCard {
                Text("服务连接提示", fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    connectionError ?: "若遇网络波动或服务休眠，可点下方按钮重试连接。",
                    fontSize = 15.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("重试连接", onClick = { store.refresh() })
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("选择体验司机账号", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { accountMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("体验司机${selectedPhone.takeLast(2)}", fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("切换账号", fontSize = 14.sp)
            }
            DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                driverAccounts.forEach { (phone, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { selectedPhone = phone; accountMenu = false }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }

        OutlinedTextField(inviteCode, { inviteCode = it.take(20) }, label = { Text("邀请码（选填，首次注册有效）") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = agreed,
                    role = Role.Checkbox,
                    onValueChange = { agreed = it },
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(agreed, onCheckedChange = null)
            Text(
                "我已了解体验说明和共享演示数据范围",
                fontSize = 15.sp,
                color = Ink,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            if (loading) "正在进入…" else "进入体验",
            enabled = !loading && agreed,
            onClick = {
                store.loginExperience(selectedPhone, inviteCode)
            },
        )

        Spacer(Modifier.height(20.dp))
        SheetCard {
            Text("体验说明", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "体验账号共用演示数据；支付、退款为模拟，不会真实扣款。请使用演示资料。",
                color = Muted,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "本应用用于独立演示验证。选择账号即可体验司机抢单、计价与行程管理；未对接真实短信、实名认证与银行结算。",
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            "本版不连接现有测试或生产环境",
            fontSize = 14.sp,
            color = Muted,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }

    if (connectionDialog) {
        CloudConnectionDialog(
            store = store,
            onDismiss = { connectionDialog = false },
        )
    }
}

@Composable
private fun CloudConnectionDialog(
    store: ConnectedStore,
    reason: String? = null,
    onDismiss: () -> Unit,
) {
    val connectionError by store.error.collectAsState()
    val sessionAvailable by store.sessionAvailable.collectAsState()
    DriverDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接状态") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                reason?.let {
                    Text(
                        it,
                        color = Green,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "联网后选择体验账号即可进入；已有登录状态时可以重试连接。",
                    fontSize = 15.sp,
                    color = Ink,
                )
                if (connectionError != null) {
                    Text(
                        connectionError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                    )
                }
            }
        },
        confirmButton = {
            CoreTextButton(
                onClick = {
                    if (sessionAvailable) store.refresh() else onDismiss()
                }
            ) {
                Text(if (sessionAvailable) "重试连接" else "返回体验")
            }
        },
        dismissButton = {
            CoreTextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
fun ConnectedHome(
    store: ConnectedStore,
    state: DriverState,
    navigate: (String) -> Unit,
    onToggle: () -> Unit,
    onGrab: (DriverOrder) -> Unit,
) {
    val payload by store.payload.collectAsState()
    val online by store.connected.collectAsState()
    val profile = payload.optJSONObject("profile")?.optJSONObject("driver") ?: JSONObject()
    val features = payload.optJSONObject("features") ?: JSONObject()
    val canAccept = features.optBoolean("canAcceptOrders", false)
    val active = state.activeOrder
    val pendingCollections = state.pendingCollections
    val waitingReservations = state.waitingReservations
    val allAvailable = state.orders.filter { it.stage == Stage.AVAILABLE }.sortedBy { it.isPrivateExample }
    val account = driverAccount(payload)
    val grabVersion by GrabActions.version.collectAsState()
    val hasHotCompetition = allAvailable.any {
        it.competition?.status == "OPEN" ||
            GrabActions.availability(account, it) == GrabAvailability.COMPETING
    }
    // 5 秒竞拍窗口或本地正在等结果时，本地按 250ms 刷新倒计时条和秒数（零网络请求）。
    val grabTick by produceState(0, hasHotCompetition) {
        while (hasHotCompetition) {
            kotlinx.coroutines.delay(250)
            value++
        }
    }
    val positionStatus by PositionBus.status.collectAsState()
    // Shown when location is abnormal while listening/in-trip, or when an explicit error/off status persists.
    val locationIssue = positionStatus.takeIf {
        !it.startsWith("已定位") && !it.startsWith("正在") && (state.online || active != null || it != "未启动定位")
    }
    val availableKinds = OrderKind.values().filter { kind -> allAvailable.any { it.kind == kind } }
    var kindFilter by rememberSaveable { mutableStateOf<String?>(null) }
    // A filter only applies while its kind is still present and there is more than one kind to pick from;
    // otherwise it is cleared so a stale, invisible filter can never hide every order.
    val selectedKind = availableKinds.takeIf { it.size > 1 }?.firstOrNull { it.name == kindFilter }
    LaunchedEffect(selectedKind, kindFilter) {
        if (kindFilter != null && selectedKind == null) kindFilter = null
    }
    val available = if (selectedKind == null) allAvailable else allAvailable.filter { it.kind == selectedKind }

    Box(Modifier.fillMaxSize().background(Page)) {
        // The identity bar sits above the map (never on top of it). The order sheet can be dragged
        // over the map or down to a handle; at half height it keeps ~360dp so hall/in-trip actions
        // stay reachable on small screens and with large text, and the map gives way first.
        // A collapsed sheet asks for a full-screen map: the qualification banner and identity bar step
        // aside with the tabs and come back as soon as the sheet opens. Gates and data are untouched.
        val homeScope = driverHomeMapScope(driverAccount(payload))
        val chromeHidden = LocalMapChromeHost.current?.hiddenFor(homeScope) == true
        Column(Modifier.fillMaxSize()) {
            if (!chromeHidden && payload.optJSONObject("operations")?.optJSONObject("company")?.optString("splitAccountStatus") == "UNSIGNED")
                Text("所属公司尚未开通模拟分账账户，开通后可上线接单", color = Orange, modifier = Modifier.fillMaxWidth().padding(12.dp))
            if (!chromeHidden) DriverQualificationBanner(payload, navigate)
            if (!chromeHidden) Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        Modifier.clickable { navigate("driver") },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(36.dp)
                                .clip(CircleShape)
                                .background(Pale),
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph("profile", Ink, Modifier.size(20.dp))
                        }
                        Column(Modifier.padding(start = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("宜捷行·司机", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Spacer(Modifier.width(6.dp))
                                Pill(profile.optString("cityName", "宜昌市"))
                            }
                            Text(
                                profile.optString("name", "司机"),
                                fontSize = 12.sp,
                                color = Muted,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(if (online) "网络正常" else "离线", if (online) Green else Orange)
                        IconButton(
                            onClick = { navigate("notifications") },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "消息通知" },
                        ) {
                            Glyph("bell", Ink, Modifier.size(22.dp))
                        }
                    }
                }
            }
            // An in-trip or listening-state change brings a collapsed sheet back so its actions stay findable.
            MapPanelLayout(
                saveKey = homeScope,
                modifier = Modifier.fillMaxWidth().weight(1f),
                attentionKey = listOf(active?.id, active?.stage, state.online, canAccept, online),
                collapsedLabel = if (active != null) "显示当前行程" else "显示听单与订单大厅",
                minHalfContent = 300.dp,
                containerColor = Page,
                map = { mapModifier -> NativeMap(mapModifier, order = active) },
                bottomBar = if (state.online && active == null) ({
                    Surface(color = Color.White, border = BorderStroke(1.dp, Line)) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            CancelActionButton(
                                "点击下线 · 停止听单",
                                modifier = Modifier.fillMaxWidth(),
                                enabled = online,
                                onClick = onToggle,
                            )
                        }
                    }
                }) else null,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!online)
                        item {
                            SheetCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Glyph("shield", Orange, Modifier.size(22.dp))
                                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                        Text("网络连接中断", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Orange)
                                        Text("当前展示离线缓存，恢复连接后方可进行接单操作", color = Muted, fontSize = 12.sp)
                                    }
                                    CoreTextButton(onClick = { store.refresh() }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("重试", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    // Location problem while listening or on a trip: the status plus a real retry button.
                    if (locationIssue != null)
                        item {
                            SheetCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(36.dp).background(WarmSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                        Glyph("location", Warm, Modifier.size(20.dp))
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text("定位异常", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Warm)
                                        Text(locationIssue, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                                    }
                                    BusinessOutlinedButton(onClick = { navigate("relocate") }) {
                                        Text("重新定位", fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    if (active != null)
                        item {
                            SheetCard {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Pill(active.stage.title)
                                    Text("进行中行程", color = Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(8.dp))
                                RouteStops(active)
                                PassengerRemarkLine(active)
                                Spacer(Modifier.height(10.dp))
                                PrimaryButton("继续当前行程", onClick = { navigate("detail:${active.id}") })
                            }
                        }
                    else if (!canAccept)
                        item {
                            SheetCard {
                                Text("当前接单权益不可用", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Text(
                                    features.optString("trialEndReason", "请查看权益状态或开通适用城市等级。"),
                                    color = Muted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Spacer(Modifier.height(10.dp))
                                PrimaryButton("查看会员权益", onClick = { navigate("benefits/expiry") })
                            }
                        }
                    else if (!state.online)
                        item {
                            SheetCard {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("当前休息中", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                                        Text("上线后将实时接收附近订单派单", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                                    }
                                    Button(
                                        onClick = onToggle,
                                        enabled = online,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                                        modifier = Modifier.height(48.dp).widthIn(min = 100.dp)
                                    ) {
                                        Text("开始听单", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    if (state.online && active == null)
                        item {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Line),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(Green))
                                        Column(Modifier.padding(start = 8.dp)) {
                                            Text("正在听单中", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Green)
                                            Text("保持手机畅通，留心语音播报", color = Muted, fontSize = 12.sp)
                                        }
                                    }
                                    if (!positionStatus.startsWith("已定位")) {
                                        DriverTextButton(onClick = { navigate("relocate") }) {
                                            Text("重新定位", fontSize = 13.sp)
                                        }
                                    } else {
                                        Pill("实时接单中", GreenStrong, Pale)
                                    }
                                }
                            }
                        }
                    if (waitingReservations.isNotEmpty()) {
                        item { SectionTitle("待出发预约", "${waitingReservations.size} 笔 · 可继续接即时单") }
                        items(waitingReservations, key = { "reservation:" + it.id }) { order ->
                            OrderCard(order, "查看预约 · ${order.scheduledTimeText}") { navigate("detail:${order.id}") }
                        }
                    }
                    if (pendingCollections.isNotEmpty())
                        item {
                            Surface(
                                color = Pale,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Line),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("待收款 ${pendingCollections.size} 笔 · 不影响继续接单", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                                        val examples = pendingCollections.count { it.isPrivateExample }
                                        if (examples > 0)
                                            Text("含 ${examples} 笔演示订单，未关联乘客 App", color = Muted, fontSize = 12.sp)
                                    }
                                    CoreTextButton(onClick = { navigate("orders/unpaid") }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("查看", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Green)
                                    }
                                }
                            }
                        }
                    if (active == null && canAccept && state.online) {
                        item { SectionTitle("订单大厅", "附近实时订单 ${allAvailable.size} 笔", "接单设置") { navigate("settings") } }
                        if (availableKinds.size > 1)
                            item {
                                val hallItems = listOf("全部 ${allAvailable.size}") +
                                    availableKinds.map { kind -> "${kind.title} ${allAvailable.count { it.kind == kind }}" }
                                val hallIdx = selectedKind?.let { availableKinds.indexOf(it) + 1 } ?: 0
                                DriverScrollableTabs(
                                    items = hallItems,
                                    selectedIndex = hallIdx,
                                    onSelect = { idx ->
                                        kindFilter = if (idx == 0) null else availableKinds.getOrNull(idx - 1)?.name
                                    },
                                )
                            }
                        if (available.isEmpty())
                            item {
                                EmptyState(
                                    "orders",
                                    "当前暂无可接订单",
                                    "保持听单，新订单会语音播报并弹出提示",
                                    "刷新订单",
                                ) { store.refresh() }
                            }
                        items(available, key = { it.id }) { order ->
                            val availability = GrabActions.availability(account, order)
                            val running = GrabActions.competitionCountdown(account, order) as? CountdownPresentation.Competition.Running
                            val label = if (order.kind == OrderKind.BID) "参与竞价" else competitionLabel(account, order, grabVersion, grabTick)
                            val canTap = order.kind == OrderKind.BID || availability == GrabAvailability.CAN_GRAB
                            val progress = when {
                                order.kind == OrderKind.BID -> null
                                running != null -> (running.seconds / 5f).coerceIn(0f, 1f)
                                availability == GrabAvailability.COMPETING -> 0.5f
                                else -> null
                            }
                            val hint = when {
                                order.kind == OrderKind.BID -> null
                                availability == GrabAvailability.COMPETING ->
                                    running?.let { "5 秒竞抢等待中 · 约 ${it.seconds} 秒后公布派单结果" } ?: "5 秒竞抢等待中 · 正在汇总周边司机"
                                availability == GrabAvailability.CLOSED_WAITING -> "本轮 5 秒竞抢已结束 · 正在等待服务端派单"
                                order.competition?.status == "OPEN" ->
                                    "5 秒公平竞抢中 · 剩 ${running?.seconds ?: 0} 秒（最近合格司机获派）"
                                order.shared && order.competition != null -> "点击立即加入 5 秒公平竞抢 · 距乘客更近优先获派"
                                else -> null
                            }
                            OrderCard(
                                order = order,
                                action = label,
                                actionEnabled = canTap,
                                competitionHint = hint,
                                competitionProgress = progress,
                            ) { onGrab(order) }
                        }
                    }
                    item {
                        Text(
                            "体验环境 · 业务数据仅供模拟展示",
                            fontSize = 12.sp,
                            color = Muted,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectedProfile(
    store: ConnectedStore,
    state: DriverState,
    navigate: (String) -> Unit,
    onOrders: () -> Unit,
    onWallet: () -> Unit,
) {
    val payload by store.payload.collectAsState()
    val positionStatus by PositionBus.status.collectAsState()
    val speechStatus by OrderAnnouncementStatus.status.collectAsState()
    val profile = payload.optJSONObject("profile") ?: JSONObject()
    val features = payload.optJSONObject("features") ?: JSONObject()
    val d = profile.optJSONObject("driver") ?: JSONObject()
    val certificates = profile.bList("certificates")
    val (qualText, qualColor, qualNeedsAction) = qualificationSummary(certificates)
    var showEndpoint by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Gap.L, vertical = Gap.M),
        verticalArrangement = Arrangement.spacedBy(Gap.M),
    ) {
        item { PageHeader("司机之家", action = "听单设置", onAction = { navigate("settings") }) }
        item { DriverQualificationBanner(payload, navigate) }
        if (payload.optJSONObject("operations")?.optJSONObject("company")?.optString("splitAccountStatus") == "UNSIGNED") item {
            BusinessHint("所属公司尚未开通模拟分账账户，开通后可上线接单")
        }
        item {
            // Gradient identity card: avatar, name, vehicle plate, organization and service score badge.
            val name = d.optString("name", "司机")
            val org = d.optString("organization", d.optString("company", ""))
            val plate = d.optString("licensePlate", d.optString("plate", "未完善车辆资料"))
            val serviceScoreObj = profile.optJSONObject("serviceScore")
            val scoreValue = serviceScoreObj?.optInt("value", serviceScoreObj.optInt("score", -1)) ?: -1
            Surface(
                onClick = { navigate("driver") },
                shape = RoundedCornerShape(16.dp),
                color = Green,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Green, GreenStrong)))
                        .drawBehind {
                            drawCircle(Color.White.copy(alpha = 0.08f), radius = size.height * 0.85f, center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, 0f))
                            drawCircle(Color.White.copy(alpha = 0.05f), radius = size.height * 0.5f, center = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height))
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(name.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GreenStrong)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${name}师傅，您好", fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Pill(
                                if (scoreValue > 0) "服务分 ${scoreValue} 分" else "服务分 暂未考核",
                                Color(0xFF1B2B29),
                                Color(0xFFF3D08A),
                            )
                        }
                        Text(
                            plate + if (org.isNotBlank()) " · $org" else "",
                            fontSize = DriverType.Caption,
                            color = Color.White.copy(alpha = 0.86f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Glyph("right", Color.White.copy(alpha = 0.85f), Modifier.size(18.dp))
                }
            }
        }
        // Membership identity is the highlighted card; orders and wallet stay in the bottom bar.
        item { MembershipIdentityCard(store, navigate) }
        item {
            SheetCard {
                Text("运营明细（累计） · 含演示数据", fontSize = DriverType.Title, fontWeight = FontWeight.Bold, color = Ink)
                Row(
                    Modifier.fillMaxWidth().padding(top = Gap.M),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf(
                        Triple(if (state.online) "出车中" else "休息中", "在线状态", if (state.online) Green else Muted),
                        Triple("¥${money(state.todayIncome)}", "累计收入", Warm),
                        Triple("${state.todayAccepted}", "累计接单", Ink),
                        Triple("${state.todayCompleted}", "累计完单", Ink),
                    ).forEach { (value, label, color) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, fontSize = DriverType.Title, fontWeight = FontWeight.Bold, color = color)
                            Text(label, fontSize = DriverType.Caption, color = Muted, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
        // Clean 2x2 grid for live status (location + voice) and driving tools (negotiation + heatmap).
        item {
            SheetCard {
                ProfileGroupTitle("出车工具与状态")
                Spacer(Modifier.height(6.dp))
                val locHealthy = positionStatus.startsWith("已定位")
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Gap.S)) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (locHealthy) Pale else WarmSoft)
                            .clickable(role = Role.Button, onClick = { navigate("relocate") })
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Glyph("location", if (locHealthy) GreenStrong else Warm, Modifier.size(16.dp))
                            Text("定位状态", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted)
                        }
                        Text(
                            positionStatus,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (locHealthy) GreenStrong else Warm,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Text(
                            "点击重新定位 ›",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (locHealthy) Green else Warm,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceMuted)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Glyph("voice", IconTone, Modifier.size(16.dp))
                            Text("语音播报", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted)
                        }
                        Text(
                            if (state.online) speechStatus else "休息中",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Text(
                            if (state.voice) "已开启提醒" else "已静音",
                            fontSize = 11.sp,
                            color = Muted,
                        )
                    }
                }
                Spacer(Modifier.height(Gap.S))
                val canNeg = features.optBoolean("canNegotiate") && state.activeOrder == null
                val canHeat = features.optBoolean("canHeatmap")
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Gap.S)) {
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canNeg) IndigoSoft else SurfaceMuted)
                            .clickable(enabled = canNeg, role = Role.Button) { navigate("negotiate") }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                            Glyph("scan", if (canNeg) Indigo else DisabledText, Modifier.size(17.dp))
                        }
                        Column {
                            Text("线下议价", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (canNeg) Ink else DisabledText)
                            Text(if (features.optBoolean("canNegotiate")) (if (state.activeOrder == null) "现场配对" else "行程中") else "未开放", fontSize = 11.sp, color = Muted)
                        }
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canHeat) SkySoft else SurfaceMuted)
                            .clickable(enabled = canHeat, role = Role.Button) { navigate("heat") }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                            Glyph("chart", if (canHeat) Sky else DisabledText, Modifier.size(17.dp))
                        }
                        Column {
                            Text("热力分布", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (canHeat) Ink else DisabledText)
                            Text(if (canHeat) "客流热区" else "未开放", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
                if (!features.optBoolean("canNegotiate"))
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("线下议价未开放：当前等级或城市暂未包含", color = Muted, fontSize = DriverType.Caption, modifier = Modifier.weight(1f))
                        DriverTextButton(onClick = { navigate("benefits") }) { Text("查看权益") }
                    }
            }
        }
        item {
            SheetCard {
                ProfileGroupTitle("出车与资料")
                MenuRow("account", "司机与车辆资料") { navigate("driver") }
                HorizontalDivider(color = Line)
                MenuRow("shield", "资质证件", qualText) { navigate("qualification") }
                if (qualNeedsAction) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (qualColor == Danger) DangerSoft else WarmSoft)
                            .clickable(role = Role.Button) { navigate("qualification") }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(qualText, color = qualColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("去更新 ›", color = qualColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = Line)
                MenuRow("settings", "听单设置", "播报、悬浮入口、接单距离") { navigate("settings") }
            }
        }
        item {
            SheetCard {
                ProfileGroupTitle("账户安全")
                MenuRow("card", "我的银行卡", "演示卡，不产生真实银行交易") { navigate("bank") }
                HorizontalDivider(color = Line)
                MenuRow("lock", "交易密码与钱包安全") { navigate("walletsettings") }
            }
        }
        item {
            SheetCard {
                ProfileGroupTitle("平台服务")
                MenuRow("gift", "城市会员权益") { navigate("benefits") }
                HorizontalDivider(color = Line)
                MenuRow("thumb", "服务分明细") { navigate("serviceScore") }
                HorizontalDivider(color = Line)
                MenuRow("star", "我的积分") { navigate("points") }
                HorizontalDivider(color = Line)
                MenuRow("share", "分享拉新") { navigate("referral") }
                HorizontalDivider(color = Line)
                MenuRow("chart", "接单排行") { navigate("ranking") }
                HorizontalDivider(color = Line)
                MenuRow("face", "乘客黑名单") { navigate("blacklist") }
            }
        }
        item {
            SheetCard {
                ProfileGroupTitle("服务细则与支持")
                MenuRow("support", "联系客服") { navigate("help") }
                HorizontalDivider(color = Line)
                MenuRow("mail", "意见反馈") { navigate("feedback") }
                HorizontalDivider(color = Line)
                MenuRow("doc", "服务细则与协议") { navigate("rules") }
                HorizontalDivider(color = Line)
                MenuRow("tool", "连接状态", "独立演示环境") { showEndpoint = true }
            }
        }
        item {
            OutlinedButton(
                onClick = { logout = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Danger.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Danger),
            ) {
                Text("退出登录", fontSize = DriverType.Body, fontWeight = FontWeight.Medium)
            }
        }
    }
    if (showEndpoint) CloudConnectionDialog(store) { showEndpoint = false }
    if (logout)
        DriverDialog(
            onDismissRequest = { logout = false },
            title = { Text("退出登录？") },
            text = { Text("本机登录状态将移除，云端订单与钱包记录保留。", color = Muted) },
            confirmButton = { DriverTextButton(onClick = { logout = false; store.logout() }) { Text("退出", color = Danger) } },
            dismissButton = { DriverTextButton(onClick = { logout = false }) { Text("取消") } },
        )
}

@Composable
private fun ProfileGroupTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Muted, modifier = Modifier.padding(bottom = 2.dp))
}

@Composable
fun ConnectedTrip(
    order: DriverOrder,
    store: ConnectedStore,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    onCancel: () -> Unit,
    onMessage: (String) -> Unit,
    onHome: () -> Unit,
    // In-app navigation mode reuses this screen's own actions, PIN dialog, consent and cancel flows.
    navigating: Boolean = false,
    onNavigate: (String) -> Unit = {},
    onExitNavigation: () -> Unit = {},
    // GRAB V1: the same single grab entry as the hall; withdraw leaves only this driver's candidacy.
    onGrab: (DriverOrder) -> Unit = {},
    onWithdraw: () -> Unit = {},
) {
    val context = LocalContext.current
    val tripBusy by store.loading.collectAsState()
    val hasPendingWaypoints = order.stage == Stage.TRIP && order.pendingWaypoints.isNotEmpty()
    val distancePending = driverHasPendingDistanceConsent(order.id, order.stage.name, store)
    var verify by remember(order.id) { mutableStateOf(false) }
    var tail by rememberSaveable(order.id) { mutableStateOf("") }
    var verifyError by remember(order.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(order.id, order.stage, order.participationCancelled) {
        if (!order.mayAdvanceFrom(Stage.ARRIVED)) verify = false
    }
    val title =
        when (order.stage) {
            Stage.PICKUP -> "前往接驾"
            Stage.ARRIVED -> "等待乘客上车"
            Stage.TRIP -> "行程进行中"
            Stage.WAIT_PAY -> "行程已结束·待付款"
            Stage.DONE -> "行程已完成"
            else -> order.stage.title
        }
    val action =
        if (distancePending) "等待乘客同意" else when (order.stage) {
            Stage.RESERVED -> "出发接驾"
            Stage.PICKUP -> "到达上车点"
            Stage.ARRIVED -> if (order.destinationPending) "等待乘客补充目的地" else "接到乘客，开始行程"
            Stage.TRIP -> if (hasPendingWaypoints) "请先确认途经点" else "到达目的地"
            Stage.WAIT_PAY -> "返回听单"
            Stage.PREPAY,
            Stage.BID_SENT -> "刷新订单状态"
            else -> "返回听单"
        }
    // Stages with a route show it as a full map behind a draggable sheet; other stages keep the list.
    val showMap = order.stage in setOf(Stage.RESERVED, Stage.PICKUP, Stage.ARRIVED, Stage.TRIP)
    val showContactFab = PassengerContactRules.eligible(order) && !order.isPrivateExample
    val tripDetails: @Composable (Modifier) -> Unit = { listModifier ->
        LazyColumn(
            listModifier,
            contentPadding = PaddingValues(
                start = Gap.L,
                end = Gap.L,
                top = Gap.M,
                bottom = if (showContactFab) 72.dp else Gap.XL,
            ),
            verticalArrangement = Arrangement.spacedBy(Gap.M),
        ) {
            item { SharedParticipationNotice(order) }
            if (order.stage == Stage.AVAILABLE && order.competition != null && order.kind != OrderKind.BID)
                item { CompetitionCard(order, store, onGrab, onWithdraw) }
            if (order.isPrivateExample)
                item {
                    SheetCard {
                        Text("演示订单 · 未关联乘客 App", color = Orange, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("此订单用于司机操作体验，未关联真实乘客账户。", color = Muted, fontSize = 13.sp)
                    }
                }
            if (order.kind == OrderKind.NEGOTIATED && (order.canPairWithPassenger || order.shared))
                item { NegotiationSupport(order, store, onMessage) }
            if (
                order.stage in setOf(Stage.BID_SENT, Stage.PREPAY, Stage.WAIT_PAY) &&
                    !(order.kind == OrderKind.NEGOTIATED && order.stage == Stage.PREPAY)
            )
                item {
                    SheetCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Pill(order.stage.title)
                            if (order.stage == Stage.WAIT_PAY) {
                                Surface(
                                    onClick = { store.refresh() },
                                    enabled = !tripBusy,
                                    shape = CircleShape,
                                    color = Pale,
                                    border = BorderStroke(1.dp, Green.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .size(38.dp)
                                        .semantics { contentDescription = "刷新支付状态" },
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Glyph("refresh", if (!tripBusy) GreenStrong else DisabledText, Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        Text(
                            when (order.stage) {
                                Stage.BID_SENT -> "报价已提交，等待乘客选择"
                                Stage.PREPAY -> "乘客完成支付后才能继续"
                                else -> "行程已结束，等待乘客付款"
                            },
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Text(
                            if (order.stage == Stage.WAIT_PAY && order.isPrivateExample)
                                "这笔演示待支付记录会保留，没有关联乘客 App，不需要等真实乘客付款。可返回听单继续接新单。"
                            else if (order.stage == Stage.WAIT_PAY) "未付款订单保留在待支付，不影响继续接单，乘客完成模拟付款后按服务端结果入账。"
                            else if (order.shared && order.stage == Stage.BID_SENT)
                                "请乘客在乘客 App 查看报价并选择司机，选中后才能确认模拟支付。结果由服务端自动同步。"
                            else if (order.shared) "请乘客在乘客 App 核对并确认模拟支付。结果由服务端自动同步，无需控制台代付。"
                            else "状态会从服务端自动刷新。旧私有演示单可在独立控制台的乘客沙箱处理。",
                            color = Muted,
                            fontSize = DriverType.Body,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            if (order.stage == Stage.DONE)
                item {
                    SheetCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(Pale),
                                contentAlignment = Alignment.Center,
                            ) {
                                Glyph("check", GreenStrong, modifier = Modifier.size(18.dp))
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("已安全送达", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "收入与支付结果已由服务端核对",
                                    fontSize = DriverType.Caption,
                                    color = Muted,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            item {
                SheetCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(order.kind.title)
                        Spacer(Modifier.weight(1f))
                        Pill(order.stage.title)
                    }
                    Spacer(Modifier.height(18.dp))
                    RouteStops(order)
                    Spacer(Modifier.height(14.dp))
                    Text("订单金额", fontSize = 13.sp, color = Muted)
                    Text(
                        if (order.destinationPending) "费用待定（不是免费）" else "¥ ${money(order.fare)}",
                        fontSize = if (order.destinationPending) 20.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                    )
                    if (order.destinationPending)
                        Text("目的地待乘客补充 · 费用待定，不是免费。补齐后双方需核对体验报价。", color = Orange, fontSize = 15.sp)
                    DetailRow("预计里程", "${order.mileage} km")
                    if (order.kind == OrderKind.RESERVED) DetailRow("计划用车时间", order.scheduledTimeText)
                    if (order.passengerRemark.isNotBlank())
                        Surface(
                            color = WarmSoft,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("乘客备注", fontSize = 13.sp, color = Muted)
                                Text(
                                    order.passengerRemark,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Ink,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                }
            }
            item { DriverTripDistanceCard(order.id, order.stage.name, store, onRelocate = { navigate("relocate") }) }
            // The summary's RouteStops already lists every stop; the separate card only carries the
            // TRIP-stage arrival confirmations, so the same route is not shown twice elsewhere.
            if (order.stage == Stage.TRIP && order.waypoints.isNotEmpty()) item { TripWaypoints(order, store, onMessage) }
            if (order.stage == Stage.ARRIVED)
                item {
                    SheetCard {
                        Text("等待核验上车", fontSize = DriverType.Title, fontWeight = FontWeight.Bold, color = Ink)
                        Text(
                            "接到乘客后请口头询问乘客手机尾号后 4 位，点击下方按钮输入核对，服务端验证通过后开始行程。",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            item {
                SheetCard {
                    Text("订单编号（客服查询用）", fontSize = 13.sp, color = Muted)
                    Text(
                        order.id,
                        fontSize = 14.sp,
                        color = Ink,
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                    )
                    DetailRow("支付状态", order.paymentStatusLabel)
                    DetailRow("入账状态", order.incomeLabel)
                    order.refundAmountText?.let { DetailRow("模拟退款金额", it) }
                    order.cancelFeeDisplay().text?.let { BusinessHint(it) }
                    MenuRow("wallet", "查看费用明细") { navigate("fees/${order.id}") }
                    if (order.stage in setOf(Stage.DONE, Stage.WAIT_PAY, Stage.CANCELLED))
                        MenuRow("support", "订单投诉") { navigate("complaint/${order.id}") }
                    if (order.shared && order.stage in setOf(Stage.DONE, Stage.WAIT_PAY, Stage.CANCELLED))
                        BlockPassengerRow(order.id, store, onMessage)
                }
            }
        }
    }
    // 一排紧凑操作栏：有导航时展示紧凑导航按钮，取消订单与主操作（到达上车点等）并排同列；联系乘客独立悬浮展示。
    val tripActions: @Composable () -> Unit = {
        Surface(color = Color.White, border = BorderStroke(1.dp, Hairline)) {
            Column(Modifier.padding(horizontal = Gap.L, vertical = Gap.M)) {
                val navLeg = (NavigationSessionRules.legFor(order) as? LegDecision.Go)?.leg
                val showNavBtn = !navigating && NavigationSessionRules.navigable(order)
                val navLabel = when (navLeg?.stop) {
                    NavigationStop.PICKUP -> "导航去上车点"
                    NavigationStop.WAYPOINT -> "导航去途经点"
                    NavigationStop.DESTINATION -> "导航去终点"
                    null -> "导航"
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Gap.S),
                ) {
                    if (showNavBtn) {
                        OutlinedButton(
                            onClick = { onNavigate(order.id) },
                            modifier = Modifier
                                .heightIn(min = DriverButtonSize.Min)
                                .then(if (order.canCancel) Modifier.width(50.dp) else Modifier)
                                .semantics { contentDescription = navLabel },
                            shape = RoundedCornerShape(DriverButtonSize.Corner),
                            border = BorderStroke(1.dp, Line),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Pale, contentColor = GreenStrong),
                            contentPadding = if (order.canCancel) PaddingValues(0.dp) else PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Glyph("navigation", GreenStrong, Modifier.size(18.dp))
                            if (!order.canCancel) {
                                Text(navLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                    if (order.canCancel) {
                        CancelActionButton(
                            "取消订单",
                            modifier = Modifier.weight(0.7f),
                            onClick = onCancel,
                        )
                    }
                    PrimaryButton(
                        action,
                        modifier = Modifier.weight(1f),
                        enabled = !tripBusy && !hasPendingWaypoints && !distancePending,
                        onClick = {
                            when (order.stage) {
                                Stage.ARRIVED -> {
                                    if (order.destinationPending) {
                                        onMessage("请乘客在乘客 App 补充目的地，核对体验报价后再开始行程")
                                        store.refresh()
                                    } else {
                                        verify = true
                                        tail = ""
                                        verifyError = null
                                    }
                                }
                                Stage.RESERVED,
                                Stage.PICKUP,
                                Stage.TRIP -> {
                                    val current = store.state.value.orders.find { it.id == order.id }
                                    if (current?.mayAdvanceFrom(order.stage) == true) {
                                        store.perform("order.advance", JSONObject().put("id", order.id).put("expectedStage", order.stage.name)) {
                                            navigateForNewLeg(context, store, order.id, order.stage, onNavigate)
                                        }
                                    } else {
                                        onMessage("订单状态已更新，请刷新后再操作")
                                        store.refresh()
                                    }
                                }
                                Stage.WAIT_PAY -> onHome()
                                Stage.PREPAY,
                                Stage.BID_SENT -> store.refresh()
                                else -> onHome()
                            }
                        },
                    )
                }
                if (hasPendingWaypoints) {
                    Text(
                        "还有 ${order.pendingWaypoints.size} 个途经点待确认，请在上方记录到达。",
                        color = Muted,
                        fontSize = DriverType.Caption,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
    // A collapsed sheet asks for a full-screen map: the page header steps aside (system back still
    // works) and returns as soon as the sheet opens or the stage leaves the map.
    val tripScope = driverTripMapScope(order.id)
    val chromeHidden = showMap && LocalMapChromeHost.current?.hiddenFor(tripScope) == true
    // System back: an expanded panel first drops to half (inside MapPanelLayout), then navigation
    // ends, then the page closes as before.
    androidx.activity.compose.BackHandler(navigating) { onExitNavigation() }
    if (navigating) {
        // Same three-position panel as the trip map: drag up for every function, down for the map,
        // to the bottom for a full-screen map (app header and tabs step aside via the trip scope).
        // The map slot hosts one InAppNavigationPanel at a fixed call site, so dragging only resizes
        // the same SDK view; it never recreates the controller, re-plans or performs anything.
        val decision = NavigationSessionRules.legFor(order)
        Column(Modifier.fillMaxSize()) {
            if (!chromeHidden)
                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    DriverTextButton(onClick = onExitNavigation) { Text("退出导航") }
                }
            MapPanelLayout(
                saveKey = tripScope,
                modifier = Modifier.fillMaxWidth().weight(1f),
                attentionKey = listOf(
                    order.stage,
                    order.pendingWaypoints.firstOrNull()?.index,
                    hasPendingWaypoints,
                    distancePending,
                    order.canCancel,
                    order.participationCancelled,
                ),
                collapsedLabel = "显示行程操作",
                map = { mapModifier ->
                    InAppNavigationPanel(
                        decision = decision,
                        sessionKey = "${driverAccount(store.payload.value)}|${order.id}",
                        modifier = mapModifier,
                        onExit = onExitNavigation,
                    )
                },
                bottomBar = tripActions,
            ) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Gap.L, end = Gap.L, top = Gap.S, bottom = if (showContactFab) 72.dp else Gap.M),
                        verticalArrangement = Arrangement.spacedBy(Gap.M),
                    ) {
                        item { SharedParticipationNotice(order) }
                        if (order.isPrivateExample)
                            item {
                                SheetCard {
                                    Text("演示订单 · 未关联乘客 App", color = Orange, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("此订单用于司机操作体验，未关联真实乘客账户。", color = Muted, fontSize = 13.sp)
                                }
                            }
                        // One list only: in TRIP the waypoint card (with its confirm buttons) shows the stops.
                        if (order.stage == Stage.TRIP && order.waypoints.isNotEmpty()) item { TripWaypoints(order, store, onMessage) }
                        else item { SheetCard { RouteStops(order, compact = true) } }
                        if (order.shared && order.passengerRemark.isNotBlank()) item { SheetCard { PassengerRemarkLine(order) } }
                        item { DriverTripDistanceCard(order.id, order.stage.name, store, onRelocate = { navigate("relocate") }) }
                        if (order.stage == Stage.ARRIVED)
                            item {
                                SheetCard {
                                    Text("等待核验上车", fontSize = DriverType.Title, fontWeight = FontWeight.Bold, color = Ink)
                                    Text(
                                        "接到乘客后请口头询问乘客手机尾号后 4 位，点击下方按钮输入核对，服务端验证通过后开始行程。",
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = Muted,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                    }
                    if (showContactFab) {
                        PassengerContactFab(
                            order,
                            store,
                            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        }
    } else
    Column(Modifier.fillMaxSize()) {
        if (!chromeHidden) PageHeader(title, onBack)
        if (showMap)
            MapPanelLayout(
                saveKey = tripScope,
                modifier = Modifier.fillMaxWidth().weight(1f),
                attentionKey = listOf(order.stage, hasPendingWaypoints, distancePending, order.canCancel),
                collapsedLabel = "显示行程操作",
                map = { mapModifier -> NativeMap(mapModifier, order) },
                bottomBar = tripActions,
            ) {
                Box(Modifier.fillMaxSize()) {
                    tripDetails(Modifier.fillMaxSize())
                    if (showContactFab) {
                        PassengerContactFab(
                            order,
                            store,
                            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        else {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                tripDetails(Modifier.fillMaxSize())
                if (showContactFab) {
                    PassengerContactFab(
                        order,
                        store,
                        Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp),
                    )
                }
            }
            tripActions()
        }
    }
    if (verify)
        DriverDialog(
            onDismissRequest = { verify = false },
            title = { Text("核对乘客手机尾号") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("请和乘客核对后四位。", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        tail,
                        {
                            tail = it.filter(Char::isDigit).take(4)
                            verifyError = null
                        },
                        singleLine = true,
                        label = { Text("手机尾号") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = verifyError != null,
                        supportingText = { verifyError?.let { Text(it) } },
                    )
                }
            },
            confirmButton = {
                CoreTextButton(
                    enabled = !tripBusy && order.mayAdvanceFrom(Stage.ARRIVED) && tail.length == 4,
                    onClick = {
                        val current = store.state.value.orders.find { it.id == order.id }
                        if (tripBusy || current?.mayAdvanceFrom(Stage.ARRIVED) != true) {
                            verify = false
                            onMessage("订单状态已更新，请刷新后再操作")
                            store.refresh()
                            return@CoreTextButton
                        }
                        store.perform(
                            "order.advance",
                            JSONObject().put("id", order.id).put("phoneTail", tail).put("expectedStage", Stage.ARRIVED.name),
                            onFailure = { verifyError = it },
                        ) {
                            verify = false
                            navigateForNewLeg(context, store, order.id, Stage.ARRIVED, onNavigate)
                        }
                    }
                ) {
                    Text("确认上车")
                }
            },
            dismissButton = { CoreTextButton(onClick = { verify = false }) { Text("暂未接到") } },
        )
}

@Composable
fun ConnectedBid(
    order: DriverOrder,
    store: ConnectedStore,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    var amount by rememberSaveable(order.id) { mutableStateOf(money(order.fare)) }
    var estimate by remember { mutableStateOf<JSONObject?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var fieldError by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader("参与竞价", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SheetCard {
                    Pill("竞价订单")
                    Spacer(Modifier.height(16.dp))
                    RouteStops(order)
                    PassengerRemarkLine(order)
                    DetailRow("预计里程", "${order.mileage} km")
                }
            }
            item {
                SheetCard {
                    Text("我的报价", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        amount,
                        {
                            amount = it
                            estimate = null
                        },
                        label = { Text("报价金额（元）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CoreTextButton(
                        onClick = {
                            runCatching { DriverRules.cents(amount) }
                                .onSuccess { cents ->
                                    store.query("/api/orders/${order.id}/estimate?amount=$cents") {
                                        estimate =
                                            it.optJSONObject("estimate")
                                                ?: it.optJSONObject("result")
                                                ?: it
                                    }
                                }
                                .onFailure { fieldError = it.message }
                        }
                    ) {
                        Text("获取服务端收入预估")
                    }
                    estimate?.let { e ->
                        DetailRow("订单金额", "¥ ${money(e.optLong("fare"))}")
                        DetailRow("预计服务费", "¥ ${money(e.optLong("fee"))}")
                        DetailRow("预计收入", "¥ ${money(e.optLong("income"))}", Green)
                    }
                }
            }
            item { Text("报价提交后不可修改。乘客选择及支付结果由服务端同步。", fontSize = 13.sp, color = Muted) }
        }
        Surface(color = Color.White) {
            Column(Modifier.padding(18.dp)) {
                fieldError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 15.sp)
                }
                PrimaryButton(
                    "确认提交报价",
                    onClick = {
                        runCatching { DriverRules.cents(amount) }
                            .onSuccess { confirm = true }
                            .onFailure { fieldError = it.message }
                    },
                )
            }
        }
    }
    if (confirm)
        DriverDialog(
            onDismissRequest = { confirm = false },
            title = { Text("确认报价 ¥ $amount") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("请确认金额，提交后不可修改。", fontSize = 15.sp)
                }
            },
            confirmButton = {
                CoreTextButton(
                    onClick = {
                        confirm = false
                        store.perform(
                            "order.bid",
                            JSONObject()
                                .put("id", order.id)
                                .put("amount", DriverRules.cents(amount)),
                        ) {
                            onSuccess()
                        }
                    }
                ) {
                    Text("提交报价")
                }
            },
            dismissButton = { CoreTextButton(onClick = { confirm = false }) { Text("再想一下") } },
        )
}

@Composable
fun ConnectedNegotiation(store: ConnectedStore, onBack: () -> Unit, onSuccess: (String) -> Unit) {
    var origin by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var originLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var originLng by rememberSaveable { mutableStateOf<Double?>(null) }
    var destinationLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var destinationLng by rememberSaveable { mutableStateOf<Double?>(null) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var draftLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var draftLng by rememberSaveable { mutableStateOf<Double?>(null) }
    var mapPickError by remember { mutableStateOf<String?>(null) }
    var originError by remember { mutableStateOf<String?>(null) }
    var destinationError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    fun openPicker(stop: String) {
        focus.clearFocus()
        draftLat = if (stop == "origin") originLat else destinationLat
        draftLng = if (stop == "origin") originLng else destinationLng
        mapPickError = null
        picking = stop
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader("线下议价", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SheetCard {
                    Text("行程信息", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "先填写上下车地点名称，再在地图中确认位置。修改名称后需要重新确认位置。",
                        fontSize = 15.sp,
                        color = Muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(15.dp))
                    OutlinedTextField(
                        origin,
                        {
                            if (it != origin) {
                                originLat = null
                                originLng = null
                            }
                            origin = it
                            originError = null
                            formError = null
                        },
                        label = { Text("上车地点名称") },
                        isError = originError != null,
                        supportingText = { originError?.let { Text(it) } },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { openPicker("origin") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        val point = wgs84Point(originLat, originLng)
                        Text(if (point == null) "在地图上确认上车点" else "上车点已确认 · 重新选择", fontSize = 16.sp)
                    }
                    OutlinedTextField(
                        destination,
                        {
                            if (it != destination) {
                                destinationLat = null
                                destinationLng = null
                            }
                            destination = it
                            destinationError = null
                            formError = null
                        },
                        label = { Text("下车地点名称") },
                        isError = destinationError != null,
                        supportingText = { destinationError?.let { Text(it) } },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { openPicker("destination") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        val point = wgs84Point(destinationLat, destinationLng)
                        Text(if (point == null) "在地图上确认下车点" else "下车点已确认 · 重新选择", fontSize = 16.sp)
                    }
                }
            }
            item {
                SheetCard {
                    OutlinedTextField(
                        amount,
                        {
                            amount = it
                            formError = null
                        },
                        label = { Text("协商金额（元）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "地点、金额和本城市开放状态将在服务端再次校验。",
                        fontSize = 15.sp,
                        color = Muted,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        Surface(color = Color.White) {
            Column(Modifier.padding(18.dp)) {
                formError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 15.sp)
                }
                PrimaryButton(
                    "确认并发起议价",
                    onClick = {
                        focus.clearFocus()
                        val pickup = wgs84Point(originLat, originLng)
                        val dropoff = wgs84Point(destinationLat, destinationLng)
                        originError =
                            when {
                                origin.trim().length !in 2..100 -> "请填写 2 至 100 字的上车地点名称"
                                pickup == null -> "请在地图中确认上车点；修改名称后需要重新选点"
                                else -> null
                            }
                        destinationError =
                            when {
                                destination.trim().length !in 2..100 -> "请填写 2 至 100 字的下车地点名称"
                                dropoff == null -> "请在地图中确认下车点；修改名称后需要重新选点"
                                else -> null
                            }
                        if (
                            originError != null ||
                                destinationError != null ||
                                pickup == null ||
                                dropoff == null
                        ) {
                            formError = originError ?: destinationError ?: "请确认上下车位置"
                        } else {
                            runCatching {
                                    require(
                                        !origin
                                            .replace(Regex("\\s+"), "")
                                            .equals(
                                                destination.replace(Regex("\\s+"), ""),
                                                ignoreCase = true,
                                            )
                                    ) {
                                        "上下车地点名称不能相同"
                                    }
                                    require(
                                        kotlin.math.abs(pickup.latitude - dropoff.latitude) >
                                            1e-7 ||
                                            kotlin.math.abs(pickup.longitude - dropoff.longitude) >
                                                1e-7
                                    ) {
                                        "上下车位置不能相同，请重新在地图中确认"
                                    }
                                    DriverRules.cents(amount)
                                }
                                .onSuccess { cents ->
                                    formError = null
                                    // Both points have been explicitly selected and validated as
                                    // finite WGS84.
                                    // Never serialize an unknown coordinate (null/NaN) or a demo
                                    // default.
                                    store.perform(
                                        "order.negotiate",
                                        JSONObject()
                                            .put("origin", origin.trim())
                                            .put("destination", destination.trim())
                                            .put("amount", cents)
                                            .put("originLat", pickup.latitude)
                                            .put("originLng", pickup.longitude)
                                            .put("destinationLat", dropoff.latitude)
                                            .put("destinationLng", dropoff.longitude),
                                    ) { data ->
                                        val id =
                                            data
                                                .optJSONObject("result")
                                                ?.optString("orderId")
                                                .orEmpty()
                                        if (id.isNotBlank()) onSuccess(id)
                                        else {
                                            formError = "服务已响应但未返回新订单编号，请返回订单列表核对，勿重复发起。"
                                            store.refresh()
                                        }
                                    }
                                }
                                .onFailure { formError = it.message }
                        }
                    },
                )
            }
        }
    }
    if (picking != null)
        DriverDialog(
            onDismissRequest = { picking = null },
            title = { Text(if (picking == "origin") "确认上车点位置" else "确认下车点位置") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        if (picking == "origin") origin.ifBlank { "请返回填写上车地点名称" }
                        else destination.ifBlank { "请返回填写下车地点名称" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("点击地图选点，核对坐标后点击“使用此位置”。", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(12.dp))
                    NativeMap(
                        Modifier.fillMaxWidth().height(190.dp),
                        onPick = { lat, lng ->
                            draftLat = lat
                            draftLng = lng
                            mapPickError = null
                        },
                    )
                    val draft = wgs84Point(draftLat, draftLng)
                    Text(
                        if (draft == null) "尚未选点，请点击地图"
                        else
                            "待确认位置：${"%.5f".format(draft.latitude)}, ${"%.5f".format(draft.longitude)}",
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                    mapPickError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 15.sp)
                    }
                }
            },
            confirmButton = {
                CoreTextButton(
                    onClick = {
                        val draft = wgs84Point(draftLat, draftLng)
                        val name = if (picking == "origin") origin else destination
                        if (name.trim().length !in 2..100) {
                            mapPickError = "请先返回填写 2 至 100 字的地点名称，再确认位置"
                        } else if (draft == null) {
                            mapPickError = "请先点击地图选择位置，再确认"
                        } else {
                            if (picking == "origin") {
                                originLat = draft.latitude
                                originLng = draft.longitude
                                originError = null
                            } else {
                                destinationLat = draft.latitude
                                destinationLng = draft.longitude
                                destinationError = null
                            }
                            formError = null
                            picking = null
                        }
                    }
                ) {
                    Text("使用此位置")
                }
            },
            dismissButton = { CoreTextButton(onClick = { picking = null }) { Text("取消选点") } },
        )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ConnectedSettings(
    store: ConnectedStore,
    state: DriverState,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    navigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val payload by store.payload.collectAsState()
    val features = payload.optJSONObject("features") ?: JSONObject()
    val floatingPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(context))
                store.perform("settings.update", JSONObject().put("floating", true))
            else onMessage("尚未允许悬浮显示，可在系统设置中授权后再开启。")
        }
    fun setting(k: String, v: Any) {
        store.perform("settings.update", JSONObject().put(k, v))
    }
    // Only listening preferences live here; membership, connection and logout are in the profile tab.
    Column(Modifier.fillMaxSize()) {
        PageHeader("听单设置", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Gap.L, vertical = Gap.M),
            verticalArrangement = Arrangement.spacedBy(Gap.M),
        ) {
            item {
                SheetCard {
                    Text("接单偏好", fontSize = DriverType.Title, fontWeight = FontWeight.Bold, color = Ink)
                    listOf(
                            Triple("即时订单", "instant", state.instant),
                            Triple("预约订单", "reservation", state.reservation),
                            Triple("竞价订单", "bidding", state.bidding),
                            Triple("声音提醒", "voice", state.voice),
                            Triple("大字模式", "largeText", state.largeText),
                        )
                        .forEach { (label, key, checked) ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, fontSize = DriverType.Body, color = Ink, modifier = Modifier.weight(1f))
                                Switch(
                                    checked,
                                    { setting(key, it) },
                                    enabled = key != "voice" || features.optBoolean("canVoice"),
                                )
                            }
                        }
                    HorizontalDivider(color = Line)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("接单悬浮入口", fontSize = DriverType.Body, color = Ink)
                            Text("听单期间离开应用后显示，可拖动并点按返回。", color = Muted, fontSize = DriverType.Caption)
                        }
                        Switch(
                            state.floating,
                            { enabled ->
                                if (!enabled || Settings.canDrawOverlays(context))
                                    setting("floating", enabled)
                                else
                                    floatingPermission.launch(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        )
                                    )
                            },
                            enabled = features.optBoolean("canFloating"),
                        )
                    }
                }
            }
                item {
                SheetCard {
                    Text("接单距离", fontSize = DriverType.Title, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(Gap.S))
                    val distances = listOf(3, 5, 10)
                    DriverSegmentTabs(
                        items = distances.map { "$it 公里" },
                        selectedIndex = distances.indexOf(state.range).coerceAtLeast(0),
                        onSelect = { idx -> setting("range", distances[idx]) },
                    )
                    Spacer(Modifier.height(Gap.S))
                    Text("实际可接范围还受城市和会员权益限制，服务端会再次核对。", fontSize = DriverType.Caption, color = Muted)
                }
            }
            item {
                SheetCard {
                    MenuRow("shield", "权限与提醒检查", "定位 / 通知 / 悬浮窗") { navigate("permissions") }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ConnectedOrders(
    state: DriverState,
    navigate: (String) -> Unit,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
) {
    val tabs = listOf("全部", "进行中", "待支付", "已完成", "已取消", "待出发预约")
    val rows =
        state.orders.filter {
            it.stage != Stage.AVAILABLE &&
                when (selected) {
                    1 -> it.isActive
                    2 -> it.stage in setOf(Stage.PREPAY, Stage.WAIT_PAY)
                    3 -> it.stage == Stage.DONE
                    4 -> it.stage == Stage.CANCELLED
                    5 -> it.isWaitingReservation
                    else -> true
                }
        }
    Column(Modifier.fillMaxSize()) {
        PageHeader("我的订单")
        DriverScrollableTabs(
            items = tabs,
            selectedIndex = selected,
            onSelect = onSelectedChange,
            modifier = Modifier.padding(horizontal = Gap.L, vertical = 4.dp),
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Gap.L, vertical = Gap.M),
            verticalArrangement = Arrangement.spacedBy(Gap.M),
        ) {
            if (rows.isEmpty())
                item {
                    EmptyState(
                        "orders",
                        "暂无${if(selected==0)"" else tabs[selected]}订单",
                        "接单后的行程、待支付与历史记录都会显示在这里",
                        if (selected != 0) "查看全部订单" else null,
                    ) { onSelectedChange(0) }
                }
            items(rows, key = { it.id }) { order ->
                OrderCard(order) { navigate("detail:${order.id}") }
            }
        }
    }
}

private const val WALLET_PREVIEW_ROWS = 3

@Composable
fun ConnectedWallet(store: ConnectedStore, state: DriverState, navigate: (String) -> Unit) {
    var settlementExpanded by rememberSaveable { mutableStateOf(false) }
    var entriesExpanded by rememberSaveable { mutableStateOf(false) }
    var walletTab by rememberSaveable { mutableStateOf(0) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Gap.L, vertical = Gap.M),
        verticalArrangement = Arrangement.spacedBy(Gap.M),
    ) {
        item { PageHeader("我的钱包", action = "提现记录", onAction = { navigate("withdrawals") }) }
        item {
            // 实体银行卡质感余额卡：芯片徽饰 + 余额主体 + 内置紧凑"申请提现"胶囊。
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = GreenStrong,
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF0A6B61), Color(0xFF073E3A))))
                        .drawBehind {
                            drawCircle(Color.White.copy(alpha = 0.06f), radius = size.height * 0.9f, center = Offset(size.width * 0.96f, 0f))
                            drawCircle(Color.White.copy(alpha = 0.05f), radius = size.height * 0.6f, center = Offset(size.width * 0.12f, size.height))
                        }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 卡片芯片纹理
                            Canvas(Modifier.size(width = 28.dp, height = 20.dp)) {
                                drawRoundRect(
                                    color = Color(0xFFEBD28A),
                                    cornerRadius = CornerRadius(4.dp.toPx()),
                                )
                                drawLine(Color(0xFFA8893A), Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), 1.dp.toPx())
                                drawLine(Color(0xFFA8893A), Offset(size.width * 0.35f, 0f), Offset(size.width * 0.35f, size.height), 1.dp.toPx())
                                drawLine(Color(0xFFA8893A), Offset(size.width * 0.65f, 0f), Offset(size.width * 0.65f, size.height), 1.dp.toPx())
                            }
                            Text("宜捷行 · 司机结算卡", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("•••• ••••", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("可提现余额（元）· 体验账户", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text(
                            "¥ ${money(state.balance)}",
                            fontSize = 32.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("累计收入", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                            Text("¥ ${money(state.todayIncome)}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Surface(
                            onClick = { navigate("withdraw") },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.96f),
                            modifier = Modifier.heightIn(min = 38.dp),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("申请提现", color = GreenStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Glyph("right", GreenStrong, Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        val settledOrders = state.orders.filter { it.shared && it.stage != Stage.AVAILABLE && it.settlementStatus.isNotBlank() }
        item {
            DriverSegmentTabs(
                items = listOf("订单入账状态 ${settledOrders.size}", "收支明细 ${state.entries.size}"),
                selectedIndex = walletTab,
                onSelect = { walletTab = it },
            )
        }
        if (walletTab == 0 && settledOrders.isEmpty())
            item { EmptyState("orders", "暂无入账记录", "共享订单完成并由服务端结算后，会显示在这里") }
        if (walletTab == 0 && settledOrders.isNotEmpty()) {
            // Only the latest few rows are shown by default so the list never grows without bound.
            val shownSettled = if (settlementExpanded) settledOrders else settledOrders.take(WALLET_PREVIEW_ROWS)
            item { Text("支付与退款均为模拟记账", fontSize = DriverType.Caption, color = Muted) }
            item {
                SheetCard {
                    shownSettled.forEachIndexed { i, order ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { navigate("detail:${order.id}") }.padding(vertical = Gap.S),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    order.origin + " → " + order.destination,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(order.incomeLabel, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                            }
                            if (order.settlementStatus in setOf("CREDITED", "REVERSED", "REFUNDED", "CLOSED_NO_INCOME"))
                                Text(
                                    "¥${money(order.netIncome ?: 0)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ink,
                                    modifier = Modifier.padding(horizontal = Gap.S),
                                )
                            Glyph("right", Muted, Modifier.size(16.dp))
                        }
                        if (i < shownSettled.lastIndex) HorizontalDivider(color = Line)
                    }
                    if (settledOrders.size > WALLET_PREVIEW_ROWS)
                        CoreTextButton(onClick = { settlementExpanded = !settlementExpanded }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (settlementExpanded) "收起" else "展开全部 ${settledOrders.size} 笔", color = Green, fontSize = 14.sp)
                        }
                }
            }
        }
        if (walletTab == 1) item { Text("金额和状态以独立服务记录为准", fontSize = DriverType.Caption, color = Muted) }
        if (walletTab == 1 && state.entries.isEmpty())
            item {
                EmptyState("wallet", "暂无收支记录", "完成订单并由服务端入账后，流水会显示在这里", "查看提现记录") {
                    navigate("withdrawals")
                }
            }
        else if (walletTab == 1)
            item {
                val shownEntries = if (entriesExpanded) state.entries else state.entries.take(WALLET_PREVIEW_ROWS)
                SheetCard {
                    shownEntries.forEachIndexed { i, e ->
                        val income = e.amount >= 0
                        Row(Modifier.fillMaxWidth().padding(vertical = Gap.M), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(if (income) SuccessSoft else WarmSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Glyph(if (income) "plus" else "wallet", if (income) Success else Warm, Modifier.size(18.dp))
                            }
                            Column(Modifier.weight(1f).padding(horizontal = Gap.M)) {
                                Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(
                                    if (income) "入账" else "支出",
                                    fontSize = 12.sp,
                                    color = if (income) Success else Warm,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (e.note.isNotBlank())
                                    Text(
                                        "记录说明：${e.note}",
                                        fontSize = 13.sp,
                                        color = Muted,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                            }
                            Text(
                                (if (income) "+" else "−") + money(kotlin.math.abs(e.amount)),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (income) Success else Ink,
                            )
                        }
                        if (i < shownEntries.lastIndex) HorizontalDivider(color = Line)
                    }
                    if (state.entries.size > WALLET_PREVIEW_ROWS)
                        CoreTextButton(onClick = { entriesExpanded = !entriesExpanded }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (entriesExpanded) "收起" else "展开全部 ${state.entries.size} 条", color = Green, fontSize = 14.sp)
                        }
                }
            }
        item { Text("演示账户资金用于联调，不产生真实银行交易。", color = Muted, fontSize = 13.sp) }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ConnectedHeat(store: ConnectedStore, onBack: () -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var type by rememberSaveable { mutableStateOf("passenger") }
    fun reload() {
        store.query("/api/heatmap?type=$type", onFailure = { failure = it }) {
            data = it
            failure = null
        }
    }
    LaunchedEffect(type) { reload() }
    val points = data?.optJSONArray("points")
    val coords =
        (0 until (points?.length() ?: 0)).map { i ->
            points!!.getJSONObject(i).let {
                it.optDouble("latitude", it.optDouble("lat")) to
                    it.optDouble("longitude", it.optDouble("lng"))
            }
        }
    Column(Modifier.fillMaxSize()) {
        PageHeader("热力分布", onBack, action = "刷新", onAction = { reload() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gap.L, vertical = Gap.M)) {
            DriverSegmentTabs(
                items = listOf("客流热力", "司机热力"),
                selectedIndex = if (type == "driver") 1 else 0,
                onSelect = { type = if (it == 1) "driver" else "passenger" },
            )
            Spacer(Modifier.height(Gap.M))
            NativeMap(Modifier.fillMaxWidth().height(260.dp), heatPoints = coords)
            Spacer(Modifier.height(Gap.M))
            if (data?.optBoolean("render") == false || (data != null && coords.isEmpty())) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceMuted,
                    border = BorderStroke(1.dp, Line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(SkySoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph("info", Sky, Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("当前暂未配置热力图层", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(
                                data?.optString("notice").orEmpty().ifBlank { "当前仅展示基础地图，热力数据接入配置后会自动呈现。" },
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = Muted,
                            )
                            failure?.let { Text(it, color = Danger, fontSize = 12.sp) }
                        }
                    }
                }
            } else {
                SheetCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("${coords.size} 个热点", GreenStrong, Pale)
                        Text(if (type == "driver") "附近司机分布" else "近期客流需求", fontSize = DriverType.Title, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    Text(
                        data?.optString("notice").orEmpty().ifBlank { "正在同步热力图数据…" },
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    failure?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
}

@Composable
fun ConnectedNotifications(store: ConnectedStore, onBack: () -> Unit, navigate: (String) -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    fun reload() {
        store.query("/api/notifications") { data = it }
    }
    LaunchedEffect(Unit) { reload() }
    val rows = data?.optJSONArray("notifications") ?: data?.optJSONArray("items")
    Column(Modifier.fillMaxSize()) {
        PageHeader("消息与通知", onBack, action = "刷新", onAction = { reload() })
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (rows == null || rows.length() == 0)
                item { EmptyState("bell", "暂无消息", "订单与平台通知会显示在这里", "刷新") { reload() } }
            items(rows?.length() ?: 0) { index ->
                val row = rows!!.getJSONObject(index)
                SheetCard {
                    Text(
                        row.optString("title", "业务通知"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        row.optString("message", row.optString("content")),
                        fontSize = 15.sp,
                        color = Muted,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                    val orderId = row.optString("orderId")
                    if (orderId.isNotBlank())
                        CoreTextButton(onClick = { navigate("detail:$orderId") }) { Text("查看订单") }
                }
            }
        }
    }
}

@Composable
fun InfoPage(title: String, onBack: () -> Unit, text: String) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
            SheetCard { Text(text, fontSize = 15.sp, lineHeight = 22.sp) }
        }
    }
}

@Composable
private fun CoreTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    DriverTextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}
