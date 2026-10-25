package pe.chihuan.app

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import pe.chihuan.app.core.announce.AnnouncementComposer
import pe.chihuan.app.core.parser.Parsed
import pe.chihuan.app.core.source.SourceId
import pe.chihuan.app.core.source.SourceRegistry
import pe.chihuan.app.data.settings.AppSettings
import pe.chihuan.app.listener.NotificationPipeline
import pe.chihuan.app.permission.NotificationAccessState
import pe.chihuan.app.permission.PermissionStateSource
import pe.chihuan.app.speech.SpeechQueue
import pe.chihuan.app.speech.TtsAnnouncer
import pe.chihuan.app.speech.TtsStatus
import pe.chihuan.app.ui.common.SourceRow
import pe.chihuan.app.ui.home.HomeScreen
import pe.chihuan.app.ui.theme.ChihuanTheme

/**
 * Single-activity host for the onboarding and the control panel.
 *
 * The process-scoped collaborators (settings, speech engine, announcement queue, notification
 * pipeline) live in [ChihuanApplication]; this Activity only reads them and renders their state, so
 * speech keeps working after the Activity is destroyed and no second engine is ever created.
 *
 * Permission and stored source state are re-read on every foreground return, never cached from
 * first launch, so returning from system settings always shows the truth.
 */
class MainActivity : ComponentActivity() {

    private lateinit var announcer: TtsAnnouncer
    private lateinit var speechQueue: SpeechQueue
    private lateinit var pipeline: NotificationPipeline
    private lateinit var settings: AppSettings
    private lateinit var permissionState: PermissionStateSource
    private var settingsObserver: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var showDebugActions = false

    private var notificationAccessState by mutableStateOf(NotificationAccessState.DENIED)
    private var batteryOptimizationExempt by mutableStateOf(false)
    private var ttsStatus by mutableStateOf<TtsStatus>(TtsStatus.Initializing)
    private var sourceRows by mutableStateOf(emptyList<SourceRow>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ChihuanApplication
        settings = app.settings
        announcer = app.ttsAnnouncer
        speechQueue = app.speechQueue
        pipeline = app.pipeline

        permissionState = PermissionStateSource(this, settings)
        startAnnouncer()
        showDebugActions = isDebuggableBuild()
        refreshPermissionState()
        refreshSourceRows()

        setContent {
            ChihuanTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        notificationAccessState = notificationAccessState,
                        batteryOptimizationExempt = batteryOptimizationExempt,
                        ttsStatus = ttsStatus,
                        sources = sourceRows,
                        onOpenNotificationAccessSettings = ::openNotificationAccessSettings,
                        onOpenBatteryOptimizationSettings = ::openBatteryOptimizationSettings,
                        onTestVoice = ::testVoice,
                        onSourceArmedChange = ::setSourceArmed,
                        showDebugActions = showDebugActions,
                        onSimulatePayment = pipeline::submitSamplePayment,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Stored source state can also change outside this screen; reflect that while visible.
        settingsObserver = settings.observeChanges { refreshSourceRows() }
    }

    override fun onResume() {
        super.onResume()
        // System settings can change while the app is in the background: never trust a cached value.
        refreshPermissionState()
        refreshSourceRows()
    }

    override fun onStop() {
        settingsObserver?.let(settings::removeObserver)
        settingsObserver = null
        super.onStop()
    }

    override fun onDestroy() {
        // The engine is process-scoped and must outlive this Activity; only the UI callback goes away.
        announcer.onStatusChanged = null
        super.onDestroy()
    }

    private fun startAnnouncer() {
        announcer.onStatusChanged = { status -> ttsStatus = status }
        announcer.initialize()
        ttsStatus = announcer.status
    }

    private fun refreshPermissionState() {
        notificationAccessState = permissionState.notificationAccessState()
        batteryOptimizationExempt = permissionState.isBatteryOptimizationExempt()
    }

    /**
     * Builds one row per registry entry, in registry order, from the stored armed flags.
     *
     * The panel never holds its own copy of the choice, so rendering and storage cannot diverge.
     */
    private fun refreshSourceRows() {
        val snapshot = settings.snapshot()
        sourceRows = SourceRegistry.entries.map { source ->
            SourceRow(source = source, armed = snapshot.isArmed(source.id))
        }
    }

    private fun setSourceArmed(sourceId: SourceId, armed: Boolean) {
        settings.setSourceArmed(sourceId, armed)
        refreshSourceRows()
    }

    /**
     * `Probar voz` speaks the fixed sample through the same composer and queue as a real payment,
     * ignoring the source toggles because it is an explicit user test.
     */
    private fun testVoice() {
        val selection = settings.snapshot().contentSelection()
        speechQueue.announce(AnnouncementComposer.compose(SAMPLE_PAYMENT, selection))
    }

    private fun isDebuggableBuild(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun openNotificationAccessSettings() {
        startSettingsScreen(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, "notification access")
    }

    private fun openBatteryOptimizationSettings() {
        startSettingsScreen(
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            "battery optimization",
        )
    }

    /**
     * Opens a system settings screen, tolerating devices whose build does not expose it.
     *
     * Both screens are guidance paths, so a missing activity must degrade to a log line instead of
     * crashing the app.
     */
    private fun startSettingsScreen(action: String, description: String) {
        try {
            startActivity(Intent(action))
        } catch (exception: Exception) {
            Log.w(LOG_TAG, "The $description settings screen could not be opened", exception)
        }
    }

    private companion object {
        const val LOG_TAG = "ChihuanMain"

        /** Fixed sample for `Probar voz`; the voice test never depends on a real payment. */
        val SAMPLE_PAYMENT: Parsed = Parsed(
            amountCents = 650,
            name = "Juan Pérez",
            code = "123",
        )
    }
}
