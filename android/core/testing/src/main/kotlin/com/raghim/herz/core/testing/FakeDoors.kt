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
    ) = Door(
        id = "door$n",
        name = "Door $n",
        area = "Area ${(n - 1) / 2 + 1}",
        linkedCameraIds = linkedCameraIds,
        contact = DoorContact.CLOSED,
        isOnline = isOnline,
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

    override suspend fun open(command: SignedDoorCommand): AppResult<Unit> {
        opened += command
        openGate?.await()
        if (openResult is AppResult.Success) {
            state.update { doors ->
                doors.map { if (it.id == command.doorId) it.copy(lock = LockState.UNLOCKED) else it }
            }
        }
        return openResult
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

class FakeUserSettingsRepository(holdToOpen: Duration = HoldToOpen.Default) : UserSettingsRepository {
    val state = MutableStateFlow(holdToOpen)
    override val holdToOpen: Flow<Duration> = state

    override suspend fun setHoldToOpen(duration: Duration) {
        state.value = duration
    }
}
