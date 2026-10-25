package pe.chihuan.app.core.announce

import pe.chihuan.app.core.amount.NumberToWords
import pe.chihuan.app.core.model.ContentSelection
import pe.chihuan.app.core.parser.ParseOutcome
import pe.chihuan.app.core.parser.Parsed
import pe.chihuan.app.core.parser.Unparsed
import java.util.Locale

/**
 * Builds the exact utterance the speech engine receives.
 *
 * Composition order is fixed by `specs/voice-announcement/spec.md`: payer name first, then amount,
 * then the security code. A parsed payment with no name opens with `Te yapearon`; the amount always
 * follows the verb directly; the code is a separate `Código:` sentence with its digits spoken one
 * by one. An [Unparsed] outcome is never composed from fields: it is [FALLBACK_PHRASE], exactly.
 *
 * Content selection only hides fields; it never changes number formatting and never changes the
 * fallback phrase.
 */
object AnnouncementComposer {

    /** Exact audible fallback fixed by the spec. Never prefixed, suffixed or composed. */
    const val FALLBACK_PHRASE: String = "Llegó un pago, pero no pude leerlo."

    fun compose(
        outcome: ParseOutcome,
        selection: ContentSelection = ContentSelection.Default,
    ): String {
        return when (outcome) {
            is Parsed -> composeParsed(outcome, selection)
            Unparsed -> FALLBACK_PHRASE
        }
    }

    private fun composeParsed(parsed: Parsed, selection: ContentSelection): String {
        val spokenName = parsed.name
            ?.trim()
            ?.takeIf { selection.sayName && it.isNotEmpty() && it.none(Char::isDigit) }

        val spokenAmount = if (selection.sayAmount) {
            NumberToWords.centsToSpanish(parsed.amountCents)
        } else {
            null
        }

        val spokenCode = if (selection.sayCode) {
            parsed.code?.let(::speakDigits)
        } else {
            null
        }

        val sentence = StringBuilder()
        if (spokenName != null) {
            sentence.append(spokenName).append(" te yapeó")
        } else {
            sentence.append("Te yapearon")
        }

        if (spokenAmount != null) {
            sentence.append(' ').append(spokenAmount)
        }

        sentence.append('.')

        if (spokenCode != null) {
            sentence.append(" Código: ").append(spokenCode).append('.')
        }

        return sentence.toString().uppercaseInitial()
    }

    /** Renders a three-digit code as words, in order: `045` → `cero cuatro cinco`. */
    private fun speakDigits(code: String): String? {
        val words = code
            .filter { it in '0'..'9' }
            .map { digit -> DIGIT_WORDS[digit - '0'] }

        return words.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun String.uppercaseInitial(): String {
        if (isEmpty()) return this
        return replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(Locale.ROOT) else character.toString()
        }
    }

    private val DIGIT_WORDS = arrayOf(
        "cero",
        "uno",
        "dos",
        "tres",
        "cuatro",
        "cinco",
        "seis",
        "siete",
        "ocho",
        "nueve",
    )
}
