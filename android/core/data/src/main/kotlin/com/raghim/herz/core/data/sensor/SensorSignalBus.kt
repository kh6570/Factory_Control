// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.sensor

import com.raghim.herz.core.domain.sensor.SensorSignalSink
import com.raghim.herz.core.domain.sensor.SensorSignalSource
import com.raghim.herz.core.model.SensorSignal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single pipe a test timer and, later, a real sensor node both write to.
 * The buffer is capped: if the collector is briefly behind, the oldest signal is dropped.
 */
@Singleton
class SensorSignalBus @Inject constructor() : SensorSignalSource, SensorSignalSink {
    private val signalsFlow = MutableSharedFlow<SensorSignal>(
        extraBufferCapacity = SIGNAL_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val signals: Flow<SensorSignal> = signalsFlow.asSharedFlow()

    override suspend fun emit(signal: SensorSignal) {
        signalsFlow.emit(signal)
    }

    private companion object {
        /** Enough for every sensor to fire once while the collector is busy. */
        const val SIGNAL_BUFFER = 64
    }
}
