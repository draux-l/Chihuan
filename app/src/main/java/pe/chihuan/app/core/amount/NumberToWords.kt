package pe.chihuan.app.core.amount

/**
 * Renders a whole number of céntimos as the Spanish phrase the speech engine receives.
 *
 * The rules are fixed by `specs/voice-announcement/spec.md`:
 *
 * - `1` before a noun is `un` (`un sol`, `un céntimo`), never `uno`;
 * - `21` is `veintiún` and tens ending in one use `<tens> y un` (`cincuenta y un soles`);
 * - `100` is `cien`, `101`–`199` use `ciento`;
 * - `1000` is `mil` (never `un mil`) and `21 000` is `veintiún mil`;
 * - accents are preserved (`dieciséis`, `veintidós`, `céntimos`);
 * - the noun is `sol` only for exactly one sol and `soles` otherwise;
 * - the cent clause is `con <cardinal>` and the word `céntimos` appears only with cents;
 * - a cents-only amount contains no `sol`/`soles` at all.
 *
 * Integer arithmetic only: no floating point is used anywhere in this slice. The rendered text
 * never contains a digit character, the word `punto`, or the word `coma`, so a numeral can never
 * reach `TextToSpeech`.
 */
object NumberToWords {

    /**
     * Renders [cents] céntimos as a complete amount phrase.
     *
     * @throws IllegalArgumentException when [cents] is negative; the parser never produces one.
     */
    fun centsToSpanish(cents: Int): String {
        require(cents >= 0) { "céntimos must be non-negative" }

        val wholeSoles = cents / 100
        val centPart = cents % 100

        if (wholeSoles == 0) {
            return "${cardinal(centPart.toLong())} ${centNoun(centPart)}"
        }

        val solWords = cardinal(wholeSoles.toLong())
        val solClause = buildString {
            append(solWords)
            // "un millón de soles", "dos millones de soles"; shorter cardinals take no connector.
            if (solWords.endsWith("millón") || solWords.endsWith("millones")) {
                append(" de")
            }
            append(' ')
            append(if (wholeSoles == 1) "sol" else "soles")
        }

        if (centPart == 0) return solClause

        // The bare cardinal (`seis soles con cincuenta`) is the idiomatic form. The noun is
        // required when the cardinal would otherwise dangle: single-digit cents ("con dos
        // céntimos") and any cardinal that apocopates, which always ends in one ("veintiún
        // céntimos", "treinta y un céntimos"). `veintiún` without a noun is ungrammatical.
        return if (centNeedsNoun(centPart)) {
            "$solClause con ${cardinal(centPart.toLong())} ${centNoun(centPart)}"
        } else {
            "$solClause con ${cardinal(centPart.toLong())}"
        }
    }

    private fun centNeedsNoun(centPart: Int): Boolean = centPart < 10 || centPart % 10 == 1

    private fun centNoun(centPart: Int): String = if (centPart == 1) "céntimo" else "céntimos"

    /** Spanish cardinal for a non-negative value, always in the apocopated form before a noun. */
    private fun cardinal(value: Long): String {
        require(value >= 0) { "cardinal must be non-negative" }
        if (value == 0L) return "cero"

        val millions = value / 1_000_000
        val thousands = (value / 1_000) % 1_000
        val units = value % 1_000

        val parts = mutableListOf<String>()

        if (millions > 0) {
            parts += if (millions == 1L) "un millón" else "${belowThousand(millions.toInt())} millones"
        }
        if (thousands > 0) {
            parts += if (thousands == 1L) "mil" else "${belowThousand(thousands.toInt())} mil"
        }
        if (units > 0) {
            parts += belowThousand(units.toInt())
        }

        return parts.joinToString(" ")
    }

    private fun belowThousand(value: Int): String {
        if (value == 0) return "cero"
        if (value == 100) return "cien"

        val hundreds = value / 100
        val remainder = value % 100

        val builder = StringBuilder()
        if (hundreds > 0) {
            builder.append(HUNDREDS[hundreds])
            if (remainder > 0) builder.append(' ')
        }
        if (remainder > 0) {
            builder.append(belowHundred(remainder))
        }
        return builder.toString()
    }

    private fun belowHundred(value: Int): String {
        if (value < 30) return SMALL_CARDINALS[value]

        val tens = value / 10
        val units = value % 10
        return if (units == 0) {
            TENS[tens]
        } else {
            "${TENS[tens]} y ${SMALL_CARDINALS[units]}"
        }
    }

    private val HUNDREDS = arrayOf(
        "",
        "ciento",
        "doscientos",
        "trescientos",
        "cuatrocientos",
        "quinientos",
        "seiscientos",
        "setecientos",
        "ochocientos",
        "novecientos",
    )

    /** Index 0 is unused for cardinals above zero. Index 1 is the apocopated `un`. */
    private val SMALL_CARDINALS = arrayOf(
        "cero",
        "un",
        "dos",
        "tres",
        "cuatro",
        "cinco",
        "seis",
        "siete",
        "ocho",
        "nueve",
        "diez",
        "once",
        "doce",
        "trece",
        "catorce",
        "quince",
        "dieciséis",
        "diecisiete",
        "dieciocho",
        "diecinueve",
        "veinte",
        "veintiún",
        "veintidós",
        "veintitrés",
        "veinticuatro",
        "veinticinco",
        "veintiséis",
        "veintisiete",
        "veintiocho",
        "veintinueve",
    )

    /** Indexed by the tens digit; 0, 1 and 2 are covered by [SMALL_CARDINALS]. */
    private val TENS = arrayOf(
        "",
        "",
        "",
        "treinta",
        "cuarenta",
        "cincuenta",
        "sesenta",
        "setenta",
        "ochenta",
        "noventa",
    )
}
