package cn.hbzjhs.passenger

import org.json.JSONObject

object PassengerDefaults {
  fun id(state: JSONObject, kind: String): String {
    val (field, list) = when(kind) {
      "FAVORITE" -> "favoriteId" to "favorites"
      "RIDER" -> "riderId" to "riders"
      "INVOICE_TITLE" -> "invoiceTitleId" to "invoiceTitles"
      else -> return ""
    }
    val id = state.optJSONObject("defaults")?.text(field).orEmpty()
    return id.takeIf { value -> value.isNotBlank() && value != "null" && state.rows(list).any { it.text("id") == value } }.orEmpty()
  }

  // Only a newly opened draft uses the server suggestion. Later refreshes preserve a
  // deliberate choice (including self/empty); a deleted choice safely falls back.
  fun draftSelection(current: String, initialized: Boolean, suggestion: String, available: Set<String>): String {
    val candidate = if (initialized) current else suggestion
    return candidate.takeIf { it in available }.orEmpty()
  }

  fun set(store: PassengerStore, kind: String, id: String?) {
    store.action("defaults.set", JSONObject().put("kind", kind).put("id", id ?: JSONObject.NULL))
  }
}
