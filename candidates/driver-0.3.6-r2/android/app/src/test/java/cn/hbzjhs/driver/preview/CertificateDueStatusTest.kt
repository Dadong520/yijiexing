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
}
