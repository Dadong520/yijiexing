package cn.hbzjhs.passenger

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookingScreen(
  state: JSONObject,
  store: PassengerStore,
  busy: Boolean,
  onNegotiation: () -> Unit,
  openOrder: (String) -> Unit,
) {
  val owner = state.optJSONObject("passenger")?.text("id").orEmpty()
  key(owner) { PassengerBookingDraft(owner, state, store, busy, onNegotiation, openOrder) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PassengerBookingDraft(owner: String, state: JSONObject, store: PassengerStore, busy: Boolean, onNegotiation: () -> Unit, openOrder: (String) -> Unit) {
  var kind by rememberSaveable { mutableStateOf("STANDARD") }
  // The pickup point starts empty and follows the phone's own location until the passenger picks one.
  var origin by rememberSaveable { mutableStateOf("") }
  var destination by rememberSaveable { mutableStateOf("夷陵广场 · 东侧入口") }
  var oLat by rememberSaveable { mutableStateOf("") }
  var oLng by rememberSaveable { mutableStateOf("") }
  var dLat by rememberSaveable { mutableStateOf("30.7040") }
  var dLng by rememberSaveable { mutableStateOf("111.2929") }
  var oMetadata by rememberSaveable { mutableStateOf("") }
  var originTouched by rememberSaveable { mutableStateOf(false) }
  var originFromGps by rememberSaveable { mutableStateOf(false) }
  var dMetadata by rememberSaveable { mutableStateOf("") }
  var remark by rememberSaveable { mutableStateOf("") }
  var date by rememberSaveable {
    mutableStateOf(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).toString())
  }
  var waypointName by rememberSaveable { mutableStateOf("") }
  var waypointLat by rememberSaveable { mutableStateOf("") }
  var waypointLng by rememberSaveable { mutableStateOf("") }
  var waypointName2 by rememberSaveable { mutableStateOf("") }
  var waypointLat2 by rememberSaveable { mutableStateOf("") }
  var waypointLng2 by rememberSaveable { mutableStateOf("") }
  var editor by remember { mutableStateOf("") }
  var showWaypoints by rememberSaveable { mutableStateOf(false) }
  var showOptions by rememberSaveable { mutableStateOf(false) }
  var submitting by remember { mutableStateOf(false) }
  val riderIds = state.rows("riders").map { it.text("id") }.toSet()
  var contactId by rememberSaveable { mutableStateOf(PassengerDefaults.draftSelection("", false, PassengerDefaults.id(state, "RIDER"), riderIds)) }
  LaunchedEffect(riderIds) { contactId = PassengerDefaults.draftSelection(contactId, true, "", riderIds) }
  val context = LocalContext.current
  val position by PassengerPositionBus.position.collectAsState()
  val amapKey by AmapRuntime.key.collectAsState()
  // Fix currently used for the automatic pickup point, and whether it already got a place name.
  // Only the passenger id, a local generation and the fix are kept here; never any credential.
  var gpsFix by rememberSaveable { mutableStateOf("") }
  var gpsGeneration by rememberSaveable { mutableStateOf(0) }
  var gpsNamed by rememberSaveable { mutableStateOf(false) }
  // Every manual pickup change (pick, swap, history) sets originTouched; it also retires any lookup in flight.
  LaunchedEffect(originTouched) { if (originTouched) gpsGeneration++ }
  LaunchedEffect(position, originTouched) {
    val fix = position?.let { PickupFix(it.latitude, it.longitude, it.accuracy, it.time) }
    // Only an empty pickup or one still filled from GPS follows the phone; a manual choice always wins.
    if (originTouched || (!originFromGps && origin.isNotBlank())) return@LaunchedEffect
    if (!PassengerPickupDefaults.shouldReplace(PickupFix.parse(gpsFix), fix, originTouched, System.currentTimeMillis())) return@LaunchedEffect
    // Device fixes are WGS84, the same system the booking coordinates use.
    originFromGps = true
    origin = "我的位置（手机定位）"
    oLat = fix!!.latitude.toString()
    oLng = fix.longitude.toString()
    oMetadata = ""
    gpsNamed = false
    gpsGeneration++
    gpsFix = fix.token()
  }
  // Keyed on the applied fix (not every location frame), so a name is looked up once per accepted fix.
  LaunchedEffect(gpsFix, amapKey, originTouched) {
    val fix = PickupFix.parse(gpsFix) ?: return@LaunchedEffect
    if (originTouched || !originFromGps || gpsNamed || amapKey.isBlank() || owner.isBlank()) return@LaunchedEffect
    val requested = PassengerPickupDefaults.lookupToken(owner, gpsGeneration, gpsFix)
    runCatching {
      val shown = amapForward(context, fix.latitude, fix.longitude)
      reverseAmapAddress(context, shown.latitude, shown.longitude)
    }.onSuccess { place ->
      // Re-read the signed-in passenger at completion: a result never lands in another account's draft.
      val currentOwner = store.state.value.optJSONObject("passenger")?.text("id").orEmpty()
      val current = PassengerPickupDefaults.lookupToken(currentOwner, gpsGeneration, gpsFix)
      if (PassengerPickupDefaults.acceptLookup(requested, current, originTouched, originFromGps)) {
        origin = place.name
        oLat = place.latitude.toString()
        oLng = place.longitude.toString()
        oMetadata = place.metadata?.toString().orEmpty()
        gpsNamed = true
      }
    }
  }
  val reservations = state.rows("orders").filter { it.text("kind") == "RESERVED" && it.text("stage") in listOf("AVAILABLE", "RESERVED") }
  val active = state.rows("orders").firstOrNull {
    it.text("stage") !in listOf("DONE", "CANCELLED") && it !in reservations
  }
  val enabledKinds = listOf("STANDARD", "RESERVED", "BID").filter { PassengerOperations.kindEnabled(state,it) }
  LaunchedEffect(enabledKinds) { if (kind !in enabledKinds) kind = enabledKinds.firstOrNull().orEmpty() }
  val submitOrder: () -> Unit = {
    runCatching {
        require(PassengerOperations.kindEnabled(state,kind)) { "当前地区未开放此叫车类型，请刷新后选择。" }
        require(origin.trim().length >= 2 && destination.trim().length >= 2) { "请填写完整起点和终点" }
        val payload =
          JSONObject()
            .put("kind", kind)
            .put("origin", origin.trim())
            .put("destination", destination.trim())
            .put("originLat", coordinate(oLat, true))
            .put("originLng", coordinate(oLng, false))
            .put("destinationLat", coordinate(dLat, true))
            .put("destinationLng", coordinate(dLng, false))
            .put("remark", remark.trim())
            .put("city", state.optJSONObject("passenger")?.text("city", "420500") ?: "420500")
        if (oMetadata.isNotBlank()) payload.put("originCoordinateMetadata", JSONObject(oMetadata))
        if (dMetadata.isNotBlank()) payload.put("destinationCoordinateMetadata", JSONObject(dMetadata))
        if (kind == "RESERVED")
          payload.put(
            "scheduledAt",
            LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant().toString(),
          )
        if (contactId.isNotBlank()) payload.put("riderId", contactId)
        if (showWaypoints) {
          val points = JSONArray()
          if (waypointName.isNotBlank())
            points.put(
              JSONObject()
                .put("name", waypointName)
                .put("latitude", coordinate(waypointLat, true))
                .put("longitude", coordinate(waypointLng, false))
            )
          if (waypointName2.isNotBlank())
            points.put(
              JSONObject()
                .put("name", waypointName2)
                .put("latitude", coordinate(waypointLat2, true))
                .put("longitude", coordinate(waypointLng2, false))
            )
          if (points.length() > 0) payload.put("waypoints", points)
        }
        store.action("order.create", payload) { result ->
          result.optJSONObject("order")?.text("id")?.let(openOrder)
        }
      }
      .onFailure { store.reportError(it.message ?: "请检查填写内容") }
  }
  Column(Modifier.fillMaxSize()) {
    PassengerMap(
      originLat = oLat,
      originLng = oLng,
      destinationLat = dLat,
      destinationLng = dLng,
      modifier = Modifier
        .fillMaxWidth()
        .weight(0.43f)
        .heightIn(min = 200.dp),
    )
    Surface(
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
      color = Color.White,
      shadowElevation = 8.dp,
      border = BorderStroke(1.dp, Line),
      modifier = Modifier
        .fillMaxWidth()
        .weight(0.57f),
    ) {
      Column(Modifier.fillMaxSize()) {
        Column(
          Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          reservations.forEach { reservation ->
            PassengerReservationNotice(formatTime(reservation.text("scheduledAt"))) { openOrder(reservation.text("id")) }
          }
          if (active != null) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = SuccessSoft,
              border = BorderStroke(1.dp, Success.copy(alpha = 0.25f)),
              modifier = Modifier.fillMaxWidth().clickable { openOrder(active.text("id")) },
            ) {
              Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Box(Modifier.size(8.dp).background(Teal, CircleShape))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text("进行中行程", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
                    Text(stageName(active.text("stage")), color = Teal, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                  }
                  Text(
                    "${active.text("origin")} → ${active.text("destination")}",
                    fontSize = 13.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Text("查看行程", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Teal)
                Icon(
                  Icons.AutoMirrored.Filled.KeyboardArrowRight,
                  contentDescription = null,
                  tint = Teal,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf(
              Triple("STANDARD", "即刻出发", Icons.Default.Send),
              Triple("RESERVED", "预约叫车", Icons.Default.DateRange),
              Triple("BID", "司机竞价", Icons.Default.Person),
            ).filter { PassengerOperations.kindEnabled(state,it.first) }.forEach { (mode, label, icon) ->
              val selected = kind == mode
              Surface(
                onClick = { kind = mode },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) Teal else Paper,
                border = BorderStroke(1.dp, if (selected) Teal else Line),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center,
                ) {
                  Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else Ink,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else Ink,
                  )
                }
              }
            }
          }
          Card(
            colors = CardDefaults.cardColors(containerColor = Paper),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
              Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("乘车路线", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
                TextButton(
                  contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                  onClick = {
                    originTouched = true
                    val address = origin
                    origin = destination
                    destination = address
                    val lat = oLat
                    oLat = dLat
                    dLat = lat
                    val lng = oLng
                    oLng = dLng
                    dLng = lng
                    val metadata = oMetadata
                    oMetadata = dMetadata
                    dMetadata = metadata
                  },
                ) {
                  Text("交换起终点", fontSize = 12.sp, color = Teal)
                }
              }
              AddressRow(
                "从这里出发",
                origin.ifBlank { if (position == null) "正在获取手机定位…（可点此选择上车点）" else "点此选择上车点" },
              ) { editor = "origin" }
              HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
              AddressRow(
                "目的地",
                destination,
                emphasize = true,
              ) {
                editor = "destination"
              }
            }
          }
          if (enabledKinds.isEmpty()) Note("当前服务地区暂无开放的叫车类型，历史行程仍可查看。")
          Note(
            if (kind == "BID") "下单后等待司机报价，选定司机后模拟预付。"
            else "下单后生成体验估价，行程结束后模拟支付。"
          )
          if (kind == "RESERVED") Note("预约出发：${date.replace('T', ' ')}")
          Panel {
            MenuRow("更多行程设置", Icons.Default.Settings, "${if(contactId.isBlank()) "本人乘车" else "已选择乘车人"} · 备注${if(remark.isBlank()) "未填写" else "已填写"} · ${if(showWaypoints) "含途经点" else "无途经点"}") { showOptions = true }
          }

          Spacer(Modifier.height(4.dp))
        }
        Surface(
          color = Color.White,
          shadowElevation = 8.dp,
          border = BorderStroke(1.dp, DividerTone),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            ActionButton(
              if (active != null) "查看当前行程" else if (enabledKinds.isEmpty()) "当前地区暂无开放叫车" else "确认呼叫 · ${kindName(kind)}",
              !busy && !submitting && (active != null || enabledKinds.isNotEmpty()),
            ) {
              if (active != null) openOrder(active.text("id")) else submitOrder()
            }
          }
        }
      }
    }
  }
  if (showOptions) PassengerFullPage("行程设置与代叫", { showOptions = false }) {
            if (kind == "RESERVED") {
              Text("预约时间：${date.replace('T', ' ')}", color = Ink, fontWeight = FontWeight.Medium, fontSize = 14.sp)
              OutlinedButton(
                onClick = {
                  val current =
                    runCatching { LocalDateTime.parse(date) }
                      .getOrDefault(LocalDateTime.now().plusHours(1))
                  DatePickerDialog(
                      context,
                      { _, y, m, d ->
                        TimePickerDialog(
                            context,
                            { _, h, min -> date = LocalDateTime.of(y, m + 1, d, h, min).toString() },
                            current.hour,
                            current.minute,
                            true,
                          )
                          .show()
                      },
                      current.year,
                      current.monthValue - 1,
                      current.dayOfMonth,
                    )
                    .show()
                },
                shape = RoundedCornerShape(10.dp),
              ) {
                Text("选择预约日期与时间", color = Teal, fontSize = 13.sp)
              }
              Note("预约范围：15 分钟后至未来 30 天；设备当地时间。")
            }
            val contacts = state.rows("riders")
            if (contacts.isNotEmpty()) {
              Text("乘车人", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                val selfSelected = contactId.isBlank()
                FilterChip(
                  selected = selfSelected,
                  onClick = { contactId = "" },
                  label = {
                    Text("本人 · 尾号 ${state.optJSONObject("passenger")?.text("mobile")?.takeLast(4)}", fontSize = 13.sp)
                  },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Teal,
                    selectedLabelColor = Color.White,
                  ),
                  shape = RoundedCornerShape(8.dp),
                )
                contacts.forEach { c ->
                  val cSelected = contactId == c.text("id")
                  FilterChip(
                    selected = cSelected,
                    onClick = { contactId = c.text("id") },
                    label = { Text("${c.text("name")} · ${c.text("mobile").takeLast(4)}${if (PassengerDefaults.id(state, "RIDER") == c.text("id")) " · 默认" else ""}", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                      selectedContainerColor = Teal,
                      selectedLabelColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                  )
                }
              }
            }
            Field(remark, { remark = it.take(500) }, "用车备注（可选）", lines = 2)
            TextButton(
              onClick = { showWaypoints = !showWaypoints },
            ) {
              Text(if (showWaypoints) "收起途经点" else "添加途经点（可选）", color = Teal, fontSize = 13.sp)
            }
            if (showWaypoints) {
              Field(waypointName, { waypointName = it }, "途经点名称")
              Field(waypointLat, { waypointLat = it }, "途经点纬度", KeyboardType.Decimal)
              Field(waypointLng, { waypointLng = it }, "途经点经度", KeyboardType.Decimal)
              HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
              Text("第二途经点（可选）", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
              Field(waypointName2, { waypointName2 = it }, "第二途经点名称")
              Field(waypointLat2, { waypointLat2 = it }, "第二途经点纬度", KeyboardType.Decimal)
              Field(waypointLng2, { waypointLng2 = it }, "第二途经点经度", KeyboardType.Decimal)
              Note("最多 2 个途经点，按顺序经过。司机到达后确认，乘客可在订单查看进度。")
            }
            HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
            MenuRow("线下议价", Icons.Default.Info, "与现场司机配对，核对报价") { showOptions = false; onNegotiation() }
            state
              .rows("orders")
              .firstOrNull { it.text("stage") in listOf("DONE", "CANCELLED") }
              ?.let { recent ->
                Card(
                  colors = CardDefaults.cardColors(containerColor = Paper),
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, Line),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                      Text("常用行程", fontSize = 12.sp, color = Muted)
                      Text(
                        "${recent.text("origin")} → ${recent.text("destination")}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    OutlinedButton(
                      onClick = {
                        originTouched = true
                        origin = recent.text("origin")
                        destination = recent.text("destination")
                        oLat = recent.coordText("originLat")
                        oLng = recent.coordText("originLng")
                        dLat = recent.coordText("destinationLat")
                        dLng = recent.coordText("destinationLng")
                        oMetadata = recent.optJSONObject("originCoordinateMetadata")?.toString().orEmpty()
                        dMetadata = recent.optJSONObject("destinationCoordinateMetadata")?.toString().orEmpty()
                      },
                      shape = RoundedCornerShape(10.dp),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                      Text("填入路线", fontSize = 12.sp, color = Teal)
                    }
                  }
                }
              }
            ExpandablePanel("地点选择说明", "系统搜索与坐标提示", initialExpanded = false) {
              Note("上车点默认使用手机定位，可点击修改；在地址框输入即自动搜索，也可选常用地点或手动WGS84坐标。")
            }
  }
  if (editor.isNotBlank())
    AddressDialog(
      if (editor == "origin") origin else destination,
      if (editor == "origin") oLat else dLat,
      if (editor == "origin") oLng else dLng,
      (state.rows("favorites") +
        state.rows("historicalLocations").map { JSONObject(it.toString()).put("label", "历史地点") }),
      { editor = "" },
      initialNormalization = (if (editor == "origin") oMetadata else dMetadata).takeIf { it.isNotBlank() }?.let { JSONObject(it) },
      city = state.optJSONObject("passenger")?.text("cityName").orEmpty(),
    ) { name, lat, lng, normalization ->
      if (editor == "origin") {
        originTouched = true
        origin = name
        oLat = lat
        oLng = lng
        oMetadata = normalization?.toString().orEmpty()
      } else {
        destination = name
        dLat = lat
        dLng = lng
        dMetadata = normalization?.toString().orEmpty()
      }
      editor = ""
    }
}

@Composable
fun AddressDialog(
  initial: String,
  initialLat: String,
  initialLng: String,
  favorites: List<JSONObject>,
  close: () -> Unit,
  initialNormalization: JSONObject? = null,
  city: String = "",
  save: (String, String, String, JSONObject?) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val amapKey by AmapRuntime.key.collectAsState()
  var searchCity by remember(city) { mutableStateOf(city) }
  var name by remember(initial, initialLat, initialLng) { mutableStateOf(initial) }
  var lat by remember(initial, initialLat, initialLng) { mutableStateOf(initialLat) }
  var lng by remember(initial, initialLat, initialLng) { mutableStateOf(initialLng) }
  var error by remember { mutableStateOf("") }
  var advanced by remember { mutableStateOf(false) }
  var searching by remember { mutableStateOf(false) }
  var searchJob by remember { mutableStateOf<Job?>(null) }
  var results by remember { mutableStateOf<List<SearchedAddress>>(emptyList()) }
  var source by remember { mutableStateOf("已有地点与坐标") }
  var selectedMetadata by remember(initial, initialLat, initialLng) { mutableStateOf(initialNormalization) }
  // Only text the passenger typed is searched; picking a result never re-triggers a search.
  var typedQuery by remember { mutableStateOf("") }
  // Bumped by the small retry action so the same text can be searched again after a failure or cancel.
  var retryTick by remember { mutableStateOf(0) }
  // Shared by text search, map lookup and every manual change: only the newest request may write.
  val gate = remember { PassengerLookupGate() }
  fun cancelSearch() { gate.invalidate(); searchJob?.cancel(); searchJob=null; searching=false; results=emptyList() }
  val closeEditor = { cancelSearch(); close() }
  fun selectPlace(n: String, a: String, b: String, from: String, metadata: JSONObject? = null) {
    cancelSearch(); name=n; lat=a; lng=b; source=from; selectedMetadata=metadata; error=""
  }
  val places =
    (favorites.filter { it.text("address").isNotBlank() }.map {
        Triple(it.text("address"), it.coordText("latitude"), it.coordText("longitude"))
      } + listOf(
        Triple("宜昌东站 · 出租车上客区", "30.6648", "111.3674"),
        Triple("夷陵广场 · 东侧入口", "30.7040", "111.2929"),
        Triple("三峡游客中心", "30.6778", "111.2944"),
      )).distinctBy { (n, a, b) -> Triple(n, a.toDoubleOrNull(), b.toDoubleOrNull()) }
  val historyKeys =
    favorites.filter { it.text("label") == "历史地点" }
      .map { Triple(it.text("address"), it.coordText("latitude").toDoubleOrNull(), it.coordText("longitude").toDoubleOrNull()) }.toSet()
  // Full-screen picker: title back and system back both call close() without touching the draft.
  PassengerFullPage(
    "选择地点",
    closeEditor,
    bottom = {
      if (name.isNotBlank())
        Text(
          "已选：${name.trim()}" + if (lat.isBlank() || lng.isBlank()) "（未确认坐标）" else "",
          color = Ink,
          fontSize = 14.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      if (error.isNotBlank()) Text(error, color=MaterialTheme.colorScheme.error, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
      ActionButton("使用此地点", !searching) {
        runCatching {
          require(name.trim().length >= 2) { "请填写地址名称" }
          require(lat.isNotBlank() && lng.isNotBlank()) { "地址文字已修改，请先选择搜索结果、常用地点或填写匹配的坐标" }
          coordinate(lat, true); coordinate(lng, false)
          save(name.trim(), lat.trim(), lng.trim(), selectedMetadata)
        }.onFailure { error=it.message.orEmpty() }
      }
    },
  ) {
    Field(name, { value ->
      if (value != name) {
        cancelSearch(); name=value.take(200); lat=""; lng=""; source=""; selectedMetadata=null
        typedQuery=name.trim()
        error=""
      }
    }, "输入地点或详细地址，自动搜索")
    if (amapKey.isNotBlank()) {
      Field(searchCity, { cancelSearch(); searchCity=it.take(40) }, "搜索城市（可选）")
      val selected = if (lat.toDoubleOrNull()?.isFinite() == true && lng.toDoubleOrNull()?.isFinite() == true)
        listOf(AmapPlacePoint(lat.toDouble(), lng.toDouble(), name)) else emptyList()
      AmapCanvas(selected, Modifier.fillMaxWidth().height(240.dp), onPick = { a, b ->
        cancelSearch(); typedQuery=""; lat=""; lng=""; selectedMetadata=null; source=""; error=""
        val token=gate.next()
        searching=true
        searchJob=scope.launch {
          try {
            val result=reverseAmapAddress(context,a,b)
            if (gate.isCurrent(token)) selectPlace(result.name,result.latitude.toString(),result.longitude.toString(),"地图选定地点",result.metadata)
          } catch (e: CancellationException) { throw e }
          catch (e: Exception) { if (gate.isCurrent(token)) error=e.message ?: "地图地点解析失败，请重试" }
          finally { if (gate.isCurrent(token)) searching=false }
        }
      }, notice = "点击地图选点，再核对地址")
    }
    LaunchedEffect(typedQuery, searchCity, retryTick) {
      val query=typedQuery
      if (query.length < 2 || name.trim()!=query) return@LaunchedEffect
      delay(400)
      if (name.trim()!=query || typedQuery!=query) return@LaunchedEffect
      // Takes ownership: an earlier map lookup or search can no longer write results, errors or progress.
      cancelSearch()
      val token=gate.next()
      error=""; searching=true
      try {
        val found=searchSystemAddresses(context, query, searchCity)
        if (gate.isCurrent(token) && name.trim()==query) {
          results=found
          if (found.isEmpty()) error="未找到地址，请补充城市和详细名称，或选择常用地点/手动坐标。"
        }
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        if (gate.isCurrent(token) && name.trim()==query) error=e.message ?: "地址搜索暂不可用，请重试或在地图选点。"
      } finally { if (gate.isCurrent(token)) searching=false }
    }
    if (!searching && results.isEmpty() && name.isNotBlank() && (lat.isBlank() || lng.isBlank()) && error.isBlank())
      Note(if (name.trim().length < 2) "再输入几个字即可自动搜索" else if (typedQuery.isBlank()) "已取消搜索，可重新搜索或改用常用、地图、手动地点" else "请从搜索结果中选择具体地点")
    // Small retry for the same text after a failure or cancel; no persistent search button.
    if (!searching && results.isEmpty() && name.trim().length >= 2 && (lat.isBlank() || lng.isBlank()) &&
      (error.isNotBlank() || typedQuery.isBlank()))
      TextButton(onClick={ cancelSearch(); error=""; typedQuery=name.trim(); retryTick++ }) { Text("重新搜索“${name.trim().take(20)}”", fontSize = 13.sp) }
    if (searching) {
      LinearProgressIndicator(Modifier.fillMaxWidth())
      TextButton(onClick={cancelSearch(); typedQuery=""}) { Text("取消搜索，改用常用或手动地点") }
    }
    results.forEach { result ->
      MenuRow(result.name, Icons.Default.LocationOn, "选择并在地图核对") {
        selectPlace(result.name, result.latitude.toString(), result.longitude.toString(), "搜索选定地点", result.metadata)
      }
    }
    if (source.isNotBlank()) Note("$source · 请核对名称与地图位置")
    Note(if (amapKey.isNotBlank()) "使用高德地点搜索与地图选点；请选择具体车站、入口或建筑。" else "地址服务可用性依赖设备服务，请核对地点后使用。")
    val (historyPlaces, commonPlaces) =
      places.partition { (n, a, b) -> Triple(n, a.toDoubleOrNull(), b.toDoubleOrNull()) in historyKeys }
    listOf("收藏与常用地点" to commonPlaces, "历史地点" to historyPlaces).forEach { (section, rows) ->
      if (rows.isNotEmpty()) {
        Text("$section · ${rows.size}", fontWeight=FontWeight.SemiBold, color = Ink)
        rows.forEach { (n, a, b) ->
          val hasPoint = a.isNotBlank() && b.isNotBlank()
          val subtitle =
            if (!hasPoint) "未提供坐标 · 选中后请搜索或地图选点"
            else if (favorites.any { it.text("address") == n && it.optBoolean("isDefault") }) "默认常用地址 · 点击选择" else "点击选择"
          MenuRow(n, if (section == "历史地点") Icons.Default.Refresh else Icons.Default.LocationOn, subtitle) {
            if (!hasPoint) {
              // Only the name is reused; never fill an unknown point as 0 or NaN.
              cancelSearch(); name=n; lat=""; lng=""; source=""; selectedMetadata=null
              error="该地点未提供坐标，请搜索并选中地点或在地图选点。"
            } else {
              val metadata=favorites.firstOrNull { it.text("address")==n && it.optDouble("latitude")==a.toDoubleOrNull() && it.optDouble("longitude")==b.toDoubleOrNull() }?.optJSONObject("coordinateMetadata")
              selectPlace(n,a,b,if (section == "历史地点") "历史地点" else "常用或体验地点",metadata)
            }
          }
          HorizontalDivider()
        }
      }
    }
    TextButton(onClick={advanced=!advanced}) { Text(if (advanced) "收起手动坐标" else "手动坐标 · 高级设置") }
    if (advanced) {
      Note("请核对名称和WGS84坐标一致；手动填写不会查询地点。")
      Field(lat, {cancelSearch(); lat=it; source="手动坐标"; selectedMetadata=null}, "WGS84 纬度", KeyboardType.Decimal)
      Field(lng, {cancelSearch(); lng=it; source="手动坐标"; selectedMetadata=null}, "WGS84 经度", KeyboardType.Decimal)
    }
  }
}

@Composable
fun Modifier.verticalScrollCompat(): Modifier =
  this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrdersScreen(orders: List<JSONObject>, open: (String) -> Unit) {
  var filter by rememberSaveable { mutableStateOf("全部") }
  val filtered = orders.filter { PassengerUiRules.matchesOrderFilter(it.text("stage"), filter) }
  PageColumn {
    SectionTitle("我的订单", "出行与预约记录")
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      PassengerUiRules.orderFilters.forEach { f ->
        val selected = filter == f
        FilterChip(
          selected = selected,
          onClick = { filter = f },
          label = { Text(f, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Teal,
            selectedLabelColor = Color.White,
            containerColor = Color.White,
            labelColor = Ink,
          ),
          border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) Teal else Line,
            selectedBorderColor = Teal,
          ),
          shape = RoundedCornerShape(10.dp),
        )
      }
    }
    if (filtered.isEmpty()) {
      EmptyCard("暂无${filter}订单", "在“出行”页面呼叫用车，订单状态会在这里实时更新。")
    }
    filtered.forEach { o ->
      Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(kindName(o.text("kind")), color = Muted, fontSize = 13.sp)
          Text(stageName(o.text("stage")), color = if (o.text("stage") == "CANCELLED") Muted else Teal, fontWeight = FontWeight.SemiBold)
        }
        PassengerCompactRecord(
          title = o.text("origin"),
          subtitle = "到 ${o.text("destination")}\n${formatTime(o.text("createdAt"))}",
          onClick = { open(o.text("id")) },
          trailing = { Text(if(o.optBoolean("destinationPending")) "待定" else "¥${money(o.optLong("fare"))}", fontWeight = FontWeight.Bold) },
        )
        if (o.text("scheduledAt").isNotBlank()) Note("预约 ${formatTime(o.text("scheduledAt"))}")
      }
    }
  }
}
