// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raghim.herz.core.domain.door.HoldToOpen
import com.raghim.herz.core.domain.door.ObserveHoldToOpenUseCase
import com.raghim.herz.core.domain.door.SetHoldToOpenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

data class SettingsUiState(
    val holdToOpen: Duration = HoldToOpen.Default,
    val isLoading: Boolean = true,
)

sealed interface SettingsIntent {
    data class SetHoldToOpen(val duration: Duration) : SettingsIntent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeHoldToOpen: ObserveHoldToOpenUseCase,
    private val setHoldToOpen: SetHoldToOpenUseCase,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = observeHoldToOpen()
        .map { SettingsUiState(holdToOpen = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetHoldToOpen -> viewModelScope.launch { setHoldToOpen(intent.duration) }
        }
    }
}
