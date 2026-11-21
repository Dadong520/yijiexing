package cn.hbzjhs.driver.preview

/**
 * 司机端定位上传与保活策略纯规则：
 * - 精准定位（0 < accuracy <= 100m）优先：不被粗定位覆盖服务端已同步的好定位；
 * - 若之前尚未上报过或上次是粗定位，精准定位到来时立即突破节流上报；
 * - 超过 110s 未同步有效精准定位时，界面诚实降级提示，避免误导司机以为当前仍可直接抢单。
 */
internal object LocationUploadPolicy {
    const val MAX_USABLE_ACCURACY_M = 100f
    const val FRESH_WINDOW_MS = 110_000L
    const val KEEPALIVE_INTERVAL_MS = 45_000L
    const val THROTTLE_MS = 14_500L
    const val STALE_GOOD_PROTECT_MS = 90_000L

    fun isUsableAccuracy(accuracyM: Float): Boolean =
        accuracyM.isFinite() && accuracyM > 0f && accuracyM <= MAX_USABLE_ACCURACY_M

    fun isRecentSample(sampleElapsedMs: Long, nowElapsedMs: Long): Boolean =
        sampleElapsedMs > 0L && sampleElapsedMs <= nowElapsedMs &&
            (nowElapsedMs - sampleElapsedMs) <= FRESH_WINDOW_MS

    fun shouldUpload(
        accuracyM: Float,
        nowElapsedMs: Long,
        lastUploadedElapsedMs: Long,
        lastUploadedAccuracyM: Float,
        forceReport: Boolean,
    ): Boolean {
        val usable = isUsableAccuracy(accuracyM)
        val lastUsable = lastUploadedElapsedMs > 0L && isUsableAccuracy(lastUploadedAccuracyM)
        if (!usable && lastUsable && (nowElapsedMs - lastUploadedElapsedMs) < STALE_GOOD_PROTECT_MS) {
            return false
        }
        if (forceReport) return true
        if (lastUploadedElapsedMs <= 0L) return true
        if (usable && !lastUsable) return true
        return (nowElapsedMs - lastUploadedElapsedMs) >= THROTTLE_MS
    }

    fun displayStatus(
        accuracyM: Float,
        fixElapsedMs: Long,
        nowElapsedMs: Long,
        syncedToServer: Boolean,
    ): String {
        if (!isRecentSample(fixElapsedMs, nowElapsedMs)) {
            return "定位已超时 · 请点击重新定位"
        }
        if (!isUsableAccuracy(accuracyM)) {
            return "定位精度不足（约 ${accuracyM.toInt()} 米）· 请移至开阔处"
        }
        return if (syncedToServer) "已定位 · 精度约 ${accuracyM.toInt()} 米"
        else "已定位 · 正在同步至服务端"
    }
}
