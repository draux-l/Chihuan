package pe.chihuan.app.core.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Matching contract used to attribute a posted notification to a registry entry.
 *
 * The label-fragment path is the documented temporary bridge for sources whose real package name
 * has not been captured yet, so it is pinned here explicitly, including the fact that an exact
 * package match always outranks it.
 */
class SourceMatcherTest {

    @Test
    fun exactYapePackageMatchesYape() {
        assertEquals(
            SourceId.YAPE,
            SourceMatcher.match("com.bcp.innovacxion.yapeapp", null),
        )
    }

    @Test
    fun exactYapePackageMatchesEvenWhenTheLabelSaysSomethingElse() {
        assertEquals(
            SourceId.YAPE,
            SourceMatcher.match("com.bcp.innovacxion.yapeapp", "BBVA Perú"),
        )
    }

    @Test
    fun unrelatedPackageWithNoMatchingLabelReturnsNull() {
        assertNull(SourceMatcher.match("com.whatsapp", "WhatsApp"))
    }

    @Test
    fun bbvaPackageWithBbvaLabelMatchesBbvaByFragment() {
        assertEquals(
            SourceId.BBVA,
            SourceMatcher.match("com.bbva.nxt_peru", "BBVA Perú"),
        )
    }

    @Test
    fun packageLabelledInterbankMatchesInterbank() {
        assertEquals(
            SourceId.INTERBANK,
            SourceMatcher.match("pe.interbank.mobile", "Interbank"),
        )
    }

    @Test
    fun labelMatchingIsCaseInsensitiveAndIgnoresSurroundingWhitespace() {
        assertEquals(SourceId.BBVA, SourceMatcher.match("com.example.other", "  bbva perú  "))
        assertEquals(SourceId.INTERBANK, SourceMatcher.match("com.example.other", "INTERBANK"))
    }

    @Test
    fun nullLabelNeverCrashesAndCannotLabelMatch() {
        assertNull(SourceMatcher.match("com.example.other", null))
    }

    @Test
    fun blankOrEmptyInputNeverCrashes() {
        assertNull(SourceMatcher.match("", null))
        assertNull(SourceMatcher.match("", ""))
        assertNull(SourceMatcher.match("com.example.other", "   "))
    }

    @Test
    fun aBlankPackageStillMatchesOnTheLabelFragment() {
        assertEquals(SourceId.YAPE, SourceMatcher.match("", "Yape"))
    }

    @Test
    fun packagePrefixIsNotAnExactMatch() {
        assertNull(SourceMatcher.match("com.bcp.innovacxion.yapeapp.debug", null))
    }

    @Test
    fun exactPackageBeatsAFragmentCollision() {
        assertEquals(
            SourceId.YAPE,
            SourceMatcher.match("com.bcp.innovacxion.yapeapp", "Interbank"),
        )
    }

    @Test
    fun fragmentCollisionResolvesToTheFirstSourceInDisplayOrder() {
        assertEquals(
            SourceId.YAPE,
            SourceMatcher.match("com.example.other", "Yape e Interbank"),
        )
        assertEquals(
            SourceId.BBVA,
            SourceMatcher.match("com.example.other", "BBVA e Interbank"),
        )
    }
}
