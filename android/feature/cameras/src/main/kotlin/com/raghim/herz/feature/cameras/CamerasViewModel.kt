// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.cameras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raghim.herz.core.domain.camera.ObserveCameraOverviewsUseCase
import com.raghim.herz.core.domain.camera.RemoveCameraUseCase
import com.raghim.herz.core.domain.camera.ReorderCamerasUseCase
import com.raghim.herz.core.domain.camera.RenameCameraUseCase
import com.raghim.herz.core.domain.camera.StartCamerasUseCase
import com.raghim.herz.core.domain.camera.StopCameraUseCase
import com.raghim.herz.feature.cameras.model.CamerasDialog
import com.raghim.herz.feature.cameras.model.CamerasEffect
import com.raghim.herz.feature.cameras.model.CamerasIntent
import com.raghim.herz.feature.cameras.model.CamerasMessage
import com.raghim.herz.feature.cameras.model.CamerasUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CamerasViewModel @Inject constructor(
    observeCameraOverviews: ObserveCameraOverviewsUseCase,
    private val startCameras: StartCamerasUseCase,
    private val stopCamera: StopCameraUseCase,
    private val renameCamera: RenameCameraUseCase,
    private val removeCamera: RemoveCameraUseCase,
    private val reorderCameras: ReorderCamerasUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CamerasUiState())
    val state: StateFlow<CamerasUiState> = _state.asStateFlow()

    private val _effects = Channel<CamerasEffect>(Channel.BUFFERED)
    val effects: Flow<CamerasEffect> = _effects.receiveAsFlow()

    init {
        observeCameraOverviews()
            .onEach { cameras ->
                val ids = cameras.mapTo(HashSet()) { it.camera.id }
                _state.update { current ->
                    current.copy(
                        cameras = cameras,
                        isLoading = false,
                        selection = current.selection.filterTo(LinkedHashSet()) { it in ids },
                        dialog = current.dialog?.takeIf { it.cameraId in ids },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: CamerasIntent) {
        when (intent) {
            is CamerasIntent.Start -> perform { startCameras(listOf(intent.id)) }
            is CamerasIntent.Stop -> perform { stopCamera(intent.id) }
            is CamerasIntent.ToggleSelect -> _state.update {
                val selection = if (intent.id in it.selection) it.selection - intent.id else it.selection + intent.id
                it.copy(selection = selection)
            }
            CamerasIntent.ClearSelection -> _state.update { it.copy(selection = emptySet()) }
            CamerasIntent.StartSelected -> startSelected()
            CamerasIntent.StopSelected -> stopSelected()
            CamerasIntent.ShowAllOnLive -> showAllOnLive()
            CamerasIntent.HideAllFromLive -> hideAllFromLive()
            is CamerasIntent.RequestRename -> findCamera(intent.id)?.let { camera ->
                _state.update { it.copy(dialog = CamerasDialog.Rename(camera.id, camera.name)) }
            }
            is CamerasIntent.ConfirmRename -> confirmRename(intent.name)
            is CamerasIntent.RequestRemove -> findCamera(intent.id)?.let { camera ->
                _state.update { it.copy(dialog = CamerasDialog.Remove(camera.id, camera.name)) }
            }
            CamerasIntent.ConfirmRemove -> confirmRemove()
            CamerasIntent.DismissDialog -> _state.update { it.copy(dialog = null) }
            is CamerasIntent.Move -> move(intent.onLive, intent.orderedIds)
        }
    }

    private fun move(onLive: Boolean, orderedIds: List<String>) {
        val all = _state.value.cameras
        val byId = all.associateBy { it.camera.id }
        val ordered = orderedIds.mapNotNull { byId[it] }
        val slots = all.indices.filter { all[it].isActive == onLive }
        if (ordered.size != slots.size) return
        val next = all.toMutableList()
        slots.forEachIndexed { index, slot -> next[slot] = ordered[index] }
        _state.update { it.copy(cameras = next) }
        perform { reorderCameras(next.map { it.camera.id }) }
    }

    private fun startSelected() {
        val ids = selectedInListOrder()
        if (ids.isEmpty()) return
        _state.update { it.copy(selection = emptySet()) }
        perform {
            startCameras(ids)
            _effects.send(CamerasEffect.ShowMessage(CamerasMessage.Started(ids.size)))
        }
    }

    private fun stopSelected() {
        val ids = selectedInListOrder()
        if (ids.isEmpty()) return
        _state.update { it.copy(selection = emptySet()) }
        perform {
            ids.forEach { stopCamera(it) }
            _effects.send(CamerasEffect.ShowMessage(CamerasMessage.Stopped(ids.size)))
        }
    }

    private fun showAllOnLive() {
        val ids = _state.value.cameras.filter { !it.isActive }.map { it.camera.id }
        if (ids.isEmpty()) return
        perform { startCameras(ids) }
    }

    private fun hideAllFromLive() {
        val ids = _state.value.cameras.filter { it.isActive }.map { it.camera.id }
        if (ids.isEmpty()) return
        perform { ids.forEach { stopCamera(it) } }
    }

    private fun confirmRename(name: String) {
        val dialog = _state.value.dialog as? CamerasDialog.Rename ?: return
        _state.update { it.copy(dialog = null) }
        if (name.isBlank() || name.trim() == dialog.currentName) return
        perform { renameCamera(dialog.cameraId, name) }
    }

    private fun confirmRemove() {
        val dialog = _state.value.dialog as? CamerasDialog.Remove ?: return
        _state.update { it.copy(dialog = null) }
        perform {
            removeCamera(dialog.cameraId)
            _effects.send(CamerasEffect.ShowMessage(CamerasMessage.Removed(dialog.name)))
        }
    }

    private fun selectedInListOrder(): List<String> {
        val current = _state.value
        return current.cameras.map { it.camera.id }.filter { it in current.selection }
    }

    private fun findCamera(id: String) = _state.value.cameras.firstOrNull { it.camera.id == id }?.camera

    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                _effects.send(CamerasEffect.ShowMessage(CamerasMessage.ActionFailed))
            }
        }
    }
}
