// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.cameras.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.raghim.herz.feature.cameras.CamerasRouteContent
import kotlinx.serialization.Serializable

@Serializable
data object CamerasRoute

fun NavController.navigateToCameras(navOptions: NavOptions? = null) = navigate(CamerasRoute, navOptions)

fun NavGraphBuilder.camerasScreen(
    onAddCamera: () -> Unit,
    onOpenLive: () -> Unit,
) {
    composable<CamerasRoute> {
        CamerasRouteContent(onAddCamera = onAddCamera, onOpenLive = onOpenLive)
    }
}
