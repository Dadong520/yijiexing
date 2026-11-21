package cn.hbzjhs.driver.preview

import org.junit.Assert.*
import org.junit.Test

class LocationUploadPolicyTest {
    @Test
    fun coarseFixDoesNotOverwriteRecentPreciseFixOnServer() {
        val lastSent = 100_000L
        assertFalse(
            LocationUploadPolicy.shouldUpload(
                accuracyM = 350f,
                nowElapsedMs = 120_000L,
                lastUploadedElapsedMs = lastSent,
                lastUploadedAccuracyM = 12f,
                forceReport = true,
            )
        )
    }

    @Test
    fun preciseFixBypassesThrottleWhenPreviousUploadWasCoarseOrMissing() {
        assertTrue(
            LocationUploadPolicy.shouldUpload(
                accuracyM = 15f,
                nowElapsedMs = 102_000L,
                lastUploadedElapsedMs = 100_000L,
                lastUploadedAccuracyM = 280f,
                forceReport = false,
            )
        )
        assertTrue(
            LocationUploadPolicy.shouldUpload(
                accuracyM = 15f,
                nowElapsedMs = 10_000L,
                lastUploadedElapsedMs = 0L,
                lastUploadedAccuracyM = 0f,
                forceReport = false,
            )
        )
    }

    @Test
    fun sampleAgeIsMeasuredFromTheFixNotTheCallback() {
        val now = 205_000L
        assertFalse(LocationUploadPolicy.isRecentSample(0L, now))
        assertFalse(LocationUploadPolicy.isRecentSample(now + 1L, now))
        assertFalse(LocationUploadPolicy.isRecentSample(now - 110_001L, now))
        assertTrue(LocationUploadPolicy.isRecentSample(now - 110_000L, now))
        assertTrue(LocationUploadPolicy.isRecentSample(now, now))
    }

    @Test
    fun staleFixDowngradesStatusTextInsteadOfShowingGreen() {
        val status = LocationUploadPolicy.displayStatus(
            accuracyM = 10f,
            fixElapsedMs = 10_000L,
            nowElapsedMs = 130_000L,
            syncedToServer = true,
        )
        assertFalse(status.startsWith("已定位"))
        assertTrue(status.contains("超时"))
    }
}
