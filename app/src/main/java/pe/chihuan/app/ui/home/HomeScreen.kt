package pe.chihuan.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import pe.chihuan.app.core.source.SourceId
import pe.chihuan.app.core.source.SourceRegistry
import pe.chihuan.app.permission.NotificationAccessState
import pe.chihuan.app.speech.TtsStatus
import pe.chihuan.app.ui.common.SourceRow
import pe.chihuan.app.ui.onboarding.OnboardingScreen
import pe.chihuan.app.ui.panel.PanelScreen
import pe.chihuan.app.ui.theme.ChihuanTheme

/** The two screens of the app. There is no navigation library: one Activity, two states. */
enum class HomeDestination {
    ONBOARDING,
    PANEL,
}

/**
 * Root screen: shows the onboarding while notification access is missing and the panel once it is
 * granted, and lets the user move between them at any time.
 *
 * Rules this shell enforces:
 * - a fresh install with no access starts on the onboarding;
 * - the onboarding can always be left for the panel, so an incomplete setup never traps the user;
 * - the onboarding stays reachable from the panel afterwards;
 * - a grant observed while the onboarding is open advances to the panel exactly once, without
 *   stopping the user from reopening the onboarding later.
 *
 * All state is handed in; this composable only decides which screen is visible.
 */
@Composable
fun HomeScreen(
    notificationAccessState: NotificationAccessState,
    batteryOptimizationExempt: Boolean,
    ttsStatus: TtsStatus,
    sources: List<SourceRow>,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onTestVoice: () -> Unit,
    onSourceArmedChange: (SourceId, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accessGranted = notificationAccessState == NotificationAccessState.GRANTED

    var destination by rememberSaveable {
        mutableStateOf(if (accessGranted) HomeDestination.PANEL else HomeDestination.ONBOARDING)
    }
    var previouslyGranted by rememberSaveable { mutableStateOf(accessGranted) }

    // Advance once when a grant is first observed; a later re-open of the onboarding is respected.
    LaunchedEffect(accessGranted) {
        if (accessGranted && !previouslyGranted) {
            destination = HomeDestination.PANEL
        }
        previouslyGranted = accessGranted
    }

    BackHandler(enabled = destination == HomeDestination.ONBOARDING) {
        destination = HomeDestination.PANEL
    }

    when (destination) {
        HomeDestination.ONBOARDING -> OnboardingScreen(
            notificationAccessState = notificationAccessState,
            batteryOptimizationExempt = batteryOptimizationExempt,
            sources = sources,
            onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
            onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
            onContinueToPanel = { destination = HomeDestination.PANEL },
            modifier = modifier,
        )

        HomeDestination.PANEL -> PanelScreen(
            notificationAccessState = notificationAccessState,
            batteryOptimizationExempt = batteryOptimizationExempt,
            ttsStatus = ttsStatus,
            sources = sources,
            onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
            onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
            onTestVoice = onTestVoice,
            onSourceArmedChange = onSourceArmedChange,
            onOpenOnboarding = { destination = HomeDestination.ONBOARDING },
            modifier = modifier,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenFreshInstallPreview() {
    ChihuanTheme {
        HomeScreen(
            notificationAccessState = NotificationAccessState.DENIED,
            batteryOptimizationExempt = false,
            ttsStatus = TtsStatus.Initializing,
            sources = SourceRegistry.entries.map { source -> SourceRow(source, armed = true) },
            onOpenNotificationAccessSettings = {},
            onOpenBatteryOptimizationSettings = {},
            onTestVoice = {},
            onSourceArmedChange = { _, _ -> },
        )
    }
}
