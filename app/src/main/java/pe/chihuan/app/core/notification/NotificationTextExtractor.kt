package pe.chihuan.app.core.notification

/**
 * Raw notification extra values, already read out of the platform bundle.
 *
 * This type keeps [NotificationTextExtractor] free of Android framework imports so the
 * extraction decision stays unit-testable on the JVM.
 */
data class NotificationExtras(
    /** `Notification.EXTRA_TITLE` as text, or null when absent. */
    val title: String? = null,
    /** `Notification.EXTRA_TEXT` as text, or null when absent. */
    val text: String? = null,
    /** `Notification.EXTRA_BIG_TEXT` as text, or null when absent. */
    val bigText: String? = null,
    /** `Notification.EXTRA_TEXT_LINES` flattened to text, or null when absent. */
    val textLines: List<String>? = null,
)

/**
 * Applies the notification text precedence fixed by the notification-listening spec:
 *
 * 1. the title is always included when present;
 * 2. a non-blank big text is used **instead of** the collapsed text;
 * 3. otherwise non-blank text lines are used **instead of** the collapsed text;
 * 4. otherwise the collapsed text is used.
 *
 * The rule is replacement, never concatenation: a notification whose collapsed text is a
 * truncated preview must not leak that preview into the extracted body.
 */
object NotificationTextExtractor {

    /**
     * Returns the extractable text of one notification, or null when every value is absent or
     * blank.
     *
     * Never throws: blank, empty and missing values are all treated as absent.
     */
    fun extract(extras: NotificationExtras): String? {
        val title = extras.title.normalized()
        val body = firstUsable(
            extras.bigText.normalized(),
            joinLines(extras.textLines),
            extras.text.normalized(),
        )

        return when {
            title != null && body != null -> title + SEPARATOR + body
            title != null -> title
            else -> body
        }
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    /** Lines are joined with newlines because inbox-style notifications use one line per fact. */
    private fun joinLines(lines: List<String>?): String? {
        val usable = lines.orEmpty().mapNotNull { it.normalized() }
        return usable.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)
    }

    private fun firstUsable(vararg candidates: String?): String? =
        candidates.firstOrNull { it != null }

    private const val SEPARATOR = "\n"
}
