// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.navOptions
import com.raghim.herz.feature.cameras.navigation.camerasScreen
import com.raghim.herz.feature.cameras.navigation.navigateToCameras
import com.raghim.herz.feature.discovery.navigation.discoveryScreen
import com.raghim.herz.feature.discovery.navigation.navigateToDiscovery
import com.raghim.herz.feature.liveview.navigation.LiveViewRoute
import com.raghim.herz.feature.liveview.navigation.liveViewScreen
import com.raghim.herz.feature.liveview.navigation.navigateToLiveView

/** Features never call each other; every cross-feature jump is wired here. */
@Composable
fun HerzNavHost(
    navController: NavHostController,
    onFullscreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = LiveViewRoute(),
        modifier = modifier,
    ) {
        liveViewScreen(
            onAddCameras = navController::navigateToDiscovery,
            onFullscreenChanged = onFullscreenChanged,
        )
        camerasScreen(
            onAddCamera = navController::navigateToDiscovery,
            onOpenLive = { navController.navigateToTopLevel(TopLevelDestination.LIVE) },
        )
        discoveryScreen(
            onBack = navController::popBackStack,
            onCameraAdded = { navController.navigateToTopLevel(TopLevelDestination.LIVE) },
        )
    }
}

fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    val options = navOptions {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    when (destination) {
        TopLevelDestination.LIVE -> navigateToLiveView(options)
        TopLevelDestination.CAMERAS -> navigateToCameras(options)
    }
}
