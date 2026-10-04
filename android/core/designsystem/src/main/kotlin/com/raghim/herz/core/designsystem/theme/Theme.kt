// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
    live = Color(0xFF7EBF8E),
    connecting = Color(0xFFE09A4A),
    offline = Color(0xFF9A9086),
    alarm = Color(0xFFD4656A),
    idle = Color(0xFF6E655C),
)

val LocalHerzStatusColors = staticCompositionLocalOf { StatusColors }

private val Dark = darkColorScheme(
    primary = Color(0xFFE4B56A),
    onPrimary = Color(0xFF2A2114),
    primaryContainer = Color(0xFF5C4524),
    onPrimaryContainer = Color(0xFFF6E6C8),
    secondary = Color(0xFFC8BBA8),
    onSecondary = Color(0xFF2A241C),
    secondaryContainer = Color(0xFF3A322A),
    onSecondaryContainer = Color(0xFFE8DDD0),
    tertiary = Color(0xFFD7A48A),
    onTertiary = Color(0xFF2E1C14),
    error = Color(0xFFE07A72),
    onError = Color(0xFF3B1210),
    errorContainer = Color(0xFF5C2A26),
    onErrorContainer = Color(0xFFF8D4D0),
    background = Color(0xFF161311),
    onBackground = Color(0xFFE8E0D6),
    surface = Color(0xFF161311),
    onSurface = Color(0xFFE8E0D6),
    surfaceVariant = Color(0xFF2C2622),
    onSurfaceVariant = Color(0xFFC4B8A8),
    surfaceContainerLowest = Color(0xFF100E0C),
    surfaceContainerLow = Color(0xFF1C1916),
    surfaceContainer = Color(0xFF221E1B),
    surfaceContainerHigh = Color(0xFF2C2723),
    surfaceContainerHighest = Color(0xFF37312C),
    outline = Color(0xFF8A7D70),
    outlineVariant = Color(0xFF3A332E),
)

/**
 * Herz theme. Always dark: a security wall is watched for long stretches, and the light
 * scheme is not filled in. [darkTheme] is kept so existing previews still compile.
 */
@Composable
fun HerzTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = Dark,
        typography = Typography(),
        content = content,
    )
}

object HerzTheme {
    val status: HerzStatusColors
        @Composable get() = LocalHerzStatusColors.current
}
