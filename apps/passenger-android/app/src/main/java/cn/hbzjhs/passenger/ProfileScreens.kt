package cn.hbzjhs.passenger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
fun ProfileScreen(
  state: JSONObject,
  store: PassengerStore,
  settings: () -> Unit,
  permissions: () -> Unit = {},
  navigate: (String) -> Unit,
) {
  val busy by store.busy.collectAsState()
  val p = state.optJSONObject("passenger") ?: JSONObject()
  var edit by remember { mutableStateOf(false) }
  var name by remember(p.text("name")) { mutableStateOf(p.text("name")) }
  var logout by remember { mutableStateOf(false) }
  var city by remember(p.text("city")) { mutableStateOf(p.text("city")) }
  val unusedCoupons = state.rows("coupons").count { it.text("status") == "UNUSED" }
  val pointsBalance = state.optJSONObject("points")?.optLong("balance") ?: 0L
  val maskedMobile = p.text("mobile").let { if (it.length >= 7) it.take(3) + "****" + it.takeLast(4) else it }
  PageColumn {
    // Rich gradient profile identity card with quick account summary.
    Surface(
      onClick = { edit = true },
      shape = RoundedCornerShape(16.dp),
      color = Teal,
      shadowElevation = 2.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        Modifier
          .fillMaxWidth()
          .background(Brush.linearGradient(listOf(Teal, BrandStrong)))
          .drawBehind {
            drawCircle(Color.White.copy(alpha = 0.08f), radius = size.height * 0.85f, center = Offset(size.width * 0.92f, 0f))
            drawCircle(Color.White.copy(alpha = 0.06f), radius = size.height * 0.45f, center = Offset(size.width * 0.72f, size.height))
          }
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            Modifier
              .size(54.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
          ) {
            Text(p.text("name", "乘").take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BrandStrong)
          }
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(p.text("name", "体验乘客"), fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
              Box(
                Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(Color.White.copy(alpha = 0.2f))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(p.text("cityName", "宜昌市"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
              }
            }
            Text(maskedMobile, color = Color.White.copy(alpha = 0.85f), fontSize = TypeScale.Caption)
          }
          Box(
            Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(Color.White.copy(alpha = 0.2f))
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text("编辑资料", color = Color.White, fontSize = TypeScale.Caption, fontWeight = FontWeight.SemiBold)
          }
        }
        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("可用卡券 $unusedCoupons 张", color = Color.White, fontSize = TypeScale.Caption, fontWeight = FontWeight.SemiBold)
            Text("·", color = Color.White.copy(alpha = 0.6f), fontSize = TypeScale.Caption)
            Text("积分 $pointsBalance 分", color = Color.White, fontSize = TypeScale.Caption, fontWeight = FontWeight.SemiBold)
          }
          CarIllustration(Modifier.size(width = 58.dp, height = 30.dp), body = Color.White)
        }
      }
    }
    // Three shortcuts in one flat strip instead of three raised tiles.
    Surface(
      color = Color.White,
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, DividerTone),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        listOf(
          Triple("常用地址", "favorites", Icons.Default.Place),
          Triple("乘车人", "riders", Icons.Default.Person),
          Triple("发票抬头", "invoiceTitles", Icons.AutoMirrored.Filled.List),
        ).forEach { (label, page, icon) ->
          Column(
            Modifier
              .weight(1f)
              .fillMaxHeight()
              .clickable(role = androidx.compose.ui.semantics.Role.Button) { navigate(page) }
              .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            val (tint, soft) = iconAccent(icon)
            Box(Modifier.size(44.dp).background(soft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
              Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Text(label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
          }
        }
      }
    }
    Panel("我的服务") {
      MenuRow(if (PassengerOperations.enabled(state,"invoice")) "模拟发票与回执" else "历史模拟回执", Icons.Default.ShoppingCart, "查看申请与记录 · 不可报销") { navigate("invoices") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("安全中心与联系人", Icons.Default.Lock, "当前账号本机保存") { navigate("safety") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("服务城市", Icons.Default.LocationOn, p.text("cityName", "宜昌市")) { edit = true }
    }
    Panel("权益与活动") {
      MenuRow("我的卡券", Icons.Default.Star, "${state.rows("coupons").count { it.text("status") == "UNUSED" }} 张可用") { navigate("coupons") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("积分商城", Icons.Default.Favorite, "${state.optJSONObject("points")?.optLong("balance") ?: 0L} 分 · 可兑换商品") { navigate("points") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("邀请好友", Icons.Default.Share, state.optJSONObject("referral")?.let { "邀请码 ${it.text("code", it.text("inviteCode"))} · ${it.optLong("awarded", it.optLong("reward"))}积分" } ?: "邀请码待同步") { navigate("invite") }
    }
    Panel("帮助与反馈") {
      MenuRow("我的投诉", Icons.Default.Warning) { navigate("complaints") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("意见反馈与记录", Icons.Default.Email) { navigate("feedback") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("已屏蔽司机", Icons.Default.Face) { navigate("blocked") }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("公告、客服与服务说明", Icons.Default.Notifications) { navigate("help") }
    }
    Panel("应用与连接") {
      MenuRow("定位权限", Icons.Default.LocationOn, "查看授权与定位状态，开启系统定位") { permissions() }
      HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
      MenuRow("连接状态", Icons.Default.Settings, "网络异常时重试") { settings() }
      Note("体验版 ${BuildConfig.VERSION_NAME} · 与司机端共用订单；支付、退款与发票均为模拟，不产生真实资金或凭证。")
      Note("定位需设备授权；地点搜索与逆地理依赖可用服务，导航通过外部地图应用打开。短信、电话中继及实时人工客服尚未接入；投诉支持后台查看和回复。")
    }
    DangerButton("退出当前账号", icon = Icons.AutoMirrored.Filled.ExitToApp) { logout = true }
  }
  if (edit)
    PassengerModal(
      title = "编辑个人资料",
      onDismiss = { edit = false },
      confirmLabel = "保存资料",
      confirmEnabled = !busy && name.trim().length in 2..30,
      onConfirm = {
        val payload = JSONObject().put("name", name.trim())
        if (city != p.text("city")) payload.put("city", city)
        store.action("profile.update", payload)
        edit = false
      },
    ) {
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(BrandMist)
          .border(1.dp, DividerTone, RoundedCornerShape(10.dp))
          .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          Modifier.size(34.dp).clip(CircleShape).background(Teal),
          contentAlignment = Alignment.Center,
        ) {
          Text(name.trim().ifBlank { p.text("name", "乘") }.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
          Text(maskedMobile.ifBlank { "体验账号" }, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
          Note("当前体验城市：${p.text("cityName", "宜昌市")}")
        }
      }
      Field(name, { name = it.take(30) }, "昵称（2–30 字）")
      HorizontalDivider(color = DividerTone)
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("选择体验城市", fontSize = TypeScale.Title, fontWeight = FontWeight.Bold, color = Ink)
        Text("云端区域配置", color = Muted, fontSize = 11.sp)
      }
      val regionRows = state.rows("regions").chunked(2)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        regionRows.forEach { pair ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { r ->
              val isOpen = r.optBoolean("enabled")
              val selected = city == r.text("id")
              Surface(
                onClick = { if (isOpen) city = r.text("id") },
                enabled = isOpen,
                shape = RoundedCornerShape(10.dp),
                color = if (!isOpen) SurfaceMuted else if (selected) BrandSoft else Color.White,
                border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (!isOpen) DividerTone else if (selected) Teal else Line),
                modifier = Modifier.weight(1f),
              ) {
                Row(
                  Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = if (!isOpen) DisabledText else if (selected) Teal else IconTone,
                    modifier = Modifier.size(16.dp),
                  )
                  Column(Modifier.weight(1f)) {
                    Text(
                      r.text("name"),
                      fontSize = 14.sp,
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                      color = if (!isOpen) DisabledText else if (selected) BrandStrong else Ink,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      if (isOpen) "可体验" else "未开放",
                      fontSize = 11.sp,
                      color = if (!isOpen) DisabledText else if (selected) Teal else Muted,
                    )
                  }
                  if (isOpen) DefaultRadioDot(selected = selected, onClick = null)
                }
              }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
          }
        }
      }
      Text(
        "进行中订单结束后方可切换城市；选择地点时需对应所选城市。",
        color = Muted,
        fontSize = TypeScale.Caption,
        lineHeight = 17.sp,
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(SurfaceMuted)
          .padding(horizontal = 10.dp, vertical = 8.dp),
      )
    }
  PassengerModalSlot(logout)
  if (logout)
    PassengerDialog(
      onDismissRequest = { logout = false },
      title = { Text("退出登录？", fontWeight = FontWeight.Bold, color = Ink) },
      text = { Text("本机登录凭证将移除，云端订单与资料仍保留。下次可选择体验账号一键进入。") },
      confirmButton = {
        PassengerTextButton(
          onClick = {
            store.logout()
            logout = false
          }
        ) {
          Text("退出", color = Danger, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        PassengerTextButton(onClick = { logout = false }) {
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
    var picking by remember(f) { mutableStateOf(false) }
    // Armed when the picker closes (chosen or back): a fast second tap must not save, toggle the
    // default, cancel or dismiss this editor (which would drop the place just picked).
    val pickShield = rememberReturnTapShield()
    // Held across the editor and its address picker so app-wide popups keep waiting.
    PassengerModalSlot()
    if (picking)
      // One tap fills the address and returns here; the favorite is still only saved by 保存 below.
      AddressDialog(address, lat, lng, emptyList(), { pickShield.arm(); picking = false }, city = state.optJSONObject("passenger")?.text("cityName").orEmpty(), title = "选择常用地点") { n, a, b, _ ->
        pickShield.arm(); address = n.take(100); lat = a; lng = b; error = ""; picking = false
      }
    else PassengerDialog(
      onDismissRequest = { if (pickShield.allows()) edit = null },
      title = { Text(if (f.has("id")) "编辑常用地址" else "添加常用地址") },
      text = {
        Column(
          Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            listOf("家", "公司", "学校").forEach { preset ->
              PassengerChoiceChip(
                label = preset,
                selected = label.trim() == preset,
                onClick = { label = preset },
              )
            }
          }
          Field(label, { label = it.take(30) }, "标签（例如家、公司）")
          Surface(
            onClick = { picking = true },
            shape = RoundedCornerShape(10.dp),
            color = Paper,
            border = BorderStroke(1.dp, if (address.isNotBlank()) Teal.copy(alpha = 0.4f) else ControlOutline),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              RouteBadgeIcon(RoutePinKind.ORIGIN)
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("地点位置", color = Muted, fontSize = TypeScale.Caption)
                Text(
                  address.ifBlank { "点击在地图上点选或搜索地点" },
                  color = if (address.isBlank()) Muted else Ink,
                  fontSize = TypeScale.Body,
                  fontWeight = if (address.isBlank()) FontWeight.Normal else FontWeight.Medium,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Chevron, modifier = Modifier.size(18.dp))
            }
          }
          PassengerDefaultCheck(isDefault, "设为默认常用地址") { if (pickShield.allows()) isDefault = it }
          if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
      },
      confirmButton = {
        PassengerTextButton(
          enabled = !busy && !pickShield.active,
          onClick = {
            // Tap-time clock check, not only the enabled look.
            if (!pickShield.allows()) return@PassengerTextButton
            runCatching {
                require(label.trim().isNotBlank() && address.trim().length >= 2 && lat.isNotBlank()) { "请填写标签并选择地点" }
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
      dismissButton = { PassengerTextButton(enabled = !pickShield.active, onClick = { if (pickShield.allows()) edit = null }) { Text("取消") } },
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
    if (state.rows("feedback").isEmpty()) {
      EmptyCard("暂无反馈", "提交后会生成云端记录。")
      EmptyHint("在上方输入至少 5 个字的意见并提交，记录与回复会显示在这里。")
    }
    state.rows("feedback").reversed().forEach { f ->
      Panel {
        Text(f.text("content"))
        RecordRow("状态", supportStatus(f.text("status")))
        RecordRow("提交时间", formatTime(f.text("createdAt")))
        Note(f.text("reply").ifBlank { "暂无回复，后台回复后会同步显示在这里。" })
      }
    }
  }
}

@Composable
fun ComplaintsScreen(state: JSONObject, store: PassengerStore) {
  val busy by store.busy.collectAsState()
  var targetOrderId by remember { mutableStateOf("") }
  var targetComplaintId by remember { mutableStateOf("") }
  val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null && targetOrderId.isNotBlank() && targetComplaintId.isNotBlank()) {
      store.uploadComplaint(targetOrderId, targetComplaintId, uri)
    }
  }
  val rows = state.rows("orders").flatMap { o -> o.rows("complaints").map { c -> o to c } }
  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item { SectionTitle("我的投诉", "可在已完成、待支付或已取消订单中提交投诉。") }
    if (rows.isEmpty()) {
      item { EmptyCard("暂无投诉记录", "进入订单详情提交问题，云端保存处理状态。") }
      item { EmptyHint("打开“订单”中已完成、待支付或已取消的订单，点按“投诉与问题反馈”。") }
    }
    items(rows.asReversed(), key = { (o, c) -> o.text("id") + ":" + c.text("id") }) { (o, c) ->
      val attachments = c.rows("attachments")
      val canAttach = attachments.size < 3 && o.text("stage") in listOf("DONE", "CANCELLED", "WAIT_PAY")
      Panel {
        Row(
          Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            c.text("reason", "订单投诉"),
            color = Ink,
            fontSize = TypeScale.Title,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          )
          ComplaintStatusBadge(c.text("status"))
        }
        Text(c.text("content").ifBlank { "未提供投诉正文" }, color = Ink, fontSize = TypeScale.Body)
        ComplaintAttachmentStrip(attachments, store)
        if (attachments.isEmpty()) Note("暂未上传投诉照片${if (canAttach) "，可点击下方按钮从相册补充。" else "。"}")
        if (canAttach) {
          SecondaryButton("从相册补充投诉照片 (${attachments.size}/3)", !busy, icon = Icons.Default.Add) {
            targetOrderId = o.text("id")
            targetComplaintId = c.text("id")
            imagePicker.launch("image/*")
          }
        }
        Text(c.text("reply").takeIf { it.isNotBlank() }?.let { "处理回复：$it" } ?: "暂无回复，后台回复后会在这里显示。", color = Muted, fontSize = 13.sp)
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
  PassengerModalSlot()
  PassengerDialog(
    onDismissRequest = close,
    title = { Text(title) },
    text = { Text("删除后此条资料不会再用于新的订单。") },
    confirmButton = { PassengerTextButton(onClick = remove) { Text("删除") } },
    dismissButton = { PassengerTextButton(onClick = close) { Text("取消") } },
  )
}
