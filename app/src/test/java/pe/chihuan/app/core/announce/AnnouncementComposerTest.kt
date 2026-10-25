package pe.chihuan.app.core.announce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.chihuan.app.core.model.ContentSelection
import pe.chihuan.app.core.parser.Parsed
import pe.chihuan.app.core.parser.Unparsed

/**
 * Composition contract from `specs/voice-announcement/spec.md` and
 * `specs/announcement-settings/spec.md`.
 */
class AnnouncementComposerTest {

    private val everything = ContentSelection()

    @Test
    fun amountNameAndCodeAreComposedNameFirst() {
        val utterance = AnnouncementComposer.compose(
            Parsed(amountCents = 650, name = "Juan Pérez", code = "123"),
            everything,
        )

        assertEquals(
            "Juan Pérez te yapeó seis soles con cincuenta. Código: uno dos tres.",
            utterance,
        )
    }

    @Test
    fun missingNameUsesTheImpersonalVerb() {
        val utterance = AnnouncementComposer.compose(
            Parsed(amountCents = 5000, name = null, code = "123"),
            everything,
        )

        assertEquals("Te yapearon cincuenta soles. Código: uno dos tres.", utterance)
    }

    @Test
    fun missingCodeIsOmittedWithoutFailing() {
        val utterance = AnnouncementComposer.compose(
            Parsed(amountCents = 650, name = "Juan Pérez", code = null),
            everything,
        )

        assertEquals("Juan Pérez te yapeó seis soles con cincuenta.", utterance)
    }

    @Test
    fun amountHiddenBySettingsLeavesTheVerbAsItsOwnSentence() {
        val utterance = AnnouncementComposer.compose(
            Parsed(amountCents = 650, name = "Juan Pérez", code = "123"),
            ContentSelection(sayAmount = false),
        )

        assertEquals("Juan Pérez te yapeó. Código: uno dos tres.", utterance)
    }

    @Test
    fun nameDisabledFallsBackToTheImpersonalVerb() {
        val utterance = AnnouncementComposer.compose(
            Parsed(amountCents = 650, name = "Juan Pérez", code = "123"),
            ContentSelection(sayName = false),
        )

        assertEquals("Te yapearon seis soles con cincuenta. Código: uno dos tres.", utterance)
    }

    @Test
    fun codeDisabledIsOmitted() {
        val utterance = AnnouncementComposer.compose(
            Parsed(amountCents = 650, name = "Juan Pérez", code = "123"),
            ContentSelection(sayCode = false),
        )

        assertEquals("Juan Pérez te yapeó seis soles con cincuenta.", utterance)
    }

    @Test
    fun hiddenAmountAndCodeStillSpeakTheVerb() {
        val utterance = AnnouncementComposer.compose(
            Parsed(amountCents = 650, name = "Juan Pérez", code = "123"),
            ContentSelection(sayAmount = false, sayCode = false),
        )

        assertEquals("Juan Pérez te yapeó.", utterance)
    }

    @Test
    fun aLeadingZeroInTheCodeIsSpokenDigitByDigit() {
        val utterance = AnnouncementComposer.compose(
            Parsed(amountCents = 5000, name = "Rosa Quispe", code = "045"),
            everything,
        )

        assertEquals(
            "Rosa Quispe te yapeó cincuenta soles. Código: cero cuatro cinco.",
            utterance,
        )
    }

    @Test
    fun aCodeIsAlwaysSpokenAsSeparateWords() {
        val utterance = AnnouncementComposer.compose(
            Parsed(amountCents = 100, name = "Ana", code = "123"),
            everything,
        )

        assertTrue(utterance.contains("Código: uno dos tres."))
    }

    @Test
    fun unparsedProducesExactlyTheFallbackPhrase() {
        assertEquals(
            "Llegó un pago, pero no pude leerlo.",
            AnnouncementComposer.compose(Unparsed, everything),
        )
    }

    @Test
    fun contentSelectionNeverAltersTheFallbackPhrase() {
        val allHidden = ContentSelection(sayAmount = false, sayName = false, sayCode = false)

        assertEquals(
            "Llegó un pago, pero no pude leerlo.",
            AnnouncementComposer.compose(Unparsed, allHidden),
        )
    }

    @Test
    fun noComposedUtteranceContainsADigitCharacter() {
        val cases = listOf(
            AnnouncementComposer.compose(
                Parsed(amountCents = 650, name = "Juan Pérez", code = "123"),
                everything,
            ),
            AnnouncementComposer.compose(
                Parsed(amountCents = 5000, name = null, code = "045"),
                everything,
            ),
            AnnouncementComposer.compose(
                Parsed(amountCents = 10100, name = "Ana Torres", code = "987"),
                everything,
            ),
            AnnouncementComposer.compose(Unparsed, everything),
        )

        for (utterance in cases) {
            assertFalse("`$utterance` contains a digit", utterance.any { it in '0'..'9' })
        }
    }

    @Test
    fun theUtteranceStartsWithUppercaseAndEndsWithAPeriod() {
        val lowerCaseName = AnnouncementComposer.compose(
            Parsed(amountCents = 650, name = "juan pérez", code = "123"),
            everything,
        )
        val noName = AnnouncementComposer.compose(
            Parsed(amountCents = 650, name = null, code = null),
            everything,
        )

        assertEquals("Juan pérez te yapeó seis soles con cincuenta. Código: uno dos tres.", lowerCaseName)
        assertTrue(lowerCaseName.first().isUpperCase())
        assertTrue(lowerCaseName.endsWith("."))
        assertTrue(noName.first().isUpperCase())
        assertTrue(noName.endsWith("."))
    }

    @Test
    fun hiddenAmountAndNameAndCodeStillProduceASentence() {
        val allHidden = ContentSelection(sayAmount = false, sayName = false, sayCode = false)

        assertEquals(
            "Te yapearon.",
            AnnouncementComposer.compose(
                Parsed(amountCents = 650, name = "Juan Pérez", code = "123"),
                allHidden,
            ),
        )
    }
}
