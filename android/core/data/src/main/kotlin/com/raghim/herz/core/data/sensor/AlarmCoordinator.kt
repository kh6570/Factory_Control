// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.sensor

import android.util.Log
import com.raghim.herz.core.common.AppScope
import com.raghim.herz.core.domain.camera.ActiveCamerasRepository
import com.raghim.herz.core.domain.sensor.AlarmAnnouncer
import com.raghim.herz.core.domain.sensor.RingingAlarmStore
import com.raghim.herz.core.domain.sensor.SensorRepository
import com.raghim.herz.core.domain.sensor.SensorSignalSource
import com.raghim.herz.core.model.RingingAlarm
import com.raghim.herz.core.model.SensorSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a sensor signal into cameras on the wall and a ringing phone.
 * Lives for the process. Stopping the ring does not stop the cameras.
 */
@Singleton
class AlarmCoordinator @Inject constructor(
    private val signals: SensorSignalSource,
    private val sensors: SensorRepository,
    private val cameras: ActiveCamerasRepository,
    private val ringing: RingingAlarmStore,
    private val announcer: AlarmAnnouncer,
    @AppScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            signals.signals.collect { signal ->
                try {
                    handle(signal)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Sensor signal was not applied: ${error.javaClass.simpleName}")
                }
            }
        }
    }

    private suspend fun handle(signal: SensorSignal) {
        val sensor = sensors.get(signal.sensorId) ?: return
        cameras.raiseAlarm(
            cameraIds = sensor.linkedCameraIds.toList(),
            alarmId = UUID.randomUUID().toString(),
            highlight = sensor.highlightOnAlarm,
        )
        val alarm = RingingAlarm(
            sensorId = sensor.id,
            sensorName = sensor.name,
            style = sensor.alarmStyle,
        )
        announcer.start(alarm)
        ringing.show(alarm)
    }

    private companion object {
        const val TAG = "AlarmCoordinator"
    }
}
