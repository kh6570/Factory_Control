// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview

import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import com.raghim.herz.core.domain.camera.NetworkMonitor
import com.raghim.herz.core.domain.camera.ObserveActiveCamerasUseCase
import com.raghim.herz.core.domain.camera.ObserveNetworkModeUseCase
import com.raghim.herz.core.domain.door.ObserveLiveGridColumnsUseCase
import com.raghim.herz.core.domain.door.SetLiveGridColumnsUseCase
import com.raghim.herz.core.model.NetworkMode
import com.raghim.herz.core.model.SessionSource
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.testing.FakeActiveCamerasRepository
import com.raghim.herz.core.testing.FakeNetworkMonitor
import com.raghim.herz.core.testing.FakeUserSettingsRepository
import com.raghim.herz.core.testing.FakeVideoPlayer
import com.raghim.herz.core.testing.FakeVideoPlayerFactory
import com.raghim.herz.core.testing.MainDispatcherRule
import com.raghim.herz.core.testing.TestCameras
import com.raghim.herz.core.video.PlayerPool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
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

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveCamerasViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cameras = (1..6).map { TestCameras.camera(it) }
    private val repository = FakeActiveCamerasRepository(initial = cameras.map { TestCameras.active(it) })
    private val network = FakeNetworkMonitor(NetworkMode.LAN)
    private val factory = FakeVideoPlayerFactory()
    private val pool = PlayerPool(factory)
    private val settings = FakeUserSettingsRepository()

    private fun viewModel(
        savedState: SavedStateHandle = SavedStateHandle(),
        monitor: NetworkMonitor = network,
    ) = ActiveCamerasViewModel(
        observeActiveCameras = ObserveActiveCamerasUseCase(repository),
        observeNetworkMode = ObserveNetworkModeUseCase(monitor),
        observeLiveGridColumns = ObserveLiveGridColumnsUseCase(settings),
        setLiveGridColumns = SetLiveGridColumnsUseCase(settings),
        pool = pool,
        savedState = savedState,
    )

    private fun TestScope.subscribe(vm: ActiveCamerasViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
    }

    private fun fake(id: String): FakeVideoPlayer = factory.created.getValue(id)

    @Test
    fun `starts loading, then shows tiles with alarm cameras first`() = runTest {
        repository.state.value = listOf(
            TestCameras.active(cameras[0]),
            TestCameras.active(cameras[1], source = SessionSource.ALARM, startedAt = TestCameras.epoch.plusSeconds(60)),
        )
        val gate = GatedNetworkMonitor()
        val vm = viewModel(monitor = gate)

        vm.state.test {
            assertTrue(awaitItem().isLoading)
            gate.mode.emit(NetworkMode.LAN)
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(listOf("cam02", "cam01"), loaded.tiles.map { it.cameraId })
            assertEquals(NetworkMode.LAN, loaded.networkMode)
        }
    }

    @Test
    fun `live tiles are limited by the network budget`() = runTest {
        network.state.value = NetworkMode.CELLULAR
        val vm = viewModel()
        subscribe(vm)

        assertEquals(6, vm.state.value.tiles.size)
        assertEquals(4, vm.state.value.liveBudget)

        network.state.value = NetworkMode.OFFLINE
        assertEquals(0, vm.state.value.liveBudget)
    }

    @Test
    fun `a camera past the budget can still be maximized and plays the main stream`() = runTest {
        network.state.value = NetworkMode.CELLULAR
        val vm = viewModel()
        subscribe(vm)

        vm.onIntent(ActiveCamerasIntent.Maximize("cam06"))

        assertEquals("cam06", vm.state.value.maximizedCameraId)
        assertEquals(StreamQuality.MAIN, fake("cam06").quality.value)
    }

    @Test
    fun `maximize switches the player to the main stream`() = runTest {
        val vm = viewModel()
        subscribe(vm)

        vm.onIntent(ActiveCamerasIntent.Maximize("cam02"))

        assertEquals("cam02", vm.state.value.maximizedCameraId)
        assertEquals(StreamQuality.MAIN, fake("cam02").quality.value)
        assertTrue("switch:MAIN" in fake("cam02").calls)
    }

    @Test
    fun `minimize switches back to the sub stream and clears the maximized camera`() = runTest {
        val vm = viewModel()
        subscribe(vm)
        vm.onIntent(ActiveCamerasIntent.Maximize("cam02"))

        vm.onIntent(ActiveCamerasIntent.Minimize)

        assertNull(vm.state.value.maximizedCameraId)
        assertEquals(StreamQuality.SUB, fake("cam02").quality.value)
    }

    @Test
    fun `swipe moves the main stream to the new camera`() = runTest {
        val vm = viewModel()
        subscribe(vm)
        vm.onIntent(ActiveCamerasIntent.Maximize("cam01"))

        vm.onIntent(ActiveCamerasIntent.SwipeTo("cam02"))

        assertEquals("cam02", vm.state.value.maximizedCameraId)
        assertEquals(StreamQuality.SUB, fake("cam01").quality.value)
        assertEquals(StreamQuality.MAIN, fake("cam02").quality.value)
    }

    @Test
    fun `maximized camera is cleared when it leaves the wall`() = runTest {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState)
        subscribe(vm)
        vm.onIntent(ActiveCamerasIntent.Maximize("cam03"))

        repository.state.update { list -> list.filterNot { it.cameraId == "cam03" } }

        assertNull(vm.state.value.maximizedCameraId)
        assertNull(savedState.get<String>(ActiveCamerasViewModel.KEY_MAXIMIZED))
    }

    @Test
    fun `maximized camera is restored from saved state`() = runTest {
        val vm = viewModel(SavedStateHandle(mapOf(ActiveCamerasViewModel.KEY_MAXIMIZED to "cam02")))
        subscribe(vm)

        assertEquals("cam02", vm.state.value.maximizedCameraId)
        assertEquals(9, vm.state.value.liveBudget)
    }

    @Test
    fun `restored camera survives an empty first list`() = runTest {
        val sessions = repository.state.value
        repository.state.value = emptyList()
        val savedState = SavedStateHandle(mapOf(ActiveCamerasViewModel.KEY_MAXIMIZED to "cam02"))
        val vm = viewModel(savedState)
        subscribe(vm)

        assertNull(vm.state.value.maximizedCameraId)
        assertEquals("cam02", savedState.get<String>(ActiveCamerasViewModel.KEY_MAXIMIZED))

        repository.state.value = sessions
        assertEquals("cam02", vm.state.value.maximizedCameraId)
    }

    @Test
    fun `players of cameras that leave the wall are released`() = runTest {
        val vm = viewModel()
        subscribe(vm)
        vm.player("cam01")
        vm.player("cam02")

        repository.state.update { list -> list.filterNot { it.cameraId == "cam01" } }

        assertTrue(fake("cam01").released)
        assertFalse(fake("cam02").released)
        assertEquals(1, pool.size)
    }

    @Test
    fun `grid column choice is saved`() = runTest {
        val vm = viewModel()
        subscribe(vm)

        vm.onIntent(ActiveCamerasIntent.SetGridColumns(4))
        assertEquals(4, vm.state.value.gridColumns)
        assertEquals(4, settings.gridColumns.value)

        vm.onIntent(ActiveCamerasIntent.SetGridColumns(null))
        assertEquals(null, vm.state.value.gridColumns)
    }

    @Test
    fun `retry reconnects the player`() = runTest {
        val vm = viewModel()
        subscribe(vm)

        vm.onIntent(ActiveCamerasIntent.Retry("cam04"))

        assertTrue("retry" in fake("cam04").calls)
    }

    /** Emits nothing until the test says so, to observe the loading state. */
    private class GatedNetworkMonitor : NetworkMonitor {
        override val mode = MutableSharedFlow<NetworkMode>(replay = 1)
    }
}
