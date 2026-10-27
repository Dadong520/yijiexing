package cn.hbzjhs.passenger

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.poisearch.PoiSearch
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery

/** The name, selected coordinates and original provider point travel together. */
data class SearchedAddress(val name: String, val latitude: Double, val longitude: Double, val metadata: JSONObject? = null)

@Suppress("DEPRECATION")
suspend fun searchSystemAddresses(context: Context, query: String, city: String = ""): List<SearchedAddress> = withContext(Dispatchers.IO) {
  require(query.trim().length in 2..200) { "请输入2至200字的地点或详细地址" }
  if (AmapRuntime.key.value.isNotBlank()) {
    AmapRuntime.prepare(context)
    try {
      val request = PoiSearch.Query(query.trim(), "", city.trim()).apply { pageSize = 12; pageNum = 0 }
      PoiSearch(context.applicationContext, request).searchPOI()?.pois.orEmpty().mapNotNull { place ->
        val point = place.latLonPoint ?: return@mapNotNull null
        val normalized = normalizeAmapPoint(context, point.latitude, point.longitude)
        val name = listOfNotNull(place.title, place.cityName, place.adName, place.snippet)
          .filter { it.isNotBlank() }.distinct().joinToString(" · ").take(100)
        if (name.length < 2) null else SearchedAddress(name, normalized.latitude, normalized.longitude, normalized.metadata)
      }.distinctBy { Triple(it.name, it.latitude, it.longitude) }
    } catch (e: AMapException) {
      throw IllegalStateException("高德地点搜索暂不可用（服务代码 ${e.errorCode}），请检查配置或稍后重试")
    }
  } else {
    check(Geocoder.isPresent()) { "地址服务暂不可用，请刷新连接后重试" }
    Geocoder(context.applicationContext, Locale.SIMPLIFIED_CHINESE).getFromLocationName(query.trim(), 8).orEmpty()
      .filter { it.hasLatitude() && it.hasLongitude() && it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -85.0..85.0 && it.longitude in -180.0..180.0 }
      .map { a -> SearchedAddress(a.getAddressLine(0)?.takeIf { it.isNotBlank() } ?: a.featureName?.takeIf { it.isNotBlank() } ?: query.trim(), a.latitude, a.longitude) }
      .distinctBy { Triple(it.name, it.latitude, it.longitude) }
  }
}

suspend fun reverseAmapAddress(context: Context, latitude: Double, longitude: Double): SearchedAddress = withContext(Dispatchers.IO) {
  AmapRuntime.prepare(context)
  try {
    val address = GeocodeSearch(context.applicationContext).getFromLocation(RegeocodeQuery(LatLonPoint(latitude, longitude), 100f, GeocodeSearch.AMAP))
    val name = address?.formatAddress?.trim().orEmpty().take(100)
    check(name.length >= 2) { "此处暂无可用地址，请搜索附近地点后选择" }
    val normalized = normalizeAmapPoint(context, latitude, longitude)
    SearchedAddress(name, normalized.latitude, normalized.longitude, normalized.metadata)
  } catch (e: AMapException) {
    throw IllegalStateException("高德地图选点暂不可用（服务代码 ${e.errorCode}），请稍后重试")
  }
}
