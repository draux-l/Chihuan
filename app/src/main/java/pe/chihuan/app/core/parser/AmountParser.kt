package pe.chihuan.app.core.parser

/**
 * Extracts the first currency-marked amount and resolves it to whole céntimos.
 *
 * The marker is required (`S/` or `S/.`), the first occurrence in reading order wins, and the
 * separator rules are the deterministic ones fixed by `specs/payment-parsing/spec.md`. No floating
 * point is used: the integer and decimal parts are parsed as digit strings.
 */
internal object AmountParser {

    /**
     * `1..9` integer digits, optional separator groups, and no digit immediately after the match
     * (so a longer digit run cannot be truncated into a plausible amount).
     */
    private val MARKED_AMOUNT = Regex("""s/\.?\s*([0-9]{1,9}(?:[.,][0-9]+)*)(?![0-9])""")

    /**
     * Returns the amount of the first currency-marked number in [normalized], or null when there is
     * none, when it resolves to zero, or when it is outside the supported céntimos domain.
     */
    fun amountCents(normalized: String): Int? {
        val raw = MARKED_AMOUNT.find(normalized)?.groupValues?.get(1) ?: return null
        return resolve(raw)
    }

    private fun resolve(raw: String): Int? {
        val lastDot = raw.lastIndexOf('.')
        val lastComma = raw.lastIndexOf(',')

        val decimalSeparator: Char?
        val thousandsSeparator: Char?

        when {
            lastDot >= 0 && lastComma >= 0 -> {
                // Rule 1: the last occurring separator is decimal, the other one is thousands.
                decimalSeparator = if (lastDot > lastComma) '.' else ','
                thousandsSeparator = if (lastDot > lastComma) ',' else '.'
            }

            lastDot >= 0 || lastComma >= 0 -> {
                val separator = if (lastDot >= 0) '.' else ','
                val occurrences = raw.count { it == separator }
                val digitsAfter = raw.length - raw.lastIndexOf(separator) - 1

                if (occurrences == 1 && digitsAfter <= 2) {
                    // Rules 2 and 3: one- or two-digit tail is decimal.
                    decimalSeparator = separator
                    thousandsSeparator = null
                } else {
                    // Rule 4 and repeated grouping separators: a three-digit (or longer) tail and
                    // repeated separators are thousands grouping, removed before parsing.
                    decimalSeparator = null
                    thousandsSeparator = separator
                }
            }

            else -> {
                decimalSeparator = null
                thousandsSeparator = null
            }
        }

        val integerAndDecimals = if (thousandsSeparator != null) {
            raw.replace(thousandsSeparator.toString(), "")
        } else {
            raw
        }

        val integerPart: String
        val decimalPart: String
        if (decimalSeparator != null) {
            val decimalIndex = integerAndDecimals.lastIndexOf(decimalSeparator)
            integerPart = integerAndDecimals.substring(0, decimalIndex)
            decimalPart = integerAndDecimals.substring(decimalIndex + 1)
        } else {
            integerPart = integerAndDecimals
            decimalPart = ""
        }

        val integerDigits = integerPart.filter { it.isDigit() }
        if (integerDigits.isEmpty() || integerDigits.length > MAX_INTEGER_DIGITS) return null

        val soles = integerDigits.toLongOrNull() ?: return null
        val cents = when {
            decimalPart.isEmpty() -> 0L
            decimalPart.length == 1 -> decimalPart[0].digitToInt() * 10L
            else -> decimalPart.take(2).toLong()
        }

        val total = soles * 100L + cents
        if (total <= 0L || total > Int.MAX_VALUE.toLong()) return null

        return total.toInt()
    }

    /** Same ceiling the renderer and the design fix for a single payment amount. */
    private const val MAX_INTEGER_DIGITS = 9
}
