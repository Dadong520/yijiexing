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
                forceReport = false,
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
    fun preGrabResyncTriggersOnlyWhenLocalFixIsFreshAndSyncIsOlderThan15s() {
        assertTrue(
            LocationUploadPolicy.needsPreGrabResync(
                latestAccuracyM = 18f,
                latestElapsedMs = 200_000L,
                lastSyncedElapsedMs = 180_000L,
                lastSyncedAccuracyM = 18f,
                nowElapsedMs = 205_000L,
            )
        )
        assertFalse(
            LocationUploadPolicy.needsPreGrabResync(
                latestAccuracyM = 18f,
                latestElapsedMs = 200_000L,
                lastSyncedElapsedMs = 200_000L,
                lastSyncedAccuracyM = 18f,
                nowElapsedMs = 205_000L,
            )
        )
        assertFalse(
            LocationUploadPolicy.needsPreGrabResync(
                latestAccuracyM = 18f,
                latestElapsedMs = 50_000L,
                lastSyncedElapsedMs = 50_000L,
                lastSyncedAccuracyM = 18f,
                nowElapsedMs = 200_000L,
            )
        )
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
