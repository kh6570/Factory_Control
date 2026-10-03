// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import com.raghim.herz.core.domain.camera.CameraConnector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OnvifModule {
    @Binds
    abstract fun bindConnector(impl: RtspCameraConnector): CameraConnector
}
