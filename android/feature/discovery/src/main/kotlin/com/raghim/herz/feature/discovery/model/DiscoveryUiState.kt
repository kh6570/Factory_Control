// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.discovery.model

import com.raghim.herz.core.model.CameraTarget
import com.raghim.herz.core.model.DiscoveredDevice

data class DiscoveryUiState(
    val isScanning: Boolean = false,
    /** True once at least one scan has finished. */
    val hasScanned: Boolean = false,
    val devices: List<DiscoveredDeviceItem> = emptyList(),
    val form: ConnectFormState? = null,
) {
    val showNothingFound: Boolean get() = hasScanned && !isScanning && devices.isEmpty()
}

data class DiscoveredDeviceItem(
    val device: DiscoveredDevice,
    val alreadyAdded: Boolean,
)

enum class ManualMode { Host, RtspUrl }

sealed interface FormSource {
    data class Device(val device: DiscoveredDevice) : FormSource
    data object ManualHost : FormSource
    data object ManualRtspUrl : FormSource
}

/** The login sheet. The password lives only in ViewModel memory, never in saved state. */
data class ConnectFormState(
    val source: FormSource,
    val host: String = "",
    val port: String = DiscoveredDevice.DEFAULT_RTSP_PORT.toString(),
    val mainUrl: String = "",
    val subUrl: String = "",
    val name: String = "",
    val username: String = "",
    val password: String = "",
    val isTesting: Boolean = false,
    val inputError: InputError? = null,
    val error: ConnectError? = null,
) {
    override fun toString(): String =
        "ConnectFormState(source=$source, host=$host, port=$port, name=$name, username=$username, " +
            "password=***, isTesting=$isTesting, inputError=$inputError, error=$error)"
}

/** Problems with what the user typed. Shown on the matching field. */
enum class InputError { HostRequired, InvalidHost, InvalidPort, InvalidMainUrl, InvalidSubUrl, CredentialsInUrl }

/** Problems reported by the camera. Shown at the bottom of the sheet. */
enum class ConnectError { WrongLogin, LoginRequired, Unreachable, NoStreamFound, Timeout, Offline, Unknown }

sealed interface DiscoveryIntent {
    data object Rescan : DiscoveryIntent
    data class SelectDevice(val device: DiscoveredDevice) : DiscoveryIntent
    data class AddManually(val mode: ManualMode) : DiscoveryIntent
    data class NameChanged(val value: String) : DiscoveryIntent
    data class UsernameChanged(val value: String) : DiscoveryIntent
    data class PasswordChanged(val value: String) : DiscoveryIntent {
        override fun toString(): String = "PasswordChanged(***)"
    }
    data class HostChanged(val value: String) : DiscoveryIntent
    data class PortChanged(val value: String) : DiscoveryIntent
    data class MainUrlChanged(val value: String) : DiscoveryIntent
    data class SubUrlChanged(val value: String) : DiscoveryIntent
    data object TestAndSave : DiscoveryIntent
    data object DismissForm : DiscoveryIntent
}

sealed interface DiscoveryEffect {
    data class CameraAdded(val cameraId: String, val name: String) : DiscoveryEffect
}

internal sealed interface TargetValidation {
    data class Valid(val target: CameraTarget) : TargetValidation
    data class Invalid(val error: InputError) : TargetValidation
}

internal fun ConnectFormState.validateTarget(): TargetValidation = when (source) {
    is FormSource.Device -> TargetValidation.Valid(CameraTarget.Device(source.device))
    FormSource.ManualHost -> validateHost()
    FormSource.ManualRtspUrl -> validateRtspUrls()
}

private fun ConnectFormState.validateHost(): TargetValidation {
    val cleanHost = host.trim()
    val portNumber = port.trim().toIntOrNull()
    return when {
        cleanHost.isEmpty() -> TargetValidation.Invalid(InputError.HostRequired)
        cleanHost.any { it.isWhitespace() || it == '/' || it == '@' } ->
            TargetValidation.Invalid(InputError.InvalidHost)
        portNumber == null || portNumber !in 1..65535 -> TargetValidation.Invalid(InputError.InvalidPort)
        else -> TargetValidation.Valid(CameraTarget.Host(cleanHost, portNumber))
    }
}

private fun ConnectFormState.validateRtspUrls(): TargetValidation {
    val main = mainUrl.trim()
    val sub = subUrl.trim()
    return when {
        !main.isRtspUrl() -> TargetValidation.Invalid(InputError.InvalidMainUrl)
        sub.isNotEmpty() && !sub.isRtspUrl() -> TargetValidation.Invalid(InputError.InvalidSubUrl)
        main.hasUserInfo() || sub.hasUserInfo() -> TargetValidation.Invalid(InputError.CredentialsInUrl)
        else -> TargetValidation.Valid(CameraTarget.RtspUrl(main, sub.ifEmpty { null }))
    }
}

private val RtspSchemes = listOf("rtsp://", "rtsps://")

private fun String.authority(): String {
    val scheme = RtspSchemes.firstOrNull { startsWith(it, ignoreCase = true) } ?: return ""
    return substring(scheme.length).substringBefore('/').substringBefore('?')
}

private fun String.isRtspUrl(): Boolean = none { it.isWhitespace() } && authority().isNotEmpty()

private fun String.hasUserInfo(): Boolean = '@' in authority()
