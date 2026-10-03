// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.di

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.AppScope
import com.raghim.herz.core.common.Dispatcher
import com.raghim.herz.core.common.HerzDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @Dispatcher(HerzDispatchers.IO)
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(HerzDispatchers.Default)
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(
        @Dispatcher(HerzDispatchers.Default) dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    fun provideAppClock(): AppClock = AppClock.System
}
