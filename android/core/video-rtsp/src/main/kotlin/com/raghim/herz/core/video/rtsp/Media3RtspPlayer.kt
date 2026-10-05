// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.video.rtsp

import android.content.Context
import android.os.Looper
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.domain.camera.CameraSource
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.model.StreamRequest
import com.raghim.herz.core.video.PlayerError
import com.raghim.herz.core.video.PlayerState
import com.raghim.herz.core.video.VideoPlayer
import com.raghim.herz.core.video.VideoScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Plays one camera's RTSP stream with ExoPlayer and renders it into a TextureView.
 *
 * Main thread only: create, call and release this class on the main thread. The
 * ExoPlayer is created lazily on the main looper and all its callbacks arrive there,
 * so no state here needs synchronisation.
 *
 * Resource bounds for a large wall: the ExoPlayer (decoder, sockets, playback thread) exists only
 * while playing and for [IDLE_RELEASE_MS] after a pause, so cameras scrolled away or behind the
 * fullscreen view hold nothing. A stream stuck connecting longer than [STALL_TIMEOUT_MS] fails
 * and reconnects with backoff, so no tile stays on "Connecting" forever.
 */
@OptIn(UnstableApi::class)
class Media3RtspPlayer(
    private val context: Context,
    private val cameraSource: CameraSource,
    override val cameraId: String,
    private val forceTcp: Boolean = false,
) : VideoPlayer {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    override val state: StateFlow<PlayerState> = _state

    private val _quality = MutableStateFlow(StreamQuality.SUB)
    override val quality: StateFlow<StreamQuality> = _quality

    /** Display aspect ratio of the current stream, null until the first frame size is known. */
    private val aspectRatio = MutableStateFlow<Float?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val backoff = ReconnectBackoff()
    private val mediaSourceFactory = RtspMediaSource.Factory()
        .setTimeoutMs(TIMEOUT_MS)
        .setForceUseRtpTcp(forceTcp)

    private var exoPlayer: ExoPlayer? = null
    private var connectJob: Job? = null
    private var reconnectJob: Job? = null
    private var stallJob: Job? = null
    private var idleReleaseJob: Job? = null

    /** The view currently showing this camera. Attached when a player exists; never creates one. */
    private var textureView: TextureView? = null

    /** True between play() and pause()/release(); drives reconnects. */
    private var wanted = false

    /** Quality of the stream being resolved or played, null when stopped. */
    private var loadedQuality: StreamQuality? = null
    private var released = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!wanted) return
            when (playbackState) {
                Player.STATE_READY -> {
                    backoff.reset()
                    stallJob?.cancel()
                    _state.value = PlayerState.Playing
                }
                Player.STATE_BUFFERING ->
                    if (_state.value == PlayerState.Playing) {
                        _state.value = PlayerState.Connecting
                        watchForStall()
                    }
                Player.STATE_ENDED -> fail(PlayerError.Lost)
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (wanted) fail(playbackError(error.errorCode, error.cause))
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                aspectRatio.value =
                    videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }
    }

    override fun play(quality: StreamQuality) {
        if (released) return
        if (wanted && loadedQuality == quality && _state.value !is PlayerState.Error) return
        _quality.value = quality
        wanted = true
        connect(quality)
    }

    override fun switchQuality(quality: StreamQuality) {
        if (released) return
        _quality.value = quality
        if (wanted && loadedQuality != quality) connect(quality)
    }

    override fun pause() {
        if (released) return
        stopPlayback()
        _state.value = PlayerState.Idle
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            if (!wanted) releaseExoPlayer()
        }
    }

    override fun retry() {
        if (released) return
        wanted = true
        connect(_quality.value)
    }

    override fun release() {
        if (released) return
        released = true
        stopPlayback()
        idleReleaseJob?.cancel()
        scope.cancel()
        releaseExoPlayer()
        textureView = null
        _state.value = PlayerState.Idle
    }

    @Composable
    override fun Surface(modifier: Modifier, scale: VideoScale) {
        val aspect = aspectRatio.collectAsState().value
        val viewModifier = when {
            aspect == null -> Modifier.fillMaxSize()
            scale == VideoScale.Fit -> Modifier.aspectRatio(aspect)
            else -> Modifier.cropToFill(aspect)
        }
        Box(
            modifier = modifier.background(Color.Black).clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { TextureView(it) },
                modifier = viewModifier,
                onRelease = { view ->
                    if (textureView === view) textureView = null
                    exoPlayer?.clearVideoTextureView(view)
                },
                update = { view ->
                    if (!released) {
                        textureView = view
                        exoPlayer?.setVideoTextureView(view)
                    }
                },
            )
        }
    }

    /** Resolves and (re)starts the stream; the old stream keeps playing until the new one is set. */
    private fun connect(quality: StreamQuality) {
        connectJob?.cancel()
        reconnectJob?.cancel()
        idleReleaseJob?.cancel()
        loadedQuality = quality
        if (_state.value != PlayerState.Playing) {
            _state.value = PlayerState.Connecting
            watchForStall()
        }
        connectJob = scope.launch {
            when (val result = resolve(quality)) {
                is AppResult.Success -> start(result.value)
                is AppResult.Failure -> fail(result.error.toPlayerError())
            }
        }
    }

    private suspend fun resolve(quality: StreamQuality): AppResult<StreamRequest> = try {
        cameraSource.stream(cameraId, quality)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(AppError.Unknown(e::class.simpleName))
    }

    private fun start(request: StreamRequest) {
        val uri = RtspUris.withCredentials(request.uri, request.credentials)
        val source = mediaSourceFactory.createMediaSource(MediaItem.fromUri(uri))
        obtainPlayer().run {
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
    }

    private fun fail(error: PlayerError) {
        stallJob?.cancel()
        exoPlayer?.stop()
        _state.value = PlayerState.Error(error)
        if (wanted && error != PlayerError.Unauthorized && error != PlayerError.Unsupported) {
            scheduleReconnect()
        }
    }

    /** Fails the stream if it is still connecting after [STALL_TIMEOUT_MS]; fail() then reconnects. */
    private fun watchForStall() {
        stallJob?.cancel()
        stallJob = scope.launch {
            delay(STALL_TIMEOUT_MS)
            if (wanted && _state.value == PlayerState.Connecting) fail(PlayerError.Lost)
        }
    }

    /** Backoff plus up to 20 % jitter, so many offline cameras do not all retry in the same instant. */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        val base = backoff.next()
        val wait = base + (base.inWholeMilliseconds * Random.nextDouble(0.0, RECONNECT_JITTER)).milliseconds
        reconnectJob = scope.launch {
            delay(wait)
            if (wanted) connect(_quality.value)
        }
    }

    private fun stopPlayback() {
        wanted = false
        loadedQuality = null
        connectJob?.cancel()
        reconnectJob?.cancel()
        stallJob?.cancel()
        exoPlayer?.stop()
    }

    private fun releaseExoPlayer() {
        exoPlayer?.let { player ->
            textureView?.let(player::clearVideoTextureView)
            player.removeListener(listener)
            player.release()
        }
        exoPlayer = null
    }

    private fun obtainPlayer(): ExoPlayer = exoPlayer ?: createPlayer().also { exoPlayer = it }

    private fun createPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()
        return ExoPlayer.Builder(context)
            .setLooper(Looper.getMainLooper())
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                addListener(listener)
                textureView?.let(::setVideoTextureView)
            }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
        const val STALL_TIMEOUT_MS = 15_000L
        const val IDLE_RELEASE_MS = 20_000L
        const val RECONNECT_JITTER = 0.2
        const val MIN_BUFFER_MS = 500
        const val MAX_BUFFER_MS = 2_000
        const val BUFFER_FOR_PLAYBACK_MS = 250
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 500
    }
}

/** Sizes the child to cover the incoming bounds at [aspect] and centres it; the parent clips. */
private fun Modifier.cropToFill(aspect: Float): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val boxWidth = constraints.maxWidth
    val boxHeight = constraints.maxHeight
    val wider = boxHeight == 0 || boxWidth.toFloat() / boxHeight > aspect
    val width = if (wider) boxWidth else (boxHeight * aspect).roundToInt()
    val height = if (wider) (boxWidth / aspect).roundToInt() else boxHeight
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(boxWidth, boxHeight) {
        placeable.place((boxWidth - width) / 2, (boxHeight - height) / 2)
    }
}
