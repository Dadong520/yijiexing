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
  var kind by rememberSaveable { mutableStateOf("STANDARD") }
  var origin by rememberSaveable { mutableStateOf("宜昌东站 · 出租车上客区") }
  var destination by rememberSaveable { mutableStateOf("夷陵广场 · 东侧入口") }
  var oLat by rememberSaveable { mutableStateOf("30.6648") }
  var oLng by rememberSaveable { mutableStateOf("111.3674") }
  var dLat by rememberSaveable { mutableStateOf("30.7040") }
  var dLng by rememberSaveable { mutableStateOf("111.2929") }
  var oMetadata by rememberSaveable { mutableStateOf("") }
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
  var submitting by remember { mutableStateOf(false) }
  var contactId by rememberSaveable { mutableStateOf("") }
  var destinationPending by rememberSaveable { mutableStateOf(false) }
  val context = LocalContext.current
  val reservations = state.rows("orders").filter { it.text("kind") == "RESERVED" && it.text("stage") in listOf("AVAILABLE", "RESERVED") }
  val active = state.rows("orders").firstOrNull {
    it.text("stage") !in listOf("DONE", "CANCELLED") && it !in reservations
  }
  val submitOrder: () -> Unit = {
    runCatching {
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
        if (destinationPending && kind == "STANDARD") {
          payload.put("destinationPending", true)
          payload.remove("destination")
          payload.remove("destinationLat")
          payload.remove("destinationLng")
          payload.remove("destinationCoordinateMetadata")
        }
        if (contactId.isNotBlank()) payload.put("riderId", contactId)
        if (showWaypoints && !(destinationPending && kind == "STANDARD")) {
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
      destinationLat = if (destinationPending && kind == "STANDARD") "" else dLat,
      destinationLng = if (destinationPending && kind == "STANDARD") "" else dLng,
      modifier = Modifier
        .fillMaxWidth()
        .weight(0.43f)
        .heightIn(min = 200.dp),
    )
    Surface(
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
      color = Color.White,
      shadowElevation = 8.dp,
      border = BorderStroke(1.dp, Color(0xFFEDEFF2)),
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
            OutlinedButton(onClick = { openOrder(reservation.text("id")) }, modifier = Modifier.fillMaxWidth()) {
              Text("待出发预约 · ${formatTime(reservation.text("scheduledAt"))} · 查看")
            }
          }
          if (active != null) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFFF0FDF4),
              border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
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
            ).forEach { (mode, label, icon) ->
              val selected = kind == mode
              Surface(
                onClick = { kind = mode },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) Teal else Color(0xFFF6F8FA),
                border = BorderStroke(1.dp, if (selected) Teal else Color(0xFFEDEFF2)),
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFEDEFF2)),
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
                    val address = origin
                    origin = destination
                    destination = address
                    val lat = oLat
                    oLat = dLat
                    dLat = lat
                    val lng = oLng
                    oLng = dLng
                    dLng = lng
                  },
                ) {
                  Text("交换起终点", fontSize = 12.sp, color = Teal)
                }
              }
              AddressRow("从这里出发", origin) { editor = "origin" }
              HorizontalDivider(color = Color(0xFFEAECEF), thickness = 0.8.dp)
              AddressRow(
                "目的地",
                if (destinationPending && kind == "STANDARD") "稍后补充目的地" else destination,
                emphasize = true,
              ) {
                editor = "destination"
              }
            }
          }
          if (kind == "STANDARD") {
            Row(
              Modifier
                .fillMaxWidth()
                .toggleable(
                  value = destinationPending,
                  role = Role.Checkbox,
                  onValueChange = { destinationPending = it },
                )
                .heightIn(min = 40.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                destinationPending,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = Teal),
              )
              Spacer(Modifier.width(8.dp))
              Text("先叫车，稍后补充目的地", fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
            }
          }
          Note(
            if (destinationPending && kind == "STANDARD") "补充目的地后生成体验估价，费用待定。"
            else if (kind == "BID") "下单后等待司机报价，选定司机后模拟预付。"
            else "下单后生成体验估价，行程结束后模拟支付。"
          )
          ExpandablePanel("更多行程设置与代叫", "乘车人、用车备注、预约时间、途经点", initialExpanded = kind == "RESERVED") {
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
                    label = { Text("${c.text("name")} · ${c.text("mobile").takeLast(4)}", fontSize = 13.sp) },
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
              enabled = !(destinationPending && kind == "STANDARD"),
            ) {
              Text(if (showWaypoints) "收起途经点" else "添加途经点（可选）", color = Teal, fontSize = 13.sp)
            }
            if (showWaypoints && !(destinationPending && kind == "STANDARD")) {
              Field(waypointName, { waypointName = it }, "途经点名称")
              Field(waypointLat, { waypointLat = it }, "途经点纬度", KeyboardType.Decimal)
              Field(waypointLng, { waypointLng = it }, "途经点经度", KeyboardType.Decimal)
              HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 0.8.dp)
              Text("第二途经点（可选）", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
              Field(waypointName2, { waypointName2 = it }, "第二途经点名称")
              Field(waypointLat2, { waypointLat2 = it }, "第二途经点纬度", KeyboardType.Decimal)
              Field(waypointLng2, { waypointLng2 = it }, "第二途经点经度", KeyboardType.Decimal)
              Note("最多 2 个途经点，按顺序经过。司机到达后确认，乘客可在订单查看进度。")
            }
            HorizontalDivider(color = Color(0xFFF1F3F5), thickness = 0.8.dp)
            MenuRow("线下议价", Icons.Default.Info, "与现场司机配对，核对报价") { onNegotiation() }
            state
              .rows("orders")
              .firstOrNull { it.text("stage") in listOf("DONE", "CANCELLED") }
              ?.let { recent ->
                Card(
                  colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, Color(0xFFEDEFF2)),
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
                        origin = recent.text("origin")
                        destination = recent.text("destination")
                        oLat = recent.optDouble("originLat").toString()
                        oLng = recent.optDouble("originLng").toString()
                        dLat = recent.optDouble("destinationLat").toString()
                        dLng = recent.optDouble("destinationLng").toString()
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
              Note("可使用设备系统搜索、常用地点或手动WGS84坐标；系统搜索可用性取决于设备，未提供自动定位或道路导航。")
            }
          }
          Spacer(Modifier.height(4.dp))
        }
        Surface(
          color = Color.White,
          shadowElevation = 8.dp,
          border = BorderStroke(1.dp, Color(0xFFF1F3F5)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            ActionButton(
              if (active != null) "查看当前行程" else "确认呼叫 · ${kindName(kind)}",
              !busy && !submitting,
            ) {
              if (active != null) openOrder(active.text("id")) else submitOrder()
            }
          }
        }
      }
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
  fun cancelSearch() { searchJob?.cancel(); searching=false; results=emptyList() }
  fun selectPlace(n: String, a: String, b: String, from: String, metadata: JSONObject? = null) {
    cancelSearch(); name=n; lat=a; lng=b; source=from; selectedMetadata=metadata; error=""
  }
  val places =
    (listOf(
        Triple("宜昌东站 · 出租车上客区", "30.6648", "111.3674"),
        Triple("夷陵广场 · 东侧入口", "30.7040", "111.2929"),
        Triple("三峡游客中心", "30.6778", "111.2944"),
      ) + favorites.map {
        Triple(it.text("address"), it.optDouble("latitude").toString(), it.optDouble("longitude").toString())
      }).distinctBy { (n, a, b) -> Triple(n, a.toDoubleOrNull(), b.toDoubleOrNull()) }
  PassengerModal(
    title = "选择地点",
    onDismiss = close,
    confirmLabel = "使用此地点",
    confirmEnabled = !searching,
    onConfirm = {
      runCatching {
        require(name.trim().length >= 2) { "请填写地址名称" }
        require(lat.isNotBlank() && lng.isNotBlank()) { "地址文字已修改，请先选择搜索结果、常用地点或填写匹配的坐标" }
        coordinate(lat, true); coordinate(lng, false)
        save(name.trim(), lat.trim(), lng.trim(), selectedMetadata)
      }.onFailure { error=it.message.orEmpty() }
    },
  ) {
    Field(name, { value ->
      if (value != name) {
        cancelSearch(); name=value.take(200); lat=""; lng=""; source=""; selectedMetadata=null
        error="地址已修改，原坐标已清除；请搜索并选中地点，或填写匹配坐标。"
      }
    }, "地点或详细地址")
    if (amapKey.isNotBlank()) {
      Field(searchCity, { cancelSearch(); searchCity=it.take(40) }, "搜索城市（可选）")
      val selected = if (lat.toDoubleOrNull()?.isFinite() == true && lng.toDoubleOrNull()?.isFinite() == true)
        listOf(AmapPlacePoint(lat.toDouble(), lng.toDouble(), name)) else emptyList()
      AmapCanvas(selected, Modifier.fillMaxWidth().height(240.dp), onPick = { a, b ->
        cancelSearch(); lat=""; lng=""; selectedMetadata=null; source=""; searching=true; error=""
        searchJob=scope.launch {
          try {
            val result=reverseAmapAddress(context,a,b)
            selectPlace(result.name,result.latitude.toString(),result.longitude.toString(),"地图选定地点",result.metadata)
          } catch (e: CancellationException) { throw e }
          catch (e: Exception) { error=e.message ?: "地图地点解析失败，请重试" }
          finally { if (isActive) searching=false }
        }
      }, notice = "点击地图选点，再核对地址")
    }
    ActionButton(if (searching) "正在查询…" else if (amapKey.isNotBlank()) "搜索高德地点" else "搜索地址", !searching && name.trim().length >= 2) {
      val query=name.trim()
      error=""; results=emptyList(); searching=true
      searchJob=scope.launch {
        try {
          val found=searchSystemAddresses(context, query, searchCity)
          if (name.trim()==query) {
            results=found
            if (found.isEmpty()) error="未找到地址，请补充城市和详细名称，或选择常用地点/手动坐标。"
          }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
          if (name.trim()==query) error=e.message ?: "地址搜索暂不可用，请重试或在地图选点。"
        } finally { if (isActive) searching=false }
      }
    }
    if (searching) {
      LinearProgressIndicator(Modifier.fillMaxWidth())
      TextButton(onClick={cancelSearch()}) { Text("取消搜索，改用常用或手动地点") }
    }
    if (error.isNotBlank()) Text(error, color=MaterialTheme.colorScheme.error)
    results.forEach { result ->
      MenuRow(result.name, Icons.Default.LocationOn, "选择并在地图核对") {
        selectPlace(result.name, result.latitude.toString(), result.longitude.toString(), "搜索选定地点", result.metadata)
      }
    }
    if (source.isNotBlank()) Note("$source · 请核对名称与地图位置")
    Note(if (amapKey.isNotBlank()) "使用高德地点搜索与地图选点；请选择具体车站、入口或建筑。" else "地址服务可用性依赖设备服务，请核对地点后使用。")
    Text("常用与体验地点", fontWeight=FontWeight.SemiBold)
    places.forEach { (n, a, b) ->
      MenuRow(n, Icons.Default.LocationOn) {
        val metadata=favorites.firstOrNull { it.text("address")==n && it.optDouble("latitude")==a.toDoubleOrNull() && it.optDouble("longitude")==b.toDoubleOrNull() }?.optJSONObject("coordinateMetadata")
        selectPlace(n,a,b,"常用或体验地点",metadata)
      }
      HorizontalDivider()
    }
    TextButton(onClick={advanced=!advanced}) { Text(if (advanced) "收起手动坐标" else "手动坐标 · 高级设置") }
    if (advanced) {
      Note("请核对名称和WGS84坐标一致；手动填写不会查询地点。")
      Field(lat, {lat=it; source="手动坐标"; selectedMetadata=null}, "WGS84 纬度", KeyboardType.Decimal)
      Field(lng, {lng=it; source="手动坐标"; selectedMetadata=null}, "WGS84 经度", KeyboardType.Decimal)
    }
  }
}

@Composable
fun Modifier.verticalScrollCompat(): Modifier =
  this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrdersScreen(orders: List<JSONObject>, open: (String) -> Unit) {
  var filter by rememberSaveable { mutableStateOf("进行中") }
  val filtered =
    orders.filter {
      when (filter) {
        "进行中" -> it.text("stage") !in listOf("DONE", "CANCELLED")
        "已完成" -> it.text("stage") == "DONE"
        "已取消" -> it.text("stage") == "CANCELLED"
        else -> true
      }
    }
  PageColumn {
    SectionTitle("我的订单", "出行与预约记录")
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      listOf("进行中", "已完成", "已取消", "全部").forEach { f ->
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
            borderColor = if (selected) Teal else Color(0xFFE5E7EB),
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
      val stage = o.text("stage")
      val isCancelled = stage == "CANCELLED"
      val isDone = stage == "DONE"
      val statusColor = when {
        isCancelled -> Muted
        isDone -> Ink
        else -> Teal
      }
      Card(
        onClick = { open(o.text("id")) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFEDEFF2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              color = Color(0xFFF3F4F6),
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                kindName(o.text("kind")),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = Ink,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
            Text(
              stageName(stage),
              color = statusColor,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(Modifier.size(8.dp).background(Teal, CircleShape))
              Text(
                o.text("origin"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                lineHeight = 22.sp,
              )
            }
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(Modifier.size(8.dp).background(Amber, CircleShape))
              Text(
                o.text("destination"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                lineHeight = 22.sp,
              )
            }
          }
          HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 0.8.dp)
          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("体验费用", color = Muted, fontSize = 13.sp)
            Text(
              if (o.optBoolean("destinationPending")) "目的地待补充 · 费用待定"
              else "¥${money(o.optLong("fare"))}",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = Ink,
            )
          }
          if (o.text("scheduledAt").isNotBlank()) {
            Row(
              Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("计划用车时间", color = Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
              Text(formatTime(o.text("scheduledAt")), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
          }
          orderPlacedTime(o.text("createdAt"))?.let { Note("创建时间：$it") }
          IdentifierButton(o.text("id"))
          if (o.rows("bids").isNotEmpty() && stage == "AVAILABLE") {
            Text(
              "${o.rows("bids").size} 位司机已报价 · 点击选择",
              color = Teal,
              fontWeight = FontWeight.Medium,
              fontSize = 13.sp,
            )
          }
        }
      }
    }
  }
}

