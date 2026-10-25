package pe.chihuan.app.listener

import pe.chihuan.app.core.notification.NotificationExtras

/**
 * One posted notification, already reduced to the fields the pipeline needs.
 *
 * This is the single entry point of the pipeline: both a real notification posted by the system and
 * the debug sample go through it, so the sample exercises filtering, parsing, composition and speech
 * exactly like a real payment.
 *
 * It carries no Android types, so it can be built by a test or by the debug action without a live
 * `StatusBarNotification`.
 *
 * @property packageName the posting app's package, compared exactly against the source registry.
 * @property statusBarKey the platform notification key (package, id and tag).
 * @property postTimeMillis the platform post time; together with [statusBarKey] it is the
 *   deduplication identity.
 */
data class IncomingNotification(
    val packageName: String,
    val statusBarKey: String,
    val postTimeMillis: Long,
    val extras: NotificationExtras,
)
