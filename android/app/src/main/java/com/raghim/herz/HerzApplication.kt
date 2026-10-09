// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz

import android.app.Application
import com.raghim.herz.core.data.sensor.AlarmCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HerzApplication : Application() {
    @Inject lateinit var alarmCoordinator: AlarmCoordinator

    override fun onCreate() {
        super.onCreate()
        alarmCoordinator.start()
    }
}
