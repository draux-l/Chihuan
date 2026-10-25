package pe.chihuan.app.core.dedup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins the deduplication identity policy: a posted notification is identified by its status-bar key
 * (package, id and tag) together with its post time, so a re-post of the same notification is one
 * identity while a genuinely new post of the same id is not.
 */
class NotificationIdentityTest {

    @Test
    fun identityIsTheStatusBarKeyFollowedByThePostTime() {
        assertEquals("0|pe.chihuan|7|payment|1000|1712345678", NotificationIdentity.of(
            statusBarKey = "0|pe.chihuan|7|payment|1000",
            postTimeMillis = 1_712_345_678L,
        ))
    }

    @Test
    fun sameKeyWithADifferentPostTimeIsADifferentIdentity() {
        val first = NotificationIdentity.of("0|pe.chihuan|7|payment|1000", 1_000L)
        val repost = NotificationIdentity.of("0|pe.chihuan|7|payment|1000", 2_000L)

        assertNotEquals(first, repost)
    }

    @Test
    fun differentKeysWithTheSamePostTimeAreDifferentIdentities() {
        val first = NotificationIdentity.of("0|pe.chihuan|7|payment|1000", 1_000L)
        val second = NotificationIdentity.of("0|pe.chihuan|8|payment|1000", 1_000L)

        assertNotEquals(first, second)
    }

    @Test
    fun identityIsStableForIdenticalInput() {
        assertEquals(
            NotificationIdentity.of("0|pe.chihuan|7|payment|1000", 1_000L),
            NotificationIdentity.of("0|pe.chihuan|7|payment|1000", 1_000L),
        )
    }
}
