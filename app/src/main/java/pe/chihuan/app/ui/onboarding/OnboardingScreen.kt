package pe.chihuan.app.ui.onboarding

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.chihuan.app.R
import pe.chihuan.app.core.source.SourceRegistry
import pe.chihuan.app.permission.NotificationAccessState
import pe.chihuan.app.ui.common.BatteryOptimizationCard
import pe.chihuan.app.ui.common.NotificationAccessCard
import pe.chihuan.app.ui.common.RestrictedSettingsCard
import pe.chihuan.app.ui.common.SourceRow
import pe.chihuan.app.ui.theme.ChihuanTheme

/**
 * First-run setup flow, shown while notification access is missing.
 *
 * The flow explains the app, lists the four setup steps, opens the system screens the user needs
 * and always offers a way out, so an incomplete setup can never trap the user. Every screen can be
 * left at any time and the state is re-read by the host on each foreground return.
 *
 * Copy is neutral professional Spanish because the app ships to Peruvian users.
 */
@Composable
fun OnboardingScreen(
    notificationAccessState: NotificationAccessState,
    batteryOptimizationExempt: Boolean,
    sources: List<SourceRow>,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onContinueToPanel: () -> Unit,
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
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyMedium,
        )

        NotificationAccessCard(
            state = notificationAccessState,
            onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
        )

        SetupSteps()

        Text(
            text = stringResource(R.string.onboarding_plin_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ArmedSourcesSummary(sources = sources)

        BatteryOptimizationCard(
            exempt = batteryOptimizationExempt,
            onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
        )

        if (showRestrictedSettingsGuidance) {
            RestrictedSettingsCard()
        }

        OutlinedButton(
            onClick = onContinueToPanel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_continue_without_access))
        }
    }
}

@Composable
private fun SetupSteps() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.onboarding_steps_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_step_1),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_step_2),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_step_3),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_step_4),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * States the armed sources plainly and reminds the user that arming alone announces nothing.
 *
 * Rendering driven by [sources] keeps this line correct when the registry grows.
 */
@Composable
private fun ArmedSourcesSummary(sources: List<SourceRow>) {
    val armedLabels = sources.filter { row -> row.armed }.map { row -> row.source.label }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.onboarding_sources_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (armedLabels.isEmpty()) {
                stringResource(R.string.onboarding_sources_none)
            } else {
                stringResource(R.string.onboarding_sources_armed, armedLabels.joinToString(", "))
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_sources_dependency),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingAccessDeniedPreview() {
    ChihuanTheme {
        OnboardingScreen(
            notificationAccessState = NotificationAccessState.DENIED,
            batteryOptimizationExempt = false,
            sources = SourceRegistry.entries.map { source -> SourceRow(source, armed = true) },
            onOpenNotificationAccessSettings = {},
            onOpenBatteryOptimizationSettings = {},
            onContinueToPanel = {},
        )
    }
}
