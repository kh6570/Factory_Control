// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.raghim.herz.feature.alarms.AlarmAlertOverlay
import com.raghim.herz.navigation.HerzNavHost
import com.raghim.herz.navigation.TopLevelDestination
import com.raghim.herz.navigation.navigateToTopLevel

private val WideLayoutMinWidth = 600.dp

/** Bottom bar on phones, navigation rail on tablets and landscape (spec D13, D15). */
@Composable
fun HerzApp(viewModel: HerzAppViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val liveCount by viewModel.liveCount.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    val current = TopLevelDestination.entries.firstOrNull { destination.isOn(it) }
    val showNavigation = !fullscreen && (destination == null || current != null)
    val onSelect: (TopLevelDestination) -> Unit = { navController.navigateToTopLevel(it) }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            Column(Modifier.fillMaxSize()) {
                if (!fullscreen) LocalNetworkAccessBanner(Modifier.statusBarsPadding())
                HerzNavHost(
                    navController = navController,
                    onFullscreenChanged = { fullscreen = it },
                    modifier = Modifier.weight(1f),
                )
            }
            AlarmAlertOverlay(
                onWatchLive = { navController.navigateToTopLevel(TopLevelDestination.LIVE) },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth(),
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= WideLayoutMinWidth) {
            Row(Modifier.fillMaxSize()) {
                if (showNavigation) {
                    NavigationRail {
                        TopLevelDestination.entries.forEach { item ->
                            NavigationRailItem(
                                selected = item == current,
                                onClick = { onSelect(item) },
                                icon = { NavIcon(item, liveCount) },
                                label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) { content(Modifier) }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (showNavigation) {
                        NavigationBar {
                            TopLevelDestination.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = item == current,
                                    onClick = { onSelect(item) },
                                    icon = { NavIcon(item, liveCount) },
                                    label = { Text(stringResource(item.label)) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                content(Modifier.padding(padding).consumeWindowInsets(padding))
            }
        }
    }
}

@Composable
private fun NavIcon(item: TopLevelDestination, liveCount: Int) {
    if (item == TopLevelDestination.LIVE && liveCount > 0) {
        BadgedBox(badge = { Badge { Text(liveCount.toString()) } }) {
            Icon(item.icon(), contentDescription = null)
        }
    } else {
        Icon(item.icon(), contentDescription = null)
    }
}

private fun NavDestination?.isOn(item: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(item.route) } == true
