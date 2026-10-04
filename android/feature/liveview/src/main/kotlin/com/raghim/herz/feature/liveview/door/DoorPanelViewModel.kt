// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview.door

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.domain.camera.ObserveActiveCamerasUseCase
import com.raghim.herz.core.domain.door.HoldToOpen
import com.raghim.herz.core.domain.door.LockDoorUseCase
import com.raghim.herz.core.domain.door.ObserveDoorsUseCase
import com.raghim.herz.core.domain.door.ObserveHoldToOpenUseCase
import com.raghim.herz.core.domain.door.ObserveRequireFingerprintUseCase
import com.raghim.herz.core.domain.door.OpenDoorUseCase
import com.raghim.herz.core.model.Door
import com.raghim.herz.core.model.LockState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

/** A door command the app is still waiting on. */
enum class DoorCommand {
    /** Challenge fetched, biometric prompt showing. */
    Confirming,

    /** Signed command sent, waiting for the lock controller. */
    Unlocking,
}

data class DoorItem(
    val door: Door,
    val command: DoorCommand? = null,
    /** A camera showing this door is on the wall. */
    val onWall: Boolean = false,
) {
    val canOpen: Boolean
        get() = command == null && door.isOnline && door.lock == LockState.LOCKED
}

data class DoorPanelUiState(
    /** Locked doors first, then open doors at the bottom. Within each group, wall cameras, then name. */
    val doors: List<DoorItem> = emptyList(),
    val holdToOpen: Duration = HoldToOpen.Default,
    val requireFingerprint: Boolean = true,
    val isLoading: Boolean = true,
) {
    val unlockedCount: Int
        get() = doors.count { it.door.lock == LockState.UNLOCKED }
}

sealed interface DoorPanelIntent {
    data class Open(val doorId: String) : DoorPanelIntent
    data class Lock(val doorId: String) : DoorPanelIntent
}

sealed interface DoorPanelEffect {
    data class OpenFailed(val doorName: String, val error: AppError) : DoorPanelEffect
}

/** Doors next to the live wall. Opening is hold, then biometric, then signed command (spec D11). */
@HiltViewModel
class DoorPanelViewModel @Inject constructor(
    observeDoors: ObserveDoorsUseCase,
    observeActiveCameras: ObserveActiveCamerasUseCase,
    observeHoldToOpen: ObserveHoldToOpenUseCase,
    observeRequireFingerprint: ObserveRequireFingerprintUseCase,
    private val openDoor: OpenDoorUseCase,
    private val lockDoor: LockDoorUseCase,
) : ViewModel() {

    private val commands = MutableStateFlow<Map<String, DoorCommand>>(emptyMap())
    private val effectChannel = Channel<DoorPanelEffect>(Channel.BUFFERED)
    val effects: Flow<DoorPanelEffect> = effectChannel.receiveAsFlow()

    private val wallCameraIds = observeActiveCameras()
        .map { tiles -> tiles.mapTo(HashSet()) { it.cameraId } }
        .distinctUntilChanged()

    val state: StateFlow<DoorPanelUiState> = combine(
        observeDoors(),
        wallCameraIds,
        observeHoldToOpen(),
        observeRequireFingerprint(),
        commands,
    ) { doors, wallIds, hold, fingerprint, pending ->
        val items = doors
            .filter { it.onLivePanel }
            .map { DoorItem(it, pending[it.id], it.linkedCameraIds.any(wallIds::contains)) }
            .sortedWith(
                compareBy<DoorItem> { it.door.lock == LockState.UNLOCKED }
                    .thenByDescending { it.onWall }
                    .thenBy { it.door.name.lowercase() },
            )
        DoorPanelUiState(doors = items, holdToOpen = hold, requireFingerprint = fingerprint, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoorPanelUiState())

    fun onIntent(intent: DoorPanelIntent) {
        when (intent) {
            is DoorPanelIntent.Open -> open(intent.doorId)
            is DoorPanelIntent.Lock -> viewModelScope.launch { lockDoor(intent.doorId) }
        }
    }

    private fun open(doorId: String) {
        val item = state.value.doors.firstOrNull { it.door.id == doorId } ?: return
        if (!item.canOpen) return
        val startedAs = if (state.value.requireFingerprint) DoorCommand.Confirming else DoorCommand.Unlocking
        var claimed = false
        commands.update { pending ->
            claimed = doorId !in pending
            if (claimed) pending + (doorId to startedAs) else pending
        }
        if (!claimed) return

        viewModelScope.launch {
            val result = openDoor(item.door) {
                commands.update { it + (doorId to DoorCommand.Unlocking) }
            }
            commands.update { it - doorId }
            if (result is AppResult.Failure && result.error != AppError.Cancelled) {
                effectChannel.send(DoorPanelEffect.OpenFailed(item.door.name, result.error))
            }
        }
    }
}
