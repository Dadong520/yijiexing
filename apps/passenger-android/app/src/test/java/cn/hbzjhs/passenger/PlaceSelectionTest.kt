package cn.hbzjhs.passenger

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaceSelectionTest {
  // Same shape as the server's historicalLocations rows.
  private fun history(address: String, lat: Any?, lng: Any?, metadata: JSONObject? = null): JSONObject {
    val row = JSONObject().put("address", address)
    if (lat != null) row.put("latitude", lat)
    if (lng != null) row.put("longitude", lng)
    row.put("coordinateMetadata", metadata ?: JSONObject.NULL)
    return row
  }

  @Test
  fun validHistoryRowIsReadyInOneTapWithItsMetadata() {
    val metadata = JSONObject().put("provider", "AMAP").put("system", "GCJ02")
    val resolved = PlaceSelection.resolveRow(history("宜昌东站", 30.6648, 111.3674, metadata))
    assertTrue(resolved is PlaceResolution.Ready)
    val place = (resolved as PlaceResolution.Ready).place
    assertEquals("宜昌东站", place.name)
    assertEquals(30.6648, place.latitude, 0.0)
    assertEquals(111.3674, place.longitude, 0.0)
    assertSame(metadata, place.metadata)
    // Numeric text from older rows is accepted as the same point.
    assertTrue(PlaceSelection.resolveRow(history("宜昌东站", "30.6648", " 111.3674 ")) is PlaceResolution.Ready)
  }

  @Test
  fun unknownOrBrokenPointsAreNeverUsedAndNeverBecomeZero() {
    val missing = listOf(
      history("夷陵广场", null, null),
      history("夷陵广场", JSONObject.NULL, 111.29),
      history("夷陵广场", 0, 0),
      history("夷陵广场", "", "111.29"),
    )
    missing.forEach { row ->
      val resolved = PlaceSelection.resolveRow(row)
      assertEquals(PlaceResolution.SearchByName("夷陵广场", PlaceSelection.MISSING_POINT), resolved)
    }
    val broken = listOf(
      history("夷陵广场", 91.0, 111.29),
      history("夷陵广场", 30.7, -181.0),
      history("夷陵广场", "abc", 111.29),
      history("夷陵广场", "NaN", 111.29),
      history("夷陵广场", "Infinity", 111.29),
    )
    broken.forEach { row ->
      val resolved = PlaceSelection.resolveRow(row)
      assertEquals(PlaceResolution.SearchByName("夷陵广场", PlaceSelection.BROKEN_POINT), resolved)
    }
    assertTrue(PlaceSelection.resolveRow(history(" ", 30.7, 111.29)) is PlaceResolution.Unusable)
    assertTrue(PlaceSelection.resolveRow(JSONObject().put("address", JSONObject.NULL)) is PlaceResolution.Unusable)
  }

  @Test
  fun pickerListsKeepUnknownHistoryVisibleAndShowRepeatsOnce() {
    val favorite = JSONObject().put("address", "公司").put("latitude", 30.70).put("longitude", 111.29).put("isDefault", true)
    val rows = PlaceSelection.pickerRows(
      favorites = listOf(favorite),
      history = listOf(
        history("公司", 30.70, 111.29),
        history("三峡游客中心", 30.6778, 111.2944),
        history("三峡游客中心", 30.6778, 111.2944),
        history("老地址", null, null),
        history("", 30.1, 111.1),
      ),
      presets = listOf(PickedPlace("宜昌东站 · 出租车上客区", 30.6648, 111.3674)),
    )
    assertEquals(listOf("公司", "宜昌东站 · 出租车上客区", "三峡游客中心", "老地址"), rows.map { it.name })
    assertEquals(listOf(PlaceGroup.COMMON, PlaceGroup.COMMON, PlaceGroup.HISTORY, PlaceGroup.HISTORY), rows.map { it.group })
    assertTrue(rows[0].isDefault)
    assertTrue(rows[3].resolution is PlaceResolution.SearchByName)
  }

  @Test
  fun aSecondTapNeverCommitsAgain() {
    val session = PlacePickSession()
    val first = PlaceSelection.resolve("宜昌东站", 30.6648, 111.3674)
    val second = PlaceSelection.resolve("夷陵广场", 30.7040, 111.2929)
    assertEquals("宜昌东站", session.commit(first)?.name)
    assertNull(session.commit(first))
    assertNull(session.commit(second))
    assertNull(session.beginLookup())
  }

  @Test
  fun anUnusableTapDoesNotUseUpTheSession() {
    val session = PlacePickSession()
    assertNull(session.commit(PlaceSelection.resolve("老地址", null, null)))
    assertFalse(session.committed)
    assertEquals("夷陵广场", session.commit(PlaceSelection.resolve("夷陵广场", 30.7040, 111.2929))?.name)
  }

  @Test
  fun lateMapLookupCannotOverwriteANewerChoice() {
    val session = PlacePickSession()
    val mapToken = session.beginLookup()!!
    // While the reverse lookup is running the passenger taps a history row.
    assertNotNull(session.commit(PlaceSelection.resolveRow(history("三峡游客中心", 30.6778, 111.2944))))
    assertNull(session.commitLookup(mapToken, PlaceSelection.resolve("地图上的点", 30.71, 111.30)))
    assertFalse(session.isCurrent(mapToken))
  }

  @Test
  fun onlyTheNewestMapLookupCommitsAndOnlyWithAValidPoint() {
    val session = PlacePickSession()
    val older = session.beginLookup()!!
    val newer = session.beginLookup()!!
    assertNull(session.commitLookup(older, PlaceSelection.resolve("旧点", 30.70, 111.29)))
    // A lookup that came back without a usable point keeps the page open for another try.
    assertNull(session.commitLookup(newer, PlaceSelection.resolve("新点", Double.NaN, 111.29)))
    assertFalse(session.committed)
    val retry = session.beginLookup()!!
    assertEquals("新点", session.commitLookup(retry, PlaceSelection.resolve("新点", 30.71, 111.30))?.name)
  }

  @Test
  fun pickDuringTheSearchWaitRetiresThatSearch() = runBlocking {
    val session = PlacePickSession()
    var published = false
    var started = false
    val wrote = PassengerAddressLookup.debounced(
      session.gate,
      stillWanted = { true },
      // The passenger taps a history row inside the 400ms wait.
      wait = { session.commit(PlaceSelection.resolve("三峡游客中心", 30.6778, 111.2944)) },
      begin = { started = true },
      fetch = { listOf("迟到的结果") },
      publish = { published = true },
      fail = { published = true },
      end = {},
    )
    assertFalse(wrote)
    assertFalse(started)
    assertFalse(published)
  }

  @Test
  fun stopsFillInOrderUpToTwo() {
    assertEquals(1, PlaceSelection.nextStopSlot("", ""))
    assertEquals(2, PlaceSelection.nextStopSlot("夷陵广场", ""))
    assertNull(PlaceSelection.nextStopSlot("夷陵广场", "三峡游客中心"))
  }
}
