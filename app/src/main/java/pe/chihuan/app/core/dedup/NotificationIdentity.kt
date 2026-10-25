package pe.chihuan.app.core.dedup

/**
 * The identity used to recognise a re-posted notification.
 *
 * A status-bar key already contains the posting package, the notification id and its tag, so pairing
 * it with the post time distinguishes the two cases the spec cares about:
 *
 * - the system re-posts the *same* notification (same key, same post time) and it must not be
 *   announced twice;
 * - a genuinely new notification with a reused id gets a new post time and *is* announced.
 *
 * The result is opaque: it is only ever compared for equality, never parsed back.
 */
object NotificationIdentity {

    /** Builds the identity for [statusBarKey] posted at [postTimeMillis]. */
    fun of(statusBarKey: String, postTimeMillis: Long): String =
        statusBarKey + SEPARATOR + postTimeMillis

    /** `|` cannot appear in a decimal post time, so the concatenation stays unambiguous. */
    private const val SEPARATOR = "|"
}
