package cn.hbzjhs.passenger

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A selected result binds its display name to this exact returned coordinate pair. */
data class SearchedAddress(val name: String, val latitude: Double, val longitude: Double)

@Suppress("DEPRECATION")
suspend fun searchSystemAddresses(context: Context, query: String): List<SearchedAddress> = withContext(Dispatchers.IO) {
  require(query.trim().length in 2..200) { "请输入2至200字的地点或详细地址" }
  check(Geocoder.isPresent()) { "此设备未提供系统地址搜索，请选择常用地点或手动填写坐标" }
  val rows = Geocoder(context.applicationContext, Locale.SIMPLIFIED_CHINESE).getFromLocationName(query.trim(), 6).orEmpty()
  rows.filter { it.hasLatitude() && it.hasLongitude() && it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    .map { a -> SearchedAddress(a.getAddressLine(0)?.takeIf { it.isNotBlank() } ?: a.featureName?.takeIf { it.isNotBlank() } ?: query.trim(), a.latitude, a.longitude) }
    .distinctBy { Triple(it.name, it.latitude, it.longitude) }
}
