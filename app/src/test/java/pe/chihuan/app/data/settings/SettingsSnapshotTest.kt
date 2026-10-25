package pe.chihuan.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.chihuan.app.core.model.ContentSelection

/**
 * The stored content toggles are the only input of the spoken-field selection, so the mapping is
 * pinned here: a fresh install speaks every group, and a stored off flag hides exactly that group.
 */
class SettingsSnapshotTest {

    @Test
    fun aFreshSnapshotSpeaksAmountNameAndCode() {
        val snapshot = SettingsSnapshot(armedSourceIds = emptySet())

        assertEquals(ContentSelection.Default, snapshot.contentSelection())
    }

    @Test
    fun storedFlagsMapToTheMatchingSelection() {
        val snapshot = SettingsSnapshot(
            armedSourceIds = emptySet(),
            sayAmount = false,
            sayName = true,
            sayCode = false,
        )

        assertEquals(
            ContentSelection(sayAmount = false, sayName = true, sayCode = false),
            snapshot.contentSelection(),
        )
    }
}
