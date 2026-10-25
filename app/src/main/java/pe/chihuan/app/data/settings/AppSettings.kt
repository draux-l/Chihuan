package pe.chihuan.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import pe.chihuan.app.core.source.SourceId
import pe.chihuan.app.core.source.SourceRegistry

/**
 * Names, defaults and key shapes of the settings file.
 *
 * Deliberately free of Android types so the persistence contract — including the "configuration
 * only, never payment data" rule — is readable and reviewable in one place.
 */
object SettingsContract {

    /** Settings live in their own file, separate from any payment record. */
    const val FILE_NAME = "chihuan_settings"

    /**
     * The single onboarding fact: access to notifications was once granted.
     *
     * It is written when a grant is observed and never cleared, which is what distinguishes a
     * revoked grant from one that never happened.
     */
    const val KEY_NOTIFICATION_ACCESS_WAS_GRANTED = "notification_access_was_granted"

    /** A missing or unreadable armed flag falls back to armed, because silence hides payments. */
    const val DEFAULT_SOURCE_ARMED = true

    /** Default for [KEY_NOTIFICATION_ACCESS_WAS_GRANTED]. */
    const val DEFAULT_NOTIFICATION_ACCESS_WAS_GRANTED = false

    /** One armed key per registry source, e.g. `source_armed_yape`. */
    fun sourceArmedKey(sourceId: SourceId): String = "source_armed_${sourceId.key}"
}

/** Immutable read of the stored configuration at one moment. */
data class SettingsSnapshot(
    /** Sources the user has left armed. */
    val armedSourceIds: Set<SourceId>,
    /** True once notification access was observed as granted on this install. */
    val notificationAccessWasGranted: Boolean,
) {
    fun isArmed(sourceId: SourceId): Boolean = sourceId in armedSourceIds
}

/**
 * SharedPreferences-backed settings store.
 *
 * Thin on purpose: it only reads and writes the configuration keys declared in [SettingsContract],
 * with per-key defaults and fault tolerance. No DataStore, no coroutines, no cached state — a
 * caller asking for [snapshot] always gets what is stored right now, which is what lets the panel
 * re-read on every foreground return without a stale copy.
 *
 * Readings are typed getters with defaults; a value stored under the wrong type falls back to the
 * default instead of throwing.
 */
class AppSettings(context: Context) {

    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        SettingsContract.FILE_NAME,
        Context.MODE_PRIVATE,
    )

    /** Reads every known setting now. Never throws. */
    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        armedSourceIds = SourceRegistry.entries
            .map { source -> source.id }
            .filter { sourceId -> isSourceArmed(sourceId) }
            .toSet(),
        notificationAccessWasGranted = readBoolean(
            SettingsContract.KEY_NOTIFICATION_ACCESS_WAS_GRANTED,
            SettingsContract.DEFAULT_NOTIFICATION_ACCESS_WAS_GRANTED,
        ),
    )

    /** Reads one source's armed flag, defaulting to armed. Never throws. */
    fun isSourceArmed(sourceId: SourceId): Boolean = readBoolean(
        SettingsContract.sourceArmedKey(sourceId),
        SettingsContract.DEFAULT_SOURCE_ARMED,
    )

    /**
     * Persists one source's armed flag. Takes the registry id rather than a raw string so an
     * orphan key cannot be written by a typo.
     */
    fun setSourceArmed(sourceId: SourceId, armed: Boolean) {
        preferences.edit().putBoolean(SettingsContract.sourceArmedKey(sourceId), armed).apply()
    }

    /**
     * Records that notification access was observed as granted. Idempotent: once stored, later
     * calls write nothing, and nothing ever clears it.
     */
    fun markNotificationAccessGranted() {
        if (readBoolean(
                SettingsContract.KEY_NOTIFICATION_ACCESS_WAS_GRANTED,
                SettingsContract.DEFAULT_NOTIFICATION_ACCESS_WAS_GRANTED,
            )
        ) {
            return
        }
        preferences.edit()
            .putBoolean(SettingsContract.KEY_NOTIFICATION_ACCESS_WAS_GRANTED, true)
            .apply()
    }

    /**
     * Registers [onChange] for any write to this settings file and returns the handle required to
     * stop observing. The listener runs on the main thread, which is where Compose state lives.
     */
    fun observeChanges(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    /** Stops observing. Safe to call with a listener that was never registered. */
    fun removeObserver(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun readBoolean(key: String, default: Boolean): Boolean = try {
        preferences.getBoolean(key, default)
    } catch (exception: ClassCastException) {
        // A mistyped value is treated as missing; disabling a source silently would be worse.
        default
    }
}
