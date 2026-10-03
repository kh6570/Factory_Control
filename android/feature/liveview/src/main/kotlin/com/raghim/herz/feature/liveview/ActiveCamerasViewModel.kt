// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raghim.herz.core.domain.camera.ObserveActiveCamerasUseCase
import com.raghim.herz.core.domain.camera.ObserveNetworkModeUseCase
import com.raghim.herz.core.domain.camera.StopCameraUseCase
import com.raghim.herz.core.domain.camera.liveBudget
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.NetworkMode
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.video.PlayerPool
import com.raghim.herz.core.video.VideoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActiveCamerasUiState(
    /** Alarm cameras first, then manual cameras in start order. */
    val tiles: List<ActiveCamera> = emptyList(),
    /** Cameras that may decode live video: the first tiles within the network budget, plus the maximized one. */
    val liveIds: Set<String> = emptySet(),
    /** Null = grid view. */
    val maximizedCameraId: String? = null,
    val networkMode: NetworkMode = NetworkMode.LAN,
    val isLoading: Boolean = true,
)

sealed interface ActiveCamerasIntent {
    data class Maximize(val cameraId: String) : ActiveCamerasIntent
    data object Minimize : ActiveCamerasIntent
    data class SwipeTo(val cameraId: String) : ActiveCamerasIntent
    data class Stop(val cameraId: String) : ActiveCamerasIntent
    data class Retry(val cameraId: String) : ActiveCamerasIntent
}

/** Active Cameras wall (spec D10). Maximize is UI state, not a new screen, so players stay alive. */
@HiltViewModel
class ActiveCamerasViewModel @Inject constructor(
    observeActiveCameras: ObserveActiveCamerasUseCase,
    observeNetworkMode: ObserveNetworkModeUseCase,
    private val stopCamera: StopCameraUseCase,
    private val pool: PlayerPool,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val maximized = savedState.getStateFlow<String?>(KEY_MAXIMIZED, null)
    private var previousIds: Set<String> = emptySet()

    val state: StateFlow<ActiveCamerasUiState> = combine(
        observeActiveCameras().onEach(::onTilesChanged),
        observeNetworkMode(),
        maximized,
    ) { tiles, mode, max ->
        val maxId = max?.takeIf { id -> tiles.any { it.cameraId == id } }
        val liveIds = buildSet {
            tiles.take(liveBudget(mode)).forEach { add(it.cameraId) }
            if (maxId != null) add(maxId)
        }
        ActiveCamerasUiState(
            tiles = tiles,
            liveIds = liveIds,
            maximizedCameraId = maxId,
            networkMode = mode,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveCamerasUiState())

    fun player(cameraId: String): VideoPlayer = pool.get(cameraId)

    fun onIntent(intent: ActiveCamerasIntent) {
        when (intent) {
            is ActiveCamerasIntent.Maximize -> {
                pool.get(intent.cameraId).switchQuality(StreamQuality.MAIN)
                savedState[KEY_MAXIMIZED] = intent.cameraId
            }
            ActiveCamerasIntent.Minimize -> {
                maximized.value?.let { pool.peek(it)?.switchQuality(StreamQuality.SUB) }
                savedState[KEY_MAXIMIZED] = null
            }
            is ActiveCamerasIntent.SwipeTo -> {
                val previous = maximized.value
                if (previous == intent.cameraId) return
                previous?.let { pool.peek(it)?.switchQuality(StreamQuality.SUB) }
                pool.get(intent.cameraId).switchQuality(StreamQuality.MAIN)
                savedState[KEY_MAXIMIZED] = intent.cameraId
            }
            is ActiveCamerasIntent.Stop -> {
                if (maximized.value == intent.cameraId) savedState[KEY_MAXIMIZED] = null
                viewModelScope.launch { stopCamera(intent.cameraId) }
            }
            is ActiveCamerasIntent.Retry -> pool.get(intent.cameraId).retry()
        }
    }

    private fun onTilesChanged(tiles: List<ActiveCamera>) {
        val ids = tiles.mapTo(HashSet()) { it.cameraId }
        pool.retain(ids)
        // An empty first list may only mean "not loaded yet", so keep a restored id until we know.
        val max = maximized.value
        if (max != null && max !in ids && (max in previousIds || ids.isNotEmpty())) {
            savedState[KEY_MAXIMIZED] = null
        }
        previousIds = ids
    }

    internal companion object {
        const val KEY_MAXIMIZED = "maximizedCameraId"
    }
}
