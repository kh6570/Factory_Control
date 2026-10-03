// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database.di

import android.content.Context
import androidx.room.Room
import com.raghim.herz.core.database.HerzDatabase
import com.raghim.herz.core.database.dao.ActiveSessionDao
import com.raghim.herz.core.database.dao.CameraDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun provideHerzDatabase(@ApplicationContext context: Context): HerzDatabase =
        Room.databaseBuilder(context, HerzDatabase::class.java, "herz.db").build()

    @Provides
    fun provideCameraDao(database: HerzDatabase): CameraDao = database.cameraDao()

    @Provides
    fun provideActiveSessionDao(database: HerzDatabase): ActiveSessionDao = database.activeSessionDao()
}
