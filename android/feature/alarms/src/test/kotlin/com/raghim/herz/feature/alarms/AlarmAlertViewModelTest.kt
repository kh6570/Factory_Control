// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.alarms

import com.raghim.herz.core.domain.sensor.DismissAlarmUseCase
import com.raghim.herz.core.domain.sensor.ObserveRingingAlarmUseCase
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.RingingAlarm
import com.raghim.herz.core.testing.FakeAlarmAnnouncer
import com.raghim.herz.core.testing.FakeRingingAlarmStore
import com.raghim.herz.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmAlertViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = FakeRingingAlarmStore()
    private val announcer = FakeAlarmAnnouncer()

    private fun viewModel() = AlarmAlertViewModel(
        observeRinging = ObserveRingingAlarmUseCase(store),
        dismissAlarm = DismissAlarmUseCase(store, announcer),
    )

    @Test
    fun stopClearsTheRingAndLeavesNoAlarmShowing() = runTest {
        store.show(RingingAlarm("sensor1", "Gate", AlarmStyle.VibrationOnly))
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.alarm.collect() }

        assertEquals("Gate", viewModel.alarm.value?.sensorName)

        viewModel.stop()

        assertNull(viewModel.alarm.value)
        assertEquals(1, announcer.stopped)
    }
}
