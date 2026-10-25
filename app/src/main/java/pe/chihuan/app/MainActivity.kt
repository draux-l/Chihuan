package pe.chihuan.app

import android.content.Intent
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
import androidx.core.app.NotificationManagerCompat
import pe.chihuan.app.speech.TtsAnnouncer
import pe.chihuan.app.speech.TtsStatus
import pe.chihuan.app.ui.home.HomeScreen
import pe.chihuan.app.ui.theme.ChihuanTheme

class MainActivity : ComponentActivity() {

    private lateinit var announcer: TtsAnnouncer
    private var notificationAccessGranted by mutableStateOf(false)
    private var ttsStatus by mutableStateOf<TtsStatus>(TtsStatus.Initializing)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startAnnouncer()

        setContent {
            ChihuanTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        notificationAccessGranted = notificationAccessGranted,
                        ttsStatus = ttsStatus,
                        onOpenNotificationAccessSettings = ::openNotificationAccessSettings,
                        onTestVoice = { announcer.speak(SAMPLE_UTTERANCE) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Read from the system on every foreground return instead of trusting a first-launch value.
        notificationAccessGranted = hasNotificationAccess()
    }

    override fun onDestroy() {
        announcer.release()
        super.onDestroy()
    }

    private fun startAnnouncer() {
        val created = TtsAnnouncer(this)
        created.onStatusChanged = { status -> ttsStatus = status }
        created.initialize()
        announcer = created
        ttsStatus = created.status
    }

    /**
     * Notification access is a settings-granted permission, so the only honest check is the
     * system's own enabled-listener set for this package.
     */
    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (exception: Exception) {
            // Some OEM builds do not expose the notification-access screen.
            Log.w(LOG_TAG, "Notification access settings could not be opened", exception)
        }
    }

    private companion object {
        const val LOG_TAG = "ChihuanMain"

        /** Fixed sample for the voice test; announcement composition is later work. */
        const val SAMPLE_UTTERANCE =
            "Juan Pérez te yapeó seis soles con cincuenta. Código: uno dos tres."
    }
}
