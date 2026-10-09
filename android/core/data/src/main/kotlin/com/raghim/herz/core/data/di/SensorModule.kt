// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.di

import com.raghim.herz.core.data.sensor.RingingAlarmSession
import com.raghim.herz.core.data.sensor.RoomSensorRepository
import com.raghim.herz.core.data.sensor.SensorSignalBus
import com.raghim.herz.core.data.sensor.SensorTestTimer
import com.raghim.herz.core.domain.sensor.RingingAlarmStore
import com.raghim.herz.core.domain.sensor.SensorRepository
import com.raghim.herz.core.domain.sensor.SensorSignalSink
import com.raghim.herz.core.domain.sensor.SensorSignalSource
import com.raghim.herz.core.domain.sensor.SensorTestControls
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Swap [SensorTestTimer] for a network node by writing to [SensorSignalSink]. Screens stay the same. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SensorModule {
    @Binds
    abstract fun bindSensorRepository(impl: RoomSensorRepository): SensorRepository

    @Binds
    abstract fun bindSensorSignalSource(impl: SensorSignalBus): SensorSignalSource

    @Binds
    abstract fun bindSensorSignalSink(impl: SensorSignalBus): SensorSignalSink

    @Binds
    abstract fun bindSensorTestControls(impl: SensorTestTimer): SensorTestControls

    @Binds
    abstract fun bindRingingAlarmStore(impl: RingingAlarmSession): RingingAlarmStore
}
