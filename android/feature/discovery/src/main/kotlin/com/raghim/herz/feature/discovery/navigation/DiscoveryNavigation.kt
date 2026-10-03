// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.discovery.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.raghim.herz.feature.discovery.DiscoveryRouteContent
import kotlinx.serialization.Serializable

@Serializable
data object DiscoveryRoute

fun NavController.navigateToDiscovery(navOptions: NavOptions? = null) = navigate(DiscoveryRoute, navOptions)

fun NavGraphBuilder.discoveryScreen(
    onBack: () -> Unit,
    onCameraAdded: () -> Unit,
) {
    composable<DiscoveryRoute> {
        DiscoveryRouteContent(onBack = onBack, onCameraAdded = onCameraAdded)
    }
}
