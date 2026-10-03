// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.network.di

import com.raghim.herz.core.domain.camera.NetworkMonitor
import com.raghim.herz.core.network.ConnectivityNetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkModule {
    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
}
