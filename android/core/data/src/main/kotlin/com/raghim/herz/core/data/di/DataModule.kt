// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.di

import com.raghim.herz.core.data.camera.DirectLanCameraSource
import com.raghim.herz.core.data.camera.LocalActiveCamerasRepository
import com.raghim.herz.core.data.camera.OfflineFirstCameraRepository
import com.raghim.herz.core.domain.camera.ActiveCamerasRepository
import com.raghim.herz.core.domain.camera.CameraRepository
import com.raghim.herz.core.domain.camera.CameraSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindCameraRepository(impl: OfflineFirstCameraRepository): CameraRepository

    @Binds
    abstract fun bindActiveCamerasRepository(impl: LocalActiveCamerasRepository): ActiveCamerasRepository

    @Binds
    abstract fun bindCameraSource(impl: DirectLanCameraSource): CameraSource
}
