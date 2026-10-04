// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.domain.door

import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.model.Door
import com.raghim.herz.core.model.DoorChallenge
import com.raghim.herz.core.model.SignedDoorCommand
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Doors and their live state. The transport is hidden here: a simulator today, the server
 * (spec D11, B8) or direct Wi-Fi / Bluetooth lock controllers later. All of them use the same
 * challenge, sign, open sequence, so screens and use cases do not change.
 */
interface DoorRepository {
    val doors: Flow<List<Door>>

    suspend fun challenge(doorId: String): AppResult<DoorChallenge>

    /** Returns when the lock controller acknowledged the command. */
    suspend fun open(command: SignedDoorCommand): AppResult<Unit>
}

/** Signs a door command with the device key after a strong biometric check (spec D11). */
interface DoorCommandSigner {
    suspend fun sign(doorName: String, payload: ByteArray): AppResult<ByteArray>
}

interface UserSettingsRepository {
    /** How long the open button must be held before the biometric prompt shows. */
    val holdToOpen: Flow<Duration>

    suspend fun setHoldToOpen(duration: Duration)
}

object HoldToOpen {
    val Min: Duration = 1500.milliseconds
    val Max: Duration = 3.seconds
    val Default: Duration = 2.seconds
    val Step: Duration = 500.milliseconds

    /** Clamps to [Min]..[Max] and rounds to the nearest [Step]. */
    fun normalize(duration: Duration): Duration {
        val steps = (duration.inWholeMilliseconds.toDouble() / Step.inWholeMilliseconds).let(Math::round)
        return (Step * steps.toInt()).coerceIn(Min, Max)
    }
}
