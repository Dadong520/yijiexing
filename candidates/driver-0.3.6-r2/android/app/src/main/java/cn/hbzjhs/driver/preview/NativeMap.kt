package cn.hbzjhs.driver.preview

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

data class RoadRoute(
    val points: List<Point>,
    val distance: Double,
    val duration: Double,
    val source: String,
)

private fun Wgs84Point.mapPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun DriverOrder.mapStops(): List<Point> =
    listOfNotNull(wgs84Point(originLat, originLng)?.mapPoint()) +
        waypoints.mapNotNull { w -> wgs84Point(w.latitude, w.longitude)?.mapPoint() } +
        listOfNotNull(wgs84Point(destinationLat, destinationLng)?.mapPoint())

private fun mapCenter(order: DriverOrder?, position: DriverPosition?): LatLng {
    val point =
        order?.let { wgs84Point(it.originLat, it.originLng) }
            ?: position?.let { wgs84Point(it.latitude, it.longitude) }
            ?: order?.let { wgs84Point(it.destinationLat, it.destinationLng) }
    // This is only the initial viewport when no location is known, never a navigation target.
    return point?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(30.6921, 111.2865)
}

private object RoadCache {
    val cache = ConcurrentHashMap<String, RoadRoute>()

    suspend fun load(o: DriverOrder): RoadRoute =
        withContext(Dispatchers.IO) {
            val stops = o.mapStops()
            if (stops.size != o.waypoints.size + 2)
                return@withContext RoadRoute(stops, 0.0, 0.0, "部分地点缺少坐标 · 仅显示已知地点")
            val key = stops.joinToString(";") { "${it.longitude()},${it.latitude()}" }
            cache[key]
                ?: runCatching {
                        val connection =
                            URL(
                                    "https://router.project-osrm.org/route/v1/driving/$key?overview=full&geometries=geojson"
                                )
                                .openConnection()
                                .apply {
                                    connectTimeout = 6000
                                    readTimeout = 7000
                                    setRequestProperty("User-Agent", "YiJieXing-LocalReview/0.2")
                                }
                        val json =
                            connection.getInputStream().bufferedReader().use {
                                JSONObject(it.readText())
                            }
                        val route = json.getJSONArray("routes").getJSONObject(0)
                        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
                        require(coords.length() >= 2) { "Route geometry is incomplete" }
                        RoadRoute(
                            (0 until coords.length()).map { i ->
                                coords.getJSONArray(i).let {
                                    requireNotNull(wgs84Point(it.getDouble(1), it.getDouble(0))) {
                                            "Route geometry contains an invalid coordinate"
                                        }
                                        .mapPoint()
                                }
                            },
                            route.getDouble("distance"),
                            route.getDouble("duration"),
                            "OSRM 公共路线",
                        )
                    }
                    .getOrElse { RoadRoute(stops, 0.0, 0.0, "路线服务暂不可用 · 仅显示各点连线") }
                    .also { cache[key] = it }
        }
}

@Composable
fun NativeMap(
    modifier: Modifier = Modifier, order: DriverOrder? = null,
    onPick: ((Double, Double) -> Unit)? = null, heatPoints: List<Pair<Double, Double>> = emptyList(),
) {
    val apiKey by AmapRuntime.key.collectAsState()
    if (apiKey.isBlank()) { NativeMapLegacy(modifier, order, onPick, heatPoints); return }
    val context = LocalContext.current
    val position by PositionBus.position.collectAsState()
    val points = mutableListOf<AmapPlacePoint>()
    order?.let { o ->
        points.add(AmapPlacePoint(o.originLat, o.originLng, "上车点"))
        o.waypoints.forEach { w ->
            val latitude = w.latitude; val longitude = w.longitude
            if (latitude != null && longitude != null) points.add(AmapPlacePoint(latitude, longitude, "途经点"))
        }
        if (!o.destinationPending) points.add(AmapPlacePoint(o.destinationLat, o.destinationLng, "目的地"))
    }
    position?.let { points.add(AmapPlacePoint(it.latitude, it.longitude, "本机上次定位", false)) }
    heatPoints.forEach { points.add(AmapPlacePoint(it.first, it.second, "订单分布点", false)) }
    var pickError by remember { mutableStateOf("") }
    Box(modifier) {
        AmapCanvas(points, Modifier.fillMaxSize(), onPick?.let { callback -> { latitude, longitude ->
            runCatching { normalizeAmapPoint(context, latitude, longitude) }
                .onSuccess { callback(it.latitude, it.longitude); pickError = "" }
                .onFailure { pickError = "地点转换失败，请重新选择" }
        } })
        if (pickError.isNotBlank()) Surface(Modifier.align(Alignment.BottomCenter)) { Text(pickError, Modifier.padding(8.dp)) }
    }
}

@Composable
private fun NativeMapLegacy(
    modifier: Modifier = Modifier,
    order: DriverOrder? = null,
    onPick: ((Double, Double) -> Unit)? = null,
    heatPoints: List<Pair<Double, Double>> = emptyList(),
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val position by PositionBus.position.collectAsState()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var road by remember(order?.id) { mutableStateOf<RoadRoute?>(null) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { failed = true }
            getMapAsync { m ->
                map = m
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isAttributionEnabled = true
                m.uiSettings.isCompassEnabled = true
                m.uiSettings.setAttributionMargins(4, 0, 4, 0)
                m.cameraPosition =
                    CameraPosition.Builder().target(mapCenter(order, position)).zoom(12.0).build()
                m.addOnMapClickListener { p ->
                    wgs84Point(p.latitude, p.longitude)?.let {
                        onPick?.invoke(it.latitude, it.longitude)
                    }
                    onPick != null
                }
                m.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                    style.addSource(
                        GeoJsonSource(
                            "driver-points",
                            FeatureCollection.fromFeatures(emptyArray<Feature>()),
                        )
                    )
                    style.addLayer(
                        CircleLayer("driver-marker", "driver-points")
                            .withProperties(
                                circleRadius(7f),
                                circleColor("#009489"),
                                circleStrokeColor("#ffffff"),
                                circleStrokeWidth(3f),
                            )
                    )
                    style.addSource(
                        GeoJsonSource(
                            "ride-route",
                            FeatureCollection.fromFeatures(emptyArray<Feature>()),
                        )
                    )
                    style.addLayer(
                        LineLayer("ride-line", "ride-route")
                            .withProperties(lineColor("#009489"), lineWidth(5f), lineOpacity(.85f))
                    )
                    style.addSource(
                        GeoJsonSource(
                            "ride-stops",
                            FeatureCollection.fromFeatures(emptyArray<Feature>()),
                        )
                    )
                    style.addLayer(
                        CircleLayer("ride-stop-markers", "ride-stops")
                            .withProperties(
                                circleRadius(6f),
                                circleColor("#E58B42"),
                                circleStrokeColor("#ffffff"),
                                circleStrokeWidth(2f),
                            )
                    )
                    style.addSource(
                        GeoJsonSource(
                            "heat-points",
                            FeatureCollection.fromFeatures(emptyArray<Feature>()),
                        )
                    )
                    style.addLayer(
                        CircleLayer("heat-areas", "heat-points")
                            .withProperties(
                                circleRadius(30f),
                                circleColor("#ED8439"),
                                circleOpacity(.32f),
                                circleBlur(.7f),
                            )
                    )
                    ready = true
                    failed = false
                }
            }
        }
    }
    DisposableEffect(owner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(order) {
        road = null
        if (order != null) road = RoadCache.load(order)
    }
    LaunchedEffect(ready, position, road, order, heatPoints) {
        val m = map ?: return@LaunchedEffect
        val style = m.style ?: return@LaunchedEffect
        val p = position?.let { wgs84Point(it.latitude, it.longitude) }
        style
            .getSourceAs<GeoJsonSource>("driver-points")
            ?.setGeoJson(
                if (p == null) FeatureCollection.fromFeatures(emptyArray<Feature>())
                else
                    FeatureCollection.fromFeature(
                        Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude))
                    )
            )
        style
            .getSourceAs<GeoJsonSource>("heat-points")
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    heatPoints
                        .mapNotNull { (lat, lng) ->
                            wgs84Point(lat, lng)?.let { Feature.fromGeometry(it.mapPoint()) }
                        }
                        .toTypedArray()
                )
            )
        if (order != null) {
            val linePoints = road?.points ?: order.mapStops()
            val completeRoute = order.mapStops().size == order.waypoints.size + 2
            style
                .getSourceAs<GeoJsonSource>("ride-route")
                ?.setGeoJson(
                    if (completeRoute && linePoints.size >= 2)
                        FeatureCollection.fromFeature(
                            Feature.fromGeometry(LineString.fromLngLats(linePoints))
                        )
                    else FeatureCollection.fromFeatures(emptyArray<Feature>())
                )
            style
                .getSourceAs<GeoJsonSource>("ride-stops")
                ?.setGeoJson(
                    FeatureCollection.fromFeatures(
                        order.mapStops().map { Feature.fromGeometry(it) }.toTypedArray()
                    )
                )
        }
    }
    Column(Modifier.fillMaxWidth()) {
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Pale)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (!ready)
            Text(
                if (failed) "地图暂不可用，请检查网络" else "正在加载地图…",
                modifier =
                    Modifier.align(Alignment.Center)
                        .background(Color.White.copy(alpha = .9f))
                        .padding(12.dp),
                fontSize = 16.sp,
                color = Muted,
            )
        if (order != null && road != null)
            Text(
                road!!.source,
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.White.copy(alpha = .92f), RoundedCornerShape(6.dp))
                        .padding(6.dp),
                fontSize = 14.sp,
                color = Muted,
            )
        IconButton(
            onClick = {
                val p = position?.let { wgs84Point(it.latitude, it.longitude) }
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        p?.let { LatLng(it.latitude, it.longitude) } ?: mapCenter(order, null),
                        14.0,
                    )
                )
            },
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .background(Color.White, RoundedCornerShape(18.dp)),
        ) {
            Glyph("navigation", modifier = Modifier.size(19.dp))
        }
    }
    Text(
        "© OpenFreeMap · OpenMapTiles · OpenStreetMap",
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = Muted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
    )
    }
}

fun openExternalNavigation(context: Context, order: DriverOrder, onError: (String) -> Unit) {
    val target = order.navigationTarget()
    if (target == null) {
        onError(if (order.stage == Stage.CANCELLED) "已取消的订单不能发起导航" else "该地点缺少名称和坐标，暂不能导航")
        return
    }
    if (context.launchNavigation(amapNavigationIntent(target))) return

    val activity = context.navigationActivity()
    if (activity == null || activity.isFinishing) {
        if (!context.launchNavigation(webNavigationIntent(target)))
            onError("未找到可用的导航应用或浏览器，请在当前地图中查看地点")
        return
    }
    AlertDialog.Builder(activity)
        .setTitle("当前无法打开高德地图")
        .setMessage(
            "${target.stop.title}：${target.name}\n" +
                if (target.point == null) "此地点暂无坐标，可按名称搜索。请核对搜索结果后再开始导航。"
                else "可以用其他地图按名称搜索，或在高德网页版查看地点。请核对位置后再开始导航。"
        )
        .setPositiveButton("其他地图搜索") { _, _ ->
            val chooser = Intent.createChooser(systemNavigationSearchIntent(target), "选择地图搜索地点")
            if (
                !context.launchNavigation(chooser) &&
                    !context.launchNavigation(webNavigationIntent(target))
            )
                onError("未找到可用的地图或浏览器，请先安装高德地图")
        }
        .setNeutralButton("网页版查看地点") { _, _ ->
            if (!context.launchNavigation(webNavigationIntent(target)))
                onError("未找到可用的浏览器，请先安装高德地图")
        }
        .setNegativeButton("取消", null)
        .show()
}

internal fun amapNavigationIntent(target: NavigationTarget): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(NavigationContract.amapUri(target)))
        .addCategory(Intent.CATEGORY_DEFAULT)
        .setPackage(NavigationContract.AMAP_PACKAGE)

internal fun systemNavigationSearchIntent(target: NavigationTarget): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(NavigationContract.systemSearchUri(target)))

internal fun webNavigationIntent(target: NavigationTarget): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(NavigationContract.webUri(target)))
        .addCategory(Intent.CATEGORY_BROWSABLE)

private fun Context.launchNavigation(intent: Intent): Boolean =
    try {
        if (navigationActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

private tailrec fun Context.navigationActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> if (baseContext !== this) baseContext.navigationActivity() else null
        else -> null
    }
