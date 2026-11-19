package cn.hbzjhs.driver.preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryFreshnessTest {
    @Test fun onlineRetryRequiresAFixSampledDuringThisRequest() {
        val requestedAt = 8_000_000_000L
        val now = requestedAt + 10_000_000_000L
        assertFalse(RetryFreshness.isFresh(0L, requestedAt, now))
        assertFalse(RetryFreshness.isFresh(requestedAt - 1L, requestedAt, now))
        assertTrue(RetryFreshness.isFresh(requestedAt, requestedAt, now))
        assertTrue(RetryFreshness.isFresh(now, requestedAt, now))
        assertFalse(RetryFreshness.isFresh(now + 1L, requestedAt, now))
    }
}
