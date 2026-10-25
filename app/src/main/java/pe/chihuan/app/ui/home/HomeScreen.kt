package pe.chihuan.app.ui.home

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Locale
import pe.chihuan.app.R
import pe.chihuan.app.speech.TtsStatus
import pe.chihuan.app.ui.theme.ChihuanTheme

/**
 * Single setup screen of the first slice: notification access state, the action that opens the
 * system access screen, and the voice test.
 *
 * Copy is neutral professional Spanish because the app ships to Peruvian users.
 */
@Composable
fun HomeScreen(
    notificationAccessGranted: Boolean,
    ttsStatus: TtsStatus,
    onOpenNotificationAccessSettings: () -> Unit,
    onTestVoice: () -> Unit,
    modifier: Modifier = Modifier,
    showRestrictedSettingsGuidance: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )

        NotificationAccessCard(
            granted = notificationAccessGranted,
            onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
        )

        VoiceSection(ttsStatus = ttsStatus, onTestVoice = onTestVoice)

        if (showRestrictedSettingsGuidance) {
            GuidanceCard(
                title = stringResource(R.string.restricted_settings_title),
                explanation = stringResource(R.string.restricted_settings_explanation),
            )
        }
    }
}

@Composable
private fun NotificationAccessCard(
    granted: Boolean,
    onOpenNotificationAccessSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (granted) R.string.access_granted_title else R.string.access_denied_title,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (granted) {
                        R.string.access_granted_explanation
                    } else {
                        R.string.access_denied_explanation
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            val label = stringResource(R.string.access_open_settings)
            if (granted) {
                OutlinedButton(onClick = onOpenNotificationAccessSettings) { Text(label) }
            } else {
                Button(onClick = onOpenNotificationAccessSettings) { Text(label) }
            }
        }
    }
}

@Composable
private fun VoiceSection(ttsStatus: TtsStatus, onTestVoice: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onTestVoice,
            enabled = ttsStatus is TtsStatus.Ready,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.voice_test_action))
        }
        Text(
            text = voiceStatusText(ttsStatus),
            style = MaterialTheme.typography.bodyMedium,
            color = if (ttsStatus is TtsStatus.Unavailable) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = stringResource(R.string.voice_test_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GuidanceCard(title: String, explanation: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = explanation, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun voiceStatusText(status: TtsStatus): String = when (status) {
    TtsStatus.Initializing -> stringResource(R.string.voice_status_initializing)
    is TtsStatus.Ready -> stringResource(
        R.string.voice_status_ready,
        status.locale.toLanguageTag(),
    )

    is TtsStatus.Unavailable -> stringResource(
        when (status.reason) {
            TtsStatus.Reason.ENGINE_INIT_FAILED -> R.string.voice_status_engine_unavailable
            TtsStatus.Reason.NO_SPANISH_LANGUAGE -> R.string.voice_status_no_spanish_voice
            TtsStatus.Reason.LANGUAGE_DATA_MISSING -> R.string.voice_status_missing_language_data
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenAccessDeniedPreview() {
    ChihuanTheme {
        HomeScreen(
            notificationAccessGranted = false,
            ttsStatus = TtsStatus.Ready(Locale.forLanguageTag("es-PE")),
            onOpenNotificationAccessSettings = {},
            onTestVoice = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenAccessGrantedPreview() {
    ChihuanTheme {
        HomeScreen(
            notificationAccessGranted = true,
            ttsStatus = TtsStatus.Unavailable(TtsStatus.Reason.NO_SPANISH_LANGUAGE),
            onOpenNotificationAccessSettings = {},
            onTestVoice = {},
        )
    }
}
