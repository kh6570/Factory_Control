// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.camera

import app.cash.turbine.test
import com.raghim.herz.core.model.CameraCredentials
import com.raghim.herz.core.testing.TestCameras
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineFirstCameraRepositoryTest {

    private val dao = FakeCameraDao()
    private val cipher = FakeCredentialCipher()
    private val clock = FakeAppClock()

    private fun TestScope.repository() =
        OfflineFirstCameraRepository(dao, cipher, clock, StandardTestDispatcher(testScheduler))

    @Test
    fun addEncryptsUsernameAndPasswordSeparately() = runTest {
        val camera = repository().add(TestCameras.info(1), "Gate", CameraCredentials("admin", "s3cret"))

        val entity = dao.rows.value.getValue(camera.id)
        assertEquals(cipher.encrypt("admin"), entity.encryptedUsername)
        assertEquals(cipher.encrypt("s3cret"), entity.encryptedPassword)
        assertNotEquals("admin", entity.encryptedUsername)
        assertNotEquals("s3cret", entity.encryptedPassword)
    }

    @Test
    fun addStoresCameraFields() = runTest {
        val info = TestCameras.info(2)
        val camera = repository().add(info, "Yard", null)

        assertEquals("Yard", camera.name)
        assertEquals(info.host, camera.host)
        assertEquals(info.mainStreamUri, camera.mainStreamUri)
        assertEquals(info.subStreamUri, camera.subStreamUri)
        assertEquals(clock.time.toEpochMilli(), camera.addedAt.toEpochMilli())
        assertEquals(camera, repository().get(camera.id))
    }

    @Test
    fun addWithoutCredentialsStoresNulls() = runTest {
        val repository = repository()
        val camera = repository.add(TestCameras.info(1), "Gate", null)

        val entity = dao.rows.value.getValue(camera.id)
        assertNull(entity.encryptedUsername)
        assertNull(entity.encryptedPassword)
        assertNull(repository.credentials(camera.id))
    }

    @Test
    fun credentialsRoundTrip() = runTest {
        val repository = repository()
        val credentials = CameraCredentials("admin", "s3cret")
        val camera = repository.add(TestCameras.info(1), "Gate", credentials)

        assertEquals(credentials, repository.credentials(camera.id))
    }

    @Test
    fun brokenCiphertextReturnsNull() = runTest {
        val repository = repository()
        val camera = repository.add(TestCameras.info(1), "Gate", CameraCredentials("admin", "s3cret"))
        val entity = dao.rows.value.getValue(camera.id)
        dao.upsert(entity.copy(encryptedPassword = "garbage"))

        assertNull(repository.credentials(camera.id))
    }

    @Test
    fun credentialsForUnknownCameraIsNull() = runTest {
        assertNull(repository().credentials("missing"))
    }

    @Test
    fun renameAndRemoveAreReflectedInCameras() = runTest {
        val repository = repository()
        val gate = repository.add(TestCameras.info(1), "Gate", null)
        val yard = repository.add(TestCameras.info(2), "Yard", null)

        repository.cameras.test {
            assertEquals(listOf("Gate", "Yard"), awaitItem().map { it.name })

            repository.rename(gate.id, "Zone")
            assertEquals(listOf("Yard", "Zone"), awaitItem().map { it.name })

            repository.remove(yard.id)
            assertEquals(listOf("Zone"), awaitItem().map { it.name })
        }
        assertNull(repository.get(yard.id))
        assertEquals("Zone", repository.cameras.first().single().name)
    }
}
