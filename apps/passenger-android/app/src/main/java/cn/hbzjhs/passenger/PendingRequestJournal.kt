package cn.hbzjhs.passenger

import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

class PendingStorageException(message: String, cause: Throwable) : Exception(message, cause)

/**
 * Production request journal shared by every passenger action, including complaint attachments.
 *
 * A transport exception never proves that an earlier submission did not commit. Consequently the
 * record is removed only after transport returns a confirmed successful response, never on 4xx,
 * 5xx, cancellation, or a missing response. Writes happen before changing in-memory state.
 */
class PendingRequestJournal(
  load: () -> String,
  private val save: (String) -> Unit,
  private val createId: () -> String = { UUID.randomUUID().toString() },
) {
  private var entries: Map<String, String> = readEntries(load)

  suspend fun <T> execute(
    accountId: String,
    action: String,
    payload: String,
    currentTokenForLegacyMigration: String? = null,
    transport: suspend (requestId: String) -> T,
  ): T {
    require(accountId.isNotBlank()) { "乘客资料尚未同步，请先刷新后再提交" }
    val signature = signature(accountId, action, payload)
    val requestId = prepare(signature, action, payload, currentTokenForLegacyMigration)
    // Deliberately no catch/finally that removes the record: 401/403 can arrive after an uncertain
    // earlier success, just like a timeout or 503. All retries keep the durable identity.
    val result = transport(requestId)
    if (entries[signature] == requestId) {
      val next = entries.toMutableMap().apply { remove(signature) }
      persist(next, "服务已返回成功，但本机重试记录未能更新。请先刷新核对；再次提交仍会复用原请求编号。")
    }
    return result
  }

  private fun prepare(
    stableSignature: String,
    action: String,
    payload: String,
    currentToken: String?,
  ): String {
    entries[stableSignature]?.let {
      return it
    }
    val legacySignature =
      currentToken?.takeIf { it.isNotBlank() }?.let { signature(it, action, payload) }
    val legacyId = legacySignature?.let { entries[it] }
    val id = legacyId ?: createId()
    val next =
      entries.toMutableMap().apply {
        put(stableSignature, id)
        if (legacyId != null && legacySignature != stableSignature) remove(legacySignature)
      }
    persist(next, "无法安全保存重试记录，本次请求尚未发送。请检查设备存储后重试。")
    return id
  }

  private fun persist(next: Map<String, String>, message: String) {
    try {
      save(JSONObject(next).toString())
    } catch (failure: Exception) {
      throw PendingStorageException(message, failure)
    }
    entries = next
  }

  private fun signature(account: String, action: String, payload: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest("$account|$action|$payload".toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }

  private fun readEntries(load: () -> String): Map<String, String> =
    try {
      val serialized = load()
      if (serialized.isBlank()) emptyMap()
      else {
        val json = JSONObject(serialized)
        json.keys().asSequence().associateWith { key ->
          require(Regex("[a-f0-9]{64}").matches(key))
          json.getString(key).also { require(it.isNotBlank()) }
        }
      }
    } catch (failure: Exception) {
      throw PendingStorageException("无法读取已有重试记录，已阻止提交。请保留应用数据并检查设备存储。", failure)
    }
}
