package pe.chihuan.app.core.parser

import pe.chihuan.app.core.model.ContentSelection
import pe.chihuan.app.core.source.SourceId

/**
 * Result of reading one notification text.
 *
 * [Unparsed] is a normal, first-class outcome: it is returned, never thrown, and it is what
 * produces the audible fallback phrase. No default, guessed or invented amount can appear here.
 */
sealed interface ParseOutcome

/**
 * A notification with an extractable amount.
 *
 * [amountCents] is a whole number of céntimos (integer, never floating point). [name] and [code]
 * are independently optional; a missing one never makes the notification unparsed.
 *
 * @throws IllegalArgumentException when [amountCents] is not positive; the parser never constructs
 *   such a value.
 */
data class Parsed(
    val amountCents: Int,
    val name: String? = null,
    val code: String? = null,
) : ParseOutcome {
    init {
        require(amountCents > 0) { "Parsed requires a positive amount in céntimos" }
    }
}

/** No valid amount could be extracted. The caller announces the fallback phrase. */
data object Unparsed : ParseOutcome

/**
 * Dispatches parsing by source and returns exactly one outcome.
 *
 * Only the pattern set of [sourceId] runs, so one source's tolerance list can never change another
 * source's result. Input is never modified and malformed text is total: empty, blank, garbage and
 * unknown shapes all return [Unparsed].
 *
 * [selection] is accepted so the pipeline can pass the same settings snapshot it uses for
 * composition, but extraction is deliberately independent of it: a hidden amount must still be
 * parsed so the minimum-amount filter and history totals can see it, and the composer is the only
 * place where a field is omitted from speech.
 */
object PaymentParser {

    fun parse(
        sourceId: SourceId,
        text: String,
        @Suppress("UNUSED_PARAMETER") selection: ContentSelection = ContentSelection.Default,
    ): ParseOutcome {
        val extracted = PatternSets.forSource(sourceId).extract(text) ?: return Unparsed

        return Parsed(
            amountCents = extracted.amountCents,
            name = extracted.name,
            code = extracted.code,
        )
    }
}
