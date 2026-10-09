// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.alarms

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
import com.raghim.herz.core.model.AlarmSound
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.testing.FakeCameraRepository
import com.raghim.herz.core.testing.FakeSensorRepository
import com.raghim.herz.core.testing.FakeSensorTestControls
import com.raghim.herz.core.testing.MainDispatcherRule
import com.raghim.herz.core.testing.TestCameras
import com.raghim.herz.core.testing.TestSensors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sensors = FakeSensorRepository(listOf(TestSensors.sensor(2), TestSensors.sensor(1)))
    private val cameras = FakeCameraRepository(listOf(TestCameras.camera(1, "Gate"), TestCameras.camera(2, "Yard")))
    private val tests = FakeSensorTestControls()

    private fun viewModel() = SensorsViewModel(
        observeSensors = ObserveSensorsUseCase(sensors),
        observeCameras = ObserveSavedCamerasUseCase(cameras),
        observeTests = ObserveSensorTestsUseCase(tests),
        addSensor = AddSensorUseCase(sensors),
        updateSensor = UpdateSensorUseCase(sensors),
        removeSensor = RemoveSensorUseCase(sensors, tests),
        setCameras = SetSensorCamerasUseCase(sensors),
        setHighlight = SetSensorHighlightUseCase(sensors),
        setAlarmStyle = SetSensorAlarmStyleUseCase(sensors),
        startTest = StartSensorTestUseCase(tests),
        cancelTest = CancelSensorTestUseCase(tests),
        reorderSensors = ReorderSensorsUseCase(sensors),
    )

    @Test
    fun holdingAndDraggingSavesTheSensorOrder() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(SensorsIntent.Move(listOf("sensor2", "sensor1")))

        assertEquals(listOf("sensor2", "sensor1"), viewModel.state.value.sensors.map { it.id })
    }

    @Test
    fun listsSensorsByName() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        assertEquals(listOf("Sensor 1", "Sensor 2"), viewModel.state.value.sensors.map { it.name })
        assertEquals(listOf("Gate", "Yard"), viewModel.state.value.cameras.map { it.name })
    }

    @Test
    fun addSavesATrimmedNameAndClosesTheDialog() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(SensorsIntent.Add)
        assertTrue(viewModel.state.value.dialog is SensorsDialog.Add)
        viewModel.onIntent(SensorsIntent.Save("  Side gate  ", "  "))

        assertNull(viewModel.state.value.dialog)
        assertEquals("Side gate", sensors.state.value.last().name)
        assertNull(sensors.state.value.last().area)
    }

    @Test
    fun blankNameIsNotSaved() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }
        val before = sensors.state.value.size

        viewModel.onIntent(SensorsIntent.Add)
        viewModel.onIntent(SensorsIntent.Save("   ", "Yard"))

        assertEquals(before, sensors.state.value.size)
    }

    @Test
    fun editRenamesAndCanSetTheArea() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(SensorsIntent.Edit("sensor1"))
        viewModel.onIntent(SensorsIntent.Save("Gate", "North"))

        val sensor = sensors.state.value.first { it.id == "sensor1" }
        assertEquals("Gate", sensor.name)
        assertEquals("North", sensor.area)
    }

    @Test
    fun removeAsksFirstThenDeletesAndCancelsTheTest() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(SensorsIntent.Remove("sensor2"))
        assertTrue(viewModel.state.value.dialog is SensorsDialog.Remove)
        viewModel.onIntent(SensorsIntent.ConfirmRemove)

        assertNull(viewModel.state.value.dialog)
        assertEquals(listOf("sensor1"), sensors.state.value.map { it.id })
        assertEquals(listOf("sensor2"), tests.cancelled)
    }

    @Test
    fun linkingCamerasStoresTheSelection() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(SensorsIntent.OpenLinks("sensor1"))
        viewModel.onIntent(SensorsIntent.SaveLinks(setOf("cam01", "cam02")))

        assertEquals(setOf("cam01", "cam02"), sensors.state.value.first { it.id == "sensor1" }.linkedCameraIds)
    }

    @Test
    fun startTestDoesNotAddCamerasByItself() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(SensorsIntent.StartTest("sensor1"))

        assertEquals(listOf("sensor1"), tests.started)
        assertTrue(sensors.state.value.first { it.id == "sensor1" }.linkedCameraIds.isEmpty())
    }

    @Test
    fun highlightChoiceIsStoredForThatSensor() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(SensorsIntent.OpenHighlight("sensor1"))
        val open = viewModel.state.value.dialog as SensorsDialog.Highlight
        assertTrue(open.enabled)

        viewModel.onIntent(SensorsIntent.SetHighlight(false))

        assertNull(viewModel.state.value.dialog)
        assertFalse(sensors.state.value.first { it.id == "sensor1" }.highlightOnAlarm)
    }

    @Test
    fun alarmChoiceStoresVibrationOrASound() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(SensorsIntent.OpenAlarm("sensor1"))
        viewModel.onIntent(SensorsIntent.SetAlarmStyle(AlarmStyle.VibrationOnly))
        assertEquals(AlarmStyle.VibrationOnly, sensors.state.value.first { it.id == "sensor1" }.alarmStyle)

        viewModel.onIntent(SensorsIntent.OpenAlarm("sensor1"))
        viewModel.onIntent(SensorsIntent.SetAlarmStyle(AlarmStyle.Sound(AlarmSound.RINGTONE)))

        assertNull(viewModel.state.value.dialog)
        assertEquals(AlarmStyle.Sound(AlarmSound.RINGTONE), sensors.state.value.first { it.id == "sensor1" }.alarmStyle)
    }
}
