package pe.chihuan.app.core.source

/**
 * Stable identifiers of the payment apps Chihuan can read notifications from.
 *
 * The enum [name] is not used for persistence; [key] is the stable, lowercase fragment written into
 * the settings file, so renaming an enum constant never orphans a stored armed flag.
 */
enum class SourceId(val key: String) {
    /** Yape, the BCP wallet. */
    YAPE("yape"),

    /** BBVA's app, which is where BBVA's Plin payments are posted. */
    BBVA("bbva"),

    /** Interbank's app, which is where Interbank's Plin payments are posted. */
    INTERBANK("interbank"),
}

/**
 * One readable notification source.
 *
 * @property label brand name shown on the panel; brand names are not translated.
 * @property packages notification package names accepted for this source, compared with exact
 *   equality. Empty is a deliberate marker meaning "not captured yet" — never a wildcard.
 * @property labelFragments lowercase fragments matched against the posting app's label. This is a
 *   documented temporary bridge, not a substitute for [packages].
 */
data class Source(
    val id: SourceId,
    val label: String,
    val packages: Set<String>,
    val labelFragments: Set<String>,
) {
    /**
     * True when this source's real package name has not been captured yet, so it can only be
     * recognised by its app label.
     */
    val isPackageUnverified: Boolean
        get() = packages.isEmpty()
}

/**
 * The single list of readable sources, in stable display order.
 *
 * ## Unverified package sets are a deliberate, temporary bridge
 *
 * Only Yape's package is verified: `com.bcp.innovacxion.yapeapp` (this project's own assumption,
 * pending a real capture). The BBVA and Interbank package names have **not** been observed, so
 * their [Source.packages] sets are intentionally empty instead of holding a guessed string, and the
 * registry does not scan unknown packages looking for a similarity.
 *
 * Until those package names are captured, [SourceMatcher] falls back to the posting app's label
 * ([Source.labelFragments]). That fallback must be **replaced with exact package names** once the
 * debug capture reveals them: enable notification access on a debug build, trigger a real
 * notification from the app, then read the `pkg=` field of the `ChihuanCapture` logcat tag
 * (`adb logcat -s ChihuanCapture`). Delete the fragment entry for each source as its package set is
 * filled so the bridge cannot outlive its purpose.
 *
 * Display order is meaningful: it is the order the panel renders and the order fragment collisions
 * resolve in ([SourceMatcher]).
 */
object SourceRegistry {

    /** Registry entries in stable display order. */
    val entries: List<Source> = listOf(
        Source(
            id = SourceId.YAPE,
            label = "Yape",
            packages = setOf("com.bcp.innovacxion.yapeapp"),
            labelFragments = setOf("yape"),
        ),
        Source(
            id = SourceId.BBVA,
            label = "BBVA",
            // UNVERIFIED — no real BBVA notification has been captured. Replace with the exact
            // package observed in the ChihuanCapture `pkg=` field and drop the label fragment.
            packages = emptySet(),
            labelFragments = setOf("bbva"),
        ),
        Source(
            id = SourceId.INTERBANK,
            label = "Interbank",
            // UNVERIFIED — no real Interbank (Plin) notification has been captured. Replace with
            // the exact package observed in the ChihuanCapture `pkg=` field and drop the fragment.
            packages = emptySet(),
            labelFragments = setOf("interbank"),
        ),
    )

    /** Returns the entry for [id]; every [SourceId] is registered, which the tests pin. */
    fun byId(id: SourceId): Source = entries.first { entry -> entry.id == id }
}
