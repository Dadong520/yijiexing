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

  /** Two stored points this close (metres) under the same name are one place saved twice. */
  const val SAME_PLACE_M = 30.0

  /** Name used to compare repeats: trimmed, inner whitespace collapsed. Display text is unchanged. */
  fun sameNameKey(name: String): String = name.trim().replace(Regex("\\s+"), " ")

  /** Great-circle distance in metres between two WGS84 points. */
  fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
    return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)))
  }

  /**
   * Whether two rows are the same place shown twice: the same name and both points usable and within
   * [SAME_PLACE_M], or the same name with both points unknown. A same-named place elsewhere (another
   * branch, another city) keeps its own row; a known point is never merged into an unknown one.
   */
  fun samePlace(aName: String, a: PlaceResolution, bName: String, b: PlaceResolution): Boolean {
    if (sameNameKey(aName) != sameNameKey(bName)) return false
    return when {
      a is PlaceResolution.Ready && b is PlaceResolution.Ready ->
        distanceMeters(a.place.latitude, a.place.longitude, b.place.latitude, b.place.longitude) <= SAME_PLACE_M
      a is PlaceResolution.SearchByName && b is PlaceResolution.SearchByName -> true
      else -> false
    }
  }

  /**
   * Common rows (favorites, then built-in experience places) followed by history rows. Rows without a
   * name are dropped; a row that repeats an earlier one ([samePlace]: same name, point within a few
   * metres) is shown once, keeping the earlier one (common first, then history in the server's order). Rows whose
   * point is unknown stay visible so the passenger can search them by name.
   */
  fun pickerRows(favorites: List<JSONObject>, history: List<JSONObject>, presets: List<PickedPlace> = emptyList()): List<PlaceRow> {
    val rows = ArrayList<PlaceRow>()
    fun add(group: PlaceGroup, name: String, resolution: PlaceResolution, isDefault: Boolean) {
      if (resolution is PlaceResolution.Unusable) return
      if (rows.none { samePlace(it.name, it.resolution, name, resolution) }) rows += PlaceRow(group, name, resolution, isDefault)
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
 * Short bounded shield for the screen a place page returns to. A fast second tap (a double tap, or
 * a tap still in flight when the picker closes) would otherwise land on whatever the page exposes
 * at the same spot, such as the call button. [arm] when the picker closes (place chosen or back);
 * risky actions then call [allows] at tap time and do nothing while it is false.
 *
 * Uses a monotonic clock (not wall time). Never armed means open; the window always ends after
 * [windowMs]; a clock that runs backwards opens it rather than holding it indefinitely.
 */
class ReturnTapGuard(val windowMs: Long = WINDOW_MS,private val clock: () -> Long = { System.nanoTime() / 1_000_000L }) {
  private var armedAt: Long? = null

  /** Starts (or restarts) the window from now. */
  fun arm() {
    armedAt = clock()
  }

  /** Milliseconds left in the window, 0 when open; never more than [windowMs]. */
  fun remainingMs(): Long {
    val start = armedAt ?: return 0L
    val elapsed = clock() - start
    if (elapsed < 0L || elapsed >= windowMs) {
      armedAt = null
      return 0L
    }
    return windowMs - elapsed
  }

  fun allows(): Boolean = remainingMs() == 0L

  companion object {
    /** Default window: well above Android's 300 ms double-tap timeout, short enough not to feel locked. */
    const val WINDOW_MS = 700L
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
