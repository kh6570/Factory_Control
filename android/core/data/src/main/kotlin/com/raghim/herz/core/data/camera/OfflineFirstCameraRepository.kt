// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.camera

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.Dispatcher
import com.raghim.herz.core.common.HerzDispatchers
import com.raghim.herz.core.database.dao.CameraDao
import com.raghim.herz.core.domain.camera.CameraRepository
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.CameraCredentials
import com.raghim.herz.core.model.CameraInfo
import com.raghim.herz.core.security.CredentialCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Saved cameras in Room; credentials are encrypted with [CredentialCipher] before storage. */
@Singleton
class OfflineFirstCameraRepository @Inject constructor(
    private val cameraDao: CameraDao,
    private val cipher: CredentialCipher,
    private val clock: AppClock,
    @Dispatcher(HerzDispatchers.IO) private val io: CoroutineDispatcher,
) : CameraRepository {

    override val cameras: Flow<List<Camera>> =
        cameraDao.observeAll().map { entities -> entities.map { it.toModel() } }

    override suspend fun get(id: String): Camera? = withContext(io) {
        cameraDao.get(id)?.toModel()
    }

    override suspend fun add(info: CameraInfo, name: String, credentials: CameraCredentials?): Camera =
        withContext(io) {
            val camera = Camera(
                id = UUID.randomUUID().toString(),
                name = name,
                host = info.host,
                mainStreamUri = info.mainStreamUri,
                subStreamUri = info.subStreamUri,
                manufacturer = info.manufacturer,
                model = info.model,
                addedAt = Instant.ofEpochMilli(clock.now().toEpochMilli()),
                sortOrder = cameraDao.maxSortOrder() + 1,
            )
            cameraDao.upsert(
                camera.toEntity(
                    encryptedUsername = credentials?.let { cipher.encrypt(it.username) },
                    encryptedPassword = credentials?.let { cipher.encrypt(it.password) },
                ),
            )
            camera
        }

    override suspend fun rename(id: String, name: String) = withContext(io) {
        cameraDao.rename(id, name)
    }

    override suspend fun remove(id: String) = withContext(io) {
        cameraDao.delete(id)
    }

    override suspend fun reorder(idsInOrder: List<String>) = withContext(io) {
        idsInOrder.forEachIndexed { index, id -> cameraDao.setSortOrder(id, index) }
    }

    /** Returns null when nothing is stored or the ciphertext can no longer be decrypted (key lost). */
    override suspend fun credentials(id: String): CameraCredentials? = withContext(io) {
        val entity = cameraDao.get(id) ?: return@withContext null
        val username = entity.encryptedUsername ?: return@withContext null
        val password = entity.encryptedPassword ?: return@withContext null
        try {
            CameraCredentials(cipher.decrypt(username), cipher.decrypt(password))
        } catch (e: Exception) {
            null
        }
    }
}
