package pe.chihuan.app.core.parser

/**
 * Extracts the optional three-digit security code.
 *
 * A labeled occurrence (`Código`, `Codigo`, `Cód.`, `Cod.`, with or without a colon) wins over a
 * standalone three-digit token. The code is returned verbatim as text so a leading zero survives:
 * `045` stays `045`.
 */
internal object CodeParser {

    private val LABELED_CODE =
        Regex("""\bcod(?:igo)?\.?\s*:?\s*([0-9]{3})(?![0-9])""")

    /** A three-digit token that is not part of a longer dotted/grouped number. */
    private val STANDALONE_CODE = Regex("""(?<![0-9.,])([0-9]{3})(?![0-9.,])""")

    fun code(normalized: String): String? {
        LABELED_CODE.find(normalized)?.let { match ->
            return match.groupValues[1]
        }

        for (match in STANDALONE_CODE.findAll(normalized)) {
            val value = match.groupValues[1]
            val before = normalized.substring(0, match.range.first).trimEnd()
            val after = normalized.substring(match.range.last + 1)

            // Not adjacent to a currency marker or a time pattern.
            if (before.endsWith("/") || before.endsWith(":")) continue
            if (after.startsWith(":")) continue

            return value
        }

        return null
    }
}
