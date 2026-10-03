// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.discovery

import com.raghim.herz.core.domain.camera.CameraDiscovery
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DiscoveryModule {
    @Binds
    abstract fun bindDiscovery(impl: LanCameraDiscovery): CameraDiscovery
}
