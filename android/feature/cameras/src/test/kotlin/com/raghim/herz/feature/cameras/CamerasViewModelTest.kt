// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.cameras

import app.cash.turbine.test
import com.raghim.herz.core.domain.camera.ObserveCameraOverviewsUseCase
import com.raghim.herz.core.domain.camera.RemoveCameraUseCase
import com.raghim.herz.core.domain.camera.RenameCameraUseCase
import com.raghim.herz.core.domain.camera.StartCamerasUseCase
import com.raghim.herz.core.domain.camera.StopCameraUseCase
import com.raghim.herz.core.testing.FakeActiveCamerasRepository
import com.raghim.herz.core.testing.FakeCameraRepository
import com.raghim.herz.core.testing.MainDispatcherRule
import com.raghim.herz.core.testing.TestCameras
import com.raghim.herz.feature.cameras.model.CamerasDialog
import com.raghim.herz.feature.cameras.model.CamerasEffect
import com.raghim.herz.feature.cameras.model.CamerasIntent
import com.raghim.herz.feature.cameras.model.CamerasMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CamerasViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val alpha = TestCameras.camera(1, name = "Alpha")
    private val bravo = TestCameras.camera(2, name = "Bravo")
    private val charlie = TestCameras.camera(3, name = "Charlie")

    private val cameras = FakeCameraRepository(listOf(alpha, bravo, charlie))
    private val active = FakeActiveCamerasRepository(cameras = cameras, initial = listOf(TestCameras.active(alpha)))

    private fun viewModel() = CamerasViewModel(
        observeCameraOverviews = ObserveCameraOverviewsUseCase(cameras, active),
        startCameras = StartCamerasUseCase(active),
        stopCamera = StopCameraUseCase(active),
        renameCamera = RenameCameraUseCase(cameras),
        removeCamera = RemoveCameraUseCase(cameras, active),
    )

    private val liveIds get() = active.state.value.map { it.cameraId }.toSet()

    @Test
    fun `lists saved cameras with live count`() = runTest {
        viewModel().state.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(listOf("Alpha", "Bravo", "Charlie"), state.cameras.map { it.camera.name })
            assertEquals(1, state.liveCount)
            assertTrue(state.cameras.first { it.camera.id == alpha.id }.isActive)
        }
    }

    @Test
    fun `start and stop update the wall and the state`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(CamerasIntent.Start(bravo.id))
        assertEquals(setOf(alpha.id, bravo.id), liveIds)
        assertEquals(2, viewModel.state.value.liveCount)
        assertTrue(viewModel.state.value.cameras.first { it.camera.id == bravo.id }.isActive)

        viewModel.onIntent(CamerasIntent.Stop(alpha.id))
        assertEquals(setOf(bravo.id), liveIds)
        assertEquals(1, viewModel.state.value.liveCount)
        assertFalse(viewModel.state.value.cameras.first { it.camera.id == alpha.id }.isActive)
    }

    @Test
    fun `start selected starts all selected cameras and clears selection`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(CamerasIntent.ToggleSelect(bravo.id))
        viewModel.onIntent(CamerasIntent.ToggleSelect(charlie.id))
        assertEquals(setOf(bravo.id, charlie.id), viewModel.state.value.selection)
        assertTrue(viewModel.state.value.isSelecting)

        viewModel.effects.test {
            viewModel.onIntent(CamerasIntent.StartSelected)
            assertEquals(CamerasEffect.ShowMessage(CamerasMessage.Started(2)), awaitItem())
        }
        assertEquals(setOf(alpha.id, bravo.id, charlie.id), liveIds)
        assertEquals(3, viewModel.state.value.liveCount)
        assertTrue(viewModel.state.value.selection.isEmpty())
    }

    @Test
    fun `toggle select twice and clear selection`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(CamerasIntent.ToggleSelect(alpha.id))
        viewModel.onIntent(CamerasIntent.ToggleSelect(bravo.id))
        viewModel.onIntent(CamerasIntent.ToggleSelect(alpha.id))
        assertEquals(setOf(bravo.id), viewModel.state.value.selection)

        viewModel.onIntent(CamerasIntent.ClearSelection)
        assertFalse(viewModel.state.value.isSelecting)
    }

    @Test
    fun `stop selected stops all selected cameras`() = runTest {
        active.state.value = listOf(TestCameras.active(alpha), TestCameras.active(bravo))
        val viewModel = viewModel()

        viewModel.onIntent(CamerasIntent.ToggleSelect(alpha.id))
        viewModel.onIntent(CamerasIntent.ToggleSelect(bravo.id))
        viewModel.onIntent(CamerasIntent.StopSelected)

        assertTrue(liveIds.isEmpty())
        assertTrue(viewModel.state.value.selection.isEmpty())
    }

    @Test
    fun `rename opens dialog and saves new name`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(CamerasIntent.RequestRename(bravo.id))
        assertEquals(CamerasDialog.Rename(bravo.id, "Bravo"), viewModel.state.value.dialog)

        viewModel.onIntent(CamerasIntent.ConfirmRename("  Gate  "))
        assertNull(viewModel.state.value.dialog)
        assertEquals("Gate", cameras.state.value.first { it.id == bravo.id }.name)
        assertEquals(listOf("Alpha", "Charlie", "Gate"), viewModel.state.value.cameras.map { it.camera.name })
    }

    @Test
    fun `dismiss closes dialog without changes`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(CamerasIntent.RequestRemove(alpha.id))
        viewModel.onIntent(CamerasIntent.DismissDialog)

        assertNull(viewModel.state.value.dialog)
        assertEquals(3, viewModel.state.value.cameras.size)
    }

    @Test
    fun `remove asks for confirmation, removes camera and shows message`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(CamerasIntent.ToggleSelect(alpha.id))

        viewModel.onIntent(CamerasIntent.RequestRemove(alpha.id))
        assertEquals(CamerasDialog.Remove(alpha.id, "Alpha"), viewModel.state.value.dialog)
        assertEquals(3, viewModel.state.value.cameras.size)

        viewModel.effects.test {
            viewModel.onIntent(CamerasIntent.ConfirmRemove)
            assertEquals(CamerasEffect.ShowMessage(CamerasMessage.Removed("Alpha")), awaitItem())
        }
        val state = viewModel.state.value
        assertNull(state.dialog)
        assertEquals(listOf("Bravo", "Charlie"), state.cameras.map { it.camera.name })
        assertEquals(0, state.liveCount)
        assertTrue(state.selection.isEmpty())
        assertFalse(alpha.id in liveIds)
    }
}
