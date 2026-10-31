package cn.hbzjhs.passenger

import org.json.JSONObject

/** A place that may be handed to a caller: a real name and finite, in-range coordinates. */
data class PickedPlace(val name: String, val latitude: Double, val longitude: Double, val metadata: JSONObject? = null)

/** What tapping one place row in the picker can do. */
sealed class PlaceResolution {
  /** A known, usable point: it is chosen with this one tap. */
  data class Ready(val place: PickedPlace) : PlaceResolution()

  /** The name is usable but the point is missing or broken: search by name, never guess a point. */
  data class SearchByName(val name: String, val reason: String) : PlaceResolution()

  /** Nothing usable to choose or search. */
  data class Unusable(val reason: String) : PlaceResolution()
}

enum class PlaceGroup { COMMON, HISTORY }

/** One row of the picker's common/history lists. */
data class PlaceRow(val group: PlaceGroup, val name: String, val resolution: PlaceResolution, val isDefault: Boolean = false)

/**
 * Pure rules of the place picker. Stored rows (favorites and `historicalLocations`) use the schema
 * `{address, latitude, longitude, coordinateMetadata}`; coordinates may arrive as numbers, numeric
 * text, JSON null or be absent. A point is only usable when both values are finite and in range; an
 * exact 0 is treated as "not provided" (the old fill for unknown points), never as a real place.
 */
object PlaceSelection {
  const val MIN_NAME = 2
  const val MAX_NAME = 100
  const val MISSING_POINT = "未提供坐标"
  const val BROKEN_POINT = "坐标无效"

  private fun number(raw: Any?): Double? = when (raw) {
    null, JSONObject.NULL -> null
    is Number -> raw.toDouble()
    is String -> raw.trim().toDoubleOrNull()
    else -> null
  }

  private fun absent(raw: Any?): Boolean =
    raw == null || raw == JSONObject.NULL || (raw is String && raw.isBlank()) || number(raw) == 0.0

  /** A usable stored coordinate, or null when missing, 0, non-numeric, NaN/Infinity or out of range. */
  fun coordinate(raw: Any?, latitude: Boolean): Double? {
    val value = number(raw) ?: return null
    val range = if (latitude) -90.0..90.0 else -180.0..180.0
    return value.takeIf { it.isFinite() && it != 0.0 && it in range }
  }

  fun resolve(name: String, latitude: Any?, longitude: Any?, metadata: JSONObject? = null): PlaceResolution {
    val shown = name.trim().take(MAX_NAME)
    if (shown.length < MIN_NAME) return PlaceResolution.Unusable("地点名称不完整，请搜索或在地图上选择")
    val lat = coordinate(latitude, true)
    val lng = coordinate(longitude, false)
    if (lat == null || lng == null)
      return PlaceResolution.SearchByName(shown, if (absent(latitude) || absent(longitude)) MISSING_POINT else BROKEN_POINT)
    return PlaceResolution.Ready(PickedPlace(shown, lat, lng, metadata))
  }

  /** A stored row: `{address, latitude, longitude, coordinateMetadata}`. */
  fun resolveRow(row: JSONObject): PlaceResolution =
    resolve(
      row.optString("address").takeUnless { it == "null" }.orEmpty(),
      row.opt("latitude"),
      row.opt("longitude"),
      row.optJSONObject("coordinateMetadata"),
    )

  /**
   * Common rows (favorites, then built-in experience places) followed by history rows. Rows without a
   * name are dropped; a history row that repeats a common row (same name and point) is shown once, as
   * common. Rows whose point is unknown stay visible so the passenger can search them by name.
   */
  fun pickerRows(favorites: List<JSONObject>, history: List<JSONObject>, presets: List<PickedPlace> = emptyList()): List<PlaceRow> {
    val seen = HashSet<Triple<String, Double?, Double?>>()
    fun key(name: String, resolution: PlaceResolution) = when (resolution) {
      is PlaceResolution.Ready -> Triple(name, resolution.place.latitude, resolution.place.longitude)
      else -> Triple(name, null, null)
    }
    val rows = ArrayList<PlaceRow>()
    fun add(group: PlaceGroup, name: String, resolution: PlaceResolution, isDefault: Boolean) {
      if (resolution is PlaceResolution.Unusable) return
      if (seen.add(key(name, resolution))) rows += PlaceRow(group, name, resolution, isDefault)
    }
    favorites.forEach { row ->
      val name = row.optString("address").takeUnless { it == "null" }.orEmpty().trim()
      add(PlaceGroup.COMMON, name, resolveRow(row), row.optBoolean("isDefault"))
    }
    presets.forEach { add(PlaceGroup.COMMON, it.name.trim(), resolve(it.name, it.latitude, it.longitude, it.metadata), false) }
    history.forEach { row ->
      val name = row.optString("address").takeUnless { it == "null" }.orEmpty().trim()
      add(PlaceGroup.HISTORY, name, resolveRow(row), false)
    }
    return rows
  }

  /** Slot (1 or 2) for a new stop between pickup and destination, or null when both are used. */
  fun nextStopSlot(first: String, second: String): Int? = when {
    first.isBlank() -> 1
    second.isBlank() -> 2
    else -> null
  }
}

/**
 * One opening of the place picker. A place is handed to the caller at most once (a second tap, a
 * late map lookup or a late search can never commit again). Text search, map lookups and every pick
 * share [gate]: committing retires all of them, and a lookup result commits only while it is still
 * the newest request.
 */
class PlacePickSession(val gate: PassengerLookupGate = PassengerLookupGate()) {
  var committed: Boolean = false
    private set

  /** The place to hand over, or null when nothing usable was picked or a place was already handed over. */
  fun commit(resolution: PlaceResolution): PickedPlace? {
    if (committed || resolution !is PlaceResolution.Ready) return null
    committed = true
    gate.invalidate()
    return resolution.place
  }

  /** Token for a new async lookup (map reverse geocoding); null once a place was handed over. */
  fun beginLookup(): Long? = if (committed) null else gate.next()

  fun isCurrent(token: Long): Boolean = !committed && gate.isCurrent(token)

  /** Commit the result of lookup [token], only while it is still the newest request. */
  fun commitLookup(token: Long, resolution: PlaceResolution): PickedPlace? =
    if (isCurrent(token)) commit(resolution) else null
}
