package pe.chihuan.app.core.model

/**
 * Which payment fields may appear in a spoken announcement.
 *
 * This is the pure counterpart of the three content toggles persisted by the settings store. It
 * lives in `core` so the composer can be exercised on the JVM; the Android settings class is not
 * referenced here.
 *
 * The defaults are the product defaults: every content group is spoken.
 */
data class ContentSelection(
    val sayAmount: Boolean = true,
    val sayName: Boolean = true,
    val sayCode: Boolean = true,
) {
    /** True when at least one field would be spoken; the settings policy never stores all-false. */
    val hasAnySelected: Boolean
        get() = sayAmount || sayName || sayCode

    companion object {
        /** Every content group enabled, matching a fresh install. */
        val Default: ContentSelection = ContentSelection()
    }
}
