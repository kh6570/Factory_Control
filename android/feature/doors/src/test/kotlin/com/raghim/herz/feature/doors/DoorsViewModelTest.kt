// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.doors

import com.raghim.herz.core.domain.door.AddDoorUseCase
import com.raghim.herz.core.domain.door.ObserveDoorsUseCase
import com.raghim.herz.core.domain.door.RemoveDoorUseCase
import com.raghim.herz.core.domain.door.ReorderDoorsUseCase
import com.raghim.herz.core.domain.door.SetDoorOnLivePanelUseCase
import com.raghim.herz.core.domain.door.UpdateDoorUseCase
import com.raghim.herz.core.testing.FakeDoorRepository
import com.raghim.herz.core.testing.MainDispatcherRule
import com.raghim.herz.core.testing.TestDoors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DoorsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeDoorRepository(listOf(TestDoors.door(2), TestDoors.door(1)))

    private fun viewModel() = DoorsViewModel(
        observeDoors = ObserveDoorsUseCase(repository),
        addDoor = AddDoorUseCase(repository),
        updateDoor = UpdateDoorUseCase(repository),
        removeDoor = RemoveDoorUseCase(repository),
        setOnLivePanel = SetDoorOnLivePanelUseCase(repository),
        reorderDoors = ReorderDoorsUseCase(repository),
    )

    @Test
    fun `dragging a section saves that order`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(DoorsIntent.Move(onLive = false, orderedIds = listOf("door2", "door1")))

        assertEquals(listOf("door2", "door1"), viewModel.state.value.doors.map { it.id })
    }

    @Test
    fun `lists doors by name`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        assertEquals(listOf("Door 1", "Door 2"), viewModel.state.value.doors.map { it.name })
    }

    @Test
    fun `add saves a trimmed door and closes the dialog`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(DoorsIntent.Add)
        assertTrue(viewModel.state.value.dialog is DoorsDialog.Add)
        viewModel.onIntent(DoorsIntent.Save("  Side gate  ", "  "))

        assertNull(viewModel.state.value.dialog)
        assertEquals("Side gate", repository.state.value.last().name)
        assertNull(repository.state.value.last().area)
    }

    @Test
    fun `edit renames and can set the area`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(DoorsIntent.Edit("door1"))
        viewModel.onIntent(DoorsIntent.Save("Gate", "North"))

        val door = repository.state.value.first { it.id == "door1" }
        assertEquals("Gate", door.name)
        assertEquals("North", door.area)
    }

    @Test
    fun `remove asks first, then deletes`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(DoorsIntent.Remove("door2"))
        assertEquals(DoorsDialog.Remove("door2", "Door 2"), viewModel.state.value.dialog)
        assertEquals(2, repository.state.value.size)

        viewModel.onIntent(DoorsIntent.ConfirmRemove)

        assertNull(viewModel.state.value.dialog)
        assertEquals(listOf("door1"), repository.state.value.map { it.id })
    }

    @Test
    fun `a door can be added to the live panel and taken off it`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(DoorsIntent.SetOnLive("door1", true))
        assertTrue(repository.state.value.first { it.id == "door1" }.onLivePanel)

        viewModel.onIntent(DoorsIntent.SetOnLive("door1", false))
        assertFalse(repository.state.value.first { it.id == "door1" }.onLivePanel)
    }

    @Test
    fun `a blank name is not saved`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(DoorsIntent.Add)
        viewModel.onIntent(DoorsIntent.Save("   ", "Yard"))

        assertEquals(2, repository.state.value.size)
    }

    @Test
    fun `select all shows every door on live and deselect all hides them`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(DoorsIntent.ShowAllOnLive)
        assertTrue(repository.state.value.all { it.onLivePanel })

        viewModel.onIntent(DoorsIntent.HideAllFromLive)
        assertTrue(repository.state.value.none { it.onLivePanel })
    }
}
