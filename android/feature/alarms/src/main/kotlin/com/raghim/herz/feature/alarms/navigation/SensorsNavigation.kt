// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.alarms.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.raghim.herz.feature.alarms.SensorsRouteContent
import kotlinx.serialization.Serializable

@Serializable
data object SensorsRoute

fun NavController.navigateToSensors(navOptions: NavOptions? = null) = navigate(SensorsRoute, navOptions)

fun NavGraphBuilder.sensorsScreen() {
    composable<SensorsRoute> { SensorsRouteContent() }
}
