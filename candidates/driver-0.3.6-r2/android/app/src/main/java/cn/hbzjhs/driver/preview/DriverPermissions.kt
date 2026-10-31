package cn.hbzjhs.driver.preview

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private fun Context.findActivity(): Activity? {
    var cur: Context? = this
    while (cur is ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    return null
}

@Composable
fun DriverPermissions(
    forceOpen: Boolean,
    onDismiss: () -> Unit,
    onEnableFloating: () -> Unit
): Boolean {
    val appContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(appContext) {
        appContext.getSharedPreferences("driver_permission_guide_prefs", Context.MODE_PRIVATE)
    }

    var seenGuide by remember {
        mutableStateOf(prefs.getBoolean("seen_driver_guide", false))
    }
    var askedLocation by remember {
        mutableStateOf(prefs.getBoolean("asked_location", false))
    }
    var askedNotification by remember {
        mutableStateOf(prefs.getBoolean("asked_notification", false))
    }

    var fineGranted by remember { mutableStateOf(false) }
    var coarseGranted by remember { mutableStateOf(false) }
    var locationServiceOn by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }
    var channelActive by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(false) }

    fun refreshStates() {
        fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        locationServiceOn = lm?.let {
            it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } ?: false

        notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        }

        channelActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val ch = nm?.getNotificationChannel("new-orders-v2")
            ch == null || ch.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            true
        }

        overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(appContext)
        } else {
            true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                refreshStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshStates()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        prefs.edit().putBoolean("asked_location", true).apply()
        askedLocation = true
        refreshStates()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        prefs.edit().putBoolean("asked_notification", true).apply()
        askedNotification = true
        refreshStates()
    }

    val shouldShow = forceOpen || !seenGuide
    if (!shouldShow) return false

    val primaryGreen = Color(0xFF00796B)
    val lightGreen = Color(0xFFE0F2F1)
    val textDark = Color(0xFF212121)
    val textGrey = Color(0xFF757575)
    val statusOk = Color(0xFF2E7D32)
    val statusWarn = Color(0xFFE65100)

    Dialog(
        onDismissRequest = {
            prefs.edit().putBoolean("seen_driver_guide", true).apply()
            seenGuide = true
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "司机听单与服务权限指引",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "请配置接单必要权限。听单期间保持前台服务运行；系统清理或强杀后需重新打开应用。",
                    fontSize = 14.sp,
                    color = textGrey
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. 精确定位
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("1. 精确位置与定位服务", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = textDark)
                                val locReady = fineGranted && coarseGranted && locationServiceOn
                                Text(
                                    text = if (locReady) "已就绪" else if (!locationServiceOn) "系统定位已关" else "未授权",
                                    color = if (locReady) statusOk else statusWarn,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("用途：计算与乘客距离、接驾导航、订单里程计费。", fontSize = 14.sp, color = textGrey)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val act = appContext.findActivity()
                                    val fineDeniedEver = askedLocation && act != null && !ActivityCompat.shouldShowRequestPermissionRationale(act, Manifest.permission.ACCESS_FINE_LOCATION)
                                    if (!locationServiceOn) {
                                        appContext.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } else if (fineDeniedEver) {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", appContext.packageName, null)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        appContext.startActivity(intent)
                                    } else {
                                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("授权定位 / 打开定位服务", fontSize = 14.sp, color = Color.White)
                            }
                        }
                    }

                    // 2. 消息通知与渠道
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("2. 新订单通知与提醒渠道", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = textDark)
                                val notifReady = notificationGranted && channelActive
                                Text(
                                    text = if (notifReady) "已正常" else if (!channelActive) "新单渠道被关" else "未开启",
                                    color = if (notifReady) statusOk else statusWarn,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("用途：新订单通知可直接点“立即抢单”（提示3秒后收起，仍可在订单大厅查看），并接收抢单结果、乘客取消及状态变动通知。", fontSize = 14.sp, lineHeight = 20.sp, color = textGrey)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
                                        val act = appContext.findActivity()
                                        val notifDeniedEver = askedNotification && act != null && !ActivityCompat.shouldShowRequestPermissionRationale(act, Manifest.permission.POST_NOTIFICATIONS)
                                        if (notifDeniedEver) {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", appContext.packageName, null)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            appContext.startActivity(intent)
                                        } else {
                                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                                            putExtra(Settings.EXTRA_CHANNEL_ID, "new-orders-v2")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        try {
                                            appContext.startActivity(intent)
                                        } catch (e: Exception) {
                                            val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            appContext.startActivity(fallback)
                                        }
                                    } else {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", appContext.packageName, null)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        appContext.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("设置系统通知与渠道", fontSize = 14.sp, color = Color.White)
                            }
                        }
                    }

                    // 3. 悬浮窗提醒
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("3. 悬浮窗订单卡片", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = textDark)
                                Text(
                                    text = if (overlayGranted) "已开启" else "未授权",
                                    color = if (overlayGranted) statusOk else statusWarn,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("用途：听单时切到后台或其他应用，弹出新单卡片，可直接点“立即抢单”（提示3秒后收起，仍可在订单大厅查看）；只需系统悬浮窗授权，与付费听单悬浮球无关。未授权时改用新订单通知上的“立即抢单”。", fontSize = 14.sp, lineHeight = 20.sp, color = textGrey)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        if (!Settings.canDrawOverlays(appContext)) {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${appContext.packageName}")
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            appContext.startActivity(intent)
                                        } else {
                                            onEnableFloating()
                                        }
                                    } else {
                                        onEnableFloating()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (overlayGranted) "已具备权限，开启浮窗功能" else "去系统授权悬浮窗", fontSize = 14.sp, color = Color.White)
                            }
                        }
                    }

                    // 4. 电池与后台说明（可选列表）
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("4. 电池优化与后台听单说明 (可选)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = textDark)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "听单以持续前台服务维持，建议将应用加入电池优化不限制白名单。本说明不伪造任何系统自启动，若系统强制清理仍需重新开启听单。",
                                fontSize = 14.sp,
                                color = textGrey
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        try {
                                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            appContext.startActivity(intent)
                                        } catch (e: Exception) {
                                            val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            appContext.startActivity(intent)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("查看系统电池优化设置", fontSize = 14.sp, color = primaryGreen)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            prefs.edit().putBoolean("seen_driver_guide", true).apply()
                            seenGuide = true
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("稍后设置", fontSize = 14.sp, color = textGrey)
                    }

                    Button(
                        onClick = {
                            prefs.edit().putBoolean("seen_driver_guide", true).apply()
                            seenGuide = true
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("完成指引", fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }
    }
    return true
}
