// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.sensor

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.AppScope
import com.raghim.herz.core.domain.sensor.SensorLimits
import com.raghim.herz.core.domain.sensor.SensorSignalSink
import com.raghim.herz.core.domain.sensor.SensorTestControls
import com.raghim.herz.core.model.SensorSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One 20 second timer per sensor. Starting again replaces that sensor's timer.
 * The map never grows past [SensorLimits.MAX_TEST_TIMERS]; the oldest extra timer is cancelled.
 */
@Singleton
class SensorTestTimer @Inject constructor(
    private val sink: SensorSignalSink,
    private val clock: AppClock,
    @AppScope private val scope: CoroutineScope,
) : SensorTestControls {
    private val lock = Any()
    private val jobs = LinkedHashMap<String, Job>()
    private val _pending = MutableStateFlow<Map<String, Instant>>(emptyMap())

    override val pending: StateFlow<Map<String, Instant>> = _pending.asStateFlow()

    override fun start(sensorId: String) {
        val firesAt = clock.now().plusMillis(SensorLimits.TEST_DELAY.inWholeMilliseconds)
        synchronized(lock) {
            jobs.remove(sensorId)?.cancel()
            while (jobs.size >= SensorLimits.MAX_TEST_TIMERS) {
                val oldest = jobs.keys.firstOrNull() ?: break
                jobs.remove(oldest)?.cancel()
                _pending.update { it - oldest }
            }
            val job = scope.launch {
                delay(SensorLimits.TEST_DELAY)
                sink.emit(SensorSignal(sensorId))
                synchronized(lock) {
                    if (jobs[sensorId] === coroutineContext[Job]) jobs.remove(sensorId)
                }
                _pending.update { current -> if (current[sensorId] == firesAt) current - sensorId else current }
            }
            jobs[sensorId] = job
            _pending.update { it + (sensorId to firesAt) }
        }
    }

    override fun cancel(sensorId: String) {
        val job = synchronized(lock) { jobs.remove(sensorId) }
        job?.cancel()
        _pending.update { it - sensorId }
    }
}
