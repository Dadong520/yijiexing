package cn.hbzjhs.driver.preview

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class CertificateDueStatusTest {
    private val today = LocalDate.of(2026, 10, 28)

    @Test fun updateWindowIncludesSixtiethDayAndExpiredDocuments() {
        assertFalse(CertificateDueStatus.canUpdate("APPROVED", today.plusDays(61).toString(), today))
        listOf(60L, 59L, 0L, -1L).forEach {
            assertTrue(CertificateDueStatus.canUpdate("APPROVED", today.plusDays(it).toString(), today))
        }
    }

    @Test fun pendingAndLongTermDoNotInventAnExpiry() {
        assertFalse(CertificateDueStatus.canUpdate("PENDING", today.minusDays(1).toString(), today))
        assertFalse(CertificateDueStatus.canUpdate("APPROVED", "LONG_TERM", today))
        assertFalse(CertificateDueStatus.canUpdate("APPROVED", "malformed", today))
        assertNull(CertificateDueStatus.daysRemaining("LONG_TERM", today))
    }

    @Test fun authoritativeServerFlagAndPendingOverrideLocalWindow() {
        val cert=JSONObject().put("status","APPROVED").put("validUntil",today.toString()).put("canUpdate",false)
        assertFalse(CertificateDueStatus.canUpdate(cert,today))
        cert.put("canUpdate",true).put("pending",JSONObject().put("submissionId","review-1"))
        assertFalse(CertificateDueStatus.canUpdate(cert,today))
        cert.remove("pending")
        assertTrue(CertificateDueStatus.canUpdate(cert,today))
    }

    @Test fun businessDateUsesShanghaiRatherThanDeviceZone() {
        assertEquals(today, CertificateDueStatus.today("2026-10-27T16:00:00Z"))
        assertEquals(today.minusDays(1), CertificateDueStatus.today("2026-10-27T15:59:59Z"))
    }

    @Test fun expiredCertificateWithLegacyApprovedStatusNeverShowsHealthy() {
        val expiredCert = JSONObject().put("status", "APPROVED").put("validity", "EXPIRED")
        val validCert = JSONObject().put("status", "APPROVED").put("validity", "VALID")

        val badge = certificateEffectiveBadge(expiredCert)
        assertEquals("已过期", badge.first)
        assertEquals(Danger, badge.second)

        val summary = qualificationSummary(listOf(validCert, expiredCert))
        assertEquals("1项证件已过期（暂停新接单）", summary.first)
        assertEquals(Danger, summary.second)
        assertTrue(summary.third)
    }

    @Test fun expiringPendingAndValidCertificatesReportAccurateSummary() {
        val expiring = JSONObject().put("status", "APPROVED").put("validity", "EXPIRING").put("daysRemaining", 5)
        assertEquals("5天后到期", certificateEffectiveBadge(expiring).first)

        val pendingExpired = JSONObject()
            .put("status", "APPROVED")
            .put("validity", "EXPIRED")
            .put("pending", JSONObject().put("submissionId", "sub-1"))
        assertEquals("已过期 · 审核中", certificateEffectiveBadge(pendingExpired).first)

        val valid = JSONObject().put("status", "APPROVED").put("validity", "LONG_TERM")
        val allGood = qualificationSummary(listOf(valid))
        assertEquals("全部证件有效", allGood.first)
        assertFalse(allGood.third)

        val missingValidity = JSONObject().put("status", "SUBMITTED")
        val fallbackInvalid = qualificationSummary(listOf(missingValidity))
        assertEquals("1项证件待核验或更新", fallbackInvalid.first)
        assertTrue(fallbackInvalid.third)
    }

    @Test fun restingRelocateFixRejectsStaleCacheZeroPointAndNonPositiveAccuracy() {
        val now = 1_700_000_000_000L
        assertTrue(isUsableDriverFix(30.6919, 111.2865, 8.0f, now - 30_000L, now))
        assertTrue(isUsableDriverFix(30.6919, 111.2865, 12.0f, now - 120_000L, now))
        assertFalse(isUsableDriverFix(30.6919, 111.2865, 8.0f, now - 120_001L, now))
        assertFalse(isUsableDriverFix(0.0, 0.0, 5.0f, now, now))
        assertFalse(isUsableDriverFix(30.6919, 111.2865, 0.0f, now, now))
        assertFalse(isUsableDriverFix(30.6919, 111.2865, -3.0f, now, now))
    }
}
