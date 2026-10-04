// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.domain.door

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.model.Door
import com.raghim.herz.core.model.SignedDoorCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Duration

class ObserveDoorsUseCase @Inject constructor(
    private val repository: DoorRepository,
) {
    operator fun invoke(): Flow<List<Door>> =
        repository.doors.map { doors -> doors.sortedBy { it.name.lowercase() } }.distinctUntilChanged()
}

/**
 * Challenge, biometric signature, open (spec D11). [onAuthorized] runs after the user
 * confirmed and before the command is sent, so the UI can switch to "Unlocking…".
 */
class OpenDoorUseCase @Inject constructor(
    private val repository: DoorRepository,
    private val signer: DoorCommandSigner,
    private val settings: UserSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(door: Door, onAuthorized: () -> Unit = {}): AppResult<Unit> {
        if (!door.isOnline) return AppResult.Failure(AppError.Offline)
        if (!settings.requireFingerprint.first()) {
            onAuthorized()
            return repository.release(door.id)
        }

        val challenge = when (val result = repository.challenge(door.id)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val timestamp = clock.now()
        if (!timestamp.isBefore(challenge.expiresAt)) return AppResult.Failure(AppError.Expired)

        val signature = when (val result = signer.sign(door.name, challenge.payload(timestamp))) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        if (!clock.now().isBefore(challenge.expiresAt)) return AppResult.Failure(AppError.Expired)

        onAuthorized()
        return repository.open(SignedDoorCommand(door.id, challenge.nonce, timestamp, signature))
    }
}

class ObserveHoldToOpenUseCase @Inject constructor(
    private val settings: UserSettingsRepository,
) {
    operator fun invoke(): Flow<Duration> = settings.holdToOpen.map(HoldToOpen::normalize).distinctUntilChanged()
}

class SetHoldToOpenUseCase @Inject constructor(
    private val settings: UserSettingsRepository,
) {
    suspend operator fun invoke(duration: Duration) = settings.setHoldToOpen(HoldToOpen.normalize(duration))
}

class ObserveRequireFingerprintUseCase @Inject constructor(
    private val settings: UserSettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> = settings.requireFingerprint.distinctUntilChanged()
}

class SetRequireFingerprintUseCase @Inject constructor(
    private val settings: UserSettingsRepository,
) {
    suspend operator fun invoke(required: Boolean) = settings.setRequireFingerprint(required)
}

class AddDoorUseCase @Inject constructor(
    private val repository: DoorRepository,
) {
    /** Returns null when [name] is blank. [area] blank becomes null. */
    suspend operator fun invoke(name: String, area: String?): Door? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return repository.add(trimmed, area?.trim()?.ifEmpty { null })
    }
}

class UpdateDoorUseCase @Inject constructor(
    private val repository: DoorRepository,
) {
    /** Does nothing when [name] is blank. */
    suspend operator fun invoke(id: String, name: String, area: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        repository.update(id, trimmed, area?.trim()?.ifEmpty { null })
    }
}

class LockDoorUseCase @Inject constructor(
    private val repository: DoorRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> = repository.lock(id)
}

class SetDoorOnLivePanelUseCase @Inject constructor(
    private val repository: DoorRepository,
) {
    suspend operator fun invoke(id: String, shown: Boolean) = repository.setOnLivePanel(id, shown)
}

class RemoveDoorUseCase @Inject constructor(
    private val repository: DoorRepository,
) {
    suspend operator fun invoke(id: String) = repository.remove(id)
}
