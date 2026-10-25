package pe.chihuan.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pe.chihuan.app.R
import pe.chihuan.app.core.source.Source
import pe.chihuan.app.permission.NotificationAccessState

/**
 * One registry source together with the user's currently stored choice for it.
 *
 * Rows are built from [pe.chihuan.app.core.source.SourceRegistry] order, never from a per-source
 * branch, so adding a source adds a row without touching any screen.
 */
data class SourceRow(
    val source: Source,
    val armed: Boolean,
)

/*
 * Setup pieces shared by the onboarding and the control panel.
 *
 * Both screens report the same permission facts with the same copy, so the state-to-copy mapping
 * lives here once instead of being duplicated per screen.
 */

/** Blocking or healthy notification-access card with the action that opens system settings. */
@Composable
internal fun NotificationAccessCard(
    state: NotificationAccessState,
    onOpenNotificationAccessSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val granted = state == NotificationAccessState.GRANTED

    Card(
        modifier = modifier.fillMaxWidth(),
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
                    when (state) {
                        NotificationAccessState.GRANTED -> R.string.access_granted_title
                        NotificationAccessState.DENIED -> R.string.access_denied_title
                        NotificationAccessState.REVOKED -> R.string.access_revoked_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    when (state) {
                        NotificationAccessState.GRANTED -> R.string.access_granted_explanation
                        NotificationAccessState.DENIED -> R.string.access_denied_explanation
                        NotificationAccessState.REVOKED -> R.string.access_revoked_explanation
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

/** Battery-optimization guidance: detected exemption state plus the system-screen action. */
@Composable
internal fun BatteryOptimizationCard(
    exempt: Boolean,
    onOpenBatteryOptimizationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.battery_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.battery_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    if (exempt) R.string.battery_exempt else R.string.battery_not_exempt,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onOpenBatteryOptimizationSettings) {
                Text(stringResource(R.string.battery_open_settings))
            }
        }
    }
}

/** Static, unverified guidance; restricted-settings state has no public API to detect. */
@Composable
internal fun RestrictedSettingsCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.restricted_settings_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.restricted_settings_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
