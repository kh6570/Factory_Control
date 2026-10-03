// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.raghim.herz.feature.liveview.ActiveCamerasRoute
import kotlinx.serialization.Serializable

/** The live wall. [alarmId] is set when an alarm opened the wall; alarm cameras already sort first. */
@Serializable
data class LiveViewRoute(val alarmId: String? = null)

fun NavController.navigateToLiveView(navOptions: NavOptions? = null, alarmId: String? = null) =
    navigate(LiveViewRoute(alarmId), navOptions)

/**
 * [onFullscreenChanged] is true while a camera is maximized; the app shell hides its
 * navigation bar or rail then, and shows it again on false.
 */
fun NavGraphBuilder.liveViewScreen(
    onAddCameras: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
) {
    composable<LiveViewRoute> {
        ActiveCamerasRoute(
            onAddCameras = onAddCameras,
            onFullscreenChanged = onFullscreenChanged,
        )
    }
}
