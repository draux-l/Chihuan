package pe.chihuan.app.core.parser

/**
 * Extracts the payer name from the words immediately before a recognized payment verb phrase.
 *
 * Matching runs on the length-preserving [TextNormalizer] form, but the reported name is sliced
 * from the **original** text, so casing and accents survive exactly as the notification carried
 * them.
 */
internal object NameParser {

    /**
     * @param original the extracted notification text, untouched
     * @param normalized [original] through [TextNormalizer], same length by construction
     * @param verbPhrase the pattern set's recognized verb phrases
     * @param sourceLabel the announcing app's label, rejected as a payer name
     */
    fun name(
        original: String,
        normalized: String,
        verbPhrase: Regex,
        sourceLabel: String,
    ): String? {
        val verbMatch = verbPhrase.find(normalized) ?: return null
        val verbStart = verbMatch.range.first

        // Walk back to the nearest bound fixed by the spec: start of text, newline, sentence
        // terminator, colon, digit or currency marker (the marker's `/`).
        var candidateStart = verbStart
        while (candidateStart > 0) {
            if (isBoundary(normalized, candidateStart - 1)) break
            candidateStart--
        }

        val rawCandidate = original.substring(candidateStart, verbStart)
        val words = rawCandidate.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null

        // The run immediately before the verb, capped at six words.
        val capped = words.takeLast(MAX_NAME_WORDS).joinToString(" ")

        // Trim surrounding punctuation and whitespace, then report the words as written.
        val firstLetter = capped.indexOfFirst { it.isLetterOrDigit() }
        if (firstLetter < 0) return null
        val lastLetter = capped.indexOfLast { it.isLetterOrDigit() }
        val trimmed = capped.substring(firstLetter, lastLetter + 1)
        val collapsed = trimmed.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
        if (collapsed.isEmpty() || collapsed.none { it.isLetter() }) return null

        if (isSourceLabel(collapsed, sourceLabel)) return null

        return collapsed
    }

    private fun isBoundary(text: String, index: Int): Boolean {
        val character = text[index]
        return when (character) {
            '\n', '\r', ';', ':', '!', '?', '/' -> true
            '.' -> endsAMultiLetterWord(text, index)
            else -> character.isDigit()
        }
    }

    /**
     * A period ends a sentence only when it follows a word of two or more letters. A one-letter
     * token (`Juan P. te yapeó`) is an abbreviation and stays part of the candidate, so trimming
     * the candidate's surrounding punctuation produces `Juan P` as the spec's scenario pins.
     */
    private fun endsAMultiLetterWord(text: String, periodIndex: Int): Boolean {
        var wordStart = periodIndex
        while (wordStart > 0) {
            val previous = text[wordStart - 1]
            val stopsWord = previous.isWhitespace() ||
                previous == ':' ||
                previous == ';' ||
                previous == '!' ||
                previous == '?' ||
                previous == '/' ||
                previous.isDigit()
            if (stopsWord) break
            wordStart--
        }
        return periodIndex - wordStart > 1
    }

    private fun isSourceLabel(candidate: String, sourceLabel: String): Boolean {
        return TextNormalizer.normalize(candidate) == TextNormalizer.normalize(sourceLabel)
    }

    private const val MAX_NAME_WORDS = 6

    private val WHITESPACE = Regex("\\s+")
}
