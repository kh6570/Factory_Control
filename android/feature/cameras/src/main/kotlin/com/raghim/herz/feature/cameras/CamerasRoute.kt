// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.cameras

import android.content.res.Resources
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raghim.herz.feature.cameras.model.CamerasEffect
import com.raghim.herz.feature.cameras.model.CamerasMessage

@Composable
internal fun CamerasRouteContent(
    onAddCamera: () -> Unit,
    viewModel: CamerasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CamerasEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message.text(resources))
            }
        }
    }

    CamerasScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        onAddCamera = onAddCamera,
    )
}

private fun CamerasMessage.text(resources: Resources): String = when (this) {
    is CamerasMessage.Removed -> resources.getString(R.string.cameras_message_removed, name)
    is CamerasMessage.Started -> resources.getQuantityString(R.plurals.cameras_message_started, count, count)
    is CamerasMessage.Stopped -> resources.getQuantityString(R.plurals.cameras_message_stopped, count, count)
    CamerasMessage.ActionFailed -> resources.getString(R.string.cameras_message_failed)
}
