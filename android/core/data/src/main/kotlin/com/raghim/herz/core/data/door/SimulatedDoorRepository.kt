// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.door

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.database.dao.DoorDao
import com.raghim.herz.core.database.model.DoorEntity
import com.raghim.herz.core.domain.door.DoorRepository
import com.raghim.herz.core.model.Door
import com.raghim.herz.core.model.DoorChallenge
import com.raghim.herz.core.model.DoorContact
import com.raghim.herz.core.model.LockState
import com.raghim.herz.core.model.SignedDoorCommand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** Checks a door command signature against the registered device key. */
fun interface DoorSignatureVerifier {
    fun verify(payload: ByteArray, signature: ByteArray): Boolean
}

/** Lock and reed state while a pulse runs. Not stored: a restart leaves every door locked. */
private data class LiveDoor(
    val lock: LockState = LockState.LOCKED,
    val contact: DoorContact? = null,
    val unlockedUntil: Instant? = null,
)

/**
 * Saved doors plus a stand-in for lock controllers. The saved list survives restarts. Opening
 * follows the real sequence: one-time nonce (30 s), signature check, acknowledgement (spec B8).
 * An opened door stays open until the app restarts. The first launch adds sample doors, one offline.
 */
@Singleton
class SimulatedDoorRepository @Inject constructor(
    private val doorsDao: DoorDao,
    private val verifier: DoorSignatureVerifier,
    private val clock: AppClock,
) : DoorRepository {

    private val live = MutableStateFlow<Map<String, LiveDoor>>(emptyMap())
    private val pending = mutableMapOf<String, DoorChallenge>()
    private val pendingLock = Mutex()
    private val seedLock = Mutex()
    private var seeded = false

    override val doors: Flow<List<Door>> = combine(doorsDao.observeAll(), live) { saved, pulsesNow ->
        saved.map { it.toDoor(pulsesNow[it.id]) }
    }

    override suspend fun challenge(doorId: String): AppResult<DoorChallenge> {
        ensureSeeded()
        val door = saved(doorId) ?: return AppResult.Failure(AppError.NotFound)
        if (!door.isOnline) return AppResult.Failure(AppError.Offline)
        val challenge = DoorChallenge(
            doorId = doorId,
            nonce = UUID.randomUUID().toString(),
            expiresAt = clock.now().plus(CHALLENGE_TTL.toJavaDuration()),
        )
        pendingLock.withLock { pending[challenge.nonce] = challenge }
        return AppResult.Success(challenge)
    }

    override suspend fun open(command: SignedDoorCommand): AppResult<Unit> {
        ensureSeeded()
        val challenge = pendingLock.withLock { pending.remove(command.nonce) }
        if (challenge == null || challenge.doorId != command.doorId || !clock.now().isBefore(challenge.expiresAt)) {
            return AppResult.Failure(AppError.Expired)
        }
        if (!verifier.verify(challenge.payload(command.timestamp), command.signature)) {
            return AppResult.Failure(AppError.Unauthorized)
        }
        return acknowledge(command.doorId)
    }

    override suspend fun release(doorId: String): AppResult<Unit> {
        ensureSeeded()
        return acknowledge(doorId)
    }

    override suspend fun add(name: String, area: String?): Door {
        ensureSeeded()
        val entity = DoorEntity(
            id = "D-" + UUID.randomUUID().toString().substringBefore('-'),
            name = name,
            area = area,
            linkedCameraIds = "",
            isOnline = true,
            onLivePanel = false,
            addedAtEpochMs = clock.now().toEpochMilli(),
        )
        doorsDao.upsert(entity)
        return entity.toDoor(live = null)
    }

    override suspend fun update(id: String, name: String, area: String?) {
        ensureSeeded()
        doorsDao.update(id, name, area)
    }

    override suspend fun setOnLivePanel(id: String, shown: Boolean) {
        ensureSeeded()
        doorsDao.setOnLivePanel(id, shown)
    }

    override suspend fun lock(doorId: String): AppResult<Unit> {
        ensureSeeded()
        if (saved(doorId) == null) return AppResult.Failure(AppError.NotFound)
        live.update { it - doorId }
        return AppResult.Success(Unit)
    }

    override suspend fun remove(id: String) {
        ensureSeeded()
        live.update { it - id }
        doorsDao.delete(id)
    }

    private suspend fun acknowledge(doorId: String): AppResult<Unit> {
        val door = saved(doorId) ?: return AppResult.Failure(AppError.NotFound)
        if (!door.isOnline) return AppResult.Failure(AppError.Offline)
        delay(ACK_DELAY)
        live.update { it + (doorId to LiveDoor(LockState.UNLOCKED, contact = DoorContact.OPEN)) }
        return AppResult.Success(Unit)
    }

    private suspend fun saved(id: String): Door? = doors.first().firstOrNull { it.id == id }

    private suspend fun ensureSeeded() {
        if (seeded) return
        seedLock.withLock {
            if (seeded) return
            if (doorsDao.count() == 0) {
                val now = clock.now().toEpochMilli()
                SEED.forEach { doorsDao.upsert(it.copy(addedAtEpochMs = now)) }
            }
            seeded = true
        }
    }

    private fun DoorEntity.toDoor(live: LiveDoor?): Door = Door(
        id = id,
        name = name,
        area = area,
        linkedCameraIds = linkedCameraIds.split(',').filter { it.isNotEmpty() }.toSet(),
        lock = live?.lock ?: LockState.LOCKED,
        contact = live?.contact ?: if (isOnline) DoorContact.CLOSED else DoorContact.UNKNOWN,
        isOnline = isOnline,
        onLivePanel = onLivePanel,
        unlockedUntil = live?.unlockedUntil,
    )

    internal companion object {
        val CHALLENGE_TTL = 30.seconds
        val ACK_DELAY = 700.milliseconds

        val SEED = listOf(
            DoorEntity("D01", "Main gate", "Gate", "", true, false, 0),
            DoorEntity("D02", "Loading bay", "Warehouse", "", true, false, 0),
            DoorEntity("D03", "Warehouse rear", "Warehouse", "", true, false, 0),
            DoorEntity("D04", "Office entrance", "Office", "", true, false, 0),
            DoorEntity("D05", "Server room", "Office", "", true, false, 0),
            DoorEntity("D06", "Roof access", "Roof", "", false, false, 0),
        )
    }
}
