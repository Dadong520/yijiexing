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
  @Test fun orderInvoiceFlagOverridesTheCurrentProfileCityInBothDirections() {
    fun state(enabled: Boolean) = JSONObject().put("operations",JSONObject().put("passengerFeatures",JSONObject().put("features",JSONObject().put("invoice",enabled))))
    assertTrue(PassengerOperations.invoiceEnabled(state(false),JSONObject().put("city","A").put("invoiceApplicationEnabled",true)))
    assertFalse(PassengerOperations.invoiceEnabled(state(true),JSONObject().put("city","A").put("invoiceApplicationEnabled",false)))
  }
  @Test fun legacyInvoiceFlagFallsBackOnlyWhenAbsentAndDoesNotTrustMalformedValues() {
    val closed=JSONObject().put("operations",JSONObject().put("passengerFeatures",JSONObject().put("features",JSONObject().put("invoice",false))))
    assertFalse(PassengerOperations.invoiceEnabled(closed,JSONObject()))
    assertTrue(PassengerOperations.invoiceEnabled(JSONObject(),JSONObject()))
    assertFalse(PassengerOperations.invoiceEnabled(JSONObject(),JSONObject().put("invoiceApplicationEnabled","true")))
    assertFalse(PassengerOperations.invoiceEnabled(JSONObject(),JSONObject().put("invoiceApplicationEnabled",JSONObject.NULL)))
  }

}
