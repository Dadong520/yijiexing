package cn.hbzjhs.driver.preview

/**
 * Decides when the app may open external navigation by itself. Only an actual move into a new
 * pickup or trip leg counts, and each account/order/leg opens at most once (the memory is persisted
 * by the caller, so restarts, retries, replayed responses or returning from the navigation app never
 * jump again). Manual navigation is never affected.
 */
object NavigationAutoRules {
    const val MAX_REMEMBERED = 200

    /** True only for real leg starts: accept/start pickup -> PICKUP, passenger boarded -> TRIP. */
    fun isNewLeg(before: Stage?, after: Stage?): Boolean =
        when (after) {
            Stage.PICKUP -> before == Stage.AVAILABLE || before == Stage.RESERVED
            Stage.TRIP -> before == Stage.ARRIVED
            else -> false
        }

    fun legKey(account: String, orderId: String, leg: Stage): String = "$account|$orderId|${leg.name}"

    /**
     * Returns the updated memory when a genuine new leg may open navigation now, or null when it
     * must not (not a new leg, missing identity, or already opened for this account/order/leg).
     */
    fun claim(memory: List<String>, account: String, orderId: String, before: Stage?, after: Stage?): List<String>? {
        if (account.isBlank() || orderId.isBlank() || after == null || !isNewLeg(before, after)) return null
        val key = legKey(account, orderId, after)
        if (key in memory) return null
        return (memory + key).takeLast(MAX_REMEMBERED)
    }

    /**
     * A snapshot may serve as observer baseline or evidence only when it is a completed, connected
     * sync for a known account (never the empty start state or a session being switched).
     */
    fun snapshotUsable(loggedIn: Boolean, initialized: Boolean, connected: Boolean, account: String): Boolean =
        loggedIn && initialized && connected && account.isNotBlank()

    /**
     * Orders that moved ARRIVED -> TRIP between two usable snapshots of the same account (e.g. the
     * passenger agreed to the boarding request on their phone). No baseline means no events.
     */
    fun boardedSince(baseline: Map<String, Stage>?, current: Map<String, Stage>): List<String> =
        if (baseline == null) emptyList()
        else current.filter { (id, stage) -> baseline[id] == Stage.ARRIVED && stage == Stage.TRIP }.keys.toList()
}
