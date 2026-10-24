package cn.hbzjhs.passenger

import android.content.Context
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class SafetyContact(val id: String, val name: String, val phone: String)

/** App-private, passenger-ID scoped contacts. No anonymous or legacy shared-key fallback. */
class PassengerLocalExtras(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences("passenger_safety_v1", Context.MODE_PRIVATE)
  private fun key(owner: String): String {
    require(owner.isNotBlank() && owner != "null") { "请先登录乘客账号" }
    val hash = MessageDigest.getInstance("SHA-256").digest(owner.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it.toInt() and 255) }
    return "contacts_$hash"
  }
  fun contacts(owner: String): List<SafetyContact> {
    val scopedKey = key(owner)
    return runCatching {
      val rows = JSONArray(preferences.getString(scopedKey, "[]"))
      (0 until rows.length()).map { rows.getJSONObject(it) }.map {
        SafetyContact(it.getString("id"), it.getString("name"), it.getString("phone"))
      }
    }.getOrDefault(emptyList())
  }
  fun save(owner: String, current: List<SafetyContact>, id: String?, name: String, phone: String) {
    val cleanName = name.trim()
    val cleanPhone = phone.trim().replace(" ", "").replace("-", "")
    require(cleanName.length in 1..30) { "联系人姓名需为1至30字" }
    require(Regex("\\+?[0-9]{5,20}").matches(cleanPhone)) { "请输入有效联系电话" }
    require(id != null || current.size < 3) { "最多保存3位安全联系人" }
    val item = SafetyContact(id ?: UUID.randomUUID().toString(), cleanName, cleanPhone)
    write(owner, current.filterNot { it.id == item.id } + item)
  }
  fun remove(owner: String, current: List<SafetyContact>, id: String) = write(owner, current.filterNot { it.id == id })
  private fun write(owner: String, contacts: List<SafetyContact>) {
    val rows = JSONArray()
    contacts.forEach { rows.put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone)) }
    check(preferences.edit().putString(key(owner), rows.toString()).commit()) { "本机保存失败，请重试" }
  }
}
