// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raghim.herz.core.domain.camera.ObserveActiveCamerasUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HerzAppViewModel @Inject constructor(
    observeActive: ObserveActiveCamerasUseCase,
) : ViewModel() {
    /** Badge on the Live tab. */
    val liveCount: StateFlow<Int> = observeActive()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
