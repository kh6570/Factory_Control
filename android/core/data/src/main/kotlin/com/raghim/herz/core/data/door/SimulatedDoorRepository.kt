// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.door

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.common.AppScope
import com.raghim.herz.core.domain.door.DoorRepository
import com.raghim.herz.core.model.Door
import com.raghim.herz.core.model.DoorChallenge
import com.raghim.herz.core.model.DoorContact
import com.raghim.herz.core.model.LockState
import com.raghim.herz.core.model.SignedDoorCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/**
 * Stand-in for door controllers until the server or direct lock controllers exist. It follows the
 * real sequence: one-time nonce (30 s), real signature check, controller ack, a 5 s release pulse,
 * reed contact open then closed (spec B8). One door is offline.
 */
@Singleton
class SimulatedDoorRepository @Inject constructor(
    private val verifier: DoorSignatureVerifier,
    private val clock: AppClock,
    @param:AppScope private val scope: CoroutineScope,
) : DoorRepository {

    private val state = MutableStateFlow(SEED)
    private val pending = mutableMapOf<String, DoorChallenge>()
    private val pendingLock = Mutex()
    private val pulses = mutableMapOf<String, Job>()

    override val doors: Flow<List<Door>> = state

    override suspend fun challenge(doorId: String): AppResult<DoorChallenge> {
        val door = state.value.firstOrNull { it.id == doorId } ?: return AppResult.Failure(AppError.NotFound)
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
        val challenge = pendingLock.withLock { pending.remove(command.nonce) }
        if (challenge == null || challenge.doorId != command.doorId || !clock.now().isBefore(challenge.expiresAt)) {
            return AppResult.Failure(AppError.Expired)
        }
        if (!verifier.verify(challenge.payload(command.timestamp), command.signature)) {
            return AppResult.Failure(AppError.Unauthorized)
        }
        val door = state.value.firstOrNull { it.id == command.doorId } ?: return AppResult.Failure(AppError.NotFound)
        if (!door.isOnline) return AppResult.Failure(AppError.Offline)

        delay(ACK_DELAY)
        startPulse(command.doorId)
        return AppResult.Success(Unit)
    }

    private fun startPulse(doorId: String) {
        val until = clock.now().plus(PULSE.toJavaDuration())
        update(doorId) { it.copy(lock = LockState.UNLOCKED, unlockedUntil = until) }
        pulses.remove(doorId)?.cancel()
        pulses[doorId] = scope.launch {
            delay(OPENS_AFTER)
            update(doorId) { it.copy(contact = DoorContact.OPEN) }
            delay(CLOSES_AFTER - OPENS_AFTER)
            update(doorId) { it.copy(contact = DoorContact.CLOSED) }
            delay(PULSE - CLOSES_AFTER)
            update(doorId) { it.copy(lock = LockState.LOCKED, unlockedUntil = null) }
        }
    }

    private fun update(doorId: String, change: (Door) -> Door) {
        state.update { doors -> doors.map { if (it.id == doorId) change(it) else it } }
    }

    internal companion object {
        val CHALLENGE_TTL = 30.seconds
        val ACK_DELAY = 700.milliseconds
        val PULSE = 5.seconds
        val OPENS_AFTER = 1200.milliseconds
        val CLOSES_AFTER = 4.seconds

        val SEED = listOf(
            Door("D01", "Main gate", "Gate", contact = DoorContact.CLOSED),
            Door("D02", "Loading bay", "Warehouse", contact = DoorContact.CLOSED),
            Door("D03", "Warehouse rear", "Warehouse", contact = DoorContact.CLOSED),
            Door("D04", "Office entrance", "Office", contact = DoorContact.CLOSED),
            Door("D05", "Server room", "Office", contact = DoorContact.CLOSED),
            Door("D06", "Roof access", "Roof", contact = DoorContact.UNKNOWN, isOnline = false),
        )
    }
}
