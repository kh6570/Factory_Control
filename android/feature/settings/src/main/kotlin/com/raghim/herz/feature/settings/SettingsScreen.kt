// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raghim.herz.core.designsystem.component.HoldToConfirmButton
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.core.domain.door.HoldToOpen
import com.raghim.herz.core.ui.LoadingState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun SettingsRouteContent(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_section_doors),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            HoldToOpenSetting(
                value = state.holdToOpen,
                onChange = { onIntent(SettingsIntent.SetHoldToOpen(it)) },
            )
            FingerprintSetting(
                required = state.requireFingerprint,
                onChange = { onIntent(SettingsIntent.SetRequireFingerprint(it)) },
            )
        }
    }
}

@Composable
private fun FingerprintSetting(required: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.settings_fingerprint), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    if (required) R.string.settings_fingerprint_on else R.string.settings_fingerprint_off,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = required,
            onCheckedChange = onChange,
        )
    }
}

@Composable
private fun HoldToOpenSetting(value: Duration, onChange: (Duration) -> Unit) {
    // Local while dragging, so the store is written once on release, not on every frame.
    var dragging by remember(value) { mutableFloatStateOf(value.inWholeMilliseconds.toFloat()) }
    val min = HoldToOpen.Min.inWholeMilliseconds.toFloat()
    val max = HoldToOpen.Max.inWholeMilliseconds.toFloat()
    val steps = ((max - min) / HoldToOpen.Step.inWholeMilliseconds).toInt() - 1
    val locale = LocalConfiguration.current.locales[0]

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_hold_to_open), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.settings_seconds, String.format(locale, "%.1f", dragging / 1000f)),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            text = stringResource(R.string.settings_hold_to_open_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onChange(dragging.toLong().milliseconds) },
            valueRange = min..max,
            steps = steps,
        )
        Text(
            text = stringResource(R.string.settings_try_it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HoldToConfirmButton(
            label = stringResource(R.string.settings_try_label),
            holdingLabel = stringResource(R.string.settings_try_holding),
            holdDuration = dragging.toLong().milliseconds,
            onConfirm = {},
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun SettingsPreview() {
    HerzTheme(darkTheme = true) {
        SettingsScreen(state = SettingsUiState(isLoading = false), onIntent = {}, onBack = {})
    }
}
