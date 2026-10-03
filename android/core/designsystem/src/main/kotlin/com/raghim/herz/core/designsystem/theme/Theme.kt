// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Status colors that must stay the same in light and dark mode. */
@Immutable
data class HerzStatusColors(
    val live: Color,
    val connecting: Color,
    val offline: Color,
    val alarm: Color,
    val idle: Color,
)

private val StatusColors = HerzStatusColors(
    live = Color(0xFF2EBD6B),
    connecting = Color(0xFFF2A93B),
    offline = Color(0xFF8A8F98),
    alarm = Color(0xFFE5484D),
    idle = Color(0xFF5B6270),
)

val LocalHerzStatusColors = staticCompositionLocalOf { StatusColors }

private val Dark = darkColorScheme(
    primary = Color(0xFF7CC4FF),
    onPrimary = Color(0xFF00324F),
    primaryContainer = Color(0xFF004B73),
    onPrimaryContainer = Color(0xFFCCE6FF),
    secondary = Color(0xFFB7C8DA),
    tertiary = Color(0xFFFFB4A9),
    error = Color(0xFFFF8A80),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF1E242C),
    onSurfaceVariant = Color(0xFFB4BCC8),
    surfaceContainerLowest = Color(0xFF090B0F),
    surfaceContainerLow = Color(0xFF151A20),
    surfaceContainer = Color(0xFF192028),
    surfaceContainerHigh = Color(0xFF212932),
    surfaceContainerHighest = Color(0xFF2A333D),
    outline = Color(0xFF5B6573),
    outlineVariant = Color(0xFF2E3640),
)

private val Light = lightColorScheme(
    primary = Color(0xFF00629A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE6FF),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF51606F),
    tertiary = Color(0xFF9C4234),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFFDEE3EB),
)

/** Herz theme. Dark by default: the app is mostly a video wall. */
@Composable
fun HerzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = Typography(),
        content = content,
    )
}

object HerzTheme {
    val status: HerzStatusColors
        @Composable get() = LocalHerzStatusColors.current
}
