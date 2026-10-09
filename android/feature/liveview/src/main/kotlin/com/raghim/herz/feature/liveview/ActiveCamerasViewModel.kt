// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raghim.herz.core.domain.camera.ObserveActiveCamerasUseCase
import com.raghim.herz.core.domain.camera.ObserveNetworkModeUseCase
import com.raghim.herz.core.domain.camera.liveBudget
import com.raghim.herz.core.domain.door.ObserveLiveGridColumnsUseCase
import com.raghim.herz.core.domain.door.SetLiveGridColumnsUseCase
import com.raghim.herz.core.domain.sensor.ObserveRingingAlarmUseCase
import com.raghim.herz.core.domain.sensor.ResetLiveAlarmUseCase
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.NetworkMode
import com.raghim.herz.core.model.SessionSource
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
    /** How many grid tiles may decode live video at once on this network. The grid spends it on visible tiles. */
    val liveBudget: Int = 0,
    /** Null = grid view. */
    val maximizedCameraId: String? = null,
    val networkMode: NetworkMode = NetworkMode.LAN,
    /** Null = the app picks the column count. Otherwise 1 to 4 per row. */
    val gridColumns: Int? = null,
    /** A sensor alarm is ringing, or alarm cameras are still marked on the wall. */
    val alarmActive: Boolean = false,
    val isLoading: Boolean = true,
)

sealed interface ActiveCamerasIntent {
    data class Maximize(val cameraId: String) : ActiveCamerasIntent
    data object Minimize : ActiveCamerasIntent
    data class SwipeTo(val cameraId: String) : ActiveCamerasIntent
    data class Retry(val cameraId: String) : ActiveCamerasIntent
    data class SetGridColumns(val columns: Int?) : ActiveCamerasIntent
    data object ResetAlarm : ActiveCamerasIntent
}

/** Active Cameras wall (spec D10). Maximize is UI state, not a new screen, so players stay alive. */
@HiltViewModel
class ActiveCamerasViewModel @Inject constructor(
    observeActiveCameras: ObserveActiveCamerasUseCase,
    observeNetworkMode: ObserveNetworkModeUseCase,
    observeLiveGridColumns: ObserveLiveGridColumnsUseCase,
    observeRingingAlarm: ObserveRingingAlarmUseCase,
    private val setLiveGridColumns: SetLiveGridColumnsUseCase,
    private val resetLiveAlarm: ResetLiveAlarmUseCase,
    private val pool: PlayerPool,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val maximized = savedState.getStateFlow<String?>(KEY_MAXIMIZED, null)
    private var previousIds: Set<String> = emptySet()

    val state: StateFlow<ActiveCamerasUiState> = combine(
        observeActiveCameras().onEach(::onTilesChanged),
        observeNetworkMode(),
        maximized,
        observeLiveGridColumns(),
        observeRingingAlarm(),
    ) { tiles, mode, max, columns, ringing ->
        val maxId = max?.takeIf { id -> tiles.any { it.cameraId == id } }
        ActiveCamerasUiState(
            tiles = tiles,
            liveBudget = liveBudget(mode),
            maximizedCameraId = maxId,
            networkMode = mode,
            gridColumns = columns,
            alarmActive = ringing != null || tiles.any { it.source == SessionSource.ALARM },
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
            is ActiveCamerasIntent.Retry -> pool.get(intent.cameraId).retry()
            is ActiveCamerasIntent.SetGridColumns -> viewModelScope.launch { setLiveGridColumns(intent.columns) }
            ActiveCamerasIntent.ResetAlarm -> viewModelScope.launch { resetLiveAlarm() }
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
