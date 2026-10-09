// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.sensor

import com.raghim.herz.core.domain.sensor.RingingAlarmStore
import com.raghim.herz.core.model.RingingAlarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One ringing alarm. A newer one replaces it. Dismissing does not touch the live wall. */
@Singleton
class RingingAlarmSession @Inject constructor() : RingingAlarmStore {
    private val state = MutableStateFlow<RingingAlarm?>(null)

    override val current: StateFlow<RingingAlarm?> = state.asStateFlow()

    override fun show(alarm: RingingAlarm) {
        state.value = alarm
    }

    override fun dismiss() {
        state.value = null
    }
}
