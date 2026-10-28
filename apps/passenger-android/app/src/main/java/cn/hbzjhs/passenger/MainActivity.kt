package cn.hbzjhs.passenger

import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

val Teal = Color(0xFF007F76)
val Ink = Color(0xFF111827)
val Muted = Color(0xFF6B7280)
val Paper = Color(0xFFF6F8FA)
val Amber = Color(0xFFF59E0B)

class MainActivity : ComponentActivity() {
  private var incomingPair by mutableStateOf<String?>(null)
  private var invalidPair by mutableStateOf(false)

  private fun receivePair(intent: Intent?) {
    if (intent?.action != Intent.ACTION_VIEW || intent.data == null) return
    runCatching { parseNegotiationCode(intent.data.toString()) }
      .onSuccess {
        incomingPair = it
        invalidPair = false
      }
      .onFailure {
        invalidPair = true
        incomingPair = null
      }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    receivePair(intent)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    receivePair(intent)
    setContent {
      PassengerVisualTheme {
        PassengerApp(
          incomingCode = incomingPair,
          invalidPair = invalidPair,
          consumed = {
            incomingPair = null
            invalidPair = false
          },
        )
      }
    }
  }
}

@Composable
fun PassengerApp(
  store: PassengerStore = viewModel(),
  incomingCode: String? = null,
  invalidPair: Boolean = false,
  consumed: () -> Unit = {},
) {
  val state by store.state.collectAsState()
  val logged by store.loggedIn.collectAsState()
  val busy by store.busy.collectAsState()
  val configured by store.configured.collectAsState()
  val error by store.error.collectAsState()
  val notice by store.notice.collectAsState()
  val connected by store.connected.collectAsState()
  val syncAt by store.syncAt.collectAsState()
  var page by rememberSaveable { mutableStateOf("home") }
  var selected by rememberSaveable { mutableStateOf("") }
  var settings by remember { mutableStateOf(false) }
  var showPermissionGuide by rememberSaveable { mutableStateOf(false) }
  PassengerPermissions(showPermissionGuide) { showPermissionGuide = false }
  PassengerTripLocationReporter(state, logged, store)
  val lifecycle = LocalLifecycleOwner.current
  LaunchedEffect(logged, incomingCode) {
    if (logged && incomingCode != null) {
      selected = ""
      page = "negotiation"
    }
  }
  LaunchedEffect(invalidPair) {
    if (invalidPair) {
      store.reportError("配对链接无效，请让司机重新提供一次配对码")
      consumed()
    }
  }
  LaunchedEffect(logged, lifecycle) {
    if (logged)
      lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        store.refresh()
        while (true) {
          delay(5000)
          store.refresh(true)
        }
      }
  }
  LaunchedEffect(logged) {
    if (!logged) {
      page = "home"
      selected = ""
    }
  }
  BackHandler(selected.isNotEmpty() || page !in listOf("home", "orders", "profile")) {
    if (selected.isNotEmpty()) selected = ""
    else page = if (page == "negotiation") "home" else "profile"
  }
  val snack = remember { SnackbarHostState() }
  LaunchedEffect(notice) {
    notice?.let {
      snack.showSnackbar(it)
      store.clearNotice()
    }
  }
  Scaffold(
    containerColor = Paper,
    snackbarHost = { SnackbarHost(snack) },
    topBar = {
      Column(Modifier.background(Color.White).statusBarsPadding()) {
        Row(
          Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (selected.isNotEmpty() || page !in listOf("home", "orders", "profile"))
            IconButton(
              onClick = {
                selected = ""
                page =
                  if (page in listOf("home", "orders", "profile")) page
                  else if (page == "negotiation") "home" else "profile"
              }
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
          Text(
            "宜捷行",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Teal,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = { showPermissionGuide = true }) { Text("权限", color = Teal) }
          if (logged) {
            Text(
              state.optJSONObject("passenger")?.text("cityName", "宜昌市").orEmpty(),
              color = Muted,
              fontSize = 14.sp,
            )
            IconButton(onClick = { store.refresh() }, enabled = !busy) {
              Icon(Icons.Default.Refresh, contentDescription = "刷新订单", tint = Teal)
            }
          } else TextButton(onClick = { settings = true }) { Text("连接设置") }
        }
        if (logged && !connected)
          Row(
            Modifier.fillMaxWidth()
              .background(Color(0xFFFFF4DC))
              .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("连接暂未更新，请刷新重试", color = Amber, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { store.refresh() }, enabled = !busy) { Text("重试连接") }
          }
        if (busy) {
          LinearProgressIndicator(Modifier.fillMaxWidth())
          Text(
            "正在更新，请稍候…",
            fontSize = 13.sp,
            color = Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
          )
        }
        error?.let {
          Row(
            Modifier.fillMaxWidth().background(Color(0xFFFFEDE8)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(it, color = Color(0xFF8F3626), fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(
              onClick = {
                store.clearError()
                if (logged) store.refresh()
              }
            ) {
              Text(if (logged) "重试" else "知道了")
            }
          }
        }
      }
    },
    bottomBar = {
      if (logged && selected.isEmpty() && page in listOf("home", "orders", "profile"))
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
          listOf(
              Triple("home", "出行", Icons.Default.Home),
              Triple("orders", "订单", Icons.AutoMirrored.Filled.List),
              Triple("profile", "我的", Icons.Default.Person),
            )
            .forEach { (key, title, icon) ->
              NavigationBarItem(
                selected = page == key,
                onClick = { page = key },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(title) },
                colors =
                  NavigationBarItemDefaults.colors(
                    indicatorColor = Color(0xFFE4F4EF),
                    selectedIconColor = Teal,
                    selectedTextColor = Teal,
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted,
                  ),
              )
            }
        }
    },
  ) { pad ->
    Box(Modifier.fillMaxSize().padding(pad).imePadding()) {
      if (!logged) LoginScreen(store, configured) { settings = true }
      else if (selected.isNotEmpty()) {
        val order = state.rows("orders").firstOrNull { it.text("id") == selected }
        if (order == null)
          PageColumn {
            EmptyCard("订单正在同步", "请刷新重试，或返回订单列表")
            ActionButton("返回订单列表") {
              selected = ""
              page = "orders"
            }
          }
        else OrderDetail(order, store, busy)
      } else
        when (page) {
          "home" ->
            BookingScreen(state, store, busy, onNegotiation = { page = "negotiation" }) {
              selected = it
              page = "orders"
            }
          "negotiation" ->
            NegotiationClaimScreen(store, incomingCode, consumed) {
              selected = it
              page = "orders"
            }
          "orders" -> OrdersScreen(state.rows("orders")) { selected = it }
          "profile" -> ProfileScreen(state, store, settings = { settings = true }) { page = it }
          "coupons", "points", "invite", "safety" -> PassengerExtrasScreen(page, state.optJSONObject("passenger")?.text("id").orEmpty())
          "favorites" -> FavoritesScreen(state, store)
          "feedback" -> FeedbackScreen(state, store)
          "complaints" -> ComplaintsScreen(state, store)
          "help" -> HelpScreen(state, store)
          else -> ExtraScreen(page, state, store)
        }
    }
  }
  PassengerAnnouncements(state, store, logged)
  if (settings) SettingsDialog(store, configured) { settings = false }
}

@Composable
fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    content = content,
  )
}

@Composable
fun Panel(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = Color.White),
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, Color(0xFFEDEFF2)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      title?.let {
        Text(
          it,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = Ink,
          lineHeight = 22.sp,
        )
      }
      content()
    }
  }
}

@Composable
fun Note(text: String) {
  Text(
    text,
    color = Muted,
    fontSize = 13.sp,
    lineHeight = 19.sp,
  )
}

@Composable
fun SectionTitle(title: String, subtitle: String = "") {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      title,
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      color = Ink,
      lineHeight = 32.sp,
    )
    if (subtitle.isNotBlank()) Note(subtitle)
  }
}

@Composable
fun ActionButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
    shape = RoundedCornerShape(14.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = Teal,
      contentColor = Color.White,
      disabledContainerColor = Color(0xFFE5E7EB),
      disabledContentColor = Color(0xFF9CA3AF),
    ),
  ) {
    Text(
      label,
      fontSize = 16.sp,
      fontWeight = FontWeight.SemiBold,
      lineHeight = 22.sp,
    )
  }
}

@Composable
fun Field(
  value: String,
  onValue: (String) -> Unit,
  label: String,
  type: KeyboardType = KeyboardType.Text,
  lines: Int = 1,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValue,
    label = { Text(label, fontSize = 14.sp) },
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = OutlinedTextFieldDefaults.colors(
      focusedBorderColor = Teal,
      unfocusedBorderColor = Color(0xFFE2E4E9),
      focusedLabelColor = Teal,
      unfocusedLabelColor = Muted,
      focusedContainerColor = Color(0xFFFBFBFC),
      unfocusedContainerColor = Color(0xFFFBFBFC),
    ),
    singleLine = lines == 1,
    minLines = lines,
    keyboardOptions = KeyboardOptions(keyboardType = type),
  )
}

@Composable
fun EmptyCard(title: String, desc: String) {
  Panel {
    Column(
      Modifier.fillMaxWidth().padding(vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = Ink,
      )
      Text(
        desc,
        color = Muted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
fun KV(label: String, value: String) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      label,
      color = Muted,
      modifier = Modifier.weight(0.38f),
      fontSize = 14.sp,
      lineHeight = 20.sp,
    )
    Text(
      value,
      color = Ink,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.weight(0.62f),
      fontSize = 14.sp,
      lineHeight = 20.sp,
    )
  }
}

@Composable
fun SettingsDialog(store: PassengerStore, configured: Boolean, close: () -> Unit) {
  val settingsError by store.error.collectAsState()
  val logged by store.loggedIn.collectAsState()
  AlertDialog(
    onDismissRequest = close,
    title = { Text("连接状态") },
    text = {
      Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Note("请选择体验账号进入；遇到连接异常时可以重试。")
        settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
        Note("已有登录状态时可重试连接；尚未登录时请返回并进入体验。")
      }
    },
    confirmButton = {
      TextButton(
        onClick = { if (logged) store.refresh() else close() },
      ) {
        Text(if (logged) "重试连接" else "返回体验", color = Teal)
      }
    },
    dismissButton = { TextButton(onClick = close) { Text("关闭") } },
  )
}

@Composable
fun LoginScreen(store: PassengerStore, configured: Boolean, settings: () -> Unit) {
  var mobile by rememberSaveable { mutableStateOf("13800000102") }
  var agreed by rememberSaveable { mutableStateOf(false) }
  var accountMenu by remember { mutableStateOf(false) }
  val busy by store.busy.collectAsState()
  val error by store.error.collectAsState()
  val passengerAccounts = listOf(
    "13800000101" to "体验乘客01 (13800000101)",
    "13800000102" to "体验乘客02 (13800000102)",
    "13800000103" to "体验乘客03 (13800000103)",
  )

  Column(
    Modifier
      .fillMaxSize()
      .background(Paper)
      .verticalScroll(rememberScrollState())
      .imePadding()
  ) {
    Image(
      painter = painterResource(id = R.drawable.yijiexing_welcome_hero),
      contentDescription = "宜捷行品牌插画",
      modifier = Modifier
        .fillMaxWidth()
        .height(240.dp),
      contentScale = ContentScale.Crop,
      alignment = Alignment.BottomCenter,
    )
    Column(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            "宜捷出行，欢迎您！",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
          )
          Note("选择目的地，查看司机报价，与司机端共享同一订单")
        }
        TextButton(
          onClick = settings,
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text("连接状态", color = Teal, fontSize = 14.sp)
        }
      }
      Panel("选择体验乘客账号") {
        Box(Modifier.fillMaxWidth()) {
          OutlinedButton(onClick = { accountMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("体验乘客${mobile.takeLast(2)}", fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text("切换账号", fontSize = 14.sp)
          }
          DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
            passengerAccounts.forEach { (phone, label) ->
              DropdownMenuItem(text = { Text(label) }, onClick = { mobile = phone; accountMenu = false }, modifier = Modifier.heightIn(min = 48.dp))
            }
          }
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = agreed, role = Role.Checkbox, onValueChange = { agreed = it }),
        ) {
          Checkbox(
            checked = agreed,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Teal),
          )
          Spacer(Modifier.width(8.dp))
          Text("我已阅读并同意下方体验说明", fontSize = 14.sp, color = Ink)
        }
        error?.let {
          Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
        ActionButton(if (busy) "正在进入…" else "进入体验", agreed && !busy) {
          store.loginExperience(mobile)
        }
      }
      Panel("体验说明与数据范围") {
        Note(
          "体验账号共用演示数据；支付、退款为模拟，不会真实扣款。请使用演示资料。"
        )
        Spacer(Modifier.height(6.dp))
        Note(
          "本应用用于独立演示环境验证。订单真实保存并同步到司机端；付款、退款和发票均为模拟，不发生真实扣款。请使用合成资料，不填写真实身份信息。未接道路导航、自动定位、短信、电话中继、推送或实时人工客服；退出登录会移除本机体验状态，云端订单记录保留。"
        )
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
fun PassengerVisualTheme(content: @Composable () -> Unit) {
  val colorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F4F2),
    onPrimaryContainer = Color(0xFF004D47),
    secondary = Ink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F3F5),
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    surfaceTint = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Muted,
    outline = Color(0xFFE5E7EB),
  )
  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography(
      bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
      bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
      bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
      titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
      titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
      labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    ),
    content = content,
  )
}

