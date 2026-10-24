package cn.hbzjhs.passenger

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
  val active = state.rows("orders").firstOrNull { it.text("stage") !in listOf("DONE", "CANCELLED") }
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
  Scaffold(
    containerColor = Paper,
    bottomBar = {
      Surface(color = Color.White, shadowElevation = 6.dp) {
        Column(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ActionButton(
            if (active != null) "查看当前行程" else "确认呼叫 · ${kindName(kind)}",
            !busy && !submitting,
          ) {
            if (active != null) openOrder(active.text("id")) else submitOrder()
          }
        }
      }
    },
  ) { insets ->
    Column(
      Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      SectionTitle("您要去哪里？")
      if (active != null)
        Panel {
          Text("您有一个进行中的订单", fontWeight = FontWeight.SemiBold)
          Text(stageName(active.text("stage")), color = Teal)
          Text("${active.text("origin")} → ${active.text("destination")}")
        }
      Panel {
        AddressRow("从这里出发", origin) { editor = "origin" }
        HorizontalDivider()
        AddressRow(
          "目的地",
          if (destinationPending && kind == "STANDARD") "稍后补充目的地" else destination,
          emphasize = true,
        ) {
          editor = "destination"
        }
        TextButton(
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
          }
        ) {
          Text("交换起终点")
        }
        Note("地点来自体验地址或手动坐标，未接入自动定位和地图搜索。")
      }
      Text("选择用车方式", fontWeight = FontWeight.SemiBold)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        listOf("STANDARD", "RESERVED", "BID").forEach { mode ->
          FilterChip(kind == mode, { kind = mode }, label = { Text(kindName(mode)) })
        }
      }
      Note(
        if (destinationPending && kind == "STANDARD") "补充目的地后生成体验估价，费用待定。"
        else if (kind == "BID") "下单后等待司机报价，选定司机后模拟预付。" else "下单后生成体验估价，行程结束后模拟支付。"
      )
      if (kind == "STANDARD")
        Row(
          Modifier.fillMaxWidth()
            .toggleable(
              value = destinationPending,
              role = Role.Checkbox,
              onValueChange = { destinationPending = it },
            )
            .heightIn(min = 48.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(destinationPending, onCheckedChange = null)
          Spacer(Modifier.width(10.dp))
          Text("先叫车，稍后补充目的地", fontSize = 15.sp, modifier = Modifier.weight(1f))
        }
      ExpandablePanel("更多行程设置", "乘车人、用车备注、途经点", initialExpanded = kind == "RESERVED") {
        if (kind == "RESERVED") {
          Text("预约时间：${date.replace('T',' ')}")
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
            }
          ) {
            Text("选择预约日期与时间")
          }
          Note("预约范围：15 分钟后至未来 30 天；设备当地时间。")
        }
        val contacts = state.rows("riders")
        if (contacts.isNotEmpty()) {
          Text("乘车人")
          FilterChip(
            contactId.isBlank(),
            { contactId = "" },
            label = {
              Text("本人 · 手机尾号 ${state.optJSONObject("passenger")?.text("mobile")?.takeLast(4)}")
            },
          )
          contacts.forEach { c ->
            FilterChip(
              contactId == c.text("id"),
              { contactId = c.text("id") },
              label = { Text("${c.text("name")} · ${c.text("mobile").takeLast(4)}") },
            )
          }
        }
        Field(remark, { remark = it.take(500) }, "用车备注（可选）", lines = 2)
        TextButton(
          onClick = { showWaypoints = !showWaypoints },
          enabled = !(destinationPending && kind == "STANDARD"),
        ) {
          Text(if (showWaypoints) "收起途经点" else "添加途经点（可选）")
        }
        if (showWaypoints && !(destinationPending && kind == "STANDARD")) {
          Field(waypointName, { waypointName = it }, "途经点名称")
          Field(waypointLat, { waypointLat = it }, "途经点纬度", KeyboardType.Decimal)
          Field(waypointLng, { waypointLng = it }, "途经点经度", KeyboardType.Decimal)
          HorizontalDivider()
          Text("第二途经点（可选）")
          Field(waypointName2, { waypointName2 = it }, "第二途经点名称")
          Field(waypointLat2, { waypointLat2 = it }, "第二途经点纬度", KeyboardType.Decimal)
          Field(waypointLng2, { waypointLng2 = it }, "第二途经点经度", KeyboardType.Decimal)
          Note("最多 2 个途经点，按顺序经过。司机到达后确认，乘客可在订单查看进度。")
        }
      }
      Panel { MenuRow("线下议价", Icons.Default.Info, "与现场司机配对，核对报价") { onNegotiation() } }
      state
        .rows("orders")
        .firstOrNull { it.text("stage") in listOf("DONE", "CANCELLED") }
        ?.let { recent ->
          Panel("常用行程") {
            Text("${recent.text("origin")} → ${recent.text("destination")}")
            TextButton(
              onClick = {
                origin = recent.text("origin")
                destination = recent.text("destination")
                oLat = recent.optDouble("originLat").toString()
                oLng = recent.optDouble("originLng").toString()
                dLat = recent.optDouble("destinationLat").toString()
                dLng = recent.optDouble("destinationLng").toString()
              }
            ) {
              Text("再叫一单 · 填入此路线")
            }
          }
        }
      Spacer(Modifier.height(8.dp))
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
    ) { name, lat, lng ->
      if (editor == "origin") {
        origin = name
        oLat = lat
        oLng = lng
      } else {
        destination = name
        dLat = lat
        dLng = lng
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
  save: (String, String, String) -> Unit,
) {
  var name by remember { mutableStateOf(initial) }
  var lat by remember { mutableStateOf(initialLat) }
  var lng by remember { mutableStateOf(initialLng) }
  var error by remember { mutableStateOf("") }
  var advanced by remember { mutableStateOf(false) }
  val places =
    (listOf(
        Triple("宜昌东站 · 出租车上客区", "30.6648", "111.3674"),
        Triple("夷陵广场 · 东侧入口", "30.7040", "111.2929"),
        Triple("三峡游客中心", "30.6778", "111.2944"),
      ) +
        favorites.map {
          Triple(
            it.text("address"),
            it.optDouble("latitude").toString(),
            it.optDouble("longitude").toString(),
          )
        })
      .distinctBy { (n, a, b) -> Triple(n, a.toDoubleOrNull(), b.toDoubleOrNull()) }
  PassengerModal(
    title = "选择地点",
    onDismiss = close,
    confirmLabel = "使用此地点",
    onConfirm = {
      runCatching {
          require(name.trim().length >= 2) { "请填写地址名称" }
          coordinate(lat, true)
          coordinate(lng, false)
          save(name.trim(), lat.trim(), lng.trim())
        }
        .onFailure {
          error = it.message.orEmpty()
          advanced = true
        }
    },
  ) {
    Field(name, { name = it }, "地址名称")
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    Note("可选体验地点或手动坐标；地址名称不会自动换算坐标。")
    Text("常用与体验地点", fontWeight = FontWeight.SemiBold)
    places.forEach { (n, a, b) ->
      MenuRow(n, Icons.Default.LocationOn) {
        name = n
        lat = a
        lng = b
        error = ""
      }
      HorizontalDivider()
    }
    TextButton(onClick = { advanced = !advanced }) {
      Text(if (advanced) "收起手动坐标" else "手动坐标 · 高级设置")
    }
    if (advanced) {
      Note("未提供自动定位或道路导航。请核对地点与 WGS84 坐标一致。")
      Field(lat, { lat = it }, "WGS84 纬度", KeyboardType.Decimal)
      Field(lng, { lng = it }, "WGS84 经度", KeyboardType.Decimal)
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
    SectionTitle("我的订单", "您的出行记录")
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      listOf("进行中", "已完成", "已取消", "全部").forEach { f ->
        FilterChip(filter == f, { filter = f }, label = { Text(f) })
      }
    }
    if (filtered.isEmpty()) EmptyCard("暂无${filter}订单", "在“出行”页面呼叫用车，订单会在这里持续更新。")
    filtered.forEach { o ->
      Card(
        onClick = { open(o.text("id")) },
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(kindName(o.text("kind")), fontWeight = FontWeight.Bold)
            Text(
              stageName(o.text("stage")),
              color = if (o.text("stage") == "CANCELLED") Muted else Teal,
            )
          }
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Default.LocationOn,
              contentDescription = null,
              tint = Muted,
              modifier = Modifier.size(18.dp),
            )
            Text(o.text("origin"))
          }
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Default.LocationOn,
              contentDescription = null,
              tint = Teal,
              modifier = Modifier.size(18.dp),
            )
            Text(o.text("destination"))
          }
          KV(
            "体验费用",
            if (o.optBoolean("destinationPending")) "目的地待补充 · 费用待定"
            else "¥${money(o.optLong("fare"))}",
          )
          orderPlacedTime(o.text("createdAt"))?.let { Note("下单时间：$it") }
          IdentifierButton(o.text("id"))
          if (o.rows("bids").isNotEmpty() && o.text("stage") == "AVAILABLE")
            Text("${o.rows("bids").size} 位司机已报价 · 点击选择", color = Teal)
        }
      }
    }
  }
}
