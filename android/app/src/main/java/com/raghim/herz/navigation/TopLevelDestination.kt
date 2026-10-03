// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.raghim.herz.R
import com.raghim.herz.core.designsystem.icon.HerzIcons
import com.raghim.herz.feature.cameras.navigation.CamerasRoute
import com.raghim.herz.feature.liveview.navigation.LiveViewRoute
import kotlin.reflect.KClass

/**
 * Bottom bar / rail entries. Spec D13 order is Dashboard, Cameras, Live, Doors, Alarms:
 * add new entries here as those features land.
 */
enum class TopLevelDestination(
    val route: KClass<*>,
    @StringRes val label: Int,
    val icon: () -> ImageVector,
) {
    CAMERAS(CamerasRoute::class, R.string.nav_cameras, { HerzIcons.Videocam }),
    LIVE(LiveViewRoute::class, R.string.nav_live, { HerzIcons.GridView }),
}
