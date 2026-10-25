package pe.chihuan.app.core.amount

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Normative amount rendering from `specs/voice-announcement/spec.md`.
 *
 * Every vector the spec pins is present here, plus the no-digit / no-`punto` / no-`coma`
 * invariant that keeps numerals away from the speech engine.
 */
class NumberToWordsTest {

    private data class Vector(val cents: Int, val expected: String)

    private val specVectors = listOf(
        Vector(650, "seis soles con cincuenta"),
        Vector(100, "un sol"),
        Vector(5100, "cincuenta y un soles"),
        Vector(2100, "veintiún soles"),
        Vector(10000, "cien soles"),
        Vector(10100, "ciento un soles"),
        Vector(100000, "mil soles"),
        Vector(10050, "cien soles con cincuenta"),
        Vector(50, "cincuenta céntimos"),
        Vector(5, "cinco céntimos"),
        Vector(1, "un céntimo"),
        Vector(101, "un sol con un céntimo"),
    )

    @Test
    fun everySpecVectorRendersExactlyAsSpecified() {
        for (vector in specVectors) {
            assertEquals(
                "céntimos ${vector.cents}",
                vector.expected,
                NumberToWords.centsToSpanish(vector.cents),
            )
        }
    }

    @Test
    fun tensEndingInOneUseTheApocopatedFormBeforeSoles() {
        assertEquals("treinta y un soles", NumberToWords.centsToSpanish(3100))
        assertEquals("cuarenta y un soles", NumberToWords.centsToSpanish(4100))
        assertEquals("noventa y un soles", NumberToWords.centsToSpanish(9100))
    }

    @Test
    fun accentedCardinalsKeepTheirAccents() {
        assertEquals("dieciséis soles", NumberToWords.centsToSpanish(1600))
        assertEquals("veintidós soles", NumberToWords.centsToSpanish(2200))
        assertEquals("veintitrés soles", NumberToWords.centsToSpanish(2300))
    }

    @Test
    fun amountsAboveOneThousandComposeWithMil() {
        assertEquals("mil quinientos soles", NumberToWords.centsToSpanish(150000))
        assertEquals("cien mil soles", NumberToWords.centsToSpanish(10000000))
        assertEquals("veintiún mil soles", NumberToWords.centsToSpanish(2100000))
        assertEquals("diez mil soles", NumberToWords.centsToSpanish(1000000))
    }

    @Test
    fun millionsAreRenderedWithoutDigits() {
        val rendered = NumberToWords.centsToSpanish(100_000_000) // one million soles
        assertEquals("un millón de soles", rendered)
        assertNoForbiddenCharacters(rendered)
    }

    @Test
    fun centClauseAppendsTheCardinalAndSpecialCasesOneCent() {
        assertEquals("un sol con un céntimo", NumberToWords.centsToSpanish(101))
        assertEquals("un sol con dos céntimos", NumberToWords.centsToSpanish(102))
        assertEquals("dos soles con veintiún céntimos", NumberToWords.centsToSpanish(221))
    }

    @Test
    fun noRenderedAmountEverContainsADigitOrTheWordsPuntoOrComa() {
        val sample = specVectors.map { it.cents } + listOf(
            0, 2, 21, 99, 199, 999, 1001, 20100, 99999, 123456, 999999999,
        )

        for (cents in sample) {
            assertNoForbiddenCharacters(NumberToWords.centsToSpanish(cents))
        }
    }

    private fun assertNoForbiddenCharacters(rendered: String) {
        assertFalse("`$rendered` contains a digit", rendered.any { it in '0'..'9' })
        assertFalse("`$rendered` contains `punto`", rendered.contains("punto"))
        assertFalse("`$rendered` contains `coma`", rendered.contains("coma"))
    }
}
