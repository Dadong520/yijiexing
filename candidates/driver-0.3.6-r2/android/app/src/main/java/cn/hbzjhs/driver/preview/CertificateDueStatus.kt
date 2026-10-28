package cn.hbzjhs.driver.preview

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.json.JSONObject

/** Presentation only: the backend decides whether new orders may be accepted. */
internal object CertificateDueStatus {
    fun today(serverTime: String = ""): LocalDate =
        runCatching { OffsetDateTime.parse(serverTime).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDate() }
            .getOrElse { LocalDate.now(ZoneId.of("Asia/Shanghai")) }

    fun daysRemaining(validUntil: String, today: LocalDate): Long? =
        runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(validUntil)) }.getOrNull()

    fun canUpdate(cert: JSONObject, today: LocalDate): Boolean =
        cert.optJSONObject("pending") == null &&
            if (cert.has("canUpdate")) cert.optBoolean("canUpdate", false)
            else canUpdate(cert.optString("status"), cert.optString("validUntil"), today)

    fun canUpdate(status: String, validUntil: String, today: LocalDate): Boolean =
        when (status.uppercase()) {
            "PENDING", "SUBMITTED", "3" -> false
            "APPROVED", "VALID", "1" -> daysRemaining(validUntil, today)?.let { it <= 60 } ?: false
            else -> true
        }
}
