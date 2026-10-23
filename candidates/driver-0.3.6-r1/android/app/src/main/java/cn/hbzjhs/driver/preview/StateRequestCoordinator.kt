package cn.hbzjhs.driver.preview

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes state requests and rejects results belonging to an invalidated session. */
internal class StateRequestCoordinator {
    private val generationLock = Any()
    private var generation = 0L
    private val requestMutex = Mutex()

    fun snapshot(): Long = synchronized(generationLock) { generation }

    fun invalidate(): Long = synchronized(generationLock) { ++generation }

    fun isCurrent(epoch: Long): Boolean = synchronized(generationLock) { generation == epoch }

    /** The epoch check and every published side effect share the invalidation lock. */
    fun commitIfCurrent(epoch: Long, block: () -> Unit): Boolean =
        synchronized(generationLock) {
            if (generation != epoch) {
                false
            } else {
                block()
                true
            }
        }

    /**
     * Holds the request lock for the entire operation, including the caller's commit.
     * Invalidation may happen while IO suspends, so publish results, callbacks and cleanup
     * through [commitIfCurrent]. A false return also covers invalidation during execution.
     */
    suspend fun runIfCurrent(epoch: Long, block: suspend () -> Unit): Boolean =
        requestMutex.withLock {
            if (!isCurrent(epoch)) {
                false
            } else {
                block()
                isCurrent(epoch)
            }
        }
}
