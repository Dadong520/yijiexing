package cn.hbzjhs.passenger

/** A device fix as used for the default pickup point (WGS84, accuracy in metres, time in epoch ms). */
data class PickupFix(val latitude: Double, val longitude: Double, val accuracy: Float, val time: Long) {
  /** Compact identity saved with the draft so an async reverse lookup can tell its fix is still current. */
  fun token(): String = "$latitude,$longitude,$accuracy,$time"

  companion object {
    fun parse(token: String): PickupFix? {
      val parts = token.split(',')
      if (parts.size != 4) return null
      return runCatching { PickupFix(parts[0].toDouble(), parts[1].toDouble(), parts[2].toFloat(), parts[3].toLong()) }.getOrNull()
    }
  }
}

/**
 * When the phone's own fix may fill the pickup point. Uses the app's existing location standards
 * (location reporting and trip-distance checks): finite in-range WGS84, 0 < accuracy <= 100 m, and
 * time within -30 s .. 120 s of now. No pickup-distance business rule is introduced here.
 */
object PassengerPickupDefaults {
  const val MAX_AGE_MS = 120_000L
  const val MAX_FUTURE_MS = 30_000L
  const val MAX_ACCURACY_M = 100f
  /** A newer fix must be at least this much more precise to replace a still-fresh one (limits re-lookups). */
  const val MIN_ACCURACY_GAIN_M = 10f

  fun usable(fix: PickupFix?, now: Long): Boolean =
    fix != null && fix.latitude.isFinite() && fix.longitude.isFinite() &&
      fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0 &&
      fix.accuracy.isFinite() && fix.accuracy > 0f && fix.accuracy <= MAX_ACCURACY_M &&
      now - fix.time in -MAX_FUTURE_MS..MAX_AGE_MS

  /**
   * Whether [candidate] should become the automatic pickup point instead of [applied] (the fix used
   * so far, null if none). Never applies once the passenger chose a pickup themselves.
   */
  fun shouldReplace(applied: PickupFix?, candidate: PickupFix?, manuallyChosen: Boolean, now: Long): Boolean {
    if (manuallyChosen || candidate == null || !usable(candidate, now)) return false
    if (applied == null || candidate == applied) return applied == null
    if (candidate.time < applied.time) return false
    return !usable(applied, now) || candidate.accuracy + MIN_ACCURACY_GAIN_M <= applied.accuracy || moved(applied, candidate)
  }

  /** A newer fix this far from the applied one (beyond both accuracies) means the phone really moved. */
  const val MIN_MOVE_M = 200.0

  /**
   * Whether [candidate] (newer) lies clearly away from [applied]: farther than [MIN_MOVE_M] plus both
   * accuracy radii. A drifted first fix (e.g. another country) is then corrected by the next real fix
   * without waiting for it to age out; small jitter never re-fills the pickup.
   */
  fun moved(applied: PickupFix, candidate: PickupFix): Boolean =
    candidate.time > applied.time &&
      PlaceSelection.distanceMeters(applied.latitude, applied.longitude, candidate.latitude, candidate.longitude) >
        MIN_MOVE_M + applied.accuracy + candidate.accuracy

  /** Same limit the server applies to the whole route (sum of legs); see [straightTripKm]. */
  const val MAX_TRIP_KM = 300.0

  /**
   * Straight-line pickup→destination distance in km when both points are usable, else null. The
   * server's route length is the sum of legs and never shorter, so a straight distance above
   * [MAX_TRIP_KM] is always refused there too; the app only says so earlier and offers a fix.
   */
  fun straightTripKm(oLat: String, oLng: String, dLat: String, dLng: String): Double? {
    val a = PlaceSelection.coordinate(oLat, true) ?: return null
    val b = PlaceSelection.coordinate(oLng, false) ?: return null
    val c = PlaceSelection.coordinate(dLat, true) ?: return null
    val d = PlaceSelection.coordinate(dLng, false) ?: return null
    return PlaceSelection.distanceMeters(a, b, c, d) / 1000.0
  }

  /** True only when the route is certainly over the limit (a small margin leaves edge cases to the server). */
  fun tripTooFar(km: Double?): Boolean = km != null && km > MAX_TRIP_KM + 1.0

  /** After "重新定位": only a fix newer than [floorTime] (the fix being retried) may fill the pickup. */
  fun freshAfter(candidate: PickupFix?, floorTime: Long): Boolean = candidate != null && candidate.time > floorTime

  /**
   * Identity of one reverse lookup: passenger id + draft generation + fix. Not a credential. Blank
   * owner or fix gives a blank token, which is never accepted.
   */
  fun lookupToken(owner: String, generation: Int, fixToken: String): String =
    if (owner.isBlank() || fixToken.isBlank()) "" else "$owner#$generation#$fixToken"

  /** A reverse-lookup result may be written only for the same owner/generation/fix and while still automatic. */
  fun acceptLookup(requestedToken: String, currentToken: String, manuallyChosen: Boolean, stillAutomatic: Boolean): Boolean =
    !manuallyChosen && stillAutomatic && requestedToken.isNotBlank() && requestedToken == currentToken
}

/**
 * The whole text-search flow of the address editor, including the debounce wait. Ownership is taken
 * from the gate *before* waiting, so any manual coordinate, place selection, map pick, cancel or
 * closing the editor (all of which invalidate the gate) also retires a search that is still waiting:
 * it then never starts a request, shows progress, or writes results/errors.
 */
object PassengerAddressLookup {
  const val DEBOUNCE_MS = 400L

  /** Returns true only when this run published a result or an error. */
  suspend fun <T> debounced(
    gate: PassengerLookupGate,
    stillWanted: () -> Boolean,
    wait: suspend () -> Unit,
    begin: () -> Unit,
    fetch: suspend () -> T,
    publish: (T) -> Unit,
    fail: (Exception) -> Unit,
    end: () -> Unit,
  ): Boolean {
    val token = gate.next()
    wait()
    if (!gate.isCurrent(token) || !stillWanted()) return false
    begin()
    try {
      val found = fetch()
      if (!gate.isCurrent(token) || !stillWanted()) return false
      publish(found)
      return true
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
      throw e
    } catch (e: Exception) {
      if (!gate.isCurrent(token) || !stillWanted()) return false
      fail(e)
      return true
    } finally {
      if (gate.isCurrent(token)) end()
    }
  }
}

/**
 * Ownership for address lookups in one editor: text search, map reverse lookup and every manual
 * change share it, so only the newest request may write results, errors or progress.
 */
class PassengerLookupGate {
  private var generation = 0L

  fun next(): Long = ++generation

  fun invalidate() {
    generation++
  }

  fun isCurrent(token: Long): Boolean = token == generation
}
