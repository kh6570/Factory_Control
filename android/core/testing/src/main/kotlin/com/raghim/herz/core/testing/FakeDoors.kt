// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.testing

import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.domain.door.DoorCommandSigner
import com.raghim.herz.core.domain.door.DoorRepository
import com.raghim.herz.core.domain.door.HoldToOpen
import com.raghim.herz.core.domain.door.UserSettingsRepository
import com.raghim.herz.core.model.Door
import com.raghim.herz.core.model.DoorChallenge
import com.raghim.herz.core.model.DoorContact
import com.raghim.herz.core.model.LockState
import com.raghim.herz.core.model.SignedDoorCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

object TestDoors {
    fun door(
        n: Int,
        linkedCameraIds: Set<String> = emptySet(),
        isOnline: Boolean = true,
        onLivePanel: Boolean = false,
    ) = Door(
        id = "door$n",
        name = "Door $n",
        area = "Area ${(n - 1) / 2 + 1}",
        linkedCameraIds = linkedCameraIds,
        contact = DoorContact.CLOSED,
        isOnline = isOnline,
        onLivePanel = onLivePanel,
    )
}

/**
 * [openGate] lets a test hold the "waiting for the lock controller" step open:
 * set it before the open call and complete it to deliver the acknowledgement.
 */
class FakeDoorRepository(initial: List<Door> = emptyList()) : DoorRepository {
    val state = MutableStateFlow(initial)
    val opened = mutableListOf<SignedDoorCommand>()
    var challengeResult: (String) -> AppResult<DoorChallenge> = { id ->
        AppResult.Success(DoorChallenge(id, "nonce-$id", TestCameras.epoch.plusSeconds(30)))
    }
    var openResult: AppResult<Unit> = AppResult.Success(Unit)
    var openGate: CompletableDeferred<Unit>? = null

    override val doors: Flow<List<Door>> = state

    override suspend fun challenge(doorId: String): AppResult<DoorChallenge> = challengeResult(doorId)

    val released = mutableListOf<String>()
    private var nextId = 1

    override suspend fun open(command: SignedDoorCommand): AppResult<Unit> {
        opened += command
        openGate?.await()
        if (openResult is AppResult.Success) unlock(command.doorId)
        return openResult
    }

    override suspend fun release(doorId: String): AppResult<Unit> {
        released += doorId
        openGate?.await()
        if (openResult is AppResult.Success) unlock(doorId)
        return openResult
    }

    override suspend fun add(name: String, area: String?): Door {
        val door = Door(id = "added${nextId++}", name = name, area = area, contact = DoorContact.CLOSED)
        state.update { it + door }
        return door
    }

    override suspend fun update(id: String, name: String, area: String?) {
        state.update { doors -> doors.map { if (it.id == id) it.copy(name = name, area = area) else it } }
    }

    override suspend fun setOnLivePanel(id: String, shown: Boolean) {
        state.update { doors -> doors.map { if (it.id == id) it.copy(onLivePanel = shown) else it } }
    }

    override suspend fun lock(doorId: String): AppResult<Unit> {
        state.update { doors ->
            doors.map {
                if (it.id == doorId) it.copy(lock = LockState.LOCKED, contact = DoorContact.CLOSED, unlockedUntil = null) else it
            }
        }
        return AppResult.Success(Unit)
    }

    override suspend fun remove(id: String) {
        state.update { doors -> doors.filterNot { it.id == id } }
    }

    private fun unlock(doorId: String) {
        state.update { doors -> doors.map { if (it.id == doorId) it.copy(lock = LockState.UNLOCKED) else it } }
    }
}

/** [gate] works like [FakeDoorRepository.openGate], for the biometric prompt step. */
class FakeDoorCommandSigner : DoorCommandSigner {
    var result: AppResult<ByteArray> = AppResult.Success(byteArrayOf(1, 2, 3))
    var gate: CompletableDeferred<Unit>? = null
    val requests = mutableListOf<String>()

    override suspend fun sign(doorName: String, payload: ByteArray): AppResult<ByteArray> {
        requests += doorName
        gate?.await()
        return result
    }
}

class FakeUserSettingsRepository(
    holdToOpen: Duration = HoldToOpen.Default,
    requireFingerprint: Boolean = true,
) : UserSettingsRepository {
    val state = MutableStateFlow(holdToOpen)
    val fingerprint = MutableStateFlow(requireFingerprint)
    val gridColumns = MutableStateFlow<Int?>(null)
    override val holdToOpen: Flow<Duration> = state
    override val requireFingerprint: Flow<Boolean> = fingerprint
    override val liveGridColumns: Flow<Int?> = gridColumns

    override suspend fun setHoldToOpen(duration: Duration) {
        state.value = duration
    }

    override suspend fun setRequireFingerprint(required: Boolean) {
        fingerprint.value = required
    }

    override suspend fun setLiveGridColumns(columns: Int?) {
        gridColumns.value = columns
    }
}
