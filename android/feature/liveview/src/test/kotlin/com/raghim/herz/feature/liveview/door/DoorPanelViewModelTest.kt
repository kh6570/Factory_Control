// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview.door

import app.cash.turbine.test
import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.domain.camera.ObserveActiveCamerasUseCase
import com.raghim.herz.core.domain.door.LockDoorUseCase
import com.raghim.herz.core.domain.door.ObserveDoorsUseCase
import com.raghim.herz.core.domain.door.ObserveHoldToOpenUseCase
import com.raghim.herz.core.domain.door.ObserveRequireFingerprintUseCase
import com.raghim.herz.core.domain.door.OpenDoorUseCase
import com.raghim.herz.core.model.DoorContact
import com.raghim.herz.core.model.LockState
import com.raghim.herz.core.testing.FakeActiveCamerasRepository
import com.raghim.herz.core.testing.FakeDoorCommandSigner
import com.raghim.herz.core.testing.FakeDoorRepository
import com.raghim.herz.core.testing.FakeUserSettingsRepository
import com.raghim.herz.core.testing.MainDispatcherRule
import com.raghim.herz.core.testing.TestCameras
import com.raghim.herz.core.testing.TestDoors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class DoorPanelViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cameras = (1..2).map { TestCameras.camera(it) }
    private val active = FakeActiveCamerasRepository(initial = listOf(TestCameras.active(cameras[1])))
    private val doors = FakeDoorRepository(
        listOf(
            TestDoors.door(1, onLivePanel = true),
            TestDoors.door(2, linkedCameraIds = setOf(cameras[1].id), onLivePanel = true),
            TestDoors.door(3, isOnline = false, onLivePanel = true),
            TestDoors.door(4),
        ),
    )
    private val signer = FakeDoorCommandSigner()
    private val settings = FakeUserSettingsRepository()

    private fun viewModel() = DoorPanelViewModel(
        observeDoors = ObserveDoorsUseCase(doors),
        observeActiveCameras = ObserveActiveCamerasUseCase(active),
        observeHoldToOpen = ObserveHoldToOpenUseCase(settings),
        observeRequireFingerprint = ObserveRequireFingerprintUseCase(settings),
        openDoor = OpenDoorUseCase(doors, signer, settings, AppClock { TestCameras.epoch }),
        lockDoor = LockDoorUseCase(doors),
    )

    private fun TestScope.subscribe(vm: DoorPanelViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
    }

    @Test
    fun `doors of cameras on the wall come first, then by name`() = runTest {
        val vm = viewModel()
        subscribe(vm)

        val items = vm.state.value.doors
        assertEquals(listOf("door2", "door1", "door3"), items.map { it.door.id })
        assertFalse(vm.state.value.doors.any { it.door.id == "door4" })
        assertTrue(items[0].onWall)
        assertFalse(items[1].onWall)
    }

    @Test
    fun `an open door moves to the bottom, and locking brings it back`() = runTest {
        val vm = viewModel()
        subscribe(vm)
        doors.state.value = doors.state.value.map { door ->
            if (door.id == "door1") door.copy(lock = LockState.UNLOCKED, contact = DoorContact.OPEN) else door
        }

        assertEquals(listOf("door2", "door3", "door1"), vm.state.value.doors.map { it.door.id })

        vm.onIntent(DoorPanelIntent.Lock("door1"))

        assertEquals(LockState.LOCKED, vm.item("door1").door.lock)
        assertEquals(listOf("door2", "door1", "door3"), vm.state.value.doors.map { it.door.id })
    }

    @Test
    fun `hold duration follows the setting`() = runTest {
        val vm = viewModel()
        subscribe(vm)

        settings.state.value = 2500.milliseconds

        assertEquals(2500.milliseconds, vm.state.value.holdToOpen)
    }

    @Test
    fun `open goes Confirming, Unlocking, then shows the door unlocked`() = runTest {
        val vm = viewModel()
        subscribe(vm)
        val biometric = CompletableDeferred<Unit>().also { signer.gate = it }
        val ack = CompletableDeferred<Unit>().also { doors.openGate = it }

        vm.onIntent(DoorPanelIntent.Open("door1"))
        assertEquals(DoorCommand.Confirming, vm.item("door1").command)

        biometric.complete(Unit)
        assertEquals(DoorCommand.Unlocking, vm.item("door1").command)

        ack.complete(Unit)
        assertNull(vm.item("door1").command)
        assertEquals(LockState.UNLOCKED, vm.item("door1").door.lock)
        assertEquals(listOf("Door 1"), signer.requests)
        assertEquals("door1", doors.opened.single().doorId)
    }

    @Test
    fun `a second open while one is pending is ignored`() = runTest {
        val vm = viewModel()
        subscribe(vm)
        signer.gate = CompletableDeferred()

        vm.onIntent(DoorPanelIntent.Open("door1"))
        vm.onIntent(DoorPanelIntent.Open("door1"))

        assertEquals(1, signer.requests.size)
    }

    @Test
    fun `offline and already unlocked doors cannot be opened`() = runTest {
        val vm = viewModel()
        subscribe(vm)
        vm.onIntent(DoorPanelIntent.Open("door1"))

        vm.onIntent(DoorPanelIntent.Open("door1"))
        vm.onIntent(DoorPanelIntent.Open("door3"))

        assertEquals(listOf("Door 1"), signer.requests)
        assertFalse(vm.item("door3").canOpen)
    }

    @Test
    fun `cancelling the fingerprint prompt shows no error`() = runTest {
        val vm = viewModel()
        subscribe(vm)
        signer.result = AppResult.Failure(AppError.Cancelled)

        vm.effects.test {
            vm.onIntent(DoorPanelIntent.Open("door1"))
            expectNoEvents()
        }
        assertNull(vm.item("door1").command)
        assertTrue(doors.opened.isEmpty())
    }

    @Test
    fun `a refused command reports the door name and error`() = runTest {
        val vm = viewModel()
        subscribe(vm)
        doors.openResult = AppResult.Failure(AppError.Unauthorized)

        vm.effects.test {
            vm.onIntent(DoorPanelIntent.Open("door2"))
            assertEquals(DoorPanelEffect.OpenFailed("Door 2", AppError.Unauthorized), awaitItem())
        }
        assertEquals(LockState.LOCKED, vm.item("door2").door.lock)
        assertTrue(vm.item("door2").canOpen)
    }

    private fun DoorPanelViewModel.item(id: String) = state.value.doors.first { it.door.id == id }
}
