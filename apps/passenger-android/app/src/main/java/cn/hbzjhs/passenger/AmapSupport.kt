package cn.hbzjhs.passenger

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.*
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.ServiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** App-scoped identifiers live only in memory after an authenticated state read. */
object AmapRuntime {
    private val mutableKey = MutableStateFlow("")
    val key = mutableKey.asStateFlow()
    private val mutableConsent = MutableStateFlow(false)
    val consent = mutableConsent.asStateFlow()
    fun configure(config: JSONObject?) {
        val value = config?.optString("androidKey").orEmpty()
        mutableKey.value = if (config?.optBoolean("enabled") == true && value.matches(Regex("[a-f0-9]{32}"))) value else ""
    }
    fun loadConsent(context: Context) {
        mutableConsent.value = context.getSharedPreferences("amap_privacy_v1", Context.MODE_PRIVATE).getBoolean("accepted", false)
    }
    fun agree(context: Context) {
        context.getSharedPreferences("amap_privacy_v1", Context.MODE_PRIVATE).edit().putBoolean("accepted", true).apply()
        mutableConsent.value = true
    }
    fun prepare(context: Context) {
        check(mutableConsent.value) { "请先阅读并同意地图服务说明" }
        check(mutableKey.value.isNotBlank()) { "地图配置暂不可用，请刷新连接后重试" }
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        MapsInitializer.setApiKey(mutableKey.value)
        ServiceSettings.updatePrivacyShow(context, true, true)
        ServiceSettings.updatePrivacyAgree(context, true)
        ServiceSettings.getInstance().apply {
            setApiKey(mutableKey.value)
            protocol = ServiceSettings.HTTPS
            connectionTimeOut = 10000
            soTimeOut = 10000
        }
    }
}

data class AmapPlacePoint(val latitude: Double, val longitude: Double, val title: String, val routeStop: Boolean = true)

data class NormalizedAmapPoint(val latitude: Double, val longitude: Double, val metadata: JSONObject)

fun amapForward(context: Context, latitude: Double, longitude: Double): LatLng {
    require(latitude.isFinite() && longitude.isFinite() && latitude in -85.0..85.0 && longitude in -180.0..180.0)
    return requireNotNull(CoordinateConverter(context).from(CoordinateConverter.CoordType.GPS).coord(LatLng(latitude, longitude)).convert())
}

/** Numerical inverse of this SDK's official forward function at a selected POI.
 * It never reads a sensor, reports a device fix, or changes the raw GPS stream. */
fun normalizeAmapPoint(context: Context, rawLatitude: Double, rawLongitude: Double): NormalizedAmapPoint {
    require(rawLatitude.isFinite() && rawLongitude.isFinite() && rawLatitude in -85.0..85.0 && rawLongitude in -180.0..180.0)
    var latitude = rawLatitude
    var longitude = rawLongitude
    repeat(12) {
        val shown = amapForward(context, latitude, longitude)
        latitude -= shown.latitude - rawLatitude
        longitude -= shown.longitude - rawLongitude
    }
    val shown = amapForward(context, latitude, longitude)
    val result = FloatArray(1)
    android.location.Location.distanceBetween(rawLatitude, rawLongitude, shown.latitude, shown.longitude, result)
    val residual = result[0].toDouble()
    check(residual.isFinite() && residual <= .2) { "地点坐标转换未达到可用精度，请重新选择" }
    return NormalizedAmapPoint(latitude, longitude, JSONObject()
        .put("provider", "AMAP").put("rawCoordinateSystem", "GCJ02")
        .put("rawLatitude", rawLatitude).put("rawLongitude", rawLongitude)
        .put("normalizedLatitude", latitude).put("normalizedLongitude", longitude)
        .put("method", "AMAP_SDK_FORWARD_ITERATIVE_V1").put("sdkVersion", "11.2.100")
        .put("residualMeters", residual))
}

@Composable
fun AmapCanvas(
    points: List<AmapPlacePoint>,
    modifier: Modifier = Modifier,
    onPick: ((Double, Double) -> Unit)? = null,
    notice: String = "高德地图 · 地点连线示意，非道路导航",
) {
    val context = LocalContext.current
    val apiKey by AmapRuntime.key.collectAsState()
    val consent by AmapRuntime.consent.collectAsState()
    LaunchedEffect(context) { AmapRuntime.loadConsent(context) }
    if (!consent) {
        Surface(modifier) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("启用地图服务", style = MaterialTheme.typography.titleMedium)
                Text("高德地图由北京高德图强科技有限公司提供。启用后将处理设备信息、IP地址、搜索词和地图操作位置，用于地图展示与地点搜索。")
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lbs.amap.com/pages/privacy/"))) }) { Text("阅读高德隐私政策") }
                Button(onClick = { AmapRuntime.agree(context) }) { Text("同意并启用地图") }
                Text("您可继续使用其他功能，稍后再启用地图。", style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    if (apiKey.isBlank()) {
        Surface(modifier) { Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) { Text("地图配置暂不可用，请刷新连接后重试") } }
        return
    }
    val owner = LocalLifecycleOwner.current
    var retry by remember { mutableIntStateOf(0) }
    var controller by remember(apiKey, retry) { mutableStateOf<AMap?>(null) }
    var ready by remember(apiKey, retry) { mutableStateOf(false) }
    var failed by remember(apiKey, retry) { mutableStateOf(false) }
    var disposed by remember(apiKey, retry) { mutableStateOf(false) }
    val pick by rememberUpdatedState(onPick)
    val mapView = remember(apiKey, retry) {
        AmapRuntime.prepare(context)
        MapView(context).apply {
            onCreate(null)
            controller = this.map
            this.map.apply {
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isCompassEnabled = true
                uiSettings.isMyLocationButtonEnabled = false
                isMyLocationEnabled = false
                setOnMapLoadedListener { if (!disposed) { ready = true; failed = false } }
                setOnMapClickListener { coordinate -> pick?.invoke(coordinate.latitude, coordinate.longitude) }
            }
        }
    }
    DisposableEffect(owner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose { disposed = true; owner.lifecycle.removeObserver(observer); mapView.onPause(); mapView.onDestroy() }
    }
    LaunchedEffect(apiKey, retry) {
        kotlinx.coroutines.delay(20000)
        if (!ready) failed = true
    }
    LaunchedEffect(ready, points) {
        if (!ready) return@LaunchedEffect
        val actual = points.filter { it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -85.0..85.0 && it.longitude in -180.0..180.0 }
        val shown = actual.map { it to amapForward(context, it.latitude, it.longitude) }
        controller?.let { m ->
            m.clear()
            shown.forEach { (p, coordinate) -> m.addMarker(MarkerOptions().position(coordinate).title(p.title)) }
            // Only the order stop list should be supplied for route drawing. No road route is claimed.
            val route = shown.filter { it.first.routeStop }.map { it.second }
            if (route.size > 1) m.addPolyline(PolylineOptions().addAll(route).width(5f).color(android.graphics.Color.rgb(0, 139, 125)))
            when {
                shown.size > 1 && shown.map { it.second }.distinct().size > 1 -> m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().apply { shown.forEach { include(it.second) } }.build(), 70))
                shown.isNotEmpty() -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(shown[0].second, 14f))
                else -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(30.70, 111.29), 12f))
            }
        }
    }
    Box(modifier) {
        key(mapView) { AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) }
        Surface(Modifier.align(Alignment.TopStart).padding(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
            Text(notice, modifier = Modifier.padding(7.dp), style = MaterialTheme.typography.labelSmall)
        }
        if (failed) Surface(Modifier.align(Alignment.Center)) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("地图暂未加载，请检查网络后重试")
                TextButton(onClick = { retry++ }) { Text("重新加载地图") }
            }
        }
    }
}
