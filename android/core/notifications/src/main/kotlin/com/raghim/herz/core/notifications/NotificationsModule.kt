// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.notifications

import com.raghim.herz.core.domain.sensor.AlarmAnnouncer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsModule {
    @Binds
    abstract fun bindAlarmAnnouncer(impl: SystemAlarmAnnouncer): AlarmAnnouncer
}
