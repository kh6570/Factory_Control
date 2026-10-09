// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.door

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.database.dao.DoorDao
import com.raghim.herz.core.database.model.DoorEntity
import com.raghim.herz.core.model.DoorChallenge
import com.raghim.herz.core.model.DoorContact
import com.raghim.herz.core.model.LockState
import com.raghim.herz.core.model.SignedDoorCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
        doorsDao = MemoryDoorDao(),
        verifier = { _, _ -> validSignature },
        clock = AppClock { Instant.ofEpochMilli(testScheduler.currentTime) },
    )

    private suspend fun SimulatedDoorRepository.signedChallenge(doorId: String): SignedDoorCommand {
        val challenge = (challenge(doorId) as AppResult.Success<DoorChallenge>).value
        return SignedDoorCommand(doorId, challenge.nonce, Instant.EPOCH, byteArrayOf(1))
    }

    private suspend fun SimulatedDoorRepository.door(id: String) = doors.first().first { it.id == id }

    @Test
    fun `an opened door stays open`() = runTest {
        val repository = repository()

        val result = repository.open(repository.signedChallenge("D01"))

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(LockState.UNLOCKED, repository.door("D01").lock)
        assertEquals(DoorContact.OPEN, repository.door("D01").contact)
        assertNull(repository.door("D01").unlockedUntil)

        advanceTimeBy(30_000)
        assertEquals(LockState.UNLOCKED, repository.door("D01").lock)
        assertEquals(DoorContact.OPEN, repository.door("D01").contact)

        assertEquals(AppResult.Success(Unit), repository.lock("D01"))
        assertEquals(LockState.LOCKED, repository.door("D01").lock)
        assertEquals(DoorContact.CLOSED, repository.door("D01").contact)
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
    fun `unused challenges expire and a fresh one still works`() = runTest {
        val repository = repository()
        repeat(50) { repository.challenge("D01") }

        advanceTimeBy(31_000)

        assertEquals(AppResult.Success(Unit), repository.open(repository.signedChallenge("D01")))
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

    @Test
    fun `added doors are saved and a removed door is gone`() = runTest {
        val repository = repository()
        repository.challenge("D01")

        val added = repository.add("Side gate", "Yard")
        repository.update(added.id, "Yard gate", "Yard")
        assertEquals("Yard gate", repository.door(added.id).name)

        repository.remove(added.id)
        assertEquals(AppResult.Failure(AppError.NotFound), repository.challenge(added.id))
    }
}

/** In memory, so the simulator tests do not need a database. */
private class MemoryDoorDao : DoorDao {
    private val state = kotlinx.coroutines.flow.MutableStateFlow<List<DoorEntity>>(emptyList())
    override fun observeAll() = state
    override suspend fun maxSortOrder(): Int = state.value.maxOfOrNull { it.sortOrder } ?: -1
    override suspend fun count(): Int = state.value.size
    override suspend fun upsert(entity: DoorEntity) {
        state.value = state.value.filterNot { it.id == entity.id } + entity
    }
    override suspend fun update(id: String, name: String, area: String?) {
        state.value = state.value.map { if (it.id == id) it.copy(name = name, area = area) else it }
    }
    override suspend fun setOnLivePanel(id: String, shown: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(onLivePanel = shown) else it }
    }
    override suspend fun setSortOrder(id: String, sortOrder: Int) {
        state.value = state.value.map { if (it.id == id) it.copy(sortOrder = sortOrder) else it }
    }
    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}
