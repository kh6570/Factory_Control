// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.domain.sensor

import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.RingingAlarm
import com.raghim.herz.core.model.Sensor
import com.raghim.herz.core.model.SensorSignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object SensorLimits {
    /**
     * A sensor can point at many cameras, and a camera can be on many sensors.
     * Capped above the 50-camera wall so a link list cannot grow without bound.
     */
    const val MAX_LINKED_CAMERAS = 64

    /** One test timer per sensor. A new start replaces the previous timer for that sensor. */
    const val MAX_TEST_TIMERS = 64

    /** Stand-in delay. When it finishes, the app behaves as if the sensor detected something. */
    val TEST_DELAY: Duration = 20.seconds

    /**
     * How long the screen stays awake after an alarm starts. The lock releases itself,
     * so a missed Stop cannot hold the CPU.
     */
    val SCREEN_WAKE: Duration = 10.seconds
}

interface SensorRepository {
    val sensors: Flow<List<Sensor>>

    suspend fun get(id: String): Sensor?

    suspend fun add(name: String, area: String?): Sensor

    suspend fun update(id: String, name: String, area: String?)

    suspend fun setLinks(id: String, cameraIds: Set<String>)

    suspend fun setHighlight(id: String, enabled: Boolean)

    suspend fun setAlarmStyle(id: String, style: AlarmStyle)

    suspend fun remove(id: String)

    /** Saves the Sensors-tab order. [idsInOrder] is every saved sensor, first to last. */
    suspend fun reorder(idsInOrder: List<String>)
}

/** Signals from whatever is standing in for a sensor. Collecting this is the only way alarms start. */
interface SensorSignalSource {
    val signals: Flow<SensorSignal>
}

/** Where a timer, and later a real node, hands a signal to [SensorSignalSource]. */
interface SensorSignalSink {
    suspend fun emit(signal: SensorSignal)
}

/** Manual test timers. The real node will not use this; it will call [SensorSignalSink] directly. */
interface SensorTestControls {
    /** Sensor id to the instant the test fires. */
    val pending: StateFlow<Map<String, Instant>>

    fun start(sensorId: String)

    fun cancel(sensorId: String)
}

/** Sound, vibration, and the notification that can bring the phone forward. One at a time. */
interface AlarmAnnouncer {
    fun start(alarm: RingingAlarm)

    fun stop()
}

/** The alarm currently ringing, if any. Clearing it does not stop the cameras. */
interface RingingAlarmStore {
    val current: StateFlow<RingingAlarm?>

    fun show(alarm: RingingAlarm)

    fun dismiss()
}
