package cn.hbzjhs.passenger

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PassengerPosition(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val time: Long
)

object PassengerPositionBus {
    internal val _position = MutableStateFlow<PassengerPosition?>(null)
    val position: StateFlow<PassengerPosition?> = _position.asStateFlow()

    internal val _status = MutableStateFlow<String>("未启动")
    val status: StateFlow<String> = _status.asStateFlow()

    fun updatePosition(pos: PassengerPosition?) {
        _position.value = pos
    }

    fun updateStatus(text: String) {
        _status.value = text
    }
}

private fun Context.findActivity(): Activity? {
    var cur: Context? = this
    while (cur is ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    return null
}

@Composable
fun PassengerPermissions(
    forceOpen: Boolean,
    onDismiss: () -> Unit
) {
    val appContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(appContext) {
        appContext.getSharedPreferences("passenger_permission_guide_prefs", Context.MODE_PRIVATE)
    }

    var seenGuide by remember {
        mutableStateOf(prefs.getBoolean("seen_passenger_guide", false))
    }
    var askedLocation by remember {
        mutableStateOf(prefs.getBoolean("asked_location", false))
    }

    var hasFine by remember { mutableStateOf(false) }
    var hasCoarse by remember { mutableStateOf(false) }
    var locationServiceOn by remember { mutableStateOf(false) }

    fun checkPermissions() {
        hasFine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        hasCoarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        locationServiceOn = lm?.let {
            it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } ?: false
    }

    DisposableEffect(lifecycleOwner, hasFine, hasCoarse, locationServiceOn) {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                val now = System.currentTimeMillis()
                // 过滤不新鲜或无精度位置 (120秒内有效，精度>0)
                if (loc.latitude.isFinite() && loc.longitude.isFinite() && loc.latitude in -90.0..90.0 && loc.longitude in -180.0..180.0 && loc.accuracy.isFinite() && loc.accuracy > 0 && now - loc.time in -30_000L..120_000L) {
                    PassengerPositionBus.updatePosition(
                        PassengerPosition(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracy = loc.accuracy,
                            time = loc.time
                        )
                    )
                    PassengerPositionBus.updateStatus("定位正常 (精度: ${loc.accuracy.toInt()}m)")
                } else {
                    PassengerPositionBus.updateStatus("已获取定位但数据过旧，等待最新信号")
                }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {
                PassengerPositionBus.updateStatus("定位服务已开启")
            }
            override fun onProviderDisabled(provider: String) {
                PassengerPositionBus.updateStatus("系统定位服务已关闭")
            }
        }

        var isListening = false

        fun startLocationUpdates() {
            if (lm == null || isListening) return
            val hasPermission = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                PassengerPositionBus.updateStatus("无定位权限")
                return
            }

            val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val netEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (!gpsEnabled && !netEnabled) {
                PassengerPositionBus.updateStatus("系统定位已关闭")
                return
            }

            // 检查最近的有效位置，若新鲜(120s内)可暂用
            val now = System.currentTimeMillis()
            val lastGps = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: SecurityException) { null }
            val lastNet = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: SecurityException) { null }
            val bestLast = when {
                lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                else -> lastNet
            }
            if (bestLast != null && bestLast.accuracy > 0 && Math.abs(now - bestLast.time) <= 120_000L) {
                PassengerPositionBus.updatePosition(
                    PassengerPosition(
                        latitude = bestLast.latitude,
                        longitude = bestLast.longitude,
                        accuracy = bestLast.accuracy,
                        time = bestLast.time
                    )
                )
                PassengerPositionBus.updateStatus("使用近期位置 (精度: ${bestLast.accuracy.toInt()}m)")
            }

            try {
                if (gpsEnabled) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 0f, locationListener, Looper.getMainLooper())
                }
                if (netEnabled) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 0f, locationListener, Looper.getMainLooper())
                }
                isListening = true
                PassengerPositionBus.updateStatus("正在搜星获取前台定位...")
            } catch (e: SecurityException) {
                PassengerPositionBus.updateStatus("定位权限不足")
            } catch (e: Exception) {
                PassengerPositionBus.updateStatus("定位启动异常: ${e.message}")
            }
        }

        fun stopLocationUpdates() {
            if (isListening && lm != null) {
                try {
                    lm.removeUpdates(locationListener)
                } catch (e: Exception) {}
                isListening = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    checkPermissions()
                    startLocationUpdates()
                }
                Lifecycle.Event.ON_STOP -> {
                    stopLocationUpdates()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        checkPermissions()
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startLocationUpdates()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopLocationUpdates()
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        prefs.edit().putBoolean("asked_location", true).apply()
        askedLocation = true
        checkPermissions()
    }

    val shouldShow = forceOpen || !seenGuide
    if (!shouldShow) return

    val primaryGreen = Color(0xFF00796B)
    val textDark = Color(0xFF212121)
    val textGrey = Color(0xFF757575)
    val statusOk = Color(0xFF2E7D32)
    val statusWarn = Color(0xFFE65100)

    Dialog(
        onDismissRequest = {
            prefs.edit().putBoolean("seen_passenger_guide", true).apply()
            seenGuide = true
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "位置服务授权指引",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "为了快速自动定位当前上车点、计算接驾预估距离以及活动订单接客核验，需要获取您的前台位置信息。",
                    fontSize = 14.sp,
                    color = textGrey
                )
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("当前状态", fontSize = 14.sp, color = textDark, fontWeight = FontWeight.Medium)
                            val isReady = (hasFine || hasCoarse) && locationServiceOn
                            Text(
                                text = if (isReady) "已正常授权" else if (!locationServiceOn) "系统定位未开启" else "未获取权限",
                                color = if (isReady) statusOk else statusWarn,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val busStatus by PassengerPositionBus.status.collectAsState()
                        Text(
                            text = "定位总线：$busStatus",
                            fontSize = 13.sp,
                            color = textGrey
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                    Text(
                        text = if (!locationServiceOn) "打开系统定位服务" else if (hasFine || hasCoarse) "重新检查定位权限" else "一键允许定位权限",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        prefs.edit().putBoolean("seen_passenger_guide", true).apply()
                        seenGuide = true
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("稍后设置", fontSize = 14.sp, color = textGrey)
                }
            }
        }
    }
}
