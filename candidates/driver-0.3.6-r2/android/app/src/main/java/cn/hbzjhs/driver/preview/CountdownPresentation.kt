package cn.hbzjhs.driver.preview

/**
 * Display-only countdown text for the two separate timers (UIFC-20261102-01). Nothing here starts,
 * extends or re-anchors a window: callers pass the remaining time they read from the existing owner
 * (the 3s reminder book, or GrabActions' server-anchored 5s deadline); null means unknown.
 */
object CountdownPresentation {
    /** Whole seconds left, rounded up so "0" only appears once the time is really over; null = unknown. */
    fun ceilSeconds(remainingMs: Long?): Int? {
        if (remainingMs == null) return null
        if (remainingMs <= 0L) return 0
        return ((remainingMs + 999L) / 1000L).toInt()
    }

    /** 0..1 share of [totalMs] still left; unknown or non-positive total gives 0. */
    fun fraction(remainingMs: Long?, totalMs: Long): Float {
        if (remainingMs == null || totalMs <= 0L) return 0f
        return (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
    }

    /** What the 5s competition area of one order shows. */
    sealed class Competition {
        /** BID/NEGOTIATED/private rows or rows without competition: no countdown at all. */
        object Hidden : Competition()
        /** READY: the window opens on the server at the first valid grab. */
        object NotStarted : Competition()
        /** OPEN with a measured local deadline still ahead. */
        data class Running(val seconds: Int, val fraction: Float) : Competition()
        /** OPEN but the deadline passed, or the round is closed: wait for the server, no grabbing. */
        object Closed : Competition()
        /** OPEN without a measured anchor (e.g. just reset): never invent a number. */
        object Unknown : Competition()
    }

    const val COMPETITION_WINDOW_MS = 5_000L

    fun competition(kind: String, status: String?, remainingMs: Long?): Competition {
        if (kind != "STANDARD" && kind != "RESERVED") return Competition.Hidden
        return when (status) {
            null -> Competition.Hidden
            "READY" -> Competition.NotStarted
            "OPEN" -> when (val s = ceilSeconds(remainingMs)) {
                null -> Competition.Unknown
                0 -> Competition.Closed
                else -> Competition.Running(s, fraction(remainingMs, COMPETITION_WINDOW_MS))
            }
            else -> Competition.Closed
        }
    }

    fun competitionText(c: Competition): String = when (c) {
        Competition.Hidden -> ""
        Competition.NotStarted -> "首位司机抢单后开始5秒倒计时"
        is Competition.Running -> "竞抢剩余 ${c.seconds} 秒"
        Competition.Closed -> "已截止，等待服务器派单结果"
        Competition.Unknown -> "剩余时间同步中，等待服务器最新状态"
    }

    /** Text of the 3s reminder's own countdown; it only ever counts down to closing. */
    fun reminderText(remainingMs: Long?): String {
        val s = ceilSeconds(remainingMs) ?: return ""
        return if (s <= 0) "提醒即将收起" else "${s} 秒后收起"
    }
}
