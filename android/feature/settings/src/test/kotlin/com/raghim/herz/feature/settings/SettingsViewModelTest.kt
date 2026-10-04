// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.settings

import com.raghim.herz.core.domain.door.HoldToOpen
import com.raghim.herz.core.domain.door.ObserveHoldToOpenUseCase
import com.raghim.herz.core.domain.door.ObserveRequireFingerprintUseCase
import com.raghim.herz.core.domain.door.SetHoldToOpenUseCase
import com.raghim.herz.core.domain.door.SetRequireFingerprintUseCase
import com.raghim.herz.core.testing.FakeUserSettingsRepository
import com.raghim.herz.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeUserSettingsRepository()
    private fun viewModel() = SettingsViewModel(
        observeHoldToOpen = ObserveHoldToOpenUseCase(settings),
        observeRequireFingerprint = ObserveRequireFingerprintUseCase(settings),
        setHoldToOpen = SetHoldToOpenUseCase(settings),
        setRequireFingerprint = SetRequireFingerprintUseCase(settings),
    )

    @Test
    fun `starts loading, then shows the stored hold time`() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isLoading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(HoldToOpen.Default, viewModel.state.value.holdToOpen)
    }

    @Test
    fun `setting a hold time stores it rounded and within limits`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        viewModel.onIntent(SettingsIntent.SetHoldToOpen(2700.milliseconds))
        assertEquals(2500.milliseconds, settings.state.value)
        assertEquals(2500.milliseconds, viewModel.state.value.holdToOpen)

        viewModel.onIntent(SettingsIntent.SetHoldToOpen(9.seconds))
        assertEquals(3.seconds, viewModel.state.value.holdToOpen)
    }

    @Test
    fun `fingerprint is on until the user turns it off`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        assertTrue(viewModel.state.value.requireFingerprint)

        viewModel.onIntent(SettingsIntent.SetRequireFingerprint(false))

        assertFalse(viewModel.state.value.requireFingerprint)
        assertFalse(settings.fingerprint.value)
    }
}
