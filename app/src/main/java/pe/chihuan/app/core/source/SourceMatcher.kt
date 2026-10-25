package pe.chihuan.app.core.source

/**
 * Attributes a posted notification to a registry source.
 *
 * Pure Kotlin and total: blank, null and unexpected input never throw, they simply fail to match.
 */
object SourceMatcher {

    /**
     * Returns the source that owns [packageName], or null when no source does.
     *
     * Resolution order:
     * 1. **Exact package match** against [Source.packages] wins outright, so a verified source can
     *    never be stolen by a label coincidence.
     * 2. Otherwise a case-insensitive `contains` match of the trimmed [appLabel] against
     *    [Source.labelFragments]. Within this step, the first entry in [SourceRegistry.entries]
     *    display order wins, which keeps the result deterministic when two fragments both appear in
     *    one label.
     * 3. Otherwise null.
     *
     * The package is compared byte for byte: no prefix, suffix or substring tolerance, so a
     * look-alike such as `com.bcp.innovacxion.yapeapp.debug` is not the Yape package.
     * A null or blank [appLabel] can only ever match through the package step.
     */
    fun match(packageName: String, appLabel: String?): SourceId? {
        SourceRegistry.entries
            .firstOrNull { source -> packageName in source.packages }
            ?.let { source -> return source.id }

        val normalizedLabel = appLabel?.trim()?.lowercase().orEmpty()
        if (normalizedLabel.isEmpty()) return null

        return SourceRegistry.entries
            .firstOrNull { source ->
                source.labelFragments.any { fragment -> normalizedLabel.contains(fragment) }
            }
            ?.id
    }
}
