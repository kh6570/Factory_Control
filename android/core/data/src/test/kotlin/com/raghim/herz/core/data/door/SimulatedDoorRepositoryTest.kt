// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.door

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.model.DoorChallenge
import com.raghim.herz.core.model.DoorContact
import com.raghim.herz.core.model.LockState
import com.raghim.herz.core.model.SignedDoorCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedDoorRepositoryTest {

    private var validSignature = true

    private fun TestScope.repository() = SimulatedDoorRepository(
        verifier = { _, _ -> validSignature },
        clock = AppClock { Instant.ofEpochMilli(testScheduler.currentTime) },
        scope = backgroundScope,
    )

    private suspend fun SimulatedDoorRepository.signedChallenge(doorId: String): SignedDoorCommand {
        val challenge = (challenge(doorId) as AppResult.Success<DoorChallenge>).value
        return SignedDoorCommand(doorId, challenge.nonce, Instant.EPOCH, byteArrayOf(1))
    }

    private suspend fun SimulatedDoorRepository.door(id: String) = doors.first().first { it.id == id }

    @Test
    fun `open releases the lock for a 5 s pulse with reed contact open then closed`() = runTest {
        val repository = repository()

        val result = repository.open(repository.signedChallenge("D01"))

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(LockState.UNLOCKED, repository.door("D01").lock)
        assertEquals(DoorContact.CLOSED, repository.door("D01").contact)

        advanceTimeBy(1_300)
        assertEquals(DoorContact.OPEN, repository.door("D01").contact)

        advanceTimeBy(3_000)
        assertEquals(DoorContact.CLOSED, repository.door("D01").contact)
        assertEquals(LockState.UNLOCKED, repository.door("D01").lock)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(LockState.LOCKED, repository.door("D01").lock)
        assertNull(repository.door("D01").unlockedUntil)
    }

    @Test
    fun `a nonce works only once`() = runTest {
        val repository = repository()
        val command = repository.signedChallenge("D01")

        repository.open(command)

        assertEquals(AppResult.Failure(AppError.Expired), repository.open(command))
    }

    @Test
    fun `an expired challenge is refused`() = runTest {
        val repository = repository()
        val command = repository.signedChallenge("D01")

        advanceTimeBy(31_000)

        assertEquals(AppResult.Failure(AppError.Expired), repository.open(command))
        assertEquals(LockState.LOCKED, repository.door("D01").lock)
    }

    @Test
    fun `a bad signature is refused`() = runTest {
        val repository = repository()
        validSignature = false

        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.open(repository.signedChallenge("D02")))
        assertEquals(LockState.LOCKED, repository.door("D02").lock)
    }

    @Test
    fun `a challenge for one door cannot open another`() = runTest {
        val repository = repository()
        val forGate = repository.signedChallenge("D01")
        val forged = SignedDoorCommand("D02", forGate.nonce, forGate.timestamp, forGate.signature)

        assertEquals(AppResult.Failure(AppError.Expired), repository.open(forged))
    }

    @Test
    fun `offline door gives no challenge`() = runTest {
        val repository = repository()

        assertEquals(AppResult.Failure(AppError.Offline), repository.challenge("D06"))
        assertTrue(repository.doors.first().any { !it.isOnline })
    }
}
