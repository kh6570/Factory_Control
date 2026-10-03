// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.discovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raghim.herz.feature.discovery.model.DiscoveryEffect

@Composable
internal fun DiscoveryRouteContent(
    onBack: () -> Unit,
    onCameraAdded: () -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnCameraAdded by rememberUpdatedState(onCameraAdded)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DiscoveryEffect.CameraAdded -> currentOnCameraAdded()
            }
        }
    }

    DiscoveryScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}
