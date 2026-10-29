package cn.hbzjhs.passenger

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
