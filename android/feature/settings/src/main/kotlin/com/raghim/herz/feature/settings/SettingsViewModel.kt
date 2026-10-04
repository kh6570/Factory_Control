// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raghim.herz.core.domain.door.HoldToOpen
import com.raghim.herz.core.domain.door.ObserveHoldToOpenUseCase
import com.raghim.herz.core.domain.door.ObserveRequireFingerprintUseCase
import com.raghim.herz.core.domain.door.SetHoldToOpenUseCase
import com.raghim.herz.core.domain.door.SetRequireFingerprintUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

data class SettingsUiState(
    val holdToOpen: Duration = HoldToOpen.Default,
    val requireFingerprint: Boolean = true,
    val isLoading: Boolean = true,
)

sealed interface SettingsIntent {
    data class SetHoldToOpen(val duration: Duration) : SettingsIntent
    data class SetRequireFingerprint(val required: Boolean) : SettingsIntent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeHoldToOpen: ObserveHoldToOpenUseCase,
    observeRequireFingerprint: ObserveRequireFingerprintUseCase,
    private val setHoldToOpen: SetHoldToOpenUseCase,
    private val setRequireFingerprint: SetRequireFingerprintUseCase,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(observeHoldToOpen(), observeRequireFingerprint()) { hold, fingerprint ->
        SettingsUiState(holdToOpen = hold, requireFingerprint = fingerprint, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetHoldToOpen -> viewModelScope.launch { setHoldToOpen(intent.duration) }
            is SettingsIntent.SetRequireFingerprint -> viewModelScope.launch { setRequireFingerprint(intent.required) }
        }
    }
}
