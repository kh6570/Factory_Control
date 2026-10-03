// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.camera

import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.domain.camera.CameraRepository
import com.raghim.herz.core.domain.camera.CameraSource
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.model.StreamRequest
import javax.inject.Inject
import javax.inject.Singleton

/** Opens saved cameras directly on the LAN. A server-backed source replaces this later. */
@Singleton
class DirectLanCameraSource @Inject constructor(
    private val cameras: CameraRepository,
) : CameraSource {

    override suspend fun stream(cameraId: String, quality: StreamQuality): AppResult<StreamRequest> {
        val camera = cameras.get(cameraId) ?: return AppResult.Failure(AppError.NotFound)
        return AppResult.Success(StreamRequest(camera.streamUri(quality), cameras.credentials(cameraId)))
    }
}
