package pe.chihuan.app.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.chihuan.app.core.model.ContentSelection
import pe.chihuan.app.core.source.SourceId

/**
 * Extraction contract from `specs/payment-parsing/spec.md`.
 *
 * These tests only use the public entry point, so they exercise normalization, first-amount
 * selection, separator resolution, tolerant verb matching and code extraction together.
 */
class PaymentParserTest {

    private fun parse(text: String, source: SourceId = SourceId.YAPE): ParseOutcome =
        PaymentParser.parse(source, text)

    private fun parsed(text: String, source: SourceId = SourceId.YAPE): Parsed {
        val outcome = parse(text, source)
        assertTrue("expected Parsed but was $outcome", outcome is Parsed)
        return outcome as Parsed
    }

    // --- Outcome classification -----------------------------------------------------------------

    @Test
    fun textWithAmountNameAndCodeIsParsed() {
        val result = parsed("Juan Pérez te envió S/ 50. Código: 123")

        assertEquals(5000, result.amountCents)
        assertEquals("Juan Pérez", result.name)
        assertEquals("123", result.code)
    }

    @Test
    fun textWithoutAnyAmountIsUnparsed() {
        assertEquals(Unparsed, parse("Recibiste un yapeo. Código: 123"))
    }

    @Test
    fun emptyTextIsUnparsedAndNeverThrows() {
        assertEquals(Unparsed, parse(""))
    }

    @Test
    fun blankTextIsUnparsedAndNeverThrows() {
        assertEquals(Unparsed, parse("   \n\t  "))
    }

    @Test
    fun garbageTextIsUnparsed() {
        assertEquals(Unparsed, parse("Tienes un nuevo movimiento en tu cuenta"))
    }

    // --- Amount variants ------------------------------------------------------------------------

    @Test
    fun markerWithoutDecimalsIsParsed() {
        assertEquals(5000, parsed("Juan Pérez te envió S/ 50").amountCents)
    }

    @Test
    fun markerWithDotDecimalsIsParsed() {
        assertEquals(5000, parsed("Juan Pérez te envió S/ 50.00").amountCents)
    }

    @Test
    fun markerWithoutSeparatingSpaceIsParsed() {
        assertEquals(650, parsed("Juan Pérez te envió S/6,50").amountCents)
    }

    @Test
    fun commaDecimalsAreParsed() {
        assertEquals(650, parsed("Juan Pérez te envió S/ 6,50. Código: 123").amountCents)
    }

    @Test
    fun dotDecimalsAreParsed() {
        assertEquals(650, parsed("Juan Pérez te envió S/ 6.50").amountCents)
    }

    @Test
    fun currencyMarkerMayCarryTheOptionalDot() {
        assertEquals(650, parsed("Juan Pérez te envió S/. 6,50").amountCents)
    }

    @Test
    fun aBareNumberIsNotAnAmount() {
        assertEquals(Unparsed, parse("Juan Pérez te envió 50. Código: 123"))
    }

    @Test
    fun thousandsSeparatorIsNotReadAsCents() {
        assertEquals(150000, parsed("Juan Pérez te envió S/ 1,500.00").amountCents)
    }

    @Test
    fun singleDecimalDigitIsPadded() {
        assertEquals(650, parsed("Juan Pérez te envió S/ 6,5").amountCents)
    }

    @Test
    fun aZeroAmountIsUnparsed() {
        assertEquals(Unparsed, parse("Juan Pérez te envió S/ 0.00"))
    }

    @Test
    fun firstAmountWinsDeterministically() {
        val text = "Juan Pérez te envió S/ 6,50. Saldo: S/ 200.00"

        assertEquals(650, parsed(text).amountCents)
        assertEquals(650, parsed(text).amountCents)
    }

    @Test
    fun aTrailingSentencePeriodIsNotSwallowedIntoTheAmount() {
        assertEquals(5000, parsed("Juan Pérez te envió S/ 50. Código: 123").amountCents)
    }

    @Test
    fun anAmountWithoutIntegerDigitsIsNotGuessed() {
        assertEquals(Unparsed, parse("Juan Pérez te envió S/ .50"))
    }

    // --- Payer names ----------------------------------------------------------------------------

    @Test
    fun nameBeforeTeEnvio() {
        assertEquals("Juan Pérez", parsed("Juan Pérez te envió S/ 50. Código: 123").name)
    }

    @Test
    fun nameBeforeTeYapeo() {
        assertEquals("Rosa Quispe", parsed("Rosa Quispe te yapeó S/ 6,50").name)
    }

    @Test
    fun nameBeforeTeTransfirio() {
        assertEquals("Luis Ramos", parsed("Luis Ramos te transfirió S/ 12.00").name)
    }

    @Test
    fun verbMatchingIsAccentInsensitive() {
        assertEquals("ANA TORRES", parsed("ANA TORRES TE ENVIO S/ 8.00").name)
    }

    @Test
    fun aPartialNameIsPreservedAsWrittenWithSurroundingPunctuationTrimmed() {
        val result = parsed("Juan P. te yapeó S/ 10")

        assertEquals("Juan P", result.name)
        assertEquals(1000, result.amountCents)
    }

    @Test
    fun aMissingNameStillYieldsParsed() {
        val result = parsed("Te enviaron S/ 50. Código: 123")

        assertEquals(5000, result.amountCents)
        assertNull(result.name)
        assertEquals("123", result.code)
    }

    @Test
    fun recibisteWordingDoesNotInventAName() {
        val result = parsed("Recibiste S/ 50 de Juan Pérez")

        assertEquals(5000, result.amountCents)
        assertNull(result.name)
    }

    @Test
    fun theSenderLabelOnItsOwnLineIsNotAPayerName() {
        val result = parsed("Yape\nte envió S/ 20. Código: 987")

        assertNull(result.name)
        assertEquals(2000, result.amountCents)
    }

    @Test
    fun theInterbankSenderLabelOnItsOwnLineIsNotAPayerName() {
        val result = parsed("Interbank\nte transfirió S/ 20. Código: 987", SourceId.INTERBANK)

        assertNull(result.name)
        assertEquals(2000, result.amountCents)
    }

    @Test
    fun aNameRunIsLimitedToSixWords() {
        val result = parsed("uno dos tres cuatro cinco seis siete te yapeó S/ 5")

        assertEquals("dos tres cuatro cinco seis siete", result.name)
    }

    // --- Security codes -------------------------------------------------------------------------

    @Test
    fun labeledCodeIsExtracted() {
        assertEquals("123", parsed("Juan Pérez te envió S/ 50. Código: 123").code)
    }

    @Test
    fun codeWithoutColonKeepsItsLeadingZero() {
        assertEquals("045", parsed("Juan Pérez te envió S/ 50. Codigo 045").code)
    }

    @Test
    fun missingCodeStillYieldsAParsedAmount() {
        val result = parsed("Juan Pérez te envió S/ 50")

        assertEquals(5000, result.amountCents)
        assertNull(result.code)
    }

    @Test
    fun aTimeIsNotMistakenForACode() {
        assertNull(parsed("Juan Pérez te envió S/ 50 a las 21:35").code)
    }

    @Test
    fun aStandaloneThreeDigitTokenIsAcceptedAsTheCode() {
        assertEquals("456", parsed("Juan Pérez te envió S/ 50. 456").code)
    }

    @Test
    fun aFourDigitLabeledNumberIsNotACode() {
        assertNull(parsed("Juan Pérez te envió S/ 50. Código: 1234").code)
    }

    @Test
    fun aThousandsAmountIsNotMistakenForACode() {
        assertNull(parsed("Juan Pérez te envió S/ 1,500").code)
    }

    // --- Normalization and tolerance ------------------------------------------------------------

    @Test
    fun extraWhitespaceAndMixedCaseDoNotPreventParsing() {
        val result = parsed("  juan   pérez   TE   ENVIÓ   S/   6,50  .  código:   123  ")

        assertEquals(650, result.amountCents)
        assertEquals("juan pérez", result.name)
        assertEquals("123", result.code)
    }

    @Test
    fun contentSelectionDoesNotChangeWhatIsExtracted() {
        val hidden = ContentSelection(sayAmount = false, sayName = false, sayCode = false)

        val result = PaymentParser.parse(
            SourceId.YAPE,
            "Juan Pérez te envió S/ 50. Código: 123",
            hidden,
        )

        assertTrue(result is Parsed)
        result as Parsed
        assertEquals(5000, result.amountCents)
        assertEquals("Juan Pérez", result.name)
        assertEquals("123", result.code)
    }

    // --- Source dispatch ------------------------------------------------------------------------

    @Test
    fun bothDeclaredPatternSetsShareTheExtractionContract() {
        val text = "Juan Pérez te yapeó S/ 6,50. Código: 123"
        val fromYape = parsed(text, SourceId.YAPE)
        val fromInterbank = parsed(text, SourceId.INTERBANK)

        assertEquals(fromYape.amountCents, fromInterbank.amountCents)
        assertEquals(fromYape.name, fromInterbank.name)
        assertEquals(fromYape.code, fromInterbank.code)
    }

    @Test
    fun placeholderInterbankFixtureSatisfiesTheSharedContract() {
        val result = parsed(
            "Juan Pérez te transfirió S/ 6,50. Código: 123",
            SourceId.INTERBANK,
        )

        assertEquals(650, result.amountCents)
        assertEquals("Juan Pérez", result.name)
        assertEquals("123", result.code)
    }

    @Test
    fun unrecognizedInterbankWordingIsUnparsed() {
        assertEquals(
            Unparsed,
            parse("Se registró un movimiento en tu cuenta", SourceId.INTERBANK),
        )
    }

    @Test
    fun anInterbankAmountWithoutCurrencyMarkerIsUnparsed() {
        assertEquals(
            Unparsed,
            parse("Juan Pérez te transfirió 50.00", SourceId.INTERBANK),
        )
    }

    @Test
    fun separatorVariantsResolveIdenticallyAcrossSources() {
        val comma = parsed("Juan Pérez te yapeó S/ 6,50", SourceId.YAPE)
        val dot = parsed("Juan Pérez te transfirió S/ 6.50", SourceId.INTERBANK)

        assertEquals(650, comma.amountCents)
        assertEquals(650, dot.amountCents)
    }
}
