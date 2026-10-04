// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.domain.door

import app.cash.turbine.test
import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.model.Door
import com.raghim.herz.core.model.DoorChallenge
import com.raghim.herz.core.model.SignedDoorCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DoorUseCasesTest {

    private val now = Instant.parse("2026-10-04T10:00:00Z")
    private var clockNow = now
    private val clock = AppClock { clockNow }

    private class Repo(var challengeResult: (String) -> AppResult<DoorChallenge>) : DoorRepository {
        val state = MutableStateFlow<List<Door>>(emptyList())
        val opened = mutableListOf<SignedDoorCommand>()
        val challenged = mutableListOf<String>()
        override val doors: Flow<List<Door>> = state
        override suspend fun challenge(doorId: String): AppResult<DoorChallenge> {
            challenged += doorId
            return challengeResult(doorId)
        }
        override suspend fun open(command: SignedDoorCommand): AppResult<Unit> {
            opened += command
            return AppResult.Success(Unit)
        }
    }

    private class Signer(var result: AppResult<ByteArray> = AppResult.Success(byteArrayOf(7, 7))) : DoorCommandSigner {
        val payloads = mutableListOf<Pair<String, ByteArray>>()
        var onSign: () -> Unit = {}
        override suspend fun sign(doorName: String, payload: ByteArray): AppResult<ByteArray> {
            payloads += doorName to payload
            onSign()
            return result
        }
    }

    private val repo = Repo { AppResult.Success(DoorChallenge(it, "n1", now.plusSeconds(30))) }
    private val signer = Signer()
    private val open = OpenDoorUseCase(repo, signer, clock)
    private val gate = Door(id = "D1", name = "Main gate")

    @Test
    fun `signs doorId, nonce and timestamp, then opens`() = runTest {
        var authorized = false

        val result = open(gate) { authorized = true }

        assertEquals(AppResult.Success(Unit), result)
        assertTrue(authorized)
        assertEquals("Main gate", signer.payloads.single().first)
        assertArrayEquals("D1|n1|${now.epochSecond}".encodeToByteArray(), signer.payloads.single().second)
        val command = repo.opened.single()
        assertEquals("D1", command.doorId)
        assertEquals("n1", command.nonce)
        assertArrayEquals(byteArrayOf(7, 7), command.signature)
    }

    @Test
    fun `cancelled biometric sends nothing`() = runTest {
        signer.result = AppResult.Failure(AppError.Cancelled)
        var authorized = false

        val result = open(gate) { authorized = true }

        assertEquals(AppResult.Failure(AppError.Cancelled), result)
        assertTrue(!authorized)
        assertTrue(repo.opened.isEmpty())
    }

    @Test
    fun `offline door is not challenged`() = runTest {
        val result = open(gate.copy(isOnline = false))

        assertEquals(AppResult.Failure(AppError.Offline), result)
        assertTrue(repo.challenged.isEmpty())
    }

    @Test
    fun `challenge failure is returned`() = runTest {
        repo.challengeResult = { AppResult.Failure(AppError.Unreachable) }

        assertEquals(AppResult.Failure(AppError.Unreachable), open(gate))
        assertTrue(signer.payloads.isEmpty())
    }

    @Test
    fun `challenge that expires during the biometric prompt is not sent`() = runTest {
        signer.onSign = { clockNow = now.plusSeconds(31) }

        assertEquals(AppResult.Failure(AppError.Expired), open(gate))
        assertTrue(repo.opened.isEmpty())
    }

    @Test
    fun `already expired challenge skips the prompt`() = runTest {
        repo.challengeResult = { AppResult.Success(DoorChallenge(it, "n1", now)) }

        assertEquals(AppResult.Failure(AppError.Expired), open(gate))
        assertTrue(signer.payloads.isEmpty())
    }

    @Test
    fun `doors are sorted by name`() = runTest {
        repo.state.value = listOf(Door("2", "loading bay"), Door("1", "Main gate"), Door("3", "Annex"))

        ObserveDoorsUseCase(repo)().test {
            assertEquals(listOf("Annex", "loading bay", "Main gate"), awaitItem().map { it.name })
        }
    }

    @Test
    fun `hold duration is clamped and rounded to half seconds`() {
        assertEquals(HoldToOpen.Min, HoldToOpen.normalize(200.milliseconds))
        assertEquals(HoldToOpen.Max, HoldToOpen.normalize(10.seconds))
        assertEquals(2500.milliseconds, HoldToOpen.normalize(2400.milliseconds))
        assertEquals(2.seconds, HoldToOpen.normalize(2.seconds))
    }

    @Test
    fun `set hold duration stores the normalized value`() = runTest {
        val settings = object : UserSettingsRepository {
            var stored: Duration? = null
            override val holdToOpen: Flow<Duration> = MutableStateFlow(HoldToOpen.Default)
            override suspend fun setHoldToOpen(duration: Duration) {
                stored = duration
            }
        }

        SetHoldToOpenUseCase(settings)(2600.milliseconds)

        assertEquals(2500.milliseconds, settings.stored)
    }
}
