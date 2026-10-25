package pe.chihuan.app.core.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry is the single list of readable sources (`source-management` spec), so its order,
 * labels and package declarations are pinned here instead of inside the panel.
 */
class SourceRegistryTest {

    @Test
    fun registryExposesEverySourceIdExactlyOnce() {
        assertEquals(SourceId.entries.toList(), SourceRegistry.entries.map { it.id })
    }

    @Test
    fun displayOrderIsYapeThenBbvaThenInterbank() {
        assertEquals(
            listOf(SourceId.YAPE, SourceId.BBVA, SourceId.INTERBANK),
            SourceRegistry.entries.map { it.id },
        )
    }

    @Test
    fun everySourceIdIsRegistered() {
        SourceId.entries.forEach { id ->
            assertTrue("missing registry entry for $id", SourceRegistry.entries.any { it.id == id })
        }
    }

    @Test
    fun yapeCarriesTheVerifiedBcpPackage() {
        assertEquals(
            setOf("com.bcp.innovacxion.yapeapp"),
            SourceRegistry.byId(SourceId.YAPE).packages,
        )
    }

    @Test
    fun unverifiedSourcesDeclareNoPackageInsteadOfGuessingOne() {
        assertTrue(SourceRegistry.byId(SourceId.BBVA).packages.isEmpty())
        assertTrue(SourceRegistry.byId(SourceId.INTERBANK).packages.isEmpty())
    }

    @Test
    fun everySourceHasLabelFragmentsForTheTemporaryBridge() {
        SourceRegistry.entries.forEach { source ->
            assertTrue("$source has no label fragment", source.labelFragments.isNotEmpty())
        }
    }

    @Test
    fun labelFragmentsAreLowercaseSoMatchingIsCaseInsensitive() {
        assertEquals(setOf("yape"), SourceRegistry.byId(SourceId.YAPE).labelFragments)
        assertEquals(setOf("bbva"), SourceRegistry.byId(SourceId.BBVA).labelFragments)
        assertEquals(setOf("interbank"), SourceRegistry.byId(SourceId.INTERBANK).labelFragments)
    }

    @Test
    fun labelsAreTheBrandNamesShownOnThePanel() {
        assertEquals("Yape", SourceRegistry.byId(SourceId.YAPE).label)
        assertEquals("BBVA", SourceRegistry.byId(SourceId.BBVA).label)
        assertEquals("Interbank", SourceRegistry.byId(SourceId.INTERBANK).label)
    }

    @Test
    fun byIdReturnsTheSameEntryAsTheOrderedList() {
        SourceRegistry.entries.forEach { entry ->
            assertEquals(entry, SourceRegistry.byId(entry.id))
        }
    }

    @Test
    fun sourceIdsAreStableSettingsKeyFragments() {
        assertEquals("yape", SourceId.YAPE.key)
        assertEquals("bbva", SourceId.BBVA.key)
        assertEquals("interbank", SourceId.INTERBANK.key)
    }
}
