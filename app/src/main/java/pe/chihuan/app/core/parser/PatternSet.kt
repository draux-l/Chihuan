package pe.chihuan.app.core.parser

import pe.chihuan.app.core.source.SourceId
import pe.chihuan.app.core.source.SourceRegistry

/** Fields one pattern set managed to extract. Absent name and code are normal, not failures. */
internal data class ExtractedPayment(
    val amountCents: Int,
    val name: String?,
    val code: String?,
)

/**
 * One source's notification pattern set.
 *
 * Every set implements the same extraction contract (currency-marked amount, optional payer name,
 * optional three-digit code, first amount wins, shared separator rules) and delegates the shared
 * logic to [AmountParser], [NameParser] and [CodeParser]. Keeping one instance per source is what
 * lets one source's tolerance list change without touching another source's results.
 */
internal class PaymentPatternSet(
    val sourceId: SourceId,
    private val sourceLabel: String,
    private val verbPhrase: Regex,
) {

    /** Returns the extracted fields, or null when no valid amount exists. Never throws. */
    fun extract(text: String): ExtractedPayment? {
        if (text.isBlank()) return null

        val normalized = TextNormalizer.normalize(text)
        val amountCents = AmountParser.amountCents(normalized) ?: return null

        return ExtractedPayment(
            amountCents = amountCents,
            name = NameParser.name(text, normalized, verbPhrase, sourceLabel),
            code = CodeParser.code(normalized),
        )
    }
}

/**
 * The pattern sets declared by the registry, one per [SourceId].
 *
 * The Yape and Interbank verb lists are the tolerance set fixed by the proposal. Interbank's set is
 * an explicit placeholder until real notifications are captured; it shares the full extraction
 * contract so it can never guess an amount or invent a name from an unverified shape.
 */
internal object PatternSets {

    private val sets: Map<SourceId, PaymentPatternSet> = buildMap {
        put(SourceId.YAPE, set(SourceId.YAPE, "envio", "yapeo", "transfirio"))
        put(SourceId.INTERBANK, set(SourceId.INTERBANK, "envio", "yapeo", "transfirio"))
        // BBVA is declared by the registry (Plin arrives through it). Its wording is unverified,
        // so it carries the same tolerant placeholder set rather than a guessed pattern.
        put(SourceId.BBVA, set(SourceId.BBVA, "envio", "yapeo", "transfirio"))
    }

    fun forSource(sourceId: SourceId): PaymentPatternSet = sets.getValue(sourceId)

    private fun set(sourceId: SourceId, vararg verbPhrases: String): PaymentPatternSet {
        // Matching runs on the normalized (accent-stripped, lowercase) text, so the alternatives
        // are stored without accents: `envio` covers `envió` and `envio`.
        val alternatives = verbPhrases.joinToString("|")
        return PaymentPatternSet(
            sourceId = sourceId,
            sourceLabel = SourceRegistry.byId(sourceId).label,
            verbPhrase = Regex("""\bte\s+(?:$alternatives)\b"""),
        )
    }
}
