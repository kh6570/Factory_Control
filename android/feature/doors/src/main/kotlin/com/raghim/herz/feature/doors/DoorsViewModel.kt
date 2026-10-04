// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.doors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raghim.herz.core.domain.door.AddDoorUseCase
import com.raghim.herz.core.domain.door.ObserveDoorsUseCase
import com.raghim.herz.core.domain.door.RemoveDoorUseCase
import com.raghim.herz.core.domain.door.SetDoorOnLivePanelUseCase
import com.raghim.herz.core.domain.door.UpdateDoorUseCase
import com.raghim.herz.core.model.Door
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DoorsDialog {
    data object Add : DoorsDialog
    data class Edit(val id: String, val name: String, val area: String) : DoorsDialog
    data class Remove(val id: String, val name: String) : DoorsDialog
}

data class DoorsUiState(
    val doors: List<Door> = emptyList(),
    val dialog: DoorsDialog? = null,
    val isLoading: Boolean = true,
)

sealed interface DoorsIntent {
    data object Add : DoorsIntent
    data class Edit(val id: String) : DoorsIntent
    data class Remove(val id: String) : DoorsIntent
    data class Save(val name: String, val area: String) : DoorsIntent
    data class SetOnLive(val id: String, val shown: Boolean) : DoorsIntent
    data object ConfirmRemove : DoorsIntent
    data object Dismiss : DoorsIntent
}

/** The door list: add, rename (with area) and remove. Opening a door stays on the live wall. */
@HiltViewModel
class DoorsViewModel @Inject constructor(
    observeDoors: ObserveDoorsUseCase,
    private val addDoor: AddDoorUseCase,
    private val updateDoor: UpdateDoorUseCase,
    private val removeDoor: RemoveDoorUseCase,
    private val setOnLivePanel: SetDoorOnLivePanelUseCase,
) : ViewModel() {

    private val dialog = MutableStateFlow<DoorsDialog?>(null)

    val state: StateFlow<DoorsUiState> = combine(
        observeDoors(),
        dialog,
    ) { doors, openDialog ->
        DoorsUiState(doors = doors, dialog = openDialog, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoorsUiState())

    fun onIntent(intent: DoorsIntent) {
        when (intent) {
            DoorsIntent.Add -> dialog.value = DoorsDialog.Add
            is DoorsIntent.Edit -> find(intent.id)?.let { door ->
                dialog.value = DoorsDialog.Edit(door.id, door.name, door.area.orEmpty())
            }
            is DoorsIntent.Remove -> find(intent.id)?.let { door ->
                dialog.value = DoorsDialog.Remove(door.id, door.name)
            }
            is DoorsIntent.Save -> save(intent.name, intent.area)
            is DoorsIntent.SetOnLive -> viewModelScope.launch { setOnLivePanel(intent.id, intent.shown) }
            DoorsIntent.ConfirmRemove -> confirmRemove()
            DoorsIntent.Dismiss -> dialog.value = null
        }
    }

    private fun save(name: String, area: String) {
        val open = dialog.value ?: return
        dialog.value = null
        viewModelScope.launch {
            when (open) {
                DoorsDialog.Add -> addDoor(name, area)
                is DoorsDialog.Edit -> updateDoor(open.id, name, area)
                is DoorsDialog.Remove -> Unit
            }
        }
    }

    private fun confirmRemove() {
        val open = dialog.value as? DoorsDialog.Remove ?: return
        dialog.value = null
        viewModelScope.launch { removeDoor(open.id) }
    }

    private fun find(id: String): Door? = state.value.doors.firstOrNull { it.id == id }
}
