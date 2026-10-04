// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.doors.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.raghim.herz.feature.doors.DoorsRouteContent
import kotlinx.serialization.Serializable

@Serializable
data object DoorsRoute

fun NavController.navigateToDoors(navOptions: NavOptions? = null) = navigate(DoorsRoute, navOptions)

fun NavGraphBuilder.doorsScreen(onOpenSettings: () -> Unit) {
    composable<DoorsRoute> { DoorsRouteContent(onOpenSettings = onOpenSettings) }
}
