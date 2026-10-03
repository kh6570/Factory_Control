// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.camera

import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.model.CameraCredentials
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.model.StreamRequest
import com.raghim.herz.core.testing.FakeCameraRepository
import com.raghim.herz.core.testing.TestCameras
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectLanCameraSourceTest {

    private val withSub = TestCameras.camera(1)
    private val mainOnly = TestCameras.camera(2).copy(subStreamUri = null)
    private val cameras = FakeCameraRepository(listOf(withSub, mainOnly))
    private val source = DirectLanCameraSource(cameras)

    @Test
    fun subQualityUsesSubStream() = runTest {
        assertEquals(
            AppResult.Success(StreamRequest(withSub.subStreamUri!!, null)),
            source.stream(withSub.id, StreamQuality.SUB),
        )
    }

    @Test
    fun mainQualityUsesMainStream() = runTest {
        assertEquals(
            AppResult.Success(StreamRequest(withSub.mainStreamUri, null)),
            source.stream(withSub.id, StreamQuality.MAIN),
        )
    }

    @Test
    fun subQualityFallsBackToMainStream() = runTest {
        assertEquals(
            AppResult.Success(StreamRequest(mainOnly.mainStreamUri, null)),
            source.stream(mainOnly.id, StreamQuality.SUB),
        )
    }

    @Test
    fun includesStoredCredentials() = runTest {
        val credentials = CameraCredentials("admin", "s3cret")
        cameras.credentialsById[withSub.id] = credentials

        assertEquals(
            AppResult.Success(StreamRequest(withSub.subStreamUri!!, credentials)),
            source.stream(withSub.id, StreamQuality.SUB),
        )
    }

    @Test
    fun missingCameraIsNotFound() = runTest {
        assertEquals(AppResult.Failure(AppError.NotFound), source.stream("missing", StreamQuality.SUB))
    }
}
