// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.video.VideoPlayer

/**
 * Plays [player] while this is composed, [shouldPlay] is true and the screen is at least STARTED.
 * Only the effect that started playback pauses it again, so a grid tile leaving composition
 * never pauses the same player that the fullscreen view just started (and the other way round).
 */
@Composable
internal fun PlaybackEffect(player: VideoPlayer, shouldPlay: Boolean, quality: StreamQuality) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val play = shouldPlay && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    DisposableEffect(player, play, quality) {
        if (play) player.play(quality)
        onDispose { if (play) player.pause() }
    }
}

/** Hides the status and navigation bars while [hidden], and keeps the screen on. */
@Composable
internal fun ImmersiveModeEffect(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, hidden) {
        val window = view.context.findActivity()?.window
        if (!hidden || window == null || view.isInEditMode) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
