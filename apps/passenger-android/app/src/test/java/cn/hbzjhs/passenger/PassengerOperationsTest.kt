package cn.hbzjhs.passenger

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PassengerOperationsTest {
  @Test fun legacyStateKeepsOriginalFunctionsWithoutInventingConfig() {
    val state=JSONObject()
    listOf("instant","reservation","bidding","invoice").forEach { assertTrue(PassengerOperations.enabled(state,it)) }
    assertFalse(state.has("operations"))
  }
  @Test fun featureRefreshImmediatelyChangesAllowedNewKinds() {
    val switches=JSONObject().put("instant",false).put("reservation",true).put("bidding",false).put("invoice",false)
    val state=JSONObject().put("operations",JSONObject().put("passengerFeatures",JSONObject().put("features",switches)))
    assertFalse(PassengerOperations.kindEnabled(state,"STANDARD"))
    assertTrue(PassengerOperations.kindEnabled(state,"RESERVED"))
    assertFalse(PassengerOperations.kindEnabled(state,"BID"))
    assertFalse(PassengerOperations.enabled(state,"invoice"))
    switches.put("instant",true)
    assertTrue(PassengerOperations.kindEnabled(state,"STANDARD"))
    assertFalse(PassengerOperations.kindEnabled(state,"FORGED"))
  }
}
