package cn.hbzjhs.passenger

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PassengerDefaultsTest {
  @Test fun oldStateDoesNotTurnTheFirstRecordIntoADefault() {
    val state = JSONObject("""{"favorites":[{"id":"A","isDefault":true}],"riders":[{"id":"R"}]}""")
    assertEquals("", PassengerDefaults.id(state, "FAVORITE"))
    assertEquals("", PassengerDefaults.id(state, "RIDER"))
  }
  @Test fun currentTopLevelReferenceWinsOverStaleRowFlags() {
    val state = JSONObject("""{"defaults":{"favoriteId":"B","riderId":"R","invoiceTitleId":null},"favorites":[{"id":"A","isDefault":true},{"id":"B","isDefault":false}],"riders":[{"id":"R"}],"invoiceTitles":[{"id":"T"}]}""")
    assertEquals("B", PassengerDefaults.id(state, "FAVORITE"))
    assertEquals("R", PassengerDefaults.id(state, "RIDER"))
    assertEquals("", PassengerDefaults.id(state, "INVOICE_TITLE"))
  }
  @Test fun removedOrOtherAccountReferencesAreNeverSelected() {
    val state = JSONObject("""{"passenger":{"id":"new-account"},"defaults":{"favoriteId":"old-account-place","riderId":"removed"},"favorites":[{"id":"new-place"}],"riders":[]}""")
    assertEquals("", PassengerDefaults.id(state, "FAVORITE"))
    assertEquals("", PassengerDefaults.id(state, "RIDER"))
  }
  @Test fun initialSuggestionCannotOverwriteUserChoiceOrSelfOnRefresh() {
    val available = setOf("default", "chosen", "changed-default")
    assertEquals("default", PassengerDefaults.draftSelection("", false, "default", available))
    assertEquals("chosen", PassengerDefaults.draftSelection("chosen", true, "changed-default", available))
    assertEquals("", PassengerDefaults.draftSelection("", true, "changed-default", available))
    assertEquals("", PassengerDefaults.draftSelection("", false, "missing", available))
  }
  @Test fun deletedSelectionFallsBackWithoutChoosingTheNextDefault() {
    assertEquals("", PassengerDefaults.draftSelection("deleted", true, "next", setOf("next")))
  }
}
