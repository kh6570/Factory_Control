// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.video.rtsp

import com.raghim.herz.core.video.VideoPlayerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VideoRtspModule {

    @Binds
    abstract fun bindVideoPlayerFactory(impl: Media3PlayerFactory): VideoPlayerFactory
}
