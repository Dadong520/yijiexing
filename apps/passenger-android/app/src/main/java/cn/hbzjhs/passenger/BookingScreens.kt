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
  // A new draft has no destination: the passenger always picks one (no built-in suggestion).
  var destination by rememberSaveable { mutableStateOf("") }
  var oLat by rememberSaveable { mutableStateOf("") }
  var oLng by rememberSaveable { mutableStateOf("") }
  var dLat by rememberSaveable { mutableStateOf("") }
  var dLng by rememberSaveable { mutableStateOf("") }
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
  val gpsStatus by PassengerPositionBus.status.collectAsState()
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
        require(destination.isNotBlank()) { "请先选择目的地" }
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
  val unpaid = state.rows("orders").firstOrNull { it.text("stage") == "WAIT_PAY" && !it.optBoolean("paid") }
  // Drag the panel over the map for every function, or down to a small handle for the whole map.
  // A new trip or unpaid order brings a collapsed panel back so its entry is never left hidden.
  MapPanelLayout(
    saveKey = passengerBookingMapScope(owner),
    attentionKey = listOf(active?.text("id"), active?.text("stage"), unpaid?.text("id")),
    collapsedLabel = "显示叫车面板",
    map = { mapModifier ->
      PassengerMap(
        originLat = oLat,
        originLng = oLng,
        destinationLat = dLat,
        destinationLng = dLng,
        modifier = mapModifier,
      )
    },
    bottomBar = {
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
    },
  ) {
        Column(
          Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          unpaid?.let { order -> OutstandingPaymentNotice { openOrder(order.text("id")) } }
          // Several waiting reservations collapse into one slim line pointing at the earliest.
          reservations.minByOrNull { it.text("scheduledAt") }?.let { reservation ->
            PassengerReservationNotice(
              formatTime(reservation.text("scheduledAt")) + if (reservations.size > 1) " 等 ${reservations.size} 笔" else "",
            ) { openOrder(reservation.text("id")) }
          }
          if (active != null) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color.White,
              shadowElevation = 3.dp,
              border = BorderStroke(1.dp, Teal.copy(alpha = 0.22f)),
              modifier = Modifier.fillMaxWidth().clickable { openOrder(active.text("id")) },
            ) {
              Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                CarIllustration(Modifier.size(width = 44.dp, height = 30.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text("进行中行程", fontWeight = FontWeight.Bold, fontSize = TypeScale.Body, color = Ink)
                    Text(stageName(active.text("stage")), color = Teal, fontSize = TypeScale.Caption, fontWeight = FontWeight.Medium)
                  }
                  Text(
                    "${active.text("origin")} → ${active.text("destination")}",
                    fontSize = TypeScale.Caption,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Text("查看", fontSize = TypeScale.Caption, fontWeight = FontWeight.SemiBold, color = Teal)
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
                color = if (selected) Teal else Color.White,
                border = BorderStroke(1.dp, if (selected) Teal else Line),
                shadowElevation = if (selected) 4.dp else 0.dp,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
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
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else Ink,
                  )
                }
              }
            }
          }
          Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Line),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
              Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("乘车路线", fontWeight = FontWeight.Bold, fontSize = TypeScale.Title, color = Ink)
                // Only a complete route can be swapped; an empty or unknown end never clears the GPS
                // pickup or marks it manual (which would stop GPS from filling it).
                PassengerTextButton(
                  contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                  enabled = routePointReady(origin, oLat, oLng) && routePointReady(destination, dLat, dLng),
                  onClick = {
                    // Re-checked at tap time with the current draft before touching any field.
                    if (routePointReady(origin, oLat, oLng) && routePointReady(destination, dLat, dLng)) {
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
                    }
                  },
                ) {
                  Text("交换起终点", fontSize = TypeScale.Caption)
                }
              }
              AddressRow(
                "从这里出发",
                origin.ifBlank {
                  // Without permission or with location off, say so instead of waiting for a fix forever.
                  if (position == null && ("权限" in gpsStatus || "关闭" in gpsStatus)) "未获取手机定位（$gpsStatus），点此选择上车点"
                  else if (position == null) "正在获取手机定位…（可点此选择上车点）" else "点此选择上车点"
                },
              ) { editor = "origin" }
              HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
              AddressRow(
                "目的地",
                destination.ifBlank { "您要去哪儿？点此选择目的地" },
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
  }
  if (showOptions) PassengerFullPage(
    "行程设置与代叫",
    { showOptions = false },
    bottom = { ActionButton("完成") { showOptions = false } },
  ) {
            val passengerTail = state.optJSONObject("passenger")?.text("mobile")?.takeLast(4).orEmpty()
            val contacts = state.rows("riders")
            TripOptionSection(Icons.Default.Person, "谁来乘车", "为家人朋友叫车时选择乘车人，司机将联系乘车人") {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                RiderChoice("本人 · 尾号 $passengerTail", contactId.isBlank()) { contactId = "" }
                contacts.forEach { c ->
                  val default = if (PassengerDefaults.id(state, "RIDER") == c.text("id")) " · 默认" else ""
                  RiderChoice("${c.text("name")} · ${c.text("mobile").takeLast(4)}$default", contactId == c.text("id")) { contactId = c.text("id") }
                }
              }
              if (contacts.isEmpty()) Note("还没有乘车人，可在“我的 → 乘车人”中添加后为他人叫车。")
            }
            if (kind == "RESERVED")
              TripOptionSection(Icons.Default.DateRange, "预约时间", "15 分钟后至未来 30 天 · 设备当地时间") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(date.replace('T', ' '), color = Ink, fontWeight = FontWeight.Bold, fontSize = TypeScale.Title, modifier = Modifier.weight(1f))
                  PassengerTextButton(onClick = {
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
                  }) { Text("修改时间") }
                }
              }
            TripOptionSection(Icons.Default.Place, "途经点", "最多 2 个，按顺序经过；司机到达后确认") {
              WaypointRow("途经点 1", waypointName) { editor = "waypoint1" }
              if (waypointName.isNotBlank())
                WaypointRow("途经点 2（可选）", waypointName2) { editor = "waypoint2" }
              if (waypointName.isNotBlank())
                PassengerTextButton(onClick = {
                  waypointName = ""; waypointLat = ""; waypointLng = ""
                  waypointName2 = ""; waypointLat2 = ""; waypointLng2 = ""
                  showWaypoints = false
                }) { Text("清除途经点", color = Muted) }
            }
            TripOptionSection(Icons.Default.Edit, "给司机捎句话", null) {
              Field(remark, { remark = it.take(500) }, "例如：在东门等、带了大件行李（可选）", lines = 2)
            }
            TripOptionSection(Icons.Default.Star, "更多方式", null) {
              MenuRow("线下议价", Icons.Default.Share, "与现场司机配对，核对报价") { showOptions = false; onNegotiation() }
              state
                .rows("orders")
                .firstOrNull { it.text("stage") in listOf("DONE", "CANCELLED") }
                ?.let { recent ->
                  HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
                  MenuRow("再走一次：${recent.text("origin")} → ${recent.text("destination")}", Icons.Default.Refresh, "填入上次路线") {
                    originTouched = true
                    origin = recent.text("origin")
                    destination = recent.text("destination")
                    oLat = recent.coordText("originLat")
                    oLng = recent.coordText("originLng")
                    dLat = recent.coordText("destinationLat")
                    dLng = recent.coordText("destinationLng")
                    oMetadata = recent.optJSONObject("originCoordinateMetadata")?.toString().orEmpty()
                    dMetadata = recent.optJSONObject("destinationCoordinateMetadata")?.toString().orEmpty()
                    showOptions = false
                  }
                }
            }
  }
  // Keyed by target so every opening starts with an empty query and no selection.
  if (editor.isNotBlank()) key(editor) {
    AddressDialog(
      when (editor) { "origin" -> origin; "waypoint1" -> waypointName; "waypoint2" -> waypointName2; else -> destination },
      when (editor) { "origin" -> oLat; "waypoint1" -> waypointLat; "waypoint2" -> waypointLat2; else -> dLat },
      when (editor) { "origin" -> oLng; "waypoint1" -> waypointLng; "waypoint2" -> waypointLng2; else -> dLng },
      (state.rows("favorites") +
        state.rows("historicalLocations").map { JSONObject(it.toString()).put("label", "历史地点") }),
      { editor = "" },
      city = state.optJSONObject("passenger")?.text("cityName").orEmpty(),
    ) { name, lat, lng, normalization ->
      when (editor) {
        "origin" -> {
          originTouched = true
          origin = name
          oLat = lat
          oLng = lng
          oMetadata = normalization?.toString().orEmpty()
        }
        "waypoint1" -> {
          waypointName = name
          waypointLat = lat
          waypointLng = lng
          showWaypoints = true
        }
        "waypoint2" -> {
          waypointName2 = name
          waypointLat2 = lat
          waypointLng2 = lng
          showWaypoints = true
        }
        else -> {
          destination = name
          dLat = lat
          dLng = lng
          dMetadata = normalization?.toString().orEmpty()
        }
      }
      editor = ""
    }
  }
}

/** A route end that may be used or swapped: a real name and finite, in-range coordinates. */
private fun routePointReady(name: String, lat: String, lng: String): Boolean =
  name.trim().length >= 2 && runCatching { coordinate(lat, true); coordinate(lng, false) }.isSuccess

/** One titled card on the trip options page, with a lit icon badge. */
@Composable
private fun TripOptionSection(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String?,
  content: @Composable ColumnScope.() -> Unit,
) {
  val (tint, soft) = iconAccent(icon)
  Card(
    colors = CardDefaults.cardColors(containerColor = Color.White),
    shape = RoundedCornerShape(16.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
          Modifier.size(32.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, soft)), RoundedCornerShape(10.dp)),
          contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f)) {
          Text(title, fontSize = TypeScale.Title, fontWeight = FontWeight.Bold, color = Ink)
          subtitle?.let { Note(it) }
        }
      }
      content()
    }
  }
}

@Composable
private fun RiderChoice(label: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label, fontSize = 14.sp) },
    leadingIcon = if (selected) ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
    colors = FilterChipDefaults.filterChipColors(
      selectedContainerColor = BrandSoft,
      selectedLabelColor = BrandStrong,
      selectedLeadingIconColor = BrandStrong,
    ),
    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = Line, selectedBorderColor = Teal),
    shape = RoundedCornerShape(10.dp),
  )
}

@Composable
private fun WaypointRow(label: String, value: String, onClick: () -> Unit) {
  AddressRow(label, value.ifBlank { "点此选择地点" }, onClick = onClick)
}

/**
 * Full-screen place picker: map on top, an always-empty query box below it, and fuzzy search results
 * under the box. [initial]/[initialLat]/[initialLng] (the current point, if any) only position the map
 * and are never prefilled or resubmitted; [save] runs only for a place chosen here (search result,
 * map pick, common or history place) with known coordinates. Back or 返回 leaves the caller unchanged.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddressDialog(
  initial: String,
  initialLat: String,
  initialLng: String,
  favorites: List<JSONObject>,
  close: () -> Unit,
  city: String = "",
  save: (String, String, String, JSONObject?) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val amapKey by AmapRuntime.key.collectAsState()
  var searchCity by remember(city) { mutableStateOf(city) }
  // What the passenger types; it starts empty every time the picker opens.
  var query by remember { mutableStateOf("") }
  // The chosen place. Empty until something is picked here; typing alone never changes it.
  var name by remember { mutableStateOf("") }
  var lat by remember { mutableStateOf("") }
  var lng by remember { mutableStateOf("") }
  var error by remember { mutableStateOf("") }
  var searching by remember { mutableStateOf(false) }
  // True from a keystroke until its search finishes or is cancelled (covers the 400ms wait).
  var pending by remember { mutableStateOf(false) }
  var searchJob by remember { mutableStateOf<Job?>(null) }
  var results by remember { mutableStateOf<List<SearchedAddress>>(emptyList()) }
  var source by remember { mutableStateOf("") }
  var selectedMetadata by remember { mutableStateOf<JSONObject?>(null) }
  // Only text the passenger typed is searched; picking a result never re-triggers a search.
  var typedQuery by remember { mutableStateOf("") }
  // Bumped by the small retry action so the same text can be searched again after a failure or cancel.
  var retryTick by remember { mutableStateOf(0) }
  // Shared by text search, map lookup and every manual change: only the newest request may write.
  val gate = remember { PassengerLookupGate() }
  fun cancelSearch() { gate.invalidate(); searchJob?.cancel(); searchJob=null; searching=false; pending=false; results=emptyList() }
  val closeEditor = { cancelSearch(); close() }
  fun clearSelection() { name=""; lat=""; lng=""; source=""; selectedMetadata=null }
  fun selectPlace(n: String, a: String, b: String, from: String, metadata: JSONObject? = null) {
    cancelSearch(); name=n; lat=a; lng=b; source=from; selectedMetadata=metadata; error=""
  }
  // Any new query text (including empty, one character or a name-only common place) voids the
  // previous pick at once, so it can never be used during the 400ms wait or after a cancel/failure.
  fun searchFor(text: String) {
    cancelSearch(); clearSelection(); query=text.take(200); typedQuery=query.trim(); pending=typedQuery.length >= 2; error=""
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
      Text(
        if (name.isNotBlank()) "已选：${name.trim()}" else "尚未选择 · 请搜索后选择结果，或在地图上点选",
        color = if (name.isNotBlank()) Ink else Muted,
        fontSize = 14.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (error.isNotBlank()) Text(error, color=MaterialTheme.colorScheme.error, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
      ActionButton("使用此地点", !searching && !pending && routePointReady(name, lat, lng)) {
        runCatching {
          // Re-checked at tap time: a search still waiting or running means there is no current pick.
          require(!searching && !pending) { "正在搜索，请从结果中选择地点后再使用" }
          require(name.trim().length >= 2) { "请选择具体地点" }
          require(lat.isNotBlank() && lng.isNotBlank()) { "请从搜索结果、地图或常用地点中选择一个具体地点" }
          coordinate(lat, true); coordinate(lng, false)
          save(name.trim(), lat.trim(), lng.trim(), selectedMetadata)
        }.onFailure { error=it.message.orEmpty() }
      }
    },
  ) {
    fun finite(value: String) = value.toDoubleOrNull()?.takeIf { it.isFinite() }
    // The map shows the chosen place, else the current point only to position the view.
    val chosenPoint = finite(lat)?.let { a -> finite(lng)?.let { b -> AmapPlacePoint(a, b, name) } }
    val shownPoint = chosenPoint
      ?: finite(initialLat)?.let { a -> finite(initialLng)?.let { b -> AmapPlacePoint(a, b, initial.ifBlank { "当前地点" }) } }
    // The keyboard takes room from the page, so the map gives way to keep results in view.
    val mapModifier = Modifier.fillMaxWidth().height(if (WindowInsets.isImeVisible) 150.dp else 240.dp)
    if (amapKey.isNotBlank()) {
      AmapCanvas(listOfNotNull(shownPoint), mapModifier, onPick = { a, b ->
        cancelSearch(); clearSelection(); error=""
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
    } else {
      PassengerMap(
        originLat = shownPoint?.latitude?.toString().orEmpty(),
        originLng = shownPoint?.longitude?.toString().orEmpty(),
        destinationLat = "",
        destinationLng = "",
        modifier = mapModifier,
      )
    }
    if (initial.isNotBlank() && chosenPoint == null) Note("当前：${initial.trim()} · 选择新地点后才会替换")
    Field(query, { value -> if (value != query) searchFor(value) }, "输入地点或详细地址，自动搜索")
    LaunchedEffect(typedQuery, searchCity, retryTick) {
      val text=typedQuery
      if (text.length < 2 || query.trim()!=text) return@LaunchedEffect
      val city=searchCity
      // Ownership is taken before the 400ms wait: manual coordinates, any selection (even one with the
      // same name), map pick, cancel or closing invalidate the gate and retire this run while it waits.
      PassengerAddressLookup.debounced(
        gate,
        stillWanted = { query.trim()==text && typedQuery==text },
        wait = { delay(PassengerAddressLookup.DEBOUNCE_MS) },
        begin = { searchJob?.cancel(); searchJob=null; error=""; results=emptyList(); searching=true },
        fetch = { searchSystemAddresses(context, text, city) },
        publish = { found ->
          results=found
          if (found.isEmpty()) error="未找到地址，请补充城市和详细名称，或在地图上点选。"
        },
        fail = { e -> error=e.message ?: "地址搜索暂不可用，请重试或在地图选点。" },
        end = { searching=false; pending=false },
      )
    }
    if (searching) {
      LinearProgressIndicator(Modifier.fillMaxWidth())
      PassengerTextButton(onClick={cancelSearch(); typedQuery=""}) { Text("取消搜索") }
    } else if (query.isNotBlank() && query.trim().length < 2) {
      Note("再输入几个字即可自动搜索")
    } else if (!pending && results.isEmpty() && query.trim().length >= 2) {
      // Small retry for the same text after a failure, cancel or another pick; no persistent search button.
      if (typedQuery.isBlank() && error.isBlank()) Note("已取消搜索，可重新搜索或改用常用、地图地点")
      PassengerTextButton(onClick={ searchFor(query); retryTick++ }) { Text("重新搜索“${query.trim().take(20)}”", fontSize = 13.sp) }
    }
    results.forEach { result ->
      MenuRow(result.name, Icons.Default.LocationOn, "选择并在地图核对") {
        selectPlace(result.name, result.latitude.toString(), result.longitude.toString(), "搜索选定地点", result.metadata)
      }
    }
    // A different city means a different search: the old pick is voided too, never confirmed by mistake.
    if (amapKey.isNotBlank()) Field(searchCity, { value ->
      if (value.take(40) != searchCity) {
        cancelSearch(); clearSelection(); error=""; searchCity=value.take(40)
        pending=typedQuery.length >= 2 && typedQuery==query.trim()
      }
    }, "搜索城市（可选）")
    if (source.isNotBlank()) Note("$source · 请核对名称与地图位置")
    Note(if (amapKey.isNotBlank()) "输入即搜索，也可点击地图选点；请选择具体车站、入口或建筑。" else "输入即搜索；地址服务可用性依赖设备服务，请核对地点后使用。")
    val (historyPlaces, commonPlaces) =
      places.partition { (n, a, b) -> Triple(n, a.toDoubleOrNull(), b.toDoubleOrNull()) in historyKeys }
    listOf("收藏与常用地点" to commonPlaces, "历史地点" to historyPlaces).forEach { (section, rows) ->
      if (rows.isNotEmpty()) {
        Text("$section · ${rows.size}", fontWeight=FontWeight.SemiBold, color = Ink)
        rows.forEach { (n, a, b) ->
          val hasPoint = a.isNotBlank() && b.isNotBlank()
          val subtitle =
            if (!hasPoint) "未提供坐标 · 点击按名称搜索后选择"
            else if (favorites.any { it.text("address") == n && it.optBoolean("isDefault") }) "默认常用地址 · 点击选择" else "点击选择"
          MenuRow(n, if (section == "历史地点") Icons.Default.Refresh else Icons.Default.LocationOn, subtitle) {
            if (!hasPoint) {
              // Only the name is reused, as a search; an unknown point is never filled as 0 or NaN.
              searchFor(n)
            } else {
              val metadata=favorites.firstOrNull { it.text("address")==n && it.optDouble("latitude")==a.toDoubleOrNull() && it.optDouble("longitude")==b.toDoubleOrNull() }?.optJSONObject("coordinateMetadata")
              selectPlace(n,a,b,if (section == "历史地点") "历史地点" else "常用或体验地点",metadata)
            }
          }
          HorizontalDivider()
        }
      }
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
