package cn.hbzjhs.passenger

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PassengerPickupDefaultsTest {
  private val now = 1_800_000_000_000L
  private fun fix(acc: Float = 20f, ageMs: Long = 1_000L, lat: Double = 30.70, lng: Double = 111.29) =
    PickupFix(lat, lng, acc, now - ageMs)

  @Test
  fun usableFollowsExistingLocationStandards() {
    assertTrue(PassengerPickupDefaults.usable(fix(), now))
    assertFalse(PassengerPickupDefaults.usable(null, now))
    assertFalse(PassengerPickupDefaults.usable(fix(ageMs = 120_001L), now))
    assertFalse(PassengerPickupDefaults.usable(fix(ageMs = -30_001L), now))
    assertFalse(PassengerPickupDefaults.usable(fix(acc = 0f), now))
    assertFalse(PassengerPickupDefaults.usable(fix(acc = 101f), now))
    assertFalse(PassengerPickupDefaults.usable(fix(acc = Float.NaN), now))
    assertFalse(PassengerPickupDefaults.usable(fix(lat = Double.NaN), now))
    assertFalse(PassengerPickupDefaults.usable(fix(lat = 91.0), now))
    assertFalse(PassengerPickupDefaults.usable(fix(lng = 181.0), now))
  }

  @Test
  fun staleFirstFixIsNotALock() {
    val stale = fix(ageMs = 300_000L)
    assertFalse(PassengerPickupDefaults.shouldReplace(null, stale, false, now))
    val applied = PickupFix(30.70, 111.29, 20f, now - 200_000L)
    assertTrue(PassengerPickupDefaults.shouldReplace(applied, fix(acc = 50f), false, now))
  }

  @Test
  fun onlyClearlyBetterFreshFixReplaces() {
    val applied = fix(acc = 60f, ageMs = 5_000L)
    assertTrue(PassengerPickupDefaults.shouldReplace(applied, fix(acc = 15f), false, now))
    // Small gains or the same fix again do not trigger another reverse lookup.
    assertFalse(PassengerPickupDefaults.shouldReplace(applied, fix(acc = 55f), false, now))
    assertFalse(PassengerPickupDefaults.shouldReplace(applied, applied, false, now))
    // Older fixes never replace newer ones.
    assertFalse(PassengerPickupDefaults.shouldReplace(applied, fix(acc = 5f, ageMs = 10_000L), false, now))
  }

  @Test
  fun aNewerFarFixCorrectsADriftedFirstFixWithoutWaitingForItToAge() {
    // WB-20261103 #3: first fix in California, then a real fix in Yichang at the same accuracy.
    val drifted = PickupFix(38.50, -122.80, 5f, now - 20_000L)
    val real = PickupFix(30.6920, 111.2865, 5f, now - 1_000L)
    assertTrue(PassengerPickupDefaults.moved(drifted, real))
    assertTrue(PassengerPickupDefaults.shouldReplace(drifted, real, false, now))
    // An older far fix never replaces a newer one, and a manual pickup is never overwritten.
    assertFalse(PassengerPickupDefaults.shouldReplace(real, drifted.copy(time = now - 30_000L), false, now))
    assertFalse(PassengerPickupDefaults.shouldReplace(drifted, real, true, now))
  }

  @Test
  fun jitterWithinAccuracyDoesNotRefillThePickup() {
    val applied = fix(acc = 30f, ageMs = 5_000L)
    // ~150 m away with 30 m + 30 m accuracy: below 200 m + both radii.
    val nearby = fix(acc = 30f, lat = 30.70135)
    assertFalse(PassengerPickupDefaults.moved(applied, nearby))
    assertFalse(PassengerPickupDefaults.shouldReplace(applied, nearby, false, now))
  }

  @Test
  fun tripLimitIsOnlyFlaggedWhenCertainlyOver300Km() {
    // California pickup → 宜昌东站: far over the limit.
    val far = PassengerPickupDefaults.straightTripKm("38.50", "-122.80", "30.6648", "111.3674")
    assertTrue(PassengerPickupDefaults.tripTooFar(far))
    // A normal city trip, and unknown or zero points, are never flagged on the device.
    assertFalse(PassengerPickupDefaults.tripTooFar(PassengerPickupDefaults.straightTripKm("30.6920", "111.2865", "30.6648", "111.3674")))
    assertNull(PassengerPickupDefaults.straightTripKm("", "111.2865", "30.6648", "111.3674"))
    assertNull(PassengerPickupDefaults.straightTripKm("0", "0", "30.6648", "111.3674"))
    assertFalse(PassengerPickupDefaults.tripTooFar(null))
    // Just over 300 km is left to the server's own check.
    assertFalse(PassengerPickupDefaults.tripTooFar(300.5))
    assertTrue(PassengerPickupDefaults.tripTooFar(301.5))
  }

  @Test
  fun retryOnlyAcceptsAFixNewerThanTheOneBeingRetried() {
    val retried = fix(ageMs = 10_000L)
    assertFalse(PassengerPickupDefaults.freshAfter(retried, retried.time))
    assertFalse(PassengerPickupDefaults.freshAfter(null, 0L))
    assertTrue(PassengerPickupDefaults.freshAfter(fix(ageMs = 1_000L), retried.time))
  }

  @Test
  fun manualChoiceAlwaysWins() {
    assertFalse(PassengerPickupDefaults.shouldReplace(null, fix(), true, now))
    assertFalse(PassengerPickupDefaults.acceptLookup("t", "t", true, true))
    assertFalse(PassengerPickupDefaults.acceptLookup("t", "t", false, false))
    assertFalse(PassengerPickupDefaults.acceptLookup("old", "new", false, true))
    assertFalse(PassengerPickupDefaults.acceptLookup("", "", false, true))
    assertTrue(PassengerPickupDefaults.acceptLookup("t", "t", false, true))
  }

  @Test
  fun lookupIsBoundToAccountGenerationAndFix() {
    val f = fix().token()
    val requested = PassengerPickupDefaults.lookupToken("p1", 3, f)
    assertTrue(PassengerPickupDefaults.acceptLookup(requested, PassengerPickupDefaults.lookupToken("p1", 3, f), false, true))
    // Another account signed in meanwhile.
    assertFalse(PassengerPickupDefaults.acceptLookup(requested, PassengerPickupDefaults.lookupToken("p2", 3, f), false, true))
    // Logged out: blank owner never matches.
    assertFalse(PassengerPickupDefaults.acceptLookup(requested, PassengerPickupDefaults.lookupToken("", 3, f), false, true))
    // A manual change or a newer accepted fix bumped the generation.
    assertFalse(PassengerPickupDefaults.acceptLookup(requested, PassengerPickupDefaults.lookupToken("p1", 4, f), false, true))
    assertFalse(PassengerPickupDefaults.acceptLookup(requested, PassengerPickupDefaults.lookupToken("p1", 3, fix(acc = 5f).token()), false, true))
    assertEquals("", PassengerPickupDefaults.lookupToken("", 1, f))
    assertEquals("", PassengerPickupDefaults.lookupToken("p1", 1, ""))
  }

  @Test
  fun fixTokenRoundTrips() {
    val f = fix(acc = 12.5f)
    assertEquals(f, PickupFix.parse(f.token()))
    assertNull(PickupFix.parse(""))
    assertNull(PickupFix.parse("1,2,x,4"))
  }

  /** Runs the real debounced flow; [duringWait]/[duringFetch] simulate passenger actions at those moments. */
  private class Run(val gate: PassengerLookupGate = PassengerLookupGate()) {
    val log = mutableListOf<String>()
    var wanted = true
    fun go(duringWait: () -> Unit = {}, duringFetch: () -> Unit = {}, failWith: Exception? = null): Boolean = runBlocking {
      PassengerAddressLookup.debounced(
        gate,
        stillWanted = { wanted },
        wait = { log += "wait"; duringWait() },
        begin = { log += "begin" },
        fetch = { log += "fetch"; duringFetch(); failWith?.let { throw it }; listOf("A") },
        publish = { log += "publish" },
        fail = { log += "fail" },
        end = { log += "end" },
      )
    }
  }

  @Test
  fun waitingSearchIsRetiredByManualActionBeforeAnyRequest() {
    // Manual coordinates / same-name favorite / map pick / cancel / close all call gate.invalidate().
    val r = Run()
    assertFalse(r.go(duringWait = { r.gate.invalidate() }))
    assertEquals(listOf("wait"), r.log)
  }

  @Test
  fun waitingSearchStopsWhenTextChanged() {
    val r = Run()
    assertFalse(r.go(duringWait = { r.wanted = false }))
    assertEquals(listOf("wait"), r.log)
  }

  @Test
  fun inFlightSearchCannotWriteAfterManualAction() {
    val r = Run()
    assertFalse(r.go(duringFetch = { r.gate.invalidate() }))
    assertEquals(listOf("wait", "begin", "fetch"), r.log)
    val f = Run()
    assertFalse(f.go(duringFetch = { f.gate.invalidate() }, failWith = IllegalStateException("x")))
    assertEquals(listOf("wait", "begin", "fetch"), f.log)
  }

  @Test
  fun newerSearchTakesOverOlderOne() {
    val r = Run()
    // A newer run (e.g. retry) takes the gate while the older one waits.
    assertFalse(r.go(duringWait = { r.gate.next() }))
    assertEquals(listOf("wait"), r.log)
  }

  @Test
  fun sameQueryRetryWorksAfterFailureOrCancel() {
    val gate = PassengerLookupGate()
    val failed = Run(gate)
    assertTrue(failed.go(failWith = IllegalStateException("offline")))
    assertEquals(listOf("wait", "begin", "fetch", "fail", "end"), failed.log)
    gate.invalidate() // cancel
    val retry = Run(gate)
    assertTrue(retry.go())
    assertEquals(listOf("wait", "begin", "fetch", "publish", "end"), retry.log)
  }

  @Test
  fun lookupGateOnlyLetsNewestWrite() {
    val gate = PassengerLookupGate()
    val text = gate.next()
    assertTrue(gate.isCurrent(text))
    val map = gate.next()
    assertFalse(gate.isCurrent(text))
    assertTrue(gate.isCurrent(map))
    gate.invalidate() // manual edit, selection, cancel or closing the editor
    assertFalse(gate.isCurrent(map))
  }
}
