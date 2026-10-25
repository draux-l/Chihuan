package pe.chihuan.app.ui.panel

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.chihuan.app.R
import pe.chihuan.app.core.source.SourceId
import pe.chihuan.app.core.source.SourceRegistry
import pe.chihuan.app.permission.NotificationAccessState
import pe.chihuan.app.speech.TtsStatus
import pe.chihuan.app.ui.common.BatteryOptimizationCard
import pe.chihuan.app.ui.common.NotificationAccessCard
import pe.chihuan.app.ui.common.RestrictedSettingsCard
import pe.chihuan.app.ui.common.SourceRow
import pe.chihuan.app.ui.theme.ChihuanTheme
import java.util.Locale

/**
 * Control panel: access state, one toggle per registry source, voice test and the setup guidance.
 *
 * The panel owns no state. Every value it renders is handed in by the host, which re-reads the
 * system and the settings store on each foreground return, so a toggle and the stored value can
 * never disagree.
 *
 * Copy is neutral professional Spanish because the app ships to Peruvian users.
 */
@Composable
fun PanelScreen(
    notificationAccessState: NotificationAccessState,
    batteryOptimizationExempt: Boolean,
    ttsStatus: TtsStatus,
    sources: List<SourceRow>,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onTestVoice: () -> Unit,
    onSourceArmedChange: (SourceId, Boolean) -> Unit,
    onOpenOnboarding: () -> Unit,
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
            text = stringResource(R.string.panel_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        NotificationAccessCard(
            state = notificationAccessState,
            onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
        )

        SourceSection(
            sources = sources,
            accessGranted = notificationAccessState == NotificationAccessState.GRANTED,
            onSourceArmedChange = onSourceArmedChange,
        )

        VoiceSection(ttsStatus = ttsStatus, onTestVoice = onTestVoice)

        BatteryOptimizationCard(
            exempt = batteryOptimizationExempt,
            onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
        )

        if (showRestrictedSettingsGuidance) {
            RestrictedSettingsCard()
        }

        OutlinedButton(
            onClick = onOpenOnboarding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.panel_open_onboarding))
        }
    }
}

/**
 * The source list, rendered from registry order.
 *
 * Turning a source off only writes the stored flag; nothing else happens in this slice, which is
 * what the spec's "no side effects" rule requires.
 */
@Composable
private fun SourceSection(
    sources: List<SourceRow>,
    accessGranted: Boolean,
    onSourceArmedChange: (SourceId, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.panel_sources_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.panel_sources_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                sources.forEach { row ->
                    SourceToggleRow(
                        row = row,
                        accessGranted = accessGranted,
                        onSourceArmedChange = onSourceArmedChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceToggleRow(
    row: SourceRow,
    accessGranted: Boolean,
    onSourceArmedChange: (SourceId, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row is the switch target; the Switch itself stays a passive indicator.
            .toggleable(
                value = row.armed,
                role = Role.Switch,
                onValueChange = { armed -> onSourceArmedChange(row.source.id, armed) },
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.source.label,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = sourceStatusText(row = row, accessGranted = accessGranted),
                style = MaterialTheme.typography.bodySmall,
                color = if (row.armed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (row.source.isPackageUnverified) {
                Text(
                    text = stringResource(R.string.source_package_unverified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Switch(checked = row.armed, onCheckedChange = null)
    }
}

/** An off source reads as off; an armed source whose access is missing never reads as reading. */
@Composable
private fun sourceStatusText(row: SourceRow, accessGranted: Boolean): String = when {
    !row.armed -> stringResource(R.string.source_status_off)
    accessGranted -> stringResource(R.string.source_status_reading)
    else -> stringResource(R.string.source_status_waiting_access)
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
private fun PanelAccessGrantedPreview() {
    ChihuanTheme {
        PanelScreen(
            notificationAccessState = NotificationAccessState.GRANTED,
            batteryOptimizationExempt = true,
            ttsStatus = TtsStatus.Ready(Locale.forLanguageTag("es-PE")),
            sources = SourceRegistry.entries.map { source -> SourceRow(source, armed = true) },
            onOpenNotificationAccessSettings = {},
            onOpenBatteryOptimizationSettings = {},
            onTestVoice = {},
            onSourceArmedChange = { _, _ -> },
            onOpenOnboarding = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PanelAccessMissingPreview() {
    ChihuanTheme {
        PanelScreen(
            notificationAccessState = NotificationAccessState.REVOKED,
            batteryOptimizationExempt = false,
            ttsStatus = TtsStatus.Unavailable(TtsStatus.Reason.NO_SPANISH_LANGUAGE),
            sources = SourceRegistry.entries.mapIndexed { index, source ->
                SourceRow(source, armed = index != 1)
            },
            onOpenNotificationAccessSettings = {},
            onOpenBatteryOptimizationSettings = {},
            onTestVoice = {},
            onSourceArmedChange = { _, _ -> },
            onOpenOnboarding = {},
        )
    }
}
