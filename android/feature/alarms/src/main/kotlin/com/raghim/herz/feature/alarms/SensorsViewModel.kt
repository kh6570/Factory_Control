// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raghim.herz.core.domain.sensor.AddSensorUseCase
import com.raghim.herz.core.domain.sensor.CancelSensorTestUseCase
import com.raghim.herz.core.domain.sensor.ObserveSavedCamerasUseCase
import com.raghim.herz.core.domain.sensor.ObserveSensorTestsUseCase
import com.raghim.herz.core.domain.sensor.ObserveSensorsUseCase
import com.raghim.herz.core.domain.sensor.RemoveSensorUseCase
import com.raghim.herz.core.domain.sensor.ReorderSensorsUseCase
import com.raghim.herz.core.domain.sensor.SetSensorAlarmStyleUseCase
import com.raghim.herz.core.domain.sensor.SetSensorCamerasUseCase
import com.raghim.herz.core.domain.sensor.SetSensorHighlightUseCase
import com.raghim.herz.core.domain.sensor.StartSensorTestUseCase
import com.raghim.herz.core.domain.sensor.UpdateSensorUseCase
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.Sensor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

sealed interface SensorsDialog {
    data object Add : SensorsDialog
    data class Edit(val id: String, val name: String, val area: String) : SensorsDialog
    data class Remove(val id: String, val name: String) : SensorsDialog
    data class Link(val id: String, val name: String, val cameraIds: Set<String>) : SensorsDialog
    data class Highlight(val id: String, val enabled: Boolean) : SensorsDialog
    data class Alarm(val id: String, val style: AlarmStyle) : SensorsDialog
}

data class SensorsUiState(
    val sensors: List<Sensor> = emptyList(),
    val cameras: List<Camera> = emptyList(),
    val pendingUntil: Map<String, Instant> = emptyMap(),
    val dialog: SensorsDialog? = null,
    val isLoading: Boolean = true,
)

sealed interface SensorsIntent {
    data object Add : SensorsIntent
    data class Edit(val id: String) : SensorsIntent
    data class Remove(val id: String) : SensorsIntent
    data class Save(val name: String, val area: String) : SensorsIntent
    data class OpenLinks(val id: String) : SensorsIntent
    data class SaveLinks(val cameraIds: Set<String>) : SensorsIntent
    data class OpenHighlight(val id: String) : SensorsIntent
    data class SetHighlight(val enabled: Boolean) : SensorsIntent
    data class OpenAlarm(val id: String) : SensorsIntent
    data class SetAlarmStyle(val style: AlarmStyle) : SensorsIntent
    data class StartTest(val id: String) : SensorsIntent
    data class CancelTest(val id: String) : SensorsIntent
    data object ConfirmRemove : SensorsIntent
    data object Dismiss : SensorsIntent
    data class Move(val orderedIds: List<String>) : SensorsIntent
}

/** Add, rename, remove, and link sensors. Starting cameras stays on the Cameras tab. */
@HiltViewModel
class SensorsViewModel @Inject constructor(
    observeSensors: ObserveSensorsUseCase,
    observeCameras: ObserveSavedCamerasUseCase,
    observeTests: ObserveSensorTestsUseCase,
    private val addSensor: AddSensorUseCase,
    private val updateSensor: UpdateSensorUseCase,
    private val removeSensor: RemoveSensorUseCase,
    private val setCameras: SetSensorCamerasUseCase,
    private val setHighlight: SetSensorHighlightUseCase,
    private val setAlarmStyle: SetSensorAlarmStyleUseCase,
    private val startTest: StartSensorTestUseCase,
    private val cancelTest: CancelSensorTestUseCase,
    private val reorderSensors: ReorderSensorsUseCase,
) : ViewModel() {

    private val dialog = MutableStateFlow<SensorsDialog?>(null)

    val state: StateFlow<SensorsUiState> = combine(
        observeSensors(),
        observeCameras(),
        observeTests(),
        dialog,
    ) { sensors, cameras, pending, openDialog ->
        SensorsUiState(
            sensors = sensors,
            cameras = cameras,
            pendingUntil = pending,
            dialog = openDialog,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SensorsUiState())

    fun onIntent(intent: SensorsIntent) {
        when (intent) {
            SensorsIntent.Add -> dialog.value = SensorsDialog.Add
            is SensorsIntent.Edit -> find(intent.id)?.let { sensor ->
                dialog.value = SensorsDialog.Edit(sensor.id, sensor.name, sensor.area.orEmpty())
            }
            is SensorsIntent.Remove -> find(intent.id)?.let { sensor ->
                dialog.value = SensorsDialog.Remove(sensor.id, sensor.name)
            }
            is SensorsIntent.Save -> save(intent.name, intent.area)
            is SensorsIntent.OpenLinks -> find(intent.id)?.let { sensor ->
                val known = state.value.cameras.map { it.id }.toSet()
                dialog.value = SensorsDialog.Link(sensor.id, sensor.name, sensor.linkedCameraIds.intersect(known))
            }
            is SensorsIntent.SaveLinks -> saveLinks(intent.cameraIds)
            is SensorsIntent.OpenHighlight -> find(intent.id)?.let { sensor ->
                dialog.value = SensorsDialog.Highlight(sensor.id, sensor.highlightOnAlarm)
            }
            is SensorsIntent.SetHighlight -> chooseHighlight(intent.enabled)
            is SensorsIntent.OpenAlarm -> find(intent.id)?.let { sensor ->
                dialog.value = SensorsDialog.Alarm(sensor.id, sensor.alarmStyle)
            }
            is SensorsIntent.SetAlarmStyle -> chooseAlarm(intent.style)
            is SensorsIntent.StartTest -> startTest(intent.id)
            is SensorsIntent.CancelTest -> cancelTest(intent.id)
            SensorsIntent.ConfirmRemove -> confirmRemove()
            SensorsIntent.Dismiss -> dialog.value = null
            is SensorsIntent.Move -> viewModelScope.launch { reorderSensors(intent.orderedIds) }
        }
    }

    private fun save(name: String, area: String) {
        val open = dialog.value ?: return
        dialog.value = null
        viewModelScope.launch {
            when (open) {
                SensorsDialog.Add -> addSensor(name, area)
                is SensorsDialog.Edit -> updateSensor(open.id, name, area)
                is SensorsDialog.Remove, is SensorsDialog.Link, is SensorsDialog.Highlight, is SensorsDialog.Alarm -> Unit
            }
        }
    }

    private fun chooseHighlight(enabled: Boolean) {
        val open = dialog.value as? SensorsDialog.Highlight ?: return
        dialog.value = null
        viewModelScope.launch { setHighlight(open.id, enabled) }
    }

    private fun chooseAlarm(style: AlarmStyle) {
        val open = dialog.value as? SensorsDialog.Alarm ?: return
        dialog.value = null
        viewModelScope.launch { setAlarmStyle(open.id, style) }
    }

    private fun saveLinks(cameraIds: Set<String>) {
        val open = dialog.value as? SensorsDialog.Link ?: return
        dialog.value = null
        viewModelScope.launch { setCameras(open.id, cameraIds) }
    }

    private fun confirmRemove() {
        val open = dialog.value as? SensorsDialog.Remove ?: return
        dialog.value = null
        viewModelScope.launch { removeSensor(open.id) }
    }

    private fun find(id: String): Sensor? = state.value.sensors.firstOrNull { it.id == id }
}
