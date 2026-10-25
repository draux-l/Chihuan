package pe.chihuan.app.core.dedup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The in-memory duplicate suppressor is what stops a re-posted notification from being announced
 * twice. Retention is at least ten minutes and the set is bounded, so it can never grow without
 * limit while the process lives.
 */
class DuplicateSuppressorTest {

    private val retentionMillis = 10 * 60 * 1000L

    @Test
    fun firstOccurrenceIsNotSuppressed() {
        val suppressor = DuplicateSuppressor()

        assertFalse(suppressor.shouldSuppress("identity", nowMillis = 0L))
    }

    @Test
    fun immediateRepostOfTheSameIdentityIsSuppressed() {
        val suppressor = DuplicateSuppressor()
        suppressor.shouldSuppress("identity", nowMillis = 0L)

        assertTrue(suppressor.shouldSuppress("identity", nowMillis = 500L))
    }

    @Test
    fun sameKeyWithANewPostTimeIsAnnouncedAgain() {
        val suppressor = DuplicateSuppressor(
            retentionMillis = retentionMillis,
        )
        val first = NotificationIdentity.of("0|pe.chihuan|7|payment|1000", 1_000L)
        val repost = NotificationIdentity.of("0|pe.chihuan|7|payment|1000", 2_000L)

        suppressor.shouldSuppress(first, nowMillis = 1_000L)

        assertFalse(suppressor.shouldSuppress(repost, nowMillis = 2_000L))
    }

    @Test
    fun aDifferentIdentityIsNotSuppressed() {
        val suppressor = DuplicateSuppressor()
        suppressor.shouldSuppress("first", nowMillis = 0L)

        assertFalse(suppressor.shouldSuppress("second", nowMillis = 100L))
    }

    @Test
    fun identityIsRetainedForTheWholeRetentionWindow() {
        val suppressor = DuplicateSuppressor(retentionMillis = retentionMillis)
        suppressor.shouldSuppress("identity", nowMillis = 0L)

        assertTrue(suppressor.shouldSuppress("identity", nowMillis = retentionMillis))
    }

    @Test
    fun identityExpiresAfterTheRetentionWindow() {
        val suppressor = DuplicateSuppressor(retentionMillis = retentionMillis)
        suppressor.shouldSuppress("identity", nowMillis = 0L)

        assertFalse(suppressor.shouldSuppress("identity", nowMillis = retentionMillis + 1))
    }

    @Test
    fun theSetIsBoundedAndEvictsTheOldestIdentityFirst() {
        val suppressor = DuplicateSuppressor(maxEntries = 2)
        suppressor.shouldSuppress("oldest", nowMillis = 0L)
        suppressor.shouldSuppress("newest", nowMillis = 1L)
        suppressor.shouldSuppress("newer", nowMillis = 2L)

        // "oldest" was evicted to make room, so a re-post of it is treated as new again.
        assertFalse(suppressor.shouldSuppress("oldest", nowMillis = 3L))
        assertTrue(suppressor.shouldSuppress("newer", nowMillis = 4L))
    }

    @Test
    fun duplicateLookupDoesNotExtendRetention() {
        val suppressor = DuplicateSuppressor(retentionMillis = retentionMillis)
        suppressor.shouldSuppress("identity", nowMillis = 0L)
        suppressor.shouldSuppress("identity", nowMillis = retentionMillis / 2)

        // Retention is measured from the first sighting, so the entry is gone at the original bound.
        assertFalse(suppressor.shouldSuppress("identity", nowMillis = retentionMillis + 1))
    }
}
