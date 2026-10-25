package cn.hbzjhs.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Selected WGS84 booking points only. This component never reads GPS or changes booking data. */
@Composable
fun PassengerMap(
  originLat: String,
  originLng: String,
  destinationLat: String,
  destinationLng: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val owner = LocalLifecycleOwner.current
  var retry by remember { mutableIntStateOf(0) }
  var map by remember(retry) { mutableStateOf<MapLibreMap?>(null) }
  var ready by remember(retry) { mutableStateOf(false) }
  var failed by remember(retry) { mutableStateOf(false) }
  var recenter by remember { mutableIntStateOf(0) }
  fun point(lat: String, lng: String): LatLng? {
    val a = lat.toDoubleOrNull() ?: return null
    val b = lng.toDoubleOrNull() ?: return null
    return if (a.isFinite() && b.isFinite() && a in -85.0..85.0 && b in -180.0..180.0) LatLng(a,b) else null
  }
  val origin = point(originLat, originLng)
  val destination = point(destinationLat, destinationLng)
  val currentOrigin by rememberUpdatedState(origin)
  var disposed by remember(retry) { mutableStateOf(false) }
  val mapView = remember(retry) {
    MapLibre.getInstance(context)
    MapView(context).apply {
      onCreate(null)
      addOnDidFailLoadingMapListener { if (!disposed) { failed = true; ready = false } }
      getMapAsync { m ->
        if (!disposed) {
          map = m
          m.uiSettings.isLogoEnabled = false
          m.uiSettings.isAttributionEnabled = true
          m.uiSettings.isCompassEnabled = true
          m.cameraPosition = CameraPosition.Builder().target(currentOrigin ?: LatLng(30.69,111.31)).zoom(12.5).build()
          m.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
            if (!disposed) {
              listOf("pickup" to "#009B90", "dropoff" to "#E99327").forEach { (id,color) ->
                style.addSource(GeoJsonSource(id, FeatureCollection.fromFeatures(emptyArray<Feature>())))
                style.addLayer(CircleLayer("$id-marker", id).withProperties(circleRadius(8f),circleColor(color),circleStrokeColor("#ffffff"),circleStrokeWidth(3f)))
              }
              ready = true
              failed = false
            }
          }
        }
      }
    }
  }
  DisposableEffect(owner, mapView) {
    val observer = LifecycleEventObserver { _, event ->
      when(event) {
        Lifecycle.Event.ON_START -> mapView.onStart()
        Lifecycle.Event.ON_RESUME -> mapView.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
        Lifecycle.Event.ON_STOP -> mapView.onStop()
        else -> Unit
      }
    }
    owner.lifecycle.addObserver(observer)
    mapView.onStart(); mapView.onResume()
    onDispose {
      disposed = true
      owner.lifecycle.removeObserver(observer)
      mapView.onPause(); mapView.onStop(); mapView.onDestroy()
    }
  }
  LaunchedEffect(mapView) {
    delay(18000)
    if (!ready) failed = true
  }
  LaunchedEffect(ready, originLat, originLng, destinationLat, destinationLng, recenter) {
    val m = map ?: return@LaunchedEffect
    if (!ready) return@LaunchedEffect
    listOf("pickup" to origin, "dropoff" to destination).forEach { (id,p) ->
      val data = if (p == null) FeatureCollection.fromFeatures(emptyArray<Feature>()) else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(p.longitude,p.latitude)))
      m.style?.getSourceAs<GeoJsonSource>(id)?.setGeoJson(data)
    }
    val points = listOfNotNull(origin,destination)
    if (points.size == 2 && points[0].distanceTo(points[1]) > 80) {
      m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(),64))
    } else points.firstOrNull()?.let { m.animateCamera(CameraUpdateFactory.newLatLngZoom(it,14.0)) }
  }
  Box(modifier.background(Color(0xFFE9F3EF))) {
    key(mapView) { AndroidView(factory={mapView},modifier=Modifier.fillMaxSize()) }
    Surface(Modifier.align(Alignment.TopStart).padding(12.dp),color=Color.White.copy(alpha=.96f),shape=RoundedCornerShape(8.dp),shadowElevation=1.dp) {
      Text("起终点预览 · 非实时定位",fontSize=12.sp,color=Muted,modifier=Modifier.padding(horizontal=10.dp,vertical=7.dp))
    }
    FilledTonalIconButton(onClick={recenter++},enabled=ready,modifier=Modifier.align(Alignment.BottomEnd).padding(end=12.dp,bottom=38.dp).size(48.dp),colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=Color.White,contentColor=Teal)) {
      Icon(Icons.Default.LocationOn,contentDescription="回到起终点")
    }
    if (!ready || failed) {
      Surface(Modifier.align(Alignment.Center).padding(20.dp),color=Color.White,shape=RoundedCornerShape(14.dp),shadowElevation=2.dp) {
        Column(Modifier.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
          if (failed) {
            Text("地图暂未加载",color=Ink,fontSize=15.sp)
            Text("仍可在下方选择地址",color=Muted,fontSize=12.sp)
            TextButton(onClick={retry++}) { Text("重新加载地图") }
          } else {
            CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp,color=Teal)
            Spacer(Modifier.height(8.dp))
            Text("地图加载中…",color=Muted,fontSize=13.sp)
          }
        }
      }
    }
  }
}
