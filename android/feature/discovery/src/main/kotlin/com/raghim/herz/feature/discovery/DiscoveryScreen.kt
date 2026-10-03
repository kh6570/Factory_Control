// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.DiscoveryMethod
import com.raghim.herz.feature.discovery.components.ConnectSheet
import com.raghim.herz.feature.discovery.components.DeviceRow
import com.raghim.herz.feature.discovery.components.NothingFoundHelp
import com.raghim.herz.feature.discovery.model.DiscoveredDeviceItem
import com.raghim.herz.feature.discovery.model.DiscoveryIntent
import com.raghim.herz.feature.discovery.model.DiscoveryUiState
import com.raghim.herz.feature.discovery.model.ManualMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryScreen(
    state: DiscoveryUiState,
    onIntent: (DiscoveryIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discovery_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.discovery_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(DiscoveryIntent.Rescan) }, enabled = !state.isScanning) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.discovery_rescan))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (state.isScanning) {
                    item(key = "scanning") { ScanningIndicator() }
                }
                if (state.devices.isNotEmpty()) {
                    item(key = "found_header") {
                        SectionHeader(stringResource(R.string.discovery_found_header))
                    }
                    items(state.devices, key = { it.device.host }) { item ->
                        DeviceRow(
                            item = item,
                            onClick = { onIntent(DiscoveryIntent.SelectDevice(item.device)) },
                        )
                    }
                }
                if (state.showNothingFound) {
                    item(key = "nothing_found") {
                        NothingFoundHelp(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                item(key = "manual") {
                    ManualOptions(
                        onEnterIp = { onIntent(DiscoveryIntent.AddManually(ManualMode.Host)) },
                        onEnterUrl = { onIntent(DiscoveryIntent.AddManually(ManualMode.RtspUrl)) },
                    )
                }
            }
        }
    }

    state.form?.let { form -> ConnectSheet(form = form, onIntent = onIntent) }
}

@Composable
private fun ScanningIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.discovery_searching),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ManualOptions(onEnterIp: () -> Unit, onEnterUrl: () -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.discovery_manual_header))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onEnterIp, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.discovery_enter_ip), textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = onEnterUrl, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.discovery_enter_rtsp_url), textAlign = TextAlign.Center)
            }
        }
    }
}

@Preview
@Composable
private fun DiscoveryScreenPreview() {
    HerzTheme {
        DiscoveryScreen(
            state = DiscoveryUiState(
                isScanning = true,
                devices = listOf(
                    DiscoveredDeviceItem(
                        DiscoveredDevice("192.168.1.20", DiscoveryMethod.ONVIF, name = "Gate"),
                        alreadyAdded = true,
                    ),
                    DiscoveredDeviceItem(
                        DiscoveredDevice("192.168.1.31", DiscoveryMethod.PORT_SCAN),
                        alreadyAdded = false,
                    ),
                ),
            ),
            onIntent = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun DiscoveryNothingFoundPreview() {
    HerzTheme {
        DiscoveryScreen(
            state = DiscoveryUiState(hasScanned = true),
            onIntent = {},
            onBack = {},
        )
    }
}
