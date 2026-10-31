package cn.hbzjhs.passenger

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
  // Up to two stops, in order: pickup → stop 1 → stop 2 → destination. Edited from the destination page.
  var waypointName by rememberSaveable { mutableStateOf("") }
  var waypointLat by rememberSaveable { mutableStateOf("") }
  var waypointLng by rememberSaveable { mutableStateOf("") }
  var waypointName2 by rememberSaveable { mutableStateOf("") }
  var waypointLat2 by rememberSaveable { mutableStateOf("") }
  var waypointLng2 by rememberSaveable { mutableStateOf("") }
  var editor by remember { mutableStateOf("") }
  // Armed whenever a place page closes back to this home (chosen or back), so a fast second tap
  // cannot reach the call button or swap that the closing page uncovers. Per owner (draft is keyed).
  val returnShield = rememberReturnTapShield()
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
  val stops = listOf(waypointName, waypointName2).filter { it.isNotBlank() }
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
      // One strong action per page: the call button. A hairline separates it instead of a shadow.
      Column(Modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(color = DividerTone)
        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
          ActionButton(
            if (active != null) "查看当前行程" else if (enabledKinds.isEmpty()) "当前地区暂无开放叫车" else "确认呼叫 · ${kindName(kind)}",
            !busy && !submitting && (active != null || enabledKinds.isNotEmpty()) && !returnShield.active,
          ) {
            // Checked against the clock at tap time: a second tap from the place page just closed is ignored.
            if (!returnShield.allows()) return@ActionButton
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          unpaid?.let { order -> OutstandingPaymentNotice { openOrder(order.text("id")) } }
          // Several waiting reservations collapse into one slim line pointing at the earliest.
          reservations.minByOrNull { it.text("scheduledAt") }?.let { reservation ->
            PassengerReservationNotice(
              formatTime(reservation.text("scheduledAt")) + if (reservations.size > 1) " 等 ${reservations.size} 笔" else "",
            ) { openOrder(reservation.text("id")) }
          }
          if (active != null) {
            Row(
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BrandSoft)
                .clickable(role = Role.Button) { openOrder(active.text("id")) }
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Box(Modifier.size(8.dp).background(Teal, CircleShape))
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("进行中行程 · ${stageName(active.text("stage"))}", fontWeight = FontWeight.SemiBold, fontSize = TypeScale.Body, color = Ink)
                Text(
                  "${active.text("origin")} → ${active.text("destination")}",
                  fontSize = TypeScale.Caption,
                  color = Muted,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Text("查看", fontSize = TypeScale.Caption, fontWeight = FontWeight.SemiBold, color = BrandStrong)
              Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = BrandStrong, modifier = Modifier.size(16.dp))
            }
          }
          if (enabledKinds.isNotEmpty())
            KindSegments(
              listOf("STANDARD" to "即刻出发", "RESERVED" to "预约叫车", "BID" to "司机竞价").filter { it.first in enabledKinds },
              kind,
            ) { kind = it }
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("您要去哪儿？", fontWeight = FontWeight.Bold, fontSize = TypeScale.Page, lineHeight = 28.sp, color = Ink, modifier = Modifier.weight(1f))
            // Only a complete route can be swapped; an empty or unknown end never clears the GPS
            // pickup or marks it manual (which would stop GPS from filling it).
            PassengerTextButton(
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
              enabled = routePointReady(origin, oLat, oLng) && routePointReady(destination, dLat, dLng) && !returnShield.active,
              onClick = {
                // Re-checked at tap time with the current draft before touching any field.
                if (returnShield.allows() && routePointReady(origin, oLat, oLng) && routePointReady(destination, dLat, dLng)) {
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
                  // The reversed trip passes its stops in reverse order too.
                  if (waypointName.isNotBlank() && waypointName2.isNotBlank()) {
                    val n = waypointName; waypointName = waypointName2; waypointName2 = n
                    val a = waypointLat; waypointLat = waypointLat2; waypointLat2 = a
                    val b = waypointLng; waypointLng = waypointLng2; waypointLng2 = b
                  }
                }
              },
            ) {
              Text("交换起终点", fontSize = TypeScale.Caption)
            }
          }
          // The route reads top to bottom as the trip runs: pickup, stops, destination.
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(Modifier.padding(start = 14.dp, end = 8.dp)) {
              AddressRow(
                "上车点",
                origin.ifBlank {
                  // Without permission or with location off, say so instead of waiting for a fix forever.
                  if (position == null && ("权限" in gpsStatus || "关闭" in gpsStatus)) "未获取手机定位（$gpsStatus），点此选择上车点"
                  else if (position == null) "正在获取手机定位…（可点此选择上车点）" else "点此选择上车点"
                },
                placeholder = origin.isBlank(),
              ) { editor = "origin" }
              HorizontalDivider(Modifier.padding(start = 22.dp), color = DividerTone)
              if (stops.isNotEmpty()) {
                AddressRow("途经点 · ${stops.size}/2 · 点此修改", stops.joinToString("  →  "), dot = IconTone) { editor = "destination" }
                HorizontalDivider(Modifier.padding(start = 22.dp), color = DividerTone)
              }
              AddressRow(
                "目的地",
                destination.ifBlank { "点此选择目的地，可添加途经点" },
                emphasize = true,
                placeholder = destination.isBlank(),
              ) {
                editor = "destination"
              }
            }
          }
          if (enabledKinds.isEmpty()) Note("当前服务地区暂无开放的叫车类型，历史行程仍可查看。")
          Note(
            (if (kind == "BID") "下单后等待司机报价，选定司机后模拟预付。"
            else "下单后生成体验估价，行程结束后模拟支付。") +
              if (kind == "RESERVED") "\n预约出发：${date.replace('T', ' ')}" else ""
          )
          MenuRow("更多行程设置", Icons.Default.Settings, "${if(contactId.isBlank()) "本人乘车" else "已选择乘车人"} · 备注${if(remark.isBlank()) "未填写" else "已填写"}${if (kind == "RESERVED") " · 预约时间" else ""}") { showOptions = true }
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
    val target = editor
    val stopSlot = when (target) { "waypoint1" -> 1; "waypoint2" -> 2; else -> 0 }
    val nextSlot = PlaceSelection.nextStopSlot(waypointName, waypointName2)
    // A stop is picked from the destination page and returns there; every other picker returns home.
    // Arms the home shield before the picker leaves, i.e. before the home controls are uncovered.
    fun leave() {
      if (stopSlot > 0) editor = "destination" else { returnShield.arm(); editor = "" }
    }
    val back = { leave() }
    val stopsEditor: (@Composable ColumnScope.() -> Unit)? =
      if (target == "destination" && stops.isNotEmpty()) {
        {
          RouteStopsEditor(
            origin = origin,
            stops = stops,
            onEdit = { index -> editor = "waypoint${index + 1}" },
            onRemove = { index ->
              // Removing stop 1 moves stop 2 up, so the order of the remaining stop never changes.
              if (index == 0) {
                waypointName = waypointName2; waypointLat = waypointLat2; waypointLng = waypointLng2
              }
              waypointName2 = ""; waypointLat2 = ""; waypointLng2 = ""
            },
          )
        }
      } else null
    AddressDialog(
      when (target) { "origin" -> origin; "waypoint1" -> waypointName; "waypoint2" -> waypointName2; else -> destination },
      when (target) { "origin" -> oLat; "waypoint1" -> waypointLat; "waypoint2" -> waypointLat2; else -> dLat },
      when (target) { "origin" -> oLng; "waypoint1" -> waypointLng; "waypoint2" -> waypointLng2; else -> dLng },
      favorites = state.rows("favorites"),
      close = back,
      city = state.optJSONObject("passenger")?.text("cityName").orEmpty(),
      title = when (target) {
        "origin" -> "选择上车点"
        "destination" -> "您要去哪儿"
        else -> if ((if (stopSlot == 1) waypointName else waypointName2).isBlank()) "添加途经点 $stopSlot" else "修改途经点 $stopSlot"
      },
      history = state.rows("historicalLocations"),
      queryLabel = when (target) { "origin" -> "搜索上车点"; "destination" -> "搜索目的地"; else -> "搜索途经点" },
      onAddStop = if (target == "destination" && nextSlot != null) ({ editor = "waypoint$nextSlot" }) else null,
      routeStops = stopsEditor,
    ) { name, lat, lng, normalization ->
      // Written only for this opening and the passenger who opened it; a late callback after a
      // page or account change is dropped. Choosing a place never creates or pays an order.
      val currentOwner = store.state.value.optJSONObject("passenger")?.text("id").orEmpty()
      if (editor != target || currentOwner != owner) return@AddressDialog
      when (target) {
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
        }
        "waypoint2" -> {
          waypointName2 = name
          waypointLat2 = lat
          waypointLng2 = lng
        }
        else -> {
          destination = name
          dLat = lat
          dLng = lng
          dMetadata = normalization?.toString().orEmpty()
        }
      }
      leave()
    }
  }
}

/**
 * Compose side of [ReturnTapGuard] for a screen that opens a place page. [arm] when the place page
 * closes; risky actions check [allows] at tap time (the real gate, read from the clock, so an old
 * closure or the first frame after closing cannot slip through) and use [active] for their enabled
 * look, which clears itself when the window ends.
 */
@Stable
class ReturnTapShield(val guard: ReturnTapGuard = ReturnTapGuard()) {
  var active by mutableStateOf(false)
    private set
  var generation by mutableIntStateOf(0)
    private set

  fun arm() {
    guard.arm()
    active = true
    generation++
  }

  fun allows(): Boolean = guard.allows().also { if (it) active = false }

  internal fun settle() {
    active = !guard.allows()
  }
}

@Composable
fun rememberReturnTapShield(): ReturnTapShield {
  val shield = remember { ReturnTapShield() }
  // Restarted by every arm; ends the enabled-look once the clock says the window is over.
  LaunchedEffect(shield, shield.generation) {
    while (true) {
      val wait = shield.guard.remainingMs()
      if (wait <= 0L) break
      delay(wait)
    }
    shield.settle()
  }
  return shield
}

/** A route end that may be used or swapped: a real name and finite, in-range coordinates. */
private fun routePointReady(name: String, lat: String, lng: String): Boolean =
  name.trim().length >= 2 && runCatching { coordinate(lat, true); coordinate(lng, false) }.isSuccess

/** Compact segmented choice of ride type: one neutral track, the chosen segment raised in white. */
@Composable
private fun KindSegments(kinds: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .height(IntrinsicSize.Min)
      .clip(RoundedCornerShape(12.dp))
      .background(SurfaceMuted)
      .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    kinds.forEach { (mode, label) ->
      val on = mode == selected
      Box(
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .heightIn(min = 40.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(if (on) Color.White else Color.Transparent)
          .then(if (on) Modifier.border(BorderStroke(1.dp, Line), RoundedCornerShape(10.dp)) else Modifier)
          .selectable(selected = on, role = Role.Tab, onClick = { onSelect(mode) })
          .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          fontSize = 14.sp,
          lineHeight = 18.sp,
          textAlign = TextAlign.Center,
          fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
          color = if (on) BrandStrong else Muted,
        )
      }
    }
  }
}

/** The trip's stops shown on the destination page, each editable or removable. */
@Composable
private fun RouteStopsEditor(origin: String, stops: List<String>, onEdit: (Int) -> Unit, onRemove: (Int) -> Unit) {
  Column(Modifier.fillMaxWidth()) {
    Note("行程路线 · 按顺序经过，最多 2 个途经点")
    StopLine(Teal, "起点", origin.ifBlank { "上车点待选择" })
    stops.forEachIndexed { index, name ->
      Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(Modifier.size(8.dp).background(IconTone, CircleShape))
        Column(Modifier.weight(1f)) {
          Text("途经点 ${index + 1}", color = Muted, fontSize = TypeScale.Caption, lineHeight = 16.sp)
          Text(name, color = Ink, fontSize = TypeScale.Body, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
        }
        PassengerTextButton(onClick = { onEdit(index) }) { Text("修改") }
        PassengerTextButton(onClick = { onRemove(index) }) { Text("删除", color = Danger) }
      }
    }
    StopLine(Amber, "终点", "在下方搜索或选择")
  }
}

@Composable
private fun StopLine(dot: Color, label: String, value: String) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(Modifier.size(8.dp).background(dot, CircleShape))
    Text(label, color = Muted, fontSize = TypeScale.Caption)
    Text(value, color = Ink, fontSize = TypeScale.Body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
  }
}

/** One titled section on the trip options page: flat, hairline edge, monochrome icon. */
@Composable
private fun TripOptionSection(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String?,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    color = Color.White,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, DividerTone),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = IconTone, modifier = Modifier.size(20.dp))
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

/** Built-in experience places offered next to the passenger's own common places. */
private val ExperiencePlaces = listOf(
  PickedPlace("宜昌东站 · 出租车上客区", 30.6648, 111.3674),
  PickedPlace("夷陵广场 · 东侧入口", 30.7040, 111.2929),
  PickedPlace("三峡游客中心", 30.6778, 111.2944),
)

/**
 * Full-screen place picker: map on top, an always-empty query box below it, and fuzzy search results
 * under the box. [initial]/[initialLat]/[initialLng] (the current point, if any) only position the map
 * and are never prefilled or resubmitted.
 *
 * One tap chooses: a search result, a common or history place with a usable point, or a map point whose
 * reverse lookup has just succeeded is handed to [save] at once (at most once per opening). That tap
 * only chooses an address; callers keep their own save, price or payment confirmations. A place whose
 * point is missing or broken is never used or guessed: it is searched by name with a visible notice.
 * Back or 返回 leaves the caller unchanged.
 *
 * [history] uses the server schema {address, latitude, longitude, coordinateMetadata}. [onAddStop] adds a
 * "+ 途经点" action beside the query; [routeStops] shows the caller's route above the query.
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
  title: String = "选择地点",
  history: List<JSONObject> = emptyList(),
  queryLabel: String = "搜索地点或详细地址",
  onAddStop: (() -> Unit)? = null,
  routeStops: (@Composable ColumnScope.() -> Unit)? = null,
  save: (String, String, String, JSONObject?) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val amapKey by AmapRuntime.key.collectAsState()
  val pageScroll = rememberScrollState()
  var searchCity by remember(city) { mutableStateOf(city) }
  // What the passenger types; it starts empty every time the picker opens.
  var query by remember { mutableStateOf("") }
  var error by remember { mutableStateOf("") }
  // Neutral guidance next to the query, e.g. why a history place is being searched by name.
  var hint by remember { mutableStateOf("") }
  var searching by remember { mutableStateOf(false) }
  // A map point is being turned into an address.
  var locating by remember { mutableStateOf(false) }
  // True from a keystroke until its search finishes or is cancelled (covers the 400ms wait).
  var pending by remember { mutableStateOf(false) }
  var searchJob by remember { mutableStateOf<Job?>(null) }
  var results by remember { mutableStateOf<List<SearchedAddress>>(emptyList()) }
  // Only text the passenger typed is searched; picking a result never re-triggers a search.
  var typedQuery by remember { mutableStateOf("") }
  // Bumped by the small retry action so the same text can be searched again after a failure or cancel.
  var retryTick by remember { mutableStateOf(0) }
  // Bumped to bring the query and its notices back into view (they sit above the place lists).
  var revealQuery by remember { mutableStateOf(0) }
  // One hand-over per opening. Its gate is shared by text search, map lookup and every pick, so only
  // the newest request may write and nothing can write after a place was handed over.
  val session = remember { PlacePickSession() }
  val gate = session.gate
  LaunchedEffect(revealQuery) { if (revealQuery > 0) pageScroll.animateScrollTo(0) }
  fun cancelSearch() { gate.invalidate(); searchJob?.cancel(); searchJob=null; searching=false; locating=false; pending=false; results=emptyList() }
  val closeEditor = { cancelSearch(); close() }
  fun deliver(place: PickedPlace?): Boolean {
    if (place == null) return false
    searching=false; locating=false; pending=false; error=""; hint=""
    save(place.name, place.latitude.toString(), place.longitude.toString(), place.metadata)
    return true
  }
  // Any new query text (including empty, one character or a name-only place) retires older requests.
  fun searchFor(text: String) {
    cancelSearch(); query=text.take(200); typedQuery=query.trim(); pending=typedQuery.length >= 2; error=""; hint=""
  }
  fun pickRow(row: PlaceRow) {
    if (session.committed) return
    when (val resolution = row.resolution) {
      is PlaceResolution.Ready -> {
        val place = session.commit(resolution) ?: return
        cancelSearch(); deliver(place)
      }
      is PlaceResolution.SearchByName -> {
        // Only the name is reused, as a search; an unknown point is never filled as 0 or NaN.
        // The tick restarts the search even when the same name is tapped again.
        searchFor(resolution.name); retryTick++
        hint = "“${resolution.name}”${resolution.reason}，不能直接使用。已按名称搜索，请在下方结果中选择，或在地图上点选。"
        revealQuery++
      }
      is PlaceResolution.Unusable -> { error = resolution.reason; revealQuery++ }
    }
  }
  fun pickResult(result: SearchedAddress) {
    if (session.committed) return
    val place = session.commit(PlaceSelection.resolve(result.name, result.latitude, result.longitude, result.metadata))
    if (place == null) { error = "该结果缺少有效坐标，请选择其他结果或在地图上点选"; return }
    cancelSearch(); deliver(place)
  }
  val rows = PlaceSelection.pickerRows(favorites.filter { it.text("address").isNotBlank() }, history, ExperiencePlaces)
  // Full-screen picker: title back and system back both call close() without touching the draft.
  PassengerFullPage(title, closeEditor, scrollState = pageScroll) {
    fun finite(value: String) = value.toDoubleOrNull()?.takeIf { it.isFinite() }
    // The current point only positions the view; it is never resubmitted.
    val shownPoint = finite(initialLat)?.let { a -> finite(initialLng)?.let { b -> AmapPlacePoint(a, b, initial.ifBlank { "当前地点" }) } }
    // The keyboard takes room from the page, so the map gives way to keep results in view.
    val mapModifier = Modifier.fillMaxWidth().height(if (WindowInsets.isImeVisible) 150.dp else 240.dp)
    if (amapKey.isNotBlank()) {
      AmapCanvas(listOfNotNull(shownPoint), mapModifier, onPick = { a, b ->
        if (!session.committed) {
          cancelSearch(); error=""; hint=""
          val token = session.beginLookup()
          if (token != null) {
            locating = true
            searchJob = scope.launch {
              try {
                val found = reverseAmapAddress(context, a, b)
                // Handed over only if this is still the newest request and its point is usable.
                if (session.isCurrent(token)) {
                  val place = session.commitLookup(token, PlaceSelection.resolve(found.name, found.latitude, found.longitude, found.metadata))
                  if (!deliver(place)) error = "这个位置没有可用的地址或坐标，请换个位置点选，或搜索附近地点"
                }
              } catch (e: CancellationException) { throw e }
              catch (e: Exception) { if (session.isCurrent(token)) error = e.message ?: "地图地点解析失败，请重试" }
              finally { if (session.isCurrent(token)) locating = false }
            }
          }
        }
      }, notice = "点地图即选定该位置")
    } else {
      PassengerMap(
        originLat = shownPoint?.latitude?.toString().orEmpty(),
        originLng = shownPoint?.longitude?.toString().orEmpty(),
        destinationLat = "",
        destinationLng = "",
        modifier = mapModifier,
      )
    }
    routeStops?.invoke(this)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.weight(1f)) { Field(query, { value -> if (value != query) searchFor(value) }, queryLabel) }
      if (onAddStop != null)
        PassengerTextButton(
          onClick = { if (!session.committed) { cancelSearch(); onAddStop() } },
          modifier = Modifier.heightIn(min = 48.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Text("途经点", modifier = Modifier.padding(start = 2.dp))
        }
    }
    if (initial.isNotBlank()) Note("当前：${initial.trim()} · 点选新地点即替换，返回则不修改")
    LaunchedEffect(typedQuery, searchCity, retryTick) {
      val text=typedQuery
      if (text.length < 2 || query.trim()!=text || session.committed) return@LaunchedEffect
      val city=searchCity
      // Ownership is taken before the 400ms wait: any pick, map tap, cancel or closing invalidates the
      // gate and retires this run while it waits.
      PassengerAddressLookup.debounced(
        gate,
        stillWanted = { query.trim()==text && typedQuery==text && !session.committed },
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
    // Progress and notices sit right under the query so they are always seen.
    if (locating) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Teal)
        Text("正在识别地图位置…", color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        PassengerTextButton(onClick = { cancelSearch() }) { Text("取消") }
      }
    } else if (searching) {
      LinearProgressIndicator(Modifier.fillMaxWidth())
      PassengerTextButton(onClick={cancelSearch(); typedQuery=""}) { Text("取消搜索") }
    } else if (query.isNotBlank() && query.trim().length < 2) {
      Note("再输入几个字即可自动搜索")
    } else if (!pending && results.isEmpty() && query.trim().length >= 2) {
      // Small retry for the same text after a failure, cancel or another pick; no persistent search button.
      if (typedQuery.isBlank() && error.isBlank()) Note("已取消搜索，可重新搜索或改用常用、地图地点")
      PassengerTextButton(onClick={ searchFor(query); retryTick++ }) { Text("重新搜索“${query.trim().take(20)}”", fontSize = 13.sp) }
    }
    if (hint.isNotBlank())
      Text(
        hint,
        color = Ink,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(WarmSoft).padding(horizontal = 10.dp, vertical = 8.dp),
      )
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 18.sp)
    if (results.isNotEmpty()) {
      Column(Modifier.fillMaxWidth()) {
        results.forEach { result ->
          PlaceListRow(result.name, "搜索结果 · 点击即选定", Icons.Default.LocationOn) { pickResult(result) }
          HorizontalDivider(Modifier.padding(start = 34.dp), color = DividerTone)
        }
      }
    }
    // A different city means a different search; older requests are retired.
    if (amapKey.isNotBlank()) Field(searchCity, { value ->
      if (value.take(40) != searchCity) {
        cancelSearch(); error=""; hint=""; searchCity=value.take(40)
        pending=typedQuery.length >= 2 && typedQuery==query.trim()
      }
    }, "搜索城市（可选）")
    listOf(PlaceGroup.COMMON to "收藏与常用地点", PlaceGroup.HISTORY to "历史地点").forEach { (group, section) ->
      val shown = rows.filter { it.group == group }
      if (shown.isNotEmpty()) {
        Column(Modifier.fillMaxWidth()) {
          Text(section, fontWeight = FontWeight.SemiBold, fontSize = TypeScale.Caption, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
          shown.forEach { row ->
            val subtitle = when (val resolution = row.resolution) {
              is PlaceResolution.Ready -> if (row.isDefault) "默认常用地址 · 点击即选定" else "点击即选定"
              is PlaceResolution.SearchByName -> "${resolution.reason} · 点击按名称搜索"
              is PlaceResolution.Unusable -> resolution.reason
            }
            PlaceListRow(
              row.name,
              subtitle,
              if (group == PlaceGroup.HISTORY) Icons.Default.Refresh else if (row.isDefault) Icons.Default.Star else Icons.Default.LocationOn,
              warn = row.resolution !is PlaceResolution.Ready,
            ) { pickRow(row) }
            HorizontalDivider(Modifier.padding(start = 34.dp), color = DividerTone)
          }
        }
      }
    }
    Note(if (amapKey.isNotBlank()) "输入即搜索，也可点地图选点；请选择具体车站、入口或建筑。" else "输入即搜索；地址服务可用性依赖设备服务，请核对地点后使用。")
  }
}

/** Flat, full-width place row: one tap chooses it. */
@Composable
private fun PlaceListRow(title: String, subtitle: String, icon: ImageVector, warn: Boolean = false, onClick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClickLabel = "选定此地点", onClick = onClick)
      .heightIn(min = 56.dp)
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(icon, contentDescription = null, tint = IconTone, modifier = Modifier.size(20.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, fontSize = TypeScale.Body, fontWeight = FontWeight.Medium, lineHeight = 20.sp, color = Ink)
      Text(subtitle, fontSize = TypeScale.Caption, lineHeight = 16.sp, color = if (warn) Warm else Muted)
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
            selectedContainerColor = BrandSoft,
            selectedLabelColor = BrandStrong,
            containerColor = Color.White,
            labelColor = Ink,
          ),
          border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Line,
            selectedBorderColor = Teal,
          ),
          shape = RoundedCornerShape(10.dp),
        )
      }
    }
    if (filtered.isEmpty()) {
      EmptyCard("暂无${filter}订单", "在“出行”页面呼叫用车，订单状态会在这里实时更新。")
    }
    // One flat card per order (no card inside a card); the whole card opens the order.
    filtered.forEach { o ->
      Surface(
        onClick = { open(o.text("id")) },
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, DividerTone),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(kindName(o.text("kind")), color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(stageName(o.text("stage")), color = if (o.text("stage") == "CANCELLED") Muted else BrandStrong, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
          }
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(o.text("origin"), color = Ink, fontWeight = FontWeight.Medium, fontSize = TypeScale.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text("到 ${o.text("destination")}", color = Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Note(formatTime(o.text("createdAt")) + if (o.text("scheduledAt").isNotBlank()) " · 预约 ${formatTime(o.text("scheduledAt"))}" else "")
            }
            Text(if(o.optBoolean("destinationPending")) "待定" else "¥${money(o.optLong("fare"))}", fontWeight = FontWeight.Bold, color = Ink)
          }
        }
      }
    }
  }
}
