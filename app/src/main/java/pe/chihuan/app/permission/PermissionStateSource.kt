package pe.chihuan.app.permission

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import pe.chihuan.app.data.settings.AppSettings

/** Notification-access state as the specs define it, derived from the live system state. */
enum class NotificationAccessState {
    /** Access has never been granted on this install. */
    DENIED,

    /** This app is present in the system's enabled notification listeners. */
    GRANTED,

    /** Access was granted on this install and is now off. */
    REVOKED,
}

/**
 * Reads the permissions Chihuan depends on, from the system, every time it is asked.
 *
 * Nothing here is cached: an instance can be created once, but every call re-queries the platform,
 * so a return from system settings is always reflected without a restart. Permission state can be
 * granted only in system settings, so this class never tries to grant anything.
 *
 * It also owns the one onboarding fact written to settings — "access was once granted" — because
 * observing a live grant is exactly where that fact becomes true.
 */
class PermissionStateSource(
    context: Context,
    private val settings: AppSettings,
) {

    private val appContext: Context = context.applicationContext

    /**
     * Current notification-access state.
     *
     * A live grant is recorded in settings before it is reported, so a later revocation is
     * distinguishable from an access that never existed.
     */
    fun notificationAccessState(): NotificationAccessState {
        val granted = NotificationManagerCompat.getEnabledListenerPackages(appContext)
            .contains(appContext.packageName)

        if (granted) {
            settings.markNotificationAccessGranted()
            return NotificationAccessState.GRANTED
        }

        return if (settings.snapshot().notificationAccessWasGranted) {
            NotificationAccessState.REVOKED
        } else {
            NotificationAccessState.DENIED
        }
    }

    /**
     * True when Android's battery optimizer is already ignoring this app.
     *
     * `isIgnoringBatteryOptimizations` is API 23+, so no version guard is needed at minSdk 24. A
     * missing service reports "not exempt", which is the honest answer for a device that cannot be
     * queried.
     */
    fun isBatteryOptimizationExempt(): Boolean {
        val powerManager = appContext.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
    }
}
