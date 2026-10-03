// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.video.rtsp

import android.content.Context
import com.raghim.herz.core.domain.camera.CameraSource
import com.raghim.herz.core.video.VideoPlayer
import com.raghim.herz.core.video.VideoPlayerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Creates [Media3RtspPlayer]s. Call [create] on the main thread. */
class Media3PlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraSource: CameraSource,
) : VideoPlayerFactory {

    override fun create(cameraId: String): VideoPlayer =
        Media3RtspPlayer(context, cameraSource, cameraId)
}
