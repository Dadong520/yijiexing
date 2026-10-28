package cn.hbzjhs.passenger

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
fun ProfileScreen(
  state: JSONObject,
  store: PassengerStore,
  settings: () -> Unit,
  navigate: (String) -> Unit,
) {
  val busy by store.busy.collectAsState()
  val p = state.optJSONObject("passenger") ?: JSONObject()
  var edit by remember { mutableStateOf(false) }
  var name by remember(p.text("name")) { mutableStateOf(p.text("name")) }
  var logout by remember { mutableStateOf(false) }
  var city by remember(p.text("city")) { mutableStateOf(p.text("city")) }
  PageColumn {
    Card(
      colors = CardDefaults.cardColors(containerColor = Color.White),
      shape = RoundedCornerShape(20.dp),
      border = BorderStroke(1.dp, Line),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Box(
        Modifier
          .fillMaxWidth()
          .background(BrandSoft)
          .padding(18.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Box(
            modifier = Modifier
              .size(56.dp)
              .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.Default.AccountCircle,
              contentDescription = null,
              tint = Teal,
              modifier = Modifier.size(52.dp),
            )
          }
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.text("name", "体验乘客"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(p.text("mobile"), color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
              Surface(
                color = Color.White,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(0.5.dp, Teal.copy(alpha = 0.4f)),
              ) {
                Text(
                  p.text("cityName", "宜昌"),
                  fontSize = 12.sp,
                  color = Teal,
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
          }
          OutlinedButton(
            onClick = { edit = true },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Teal),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Text("编辑", fontSize = 13.sp, color = Teal)
          }
        }
      }
    }
    PassengerSectionHeader("出行资料")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("常用地址" to "favorites", "乘车人" to "riders", "发票抬头" to "invoiceTitles").forEach { (label, page) ->
        val (tileTint, tileSoft) = when (page) { "favorites" -> Sky to SkySoft; "riders" -> Teal to BrandSoft; else -> Warm to WarmSoft }
        Card(onClick = { navigate(page) }, colors = CardDefaults.cardColors(containerColor = tileSoft), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, tileTint.copy(alpha = 0.18f)), modifier = Modifier.weight(1f)) {
          Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (page == "favorites") Icons.Default.Place else if (page == "riders") Icons.Default.Person else Icons.AutoMirrored.Filled.List, null, tint = tileTint)
            Text(label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
          }
        }
      }
    }
    Panel {
      MenuRow(if (PassengerOperations.enabled(state,"invoice")) "模拟发票与回执" else "历史模拟回执", Icons.AutoMirrored.Filled.List, "查看申请与记录 · 不可报销") { navigate("invoices") }
      MenuRow("安全中心与联系人", Icons.Default.Lock, "当前账号本机保存") { navigate("safety") }
    }
    ExpandablePanel("更多体验", "卡券、积分与邀请 · 未接营销账务") {
      MenuRow("我的卡券 · 演示", Icons.Default.Info, "0张 · 不参与订单抵扣") { navigate("coupons") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("积分 · 演示", Icons.Default.Info, "0分 · 未接营销账务") { navigate("points") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("邀请体验 · 演示", Icons.Default.Person, "无实际奖励或拉新绑定") { navigate("invite") }
    }
    Panel("帮助与反馈") {
      MenuRow("我的投诉", Icons.Default.Email) { navigate("complaints") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("意见反馈与记录", Icons.Default.Email) { navigate("feedback") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("已屏蔽司机", Icons.Default.Lock) { navigate("blocked") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("公告、客服与服务说明", Icons.Default.Info) { navigate("help") }
    }
    ExpandablePanel("应用与连接", "版本与功能范围") {
      MenuRow("连接状态", Icons.Default.Settings, "网络异常时重试") { settings() }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      Note("体验版 ${BuildConfig.VERSION_NAME}")
      Note("与司机端共用订单。支付、退款与发票均为模拟，不产生真实资金或凭证。地址搜索使用设备系统Geocoder；自动定位、道路导航、短信、电话中继及实时人工客服尚未接入；投诉支持后台查看和回复。")
    }
    OutlinedButton(
      onClick = { logout = true },
      modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
      shape = RoundedCornerShape(14.dp),
      colors = ButtonDefaults.outlinedButtonColors(
        contentColor = Danger,
      ),
      border = BorderStroke(1.dp, DangerLine),
    ) {
      Text("退出当前账号", fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
  }
  if (edit)
    PassengerModal(
      title = "编辑个人资料",
      onDismiss = { edit = false },
      confirmLabel = "保存",
      confirmEnabled = !busy && name.trim().length in 2..30,
      onConfirm = {
        val payload = JSONObject().put("name", name.trim())
        if (city != p.text("city")) payload.put("city", city)
        store.action("profile.update", payload)
        edit = false
      },
    ) {
      Field(name, { name = it.take(30) }, "昵称（2–30 字）")
      Text("体验城市", fontWeight = FontWeight.SemiBold, color = Ink)
      state.rows("regions").forEach { r ->
        val selected = city == r.text("id")
        FilterChip(
          selected = selected,
          onClick = { city = r.text("id") },
          enabled = r.optBoolean("enabled"),
          label = { Text(r.text("name") + if (r.optBoolean("enabled")) "" else " · 未开放") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Teal,
            selectedLabelColor = Color.White,
          ),
          shape = RoundedCornerShape(8.dp),
        )
      }
      Note("区域开通范围由云端控制；存在进行中的订单时不能切换城市。地点坐标需自行选择到对应城市。")
    }
  if (logout)
    AlertDialog(
      onDismissRequest = { logout = false },
      title = { Text("退出登录？", fontWeight = FontWeight.Bold, color = Ink) },
      text = { Text("本机登录凭证将移除，云端订单与资料仍保留。下次可选择体验账号一键进入。") },
      confirmButton = {
        TextButton(
          onClick = {
            store.logout()
            logout = false
          }
        ) {
          Text("退出", color = Danger, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { logout = false }) {
          Text("取消", color = Muted)
        }
      },
    )
}

@Composable
fun FavoritesScreen(state: JSONObject, store: PassengerStore) {
  val busy by store.busy.collectAsState()
  var edit by remember { mutableStateOf<JSONObject?>(null) }
  var remove by remember { mutableStateOf<JSONObject?>(null) }
  PageColumn {
    PassengerSectionHeader("常用地址", "＋ 添加") { edit = JSONObject() }
    Note("默认地点在选址时优先显示，仍需自行确认上车点和目的地。")
    if (state.rows("favorites").isEmpty()) EmptyCard("尚未保存常用地址", "添加家、公司或经常出行的地点，下次下单直接选择。")
    state.rows("favorites").forEach { f ->
      val isDefault = PassengerDefaults.id(state, "FAVORITE") == f.text("id")
      PassengerManagedRecord(f.text("label"), f.text("address"), isDefault, busy,
        onEdit = { edit = f }, onRemove = { remove = f },
        onDefault = { PassengerDefaults.set(store, "FAVORITE", if (isDefault) null else f.text("id")) })
    }
  }
  edit?.let { f ->
    var label by remember(f) { mutableStateOf(f.text("label", "家")) }
    var address by remember(f) { mutableStateOf(f.text("address")) }
    var lat by
      remember(f) {
        mutableStateOf(if (f.has("latitude")) f.optDouble("latitude").toString() else "")
      }
    var lng by
      remember(f) {
        mutableStateOf(if (f.has("longitude")) f.optDouble("longitude").toString() else "")
      }
    var error by remember { mutableStateOf("") }
    var isDefault by remember(f) { mutableStateOf(PassengerDefaults.id(state, "FAVORITE") == f.text("id") && f.text("id").isNotBlank()) }
    AlertDialog(
      onDismissRequest = { edit = null },
      title = { Text("保存常用地址") },
      text = {
        Column(
          Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Field(label, { label = it.take(30) }, "标签（例如家、公司）")
          Field(address, { address = it.take(100) }, "地址名称")
          Field(lat, { lat = it }, "WGS84 纬度", KeyboardType.Decimal)
          Field(lng, { lng = it }, "WGS84 经度", KeyboardType.Decimal)
          PassengerDefaultCheck(isDefault, "设为默认常用地址") { isDefault = it }
          if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
      },
      confirmButton = {
        TextButton(
          enabled = !busy,
          onClick = {
            runCatching {
                require(label.trim().isNotBlank() && address.trim().length >= 2) { "请填写标签与地址" }
                val payload =
                  JSONObject()
                    .put("isDefault", isDefault)
                    .put("label", label.trim())
                    .put("address", address.trim())
                    .put("latitude", coordinate(lat, true))
                    .put("longitude", coordinate(lng, false))
                if (f.has("id")) payload.put("id", f.text("id"))
                store.action("favorite.save", payload) { edit = null }
              }
              .onFailure { error = it.message.orEmpty() }
          },
        ) {
          Text("保存")
        }
      },
      dismissButton = { TextButton(onClick = { edit = null }) { Text("取消") } },
    )
  }
  remove?.let { f ->
    DeleteDialog("删除 ${f.text("label")}？", { remove = null }) {
      store.action("favorite.remove", JSONObject().put("id", f.text("id")))
      remove = null
    }
  }
}

@Composable
fun FeedbackScreen(state: JSONObject, store: PassengerStore) {
  var content by remember { mutableStateOf("") }
  val busy by store.busy.collectAsState()
  PageColumn {
    SectionTitle("意见反馈", "反馈保存到独立云环境，可在这里查看提交记录。")
    Panel {
      Field(content, { content = it.take(2000) }, "您的意见（5–2000 字）", lines = 4)
      Note("反馈会保存到后台，工作人员可以查看和回复；尚未配置实时人工客服，不承诺回复时限。")
      ActionButton("提交反馈", content.trim().length >= 5 && !busy) {
        store.action("feedback.submit", JSONObject().put("content", content.trim())) {
          content = ""
        }
      }
    }
    Text("提交记录", fontWeight = FontWeight.Bold)
    if (state.rows("feedback").isEmpty()) EmptyCard("暂无反馈", "提交后会生成云端记录。")
    state.rows("feedback").reversed().forEach { f ->
      Panel {
        Text(f.text("content"))
        KV("状态", supportStatus(f.text("status")))
        Note(formatTime(f.text("createdAt")))
        Note(f.text("reply").ifBlank { "暂无回复，后台回复后会同步显示在这里。" })
      }
    }
  }
}

@Composable
fun ComplaintsScreen(state: JSONObject, store: PassengerStore) {
  PageColumn {
    SectionTitle("我的投诉", "可在已完成、待支付或已取消订单中提交投诉。")
    val rows = state.rows("orders").flatMap { o -> o.rows("complaints").map { c -> o to c } }
    if (rows.isEmpty()) EmptyCard("暂无投诉记录", "进入订单详情提交问题，云端保存处理状态。")
    rows.reversed().forEach { (o, c) ->
      Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(c.text("reason", "订单投诉"), color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
          Text(supportStatus(c.text("status")), color = Teal)
        }
        Text(c.text("content").ifBlank { "未提供投诉正文" }, color = Ink)
        c.rows("attachments").forEach { ComplaintImage(it, store) }
        Text(c.text("reply").takeIf { it.isNotBlank() }?.let { "处理回复：$it" } ?: "暂无回复，后台回复后会在这里显示。", color = Muted)
        Note(formatTime(c.text("createdAt")))
        ExpandablePanel("关联行程", "${o.text("origin")} → ${o.text("destination")}") {
          Note(orderSummary(o))
          IdentifierButton(o.text("id"))
          IdentifierButton(c.text("id"), "投诉编号")
        }
      }
    }
  }
}

@Composable
fun HelpScreen(state: JSONObject, store: PassengerStore) {
  val support = state.optJSONObject("support") ?: JSONObject()
  PageColumn {
    SectionTitle("公告与服务说明", "所有体验边界均在此说明。")
    val notices = support.rows("notices") + state.rows("notices")
    notices.forEach { n ->
      Panel(n.text("title", "服务公告")) {
        Text(
          n.text("content", n.text("message"))
            .replace("投诉反馈已留存，未接人工处理", "投诉反馈已留存，支持后台查看和回复")
            .replace("未连接人工客服", "未配置实时人工客服")
        )
      }
    }
    PassengerCompanySelector(state, store)
    Panel("客服与紧急协助") {
      Note("后台工作人员可以查看和回复已保存的投诉与反馈，回复会在乘客端记录中同步展示；实时人工客服、电话中继和后台推送尚未配置。")
      Note("紧急情况请使用手机系统电话联系当地紧急服务；本应用不能代替紧急呼叫。")
    }
    Panel("第三方服务配置") {
      KV("短信", "未接真实短信；体验账号一键进入")
      KV("支付与退款", "模拟，不发生资金变动")
      KV("地图与路线", "坐标连线示意，未接真实导航")
      KV("通知", "前台轮询，未接后台推送")
      KV("发票", "模拟申请回执，不是真实税务发票")
      KV("订单数据", "乘客与司机真实共享持久化")
      KV("投诉与反馈", "支持后台查看和回复，前台同步回复")
    }
    support.rows("capabilities").forEach { c ->
      if (c.text("key") == "customerService") {
        Panel("实时客服与电话中继") {
          KV("状态", "尚未配置")
          Note("后台查看及回复投诉功能已可用；实时聊天、电话中继与后台推送尚未配置。")
        }
      } else
        Panel(c.text("label")) {
          KV(
            "状态",
            when (c.text("status")) {
              "AVAILABLE" -> "可用"
              "SIMULATED" -> "模拟体验"
              "UNCONFIGURED" -> "尚未配置"
              else -> c.text("status")
            },
          )
          Note(c.text("notice"))
        }
    }
    Panel("体验操作说明") {
      Note(
        "1. 乘客下单后，司机端上线查看订单并接单或报价。\n2. 竞价订单需要乘客选司机并模拟预付。\n3. 司机到达后核验乘车手机号末四位，再开始行程。\n4. 司机结束普通/预约行程后，乘客在订单内模拟付款。\n5. 完成后可评价、投诉或申请模拟发票。\n6. App 进入后台停止轮询；暂停云服务后，恢复服务并手动刷新。"
      )
    }
  }
}

@Composable
fun DeleteDialog(title: String, close: () -> Unit, remove: () -> Unit) {
  AlertDialog(
    onDismissRequest = close,
    title = { Text(title) },
    text = { Text("删除后此条资料不会再用于新的订单。") },
    confirmButton = { TextButton(onClick = remove) { Text("删除") } },
    dismissButton = { TextButton(onClick = close) { Text("取消") } },
  )
}
