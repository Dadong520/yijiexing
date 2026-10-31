package cn.hbzjhs.driver.preview

import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
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
        Text("宜捷行 · 司机", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Green)
        Text("欢迎回来，师傅", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
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
                store.loginExperience(selectedPhone)
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
    // Ticks only while some round's 5s window is open, so "已截止" appears without a server poll.
    val grabTick by produceState(0, allAvailable.any { it.competition?.status == "OPEN" }) {
        while (allAvailable.any { it.competition?.status == "OPEN" }) {
            kotlinx.coroutines.delay(500)
            value++
        }
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
                                        Text("当前休息中", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
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
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Line),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(Green))
                                        Column(Modifier.padding(start = 8.dp)) {
                                            Text("正在听单中", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Green)
                                            Text("保持手机畅通，留心语音播报", color = Muted, fontSize = 12.sp)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = onToggle,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Line)
                                    ) {
                                        Text("点击下线", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(Gap.S),
                                ) {
                                    FilterChip(
                                        selected = selectedKind == null,
                                        onClick = { kindFilter = null },
                                        label = { Text("全部 ${allAvailable.size}", fontSize = 13.sp) },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                    availableKinds.forEach { kind ->
                                        FilterChip(
                                            selected = selectedKind == kind,
                                            onClick = { kindFilter = kind.name },
                                            label = { Text("${kind.title} ${allAvailable.count { it.kind == kind }}", fontSize = 13.sp) },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        )
                                    }
                                }
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
                        items(available, key = { it.id }) {
                            order -> OrderCard(order, if (order.kind == OrderKind.BID) "参与竞价" else competitionLabel(account, order, grabVersion, grabTick)) {
                                onGrab(order)
                            }
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
    var showEndpoint by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Gap.L, vertical = Gap.M),
        verticalArrangement = Arrangement.spacedBy(Gap.M),
    ) {
        item { PageHeader("司机之家", action = "听单设置", onAction = { navigate("settings") }) }
        item {
            // Plain identity card: initial avatar, name, vehicle and service score in ink on white.
            val name = d.optString("name", "司机")
            SheetCard(Modifier.clickable(role = Role.Button) { navigate("driver") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).background(Pale, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(name.take(1), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenStrong)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${name}师傅，您好", fontSize = DriverType.Page, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                        val org = d.optString("organization", d.optString("company", ""))
                        Text(
                            d.optString("licensePlate", d.optString("plate", "未完善车辆资料")) + if (org.isNotBlank()) " · $org" else "",
                            fontSize = DriverType.Caption,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val serviceScoreObj = profile.optJSONObject("serviceScore")
                        val scoreValue = serviceScoreObj?.optInt("value", serviceScoreObj.optInt("score", -1)) ?: -1
                        Pill(if (scoreValue > 0) "服务分 ${scoreValue} 分" else "服务分 暂未考核", GreenStrong, Pale)
                    }
                    Glyph("right", Muted, Modifier.size(18.dp))
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
        // Moved here from the listening screen so that screen stays clean.
        item {
            SheetCard {
                ProfileGroupTitle("出车工具与状态")
                DetailRow("定位", positionStatus)
                DetailRow("语音播报", if (state.online) speechStatus else "下线中")
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp).background(Pale, RoundedCornerShape(8.dp)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Glyph("shield", Green, Modifier.size(16.dp))
                    Text("连续驾驶请劳逸结合，保持车距安全行车。", color = Muted, fontSize = DriverType.Caption, modifier = Modifier.padding(start = 6.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = Gap.S), horizontalArrangement = Arrangement.spacedBy(Gap.S)) {
                    BusinessOutlinedButton(
                        onClick = { navigate("negotiate") },
                        enabled = features.optBoolean("canNegotiate") && state.activeOrder == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Glyph("scan", modifier = Modifier.size(16.dp))
                        Text("线下议价", modifier = Modifier.padding(start = 4.dp))
                    }
                    BusinessOutlinedButton(
                        onClick = { navigate("heat") },
                        enabled = features.optBoolean("canHeatmap"),
                        modifier = Modifier.weight(1f),
                    ) {
                        Glyph("chart", modifier = Modifier.size(16.dp))
                        Text("热力分布", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (!features.optBoolean("canNegotiate"))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("线下议价未开放：当前城市或会员等级未包含此权益。", color = Muted, fontSize = DriverType.Caption, modifier = Modifier.weight(1f))
                        DriverTextButton(onClick = { navigate("benefits") }) { Text("查看权益") }
                    }
            }
        }
        item {
            SheetCard {
                ProfileGroupTitle("出车与资料")
                MenuRow("account", "司机与车辆资料") { navigate("driver") }
                HorizontalDivider(color = Line)
                MenuRow("shield", "资质证件") { navigate("qualification") }
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
    val tripDetails: @Composable (Modifier) -> Unit = { listModifier ->
        LazyColumn(
            listModifier,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        Pill(order.stage.title)
                        Text(
                            when (order.stage) {
                                Stage.BID_SENT -> "报价已提交，等待乘客选择"
                                Stage.PREPAY -> "乘客完成支付后才能继续"
                                else -> "行程已结束，等待乘客付款"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 14.dp),
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
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        if (order.stage == Stage.WAIT_PAY)
                            CoreTextButton(onClick = { store.refresh() }, enabled = !tripBusy) {
                                Text("刷新支付状态")
                            }
                    }
                }
            if (order.stage == Stage.DONE)
                item {
                    SheetCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph("check", modifier = Modifier.size(36.dp))
                            Column(Modifier.padding(start = 14.dp)) {
                                Text("已安全送达", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "收入与支付结果已由服务端核对",
                                    fontSize = 15.sp,
                                    color = Muted,
                                    modifier = Modifier.padding(top = 5.dp),
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
                        fontSize = if (order.destinationPending) 24.sp else 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                    )
                    if (order.destinationPending)
                        Text("目的地待乘客补充 · 费用待定，不是免费。补齐后双方需核对体验报价。", color = Orange, fontSize = 15.sp)
                    DetailRow("预计里程", "${order.mileage} km")
                    if (order.kind == OrderKind.RESERVED) DetailRow("计划用车时间", order.scheduledTimeText)
                    if (order.remark.isNotBlank())
                        Text("备注：${order.remark}", fontSize = 13.sp, color = Muted)
                }
            }
            item { DriverTripDistanceCard(order.id, order.stage.name, store) }
            // The summary's RouteStops already lists every stop; the separate card only carries the
            // TRIP-stage arrival confirmations, so the same route is not shown twice elsewhere.
            if (order.stage == Stage.TRIP && order.waypoints.isNotEmpty()) item { TripWaypoints(order, store, onMessage) }
            if (order.stage == Stage.ARRIVED && order.phoneTail.isNotBlank())
                item {
                    SheetCard {
                        DetailRow("乘客手机尾号", order.phoneTail)
                        Text("接到乘客时需要核对尾号，服务端会校验。", fontSize = 13.sp, color = Muted)
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
                    MenuRow("wallet", "查看费用明细") { navigate("fees/${order.id}") }
                    if (order.stage in setOf(Stage.DONE, Stage.WAIT_PAY, Stage.CANCELLED))
                        MenuRow("support", "订单投诉") { navigate("complaint/${order.id}") }
                }
            }
        }
    }
    // Compact action bar: navigation and the next step share one row so the map and trip details
    // keep most of the screen; cancelling is a small secondary link below.
    val tripActions: @Composable () -> Unit = {
        Surface(color = Color.White, border = BorderStroke(1.dp, Hairline)) {
            Column(Modifier.padding(horizontal = Gap.L, vertical = 10.dp)) {
                // Waiting reservations may preview navigation too; departing still needs "出发接驾".
                val navLeg = (NavigationSessionRules.legFor(order) as? LegDecision.Go)?.leg
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.S)) {
                if (!navigating && NavigationSessionRules.navigable(order))
                    BusinessOutlinedButton(
                        onClick = { onNavigate(order.id) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Glyph("navigation", GreenStrong, Modifier.size(16.dp))
                        Text(
                            when (navLeg?.stop) {
                                NavigationStop.PICKUP -> "导航去上车点"
                                NavigationStop.WAYPOINT -> "导航去下一途经点"
                                NavigationStop.DESTINATION -> "导航去终点"
                                null -> "导航"
                            },
                            modifier = Modifier.padding(start = 4.dp),
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
                if (order.canCancel || PassengerContactRules.eligible(order))
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                        PassengerContactAction(order, store, Modifier.weight(1f))
                        if (order.canCancel) DriverTextButton(onClick = onCancel) { Text("取消订单", color = Muted) }
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
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    item { DriverTripDistanceCard(order.id, order.stage.name, store) }
                    if (order.stage == Stage.ARRIVED && order.phoneTail.isNotBlank())
                        item {
                            SheetCard {
                                DetailRow("乘客手机尾号", order.phoneTail)
                                Text("接到乘客时需要核对尾号，服务端会校验。", fontSize = 13.sp, color = Muted)
                            }
                        }
                }
            }
        }
    } else
    Column(Modifier.fillMaxSize()) {
        if (!chromeHidden) PageHeader(title, onBack)
        if (showMap)
            // The action bar stays pinned under the sheet content; a stage or waypoint/consent change
            // brings a collapsed sheet back so the next step is never left hidden.
            MapPanelLayout(
                saveKey = tripScope,
                modifier = Modifier.fillMaxWidth().weight(1f),
                attentionKey = listOf(order.stage, hasPendingWaypoints, distancePending, order.canCancel),
                collapsedLabel = "显示行程操作",
                map = { mapModifier -> NativeMap(mapModifier, order) },
                bottomBar = tripActions,
            ) { tripDetails(Modifier.fillMaxSize()) }
        else {
            tripDetails(Modifier.weight(1f))
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
                    FlowRow(
                        Modifier.padding(top = Gap.S),
                        horizontalArrangement = Arrangement.spacedBy(Gap.S),
                        verticalArrangement = Arrangement.spacedBy(Gap.S),
                    ) {
                        listOf(3, 5, 10).forEach { km ->
                            FilterChip(
                                state.range == km,
                                { setting("range", km) },
                                label = { Text("$km 公里", fontSize = 14.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pale, selectedLabelColor = GreenStrong),
                            )
                        }
                    }
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
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { i, t ->
                FilterChip(
                    selected = selected == i,
                    onClick = { onSelectedChange(i) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(t, fontSize = 13.sp) },
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader("我的钱包", action = "提现记录", onAction = { navigate("withdrawals") }) }
        item {
            // Balance on a white card in ink; the single teal button is the page's primary action.
            SheetCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("可提现余额（元）· 体验账户", color = Muted, fontSize = 14.sp)
                        Text(
                            money(state.balance),
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("累计收入", color = Muted, fontSize = 14.sp)
                        Text(
                            "¥ ${money(state.todayIncome)}",
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(Gap.M))
                PrimaryButton("申请提现", Modifier.heightIn(min = 52.dp)) { navigate("withdraw") }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SkySoft).clickable { navigate("bank") }.padding(Gap.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("card", Sky, Modifier.size(18.dp))
                Text(
                    "银行卡与交易密码已移至「我的 › 账户安全」",
                    color = Ink,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f).padding(start = Gap.S),
                )
                Text("银行卡", color = Sky, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Glyph("right", Sky, Modifier.size(16.dp))
            }
        }
        val settledOrders = state.orders.filter { it.shared && it.stage != Stage.AVAILABLE && it.settlementStatus.isNotBlank() }
        // One switch picks which list to read, instead of stacking both lists down the page.
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceMuted).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("订单入账状态 ${settledOrders.size}", "收支明细 ${state.entries.size}").forEachIndexed { index, label ->
                    val selected = walletTab == index
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .shadow(if (selected) 2.dp else 0.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color.White else Color.Transparent)
                            .selectable(selected = selected, role = Role.Tab, onClick = { walletTab = index }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) GreenStrong else Muted,
                        )
                    }
                }
            }
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
    var period by rememberSaveable { mutableStateOf("current") }
    fun reload() {
        store.query("/api/heatmap?period=$period", onFailure = { failure = it }) {
            data = it
            failure = null
        }
    }
    LaunchedEffect(period) { reload() }
    val points = data?.optJSONArray("points")
    val coords =
        (0 until (points?.length() ?: 0)).map { i ->
            points!!.getJSONObject(i).let {
                it.optDouble("latitude", it.optDouble("lat")) to
                    it.optDouble("longitude", it.optDouble("lng"))
            }
        }
    Column(Modifier.fillMaxSize()) {
        PageHeader("订单热力分布", onBack, action = "刷新", onAction = { reload() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("current" to "当前", "peak" to "晚高峰", "day" to "全天").forEach { (key, label) ->
                    FilterChip(
                        period == key,
                        { period = key },
                        label = { Text(label, fontSize = 15.sp) },
                        modifier = Modifier.heightIn(min = 52.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            NativeMap(Modifier.fillMaxWidth().height(240.dp), heatPoints = coords)
            Spacer(Modifier.height(16.dp))
            SheetCard {
                Text("${coords.size} 个热点", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    data?.optString("source", "独立服务热区") ?: "加载中",
                    fontSize = 15.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 15.sp) }
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
