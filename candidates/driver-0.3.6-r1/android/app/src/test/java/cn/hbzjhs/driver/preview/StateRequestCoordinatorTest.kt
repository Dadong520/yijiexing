package cn.hbzjhs.driver.preview

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateRequestCoordinatorTest {
    @Test
    fun refreshAndActionExecuteAndCommitInOrder() = runBlocking {
        val coordinator = StateRequestCoordinator()
        val epoch = coordinator.snapshot()
        val refreshEntered = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var state = "initial"

        val refresh = launch(start = CoroutineStart.UNDISPATCHED) {
            assertTrue(coordinator.runIfCurrent(epoch) {
                events += "refresh:start"
                refreshEntered.complete(Unit)
                releaseRefresh.await()
                assertTrue(coordinator.commitIfCurrent(epoch) {
                    state = "A"
                    events += "refresh:commit:A"
                })
            })
        }
        refreshEntered.await()
        val action = launch(start = CoroutineStart.UNDISPATCHED) {
            assertTrue(coordinator.runIfCurrent(epoch) {
                events += "action:start"
                assertTrue(coordinator.commitIfCurrent(epoch) {
                    state = "B"
                    events += "action:commit:B"
                })
            })
        }

        // The action has attempted to acquire the lock and is waiting behind the refresh.
        assertEquals(listOf("refresh:start"), events)
        releaseRefresh.complete(Unit)
        refresh.join()
        action.join()
        assertEquals("B", state)
        assertEquals(
            listOf("refresh:start", "refresh:commit:A", "action:start", "action:commit:B"),
            events,
        )
    }

    @Test
    fun invalidatedInflightResultAndCallbackAreRejected() = runBlocking {
        val coordinator = StateRequestCoordinator()
        val oldEpoch = coordinator.snapshot()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var state = "old"
        var callbacks = 0
        var requestStillCurrent = true
        var committed = true
        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            requestStillCurrent = coordinator.runIfCurrent(oldEpoch) {
                entered.complete(Unit)
                release.await()
                committed = coordinator.commitIfCurrent(oldEpoch) {
                    state = "stale result"
                    callbacks++
                }
            }
        }
        entered.await()
        val newEpoch = coordinator.invalidate()
        assertTrue(coordinator.commitIfCurrent(newEpoch) { state = "new session" })
        release.complete(Unit)
        request.join()

        assertFalse(committed)
        assertFalse(requestStillCurrent)
        assertFalse(coordinator.isCurrent(oldEpoch))
        assertTrue(coordinator.isCurrent(newEpoch))
        assertEquals("new session", state)
        assertEquals(0, callbacks)
    }

    @Test
    fun queuedOldEpochDoesNotExecuteAfterInvalidation() = runBlocking {
        val coordinator = StateRequestCoordinator()
        val oldEpoch = coordinator.snapshot()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var queuedExecuted = false
        var queuedCurrent = true
        val inflight = launch(start = CoroutineStart.UNDISPATCHED) {
            assertFalse(coordinator.runIfCurrent(oldEpoch) {
                entered.complete(Unit)
                release.await()
            })
        }
        entered.await()
        val queued = launch(start = CoroutineStart.UNDISPATCHED) {
            queuedCurrent = coordinator.runIfCurrent(oldEpoch) { queuedExecuted = true }
        }
        val newEpoch = coordinator.invalidate()
        release.complete(Unit)
        inflight.join()
        queued.join()

        assertFalse(queuedExecuted)
        assertFalse(queuedCurrent)
        var newExecuted = false
        assertTrue(coordinator.runIfCurrent(newEpoch) { newExecuted = true })
        assertTrue(newExecuted)
    }

    @Test
    fun staleUnauthorizedAndCleanupCannotClearNewSession() = runBlocking {
        val coordinator = StateRequestCoordinator()
        val oldEpoch = coordinator.snapshot()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var session = "session A"
        var loading = false
        var error: String? = null
        var failureCallbacks = 0
        val oldRequest = launch(start = CoroutineStart.UNDISPATCHED) {
            assertFalse(coordinator.runIfCurrent(oldEpoch) {
                entered.complete(Unit)
                release.await()
                // Model the old request's 401 handler and its finally cleanup separately.
                assertFalse(coordinator.commitIfCurrent(oldEpoch) {
                    session = ""
                    error = "expired session A"
                    failureCallbacks++
                })
                assertFalse(coordinator.commitIfCurrent(oldEpoch) { loading = false })
            })
        }
        entered.await()
        val newEpoch = coordinator.invalidate()
        assertTrue(coordinator.commitIfCurrent(newEpoch) {
            session = "session B"
            loading = true
        })
        release.complete(Unit)
        oldRequest.join()

        assertEquals("session B", session)
        assertTrue(loading)
        assertEquals(null, error)
        assertEquals(0, failureCallbacks)
    }

    @Test
    fun exceptionReleasesRequestLockAndDoesNotInvalidateEpoch() = runBlocking {
        val coordinator = StateRequestCoordinator()
        val epoch = coordinator.snapshot()
        val failure = runCatching {
            coordinator.runIfCurrent(epoch) { throw IllegalStateException("synthetic failure") }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("synthetic failure", failure?.message)
        var nextCommitted = false
        assertTrue(coordinator.runIfCurrent(epoch) {
            assertTrue(coordinator.commitIfCurrent(epoch) { nextCommitted = true })
        })
        assertTrue(nextCommitted)
        assertEquals(epoch, coordinator.snapshot())
    }
    @Test
    fun cancellingQueuedRequestCleansItsLoadingWithoutReleasingInflightLock() = runBlocking {
        val coordinator = StateRequestCoordinator()
        val epoch = coordinator.snapshot()
        val release = CompletableDeferred<Unit>()
        var busy = 1
        var queuedExecuted = false
        val inflight = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.runIfCurrent(epoch) { release.await() }
        }
        busy++
        val queued = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                coordinator.runIfCurrent(epoch) { queuedExecuted = true }
            } finally {
                coordinator.commitIfCurrent(epoch) { busy-- }
            }
        }
        queued.cancel()
        queued.join()
        assertEquals(1, busy)
        assertFalse(queuedExecuted)
        assertFalse(inflight.isCompleted)
        release.complete(Unit)
        inflight.join()
    }
}
