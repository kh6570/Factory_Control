// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.cameras.model

import com.raghim.herz.core.model.CameraOverview

data class CamerasUiState(
    val cameras: List<CameraOverview> = emptyList(),
    val isLoading: Boolean = true,
    val selection: Set<String> = emptySet(),
    val dialog: CamerasDialog? = null,
) {
    val liveCount: Int = cameras.count { it.isActive }
    val isSelecting: Boolean get() = selection.isNotEmpty()
}

sealed interface CamerasDialog {
    val cameraId: String

    data class Rename(override val cameraId: String, val currentName: String) : CamerasDialog

    data class Remove(override val cameraId: String, val name: String) : CamerasDialog
}

sealed interface CamerasIntent {
    data class Start(val id: String) : CamerasIntent
    data class Stop(val id: String) : CamerasIntent
    data class ToggleSelect(val id: String) : CamerasIntent
    data object ClearSelection : CamerasIntent
    data object StartSelected : CamerasIntent
    data object StopSelected : CamerasIntent
    data class RequestRename(val id: String) : CamerasIntent
    data class ConfirmRename(val name: String) : CamerasIntent
    data class RequestRemove(val id: String) : CamerasIntent
    data object ConfirmRemove : CamerasIntent
    data object DismissDialog : CamerasIntent
}

sealed interface CamerasEffect {
    data class ShowMessage(val message: CamerasMessage) : CamerasEffect
}

/** Snackbar messages. The screen turns them into string resources. */
sealed interface CamerasMessage {
    data class Removed(val name: String) : CamerasMessage
    data class Started(val count: Int) : CamerasMessage
    data class Stopped(val count: Int) : CamerasMessage
    data object ActionFailed : CamerasMessage
}
