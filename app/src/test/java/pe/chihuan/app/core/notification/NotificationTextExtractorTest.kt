package pe.chihuan.app.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Precedence contract from `specs/notification-listening/spec.md`:
 * title always included when present, `bigText` replaces `text`, `textLines` replace `text`,
 * and missing values never throw.
 */
class NotificationTextExtractorTest {

    @Test
    fun bigTextReplacesTextInsteadOfBeingConcatenatedWithIt() {
        val extracted = NotificationTextExtractor.extract(
            NotificationExtras(
                title = null,
                text = "Juan Pérez te envió S/ 5...",
                bigText = "Juan Pérez te envió S/ 50. Código: 123",
                textLines = null,
            ),
        )

        assertEquals("Juan Pérez te envió S/ 50. Código: 123", extracted)
        assertFalse(extracted!!.contains("S/ 5..."))
    }

    @Test
    fun textLinesAreUsedWhenBigTextIsAbsent() {
        val extracted = NotificationTextExtractor.extract(
            NotificationExtras(
                title = null,
                text = "collapsed text",
                bigText = null,
                textLines = listOf("Pago recibido", "S/ 6,50 de Juan Pérez", "Código: 123"),
            ),
        )

        assertEquals("Pago recibido\nS/ 6,50 de Juan Pérez\nCódigo: 123", extracted)
    }

    @Test
    fun textIsUsedWhenBigTextAndTextLinesAreAbsent() {
        val extracted = NotificationTextExtractor.extract(
            NotificationExtras(
                title = null,
                text = "Juan Pérez te yapeó S/ 12. Código: 456",
                bigText = null,
                textLines = null,
            ),
        )

        assertEquals("Juan Pérez te yapeó S/ 12. Código: 456", extracted)
    }

    @Test
    fun blankBigTextFallsThroughToTextLines() {
        val extracted = NotificationTextExtractor.extract(
            NotificationExtras(
                title = null,
                text = "collapsed text",
                bigText = "   ",
                textLines = listOf("Pago recibido", "S/ 4 de Rosa Quispe"),
            ),
        )

        assertEquals("Pago recibido\nS/ 4 de Rosa Quispe", extracted)
    }

    @Test
    fun blankTextLinesFallThroughToText() {
        val extracted = NotificationTextExtractor.extract(
            NotificationExtras(
                title = null,
                text = "Luis Torres te envió S/ 8,50. Código: 012",
                bigText = null,
                textLines = listOf("", "   "),
            ),
        )

        assertEquals("Luis Torres te envió S/ 8,50. Código: 012", extracted)
    }

    @Test
    fun titleIsIncludedAheadOfTheBodyWhenPresent() {
        val extracted = NotificationTextExtractor.extract(
            NotificationExtras(
                title = "Yape",
                text = "Juan Pérez te yapeó S/ 6,50. Código: 123",
                bigText = null,
                textLines = null,
            ),
        )

        assertEquals("Yape\nJuan Pérez te yapeó S/ 6,50. Código: 123", extracted)
    }

    @Test
    fun titleAloneIsUsableContent() {
        val extracted = NotificationTextExtractor.extract(
            NotificationExtras(title = "Yape", text = null, bigText = null, textLines = null),
        )

        assertEquals("Yape", extracted)
    }

    @Test
    fun blankTitleIsTreatedAsAbsent() {
        val extracted = NotificationTextExtractor.extract(
            NotificationExtras(
                title = "  ",
                text = "S/ 6,50 de Juan Pérez",
                bigText = null,
                textLines = null,
            ),
        )

        assertEquals("S/ 6,50 de Juan Pérez", extracted)
    }

    @Test
    fun everyValueAbsentReturnsNull() {
        assertNull(
            NotificationTextExtractor.extract(
                NotificationExtras(title = null, text = null, bigText = null, textLines = null),
            ),
        )
    }

    @Test
    fun everyValueBlankOrEmptyReturnsNull() {
        assertNull(
            NotificationTextExtractor.extract(
                NotificationExtras(
                    title = " ",
                    text = "\t",
                    bigText = "\n",
                    textLines = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun outerWhitespaceIsNormalizedWithoutTouchingAccentsOrCase() {
        val extracted = NotificationTextExtractor.extract(
            NotificationExtras(
                title = "  Yape  ",
                text = "  Juan Pérez TE YAPEÓ S/ 6,50. Código: 045  ",
                bigText = null,
                textLines = null,
            ),
        )

        assertEquals("Yape\nJuan Pérez TE YAPEÓ S/ 6,50. Código: 045", extracted)
    }

    @Test
    fun blankLinesInsideTextLinesAreDroppedInsteadOfSplittingTheBody() {
        val extracted = NotificationTextExtractor.extract(
            NotificationExtras(
                title = null,
                text = null,
                bigText = null,
                textLines = listOf("Pago recibido", "  ", "Código: 123"),
            ),
        )

        assertEquals("Pago recibido\nCódigo: 123", extracted)
    }
}
