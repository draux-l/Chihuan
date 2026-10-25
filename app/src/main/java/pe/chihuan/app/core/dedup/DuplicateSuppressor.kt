package pe.chihuan.app.core.dedup

/**
 * In-memory record of the notifications already accepted, so a re-post is announced only once.
 *
 * Deliberately not persisted: the specs accept that a notification re-posted after process death is
 * announced again, and keeping raw notification identity out of storage is the simpler, safer trade.
 *
 * The set is bounded by insertion order (oldest first) and expires entries ten minutes after their
 * first sighting. A duplicate lookup never extends retention: retention is a property of the first
 * post, not of every repost. The clock is injected so the policy is testable without a device.
 */
class DuplicateSuppressor(
    private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val firstSeenAtMillis = LinkedHashMap<String, Long>()

    /**
     * Returns true when [identity] was already accepted less than [retentionMillis] ago, and records
     * it first when it is new.
     */
    fun shouldSuppress(identity: String, nowMillis: Long): Boolean {
        evictExpired(nowMillis)

        if (firstSeenAtMillis.containsKey(identity)) return true

        firstSeenAtMillis[identity] = nowMillis
        evictOverflow()
        return false
    }

    private fun evictExpired(nowMillis: Long) {
        val expired = firstSeenAtMillis
            .filterValues { seenAt -> nowMillis - seenAt > retentionMillis }
            .keys
        firstSeenAtMillis.keys.removeAll(expired)
    }

    private fun evictOverflow() {
        val iterator = firstSeenAtMillis.keys.iterator()
        while (firstSeenAtMillis.size > maxEntries && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    companion object {
        /** Ten minutes, the retention floor fixed by the notification-listening spec. */
        const val DEFAULT_RETENTION_MILLIS: Long = 10 * 60 * 1000L

        /**
         * Memory bound only. It exceeds any realistic payment cadence inside ten minutes, so the
         * cap never shortens the retention window in practice.
         */
        const val DEFAULT_MAX_ENTRIES: Int = 512
    }
}
