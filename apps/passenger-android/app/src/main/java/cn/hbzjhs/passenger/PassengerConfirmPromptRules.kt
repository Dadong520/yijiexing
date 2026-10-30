package cn.hbzjhs.passenger

import java.time.Instant
import java.time.OffsetDateTime

/** Plain view of an order's distance request, so the checks below stay free of Android/JSON. */
data class DistanceConsentView(
  val account: String,
  val orderId: String,
  val orderStage: String,
  val consentId: String,
  val status: String,
  val expectedStage: String,
  val expiresAt: String,
)

/** Pure rules behind [PassengerConfirmPrompts]; the server still validates every answer. */
object PassengerConfirmPromptRules {
  /** Null for blank or malformed text, so a missing expiry can never count as "not expired". */
  fun parseInstant(iso: String): Instant? {
    if (iso.isBlank()) return null
    return runCatching { OffsetDateTime.parse(iso).toInstant() }.recoverCatching { Instant.parse(iso) }.getOrNull()
  }

  fun unexpired(expiresAt: String, now: Instant): Boolean = parseInstant(expiresAt)?.isAfter(now) == true

  /** Answerable only while still pending for the same stage and before a valid expiry. */
  fun answerable(c: DistanceConsentView, now: Instant): Boolean =
    c.account.isNotBlank() && c.orderId.isNotBlank() && c.consentId.isNotBlank() &&
      c.status == "PENDING" && c.expectedStage == c.orderStage && unexpired(c.expiresAt, now)

  /**
   * Re-check at tap time against the latest store copy. Returns null when the answer may be sent,
   * otherwise the passenger-facing reason it was not sent.
   */
  fun rejectReason(shown: DistanceConsentView, current: DistanceConsentView?, now: Instant): String? =
    when {
      current == null || current.account != shown.account || current.orderId != shown.orderId ->
        "订单已更新，请在订单页核对后再处理。"
      current.consentId != shown.consentId || current.expectedStage != shown.expectedStage || current.status != "PENDING" ->
        "司机的确认请求已变更，请核对最新请求。"
      current.expectedStage != current.orderStage -> "订单阶段已变化，此确认请求不再适用。"
      !unexpired(current.expiresAt, now) -> "确认请求已过期或时间无效，请等待司机重新申请。"
      else -> null
    }

  /** Prompt identity: one account + one order + one question (consent id or financial kind/version). */
  fun key(account: String, orderId: String, question: String): String = "$account|$orderId|$question"

  /**
   * The first prompt not yet settled for this account. Prompts about the order already open are
   * skipped, except distance requests, which need an answer there too.
   */
  fun <T> pick(prompts: List<T>, key: (T) -> String, orderId: (T) -> String, isConsent: (T) -> Boolean, settled: Set<String>, openOrderId: String): T? =
    prompts.firstOrNull { key(it) !in settled && (isConsent(it) || orderId(it) != openOrderId) }

  /**
   * A submitted answer failed once the store is idle again without the prompt having been settled
   * by the success callback. Only [settled] proves success; idle alone never does.
   */
  fun submitFailed(busy: Boolean, submittingKey: String, settled: Set<String>): Boolean =
    !busy && submittingKey.isNotBlank() && submittingKey !in settled

  /** Settles exactly one prompt; other orders' questions stay pending. Bounded to the newest [limit]. */
  fun settle(settled: List<String>, key: String, limit: Int = 50): List<String> = (settled - key + key).takeLast(limit)
}
