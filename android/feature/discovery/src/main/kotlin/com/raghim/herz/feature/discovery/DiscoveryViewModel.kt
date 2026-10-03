// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.domain.camera.AddCameraUseCase
import com.raghim.herz.core.domain.camera.ObserveCameraOverviewsUseCase
import com.raghim.herz.core.domain.camera.ScanForCamerasUseCase
import com.raghim.herz.core.domain.camera.TestCameraConnectionUseCase
import com.raghim.herz.core.model.CameraCredentials
import com.raghim.herz.core.model.CameraTarget
import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.feature.discovery.model.ConnectError
import com.raghim.herz.feature.discovery.model.ConnectFormState
import com.raghim.herz.feature.discovery.model.DiscoveredDeviceItem
import com.raghim.herz.feature.discovery.model.DiscoveryEffect
import com.raghim.herz.feature.discovery.model.DiscoveryIntent
import com.raghim.herz.feature.discovery.model.DiscoveryUiState
import com.raghim.herz.feature.discovery.model.FormSource
import com.raghim.herz.feature.discovery.model.ManualMode
import com.raghim.herz.feature.discovery.model.TargetValidation
import com.raghim.herz.feature.discovery.model.validateTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val scanForCameras: ScanForCamerasUseCase,
    observeCameraOverviews: ObserveCameraOverviewsUseCase,
    private val testConnection: TestCameraConnectionUseCase,
    private val addCamera: AddCameraUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoveryUiState())
    val state: StateFlow<DiscoveryUiState> = _state.asStateFlow()

    private val _effects = Channel<DiscoveryEffect>(Channel.BUFFERED)
    val effects: Flow<DiscoveryEffect> = _effects.receiveAsFlow()

    private val found = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    private var scanJob: Job? = null
    private var scanGeneration = 0
    private var testJob: Job? = null

    init {
        val savedHosts = observeCameraOverviews()
            .map { cameras -> cameras.mapTo(HashSet()) { it.camera.host.lowercase() } }
            .onStart { emit(HashSet()) }
        combine(found, savedHosts) { devices, hosts ->
            devices.map { DiscoveredDeviceItem(it, alreadyAdded = it.host.lowercase() in hosts) }
        }
            .onEach { items -> _state.update { it.copy(devices = items) } }
            .launchIn(viewModelScope)
        scan()
    }

    fun onIntent(intent: DiscoveryIntent) {
        when (intent) {
            DiscoveryIntent.Rescan -> scan()
            is DiscoveryIntent.SelectDevice -> openForm(
                ConnectFormState(
                    source = FormSource.Device(intent.device),
                    host = intent.device.host,
                    port = intent.device.rtspPort.toString(),
                    name = intent.device.displayName,
                ),
            )
            is DiscoveryIntent.AddManually -> openForm(
                ConnectFormState(
                    source = when (intent.mode) {
                        ManualMode.Host -> FormSource.ManualHost
                        ManualMode.RtspUrl -> FormSource.ManualRtspUrl
                    },
                ),
            )
            is DiscoveryIntent.NameChanged -> editForm { it.copy(name = intent.value) }
            is DiscoveryIntent.UsernameChanged -> editForm { it.copy(username = intent.value) }
            is DiscoveryIntent.PasswordChanged -> editForm { it.copy(password = intent.value) }
            is DiscoveryIntent.HostChanged -> editForm { it.copy(host = intent.value) }
            is DiscoveryIntent.PortChanged -> editForm { it.copy(port = intent.value.filter(Char::isDigit).take(5)) }
            is DiscoveryIntent.MainUrlChanged -> editForm { it.copy(mainUrl = intent.value) }
            is DiscoveryIntent.SubUrlChanged -> editForm { it.copy(subUrl = intent.value) }
            DiscoveryIntent.TestAndSave -> testAndSave()
            DiscoveryIntent.DismissForm -> {
                testJob?.cancel()
                testJob = null
                _state.update { it.copy(form = null) }
            }
        }
    }

    private fun scan() {
        scanJob?.cancel()
        val generation = ++scanGeneration
        found.value = emptyList()
        _state.update { it.copy(isScanning = true) }
        scanJob = viewModelScope.launch {
            try {
                scanForCameras()
                    .catch { }
                    .collect { found.value = it }
            } finally {
                if (generation == scanGeneration) {
                    _state.update { it.copy(isScanning = false, hasScanned = true) }
                }
            }
        }
    }

    private fun openForm(form: ConnectFormState) {
        testJob?.cancel()
        testJob = null
        _state.update { it.copy(form = form) }
    }

    private inline fun editForm(crossinline change: (ConnectFormState) -> ConnectFormState) {
        _state.update { current ->
            val form = current.form?.takeUnless { it.isTesting } ?: return@update current
            current.copy(form = change(form).copy(inputError = null, error = null))
        }
    }

    private fun testAndSave() {
        val form = _state.value.form ?: return
        if (form.isTesting) return
        val target = when (val validation = form.validateTarget()) {
            is TargetValidation.Invalid -> {
                updateForm { it.copy(inputError = validation.error, error = null) }
                return
            }
            is TargetValidation.Valid -> validation.target
        }
        val username = form.username.trim()
        val credentials = if (username.isEmpty()) null else CameraCredentials(username, form.password)
        updateForm { it.copy(isTesting = true, inputError = null, error = null) }

        testJob = viewModelScope.launch {
            try {
                when (val result = testConnection(target, credentials)) {
                    is AppResult.Success -> {
                        val info = result.value
                        val camera = addCamera(
                            info = info,
                            name = form.name.ifBlank { info.suggestedName ?: target.fallbackName() },
                            credentials = credentials,
                        )
                        _state.update { it.copy(form = null) }
                        _effects.send(DiscoveryEffect.CameraAdded(camera.id, camera.name))
                    }
                    is AppResult.Failure -> updateForm {
                        it.copy(isTesting = false, error = result.error.toConnectError(loginGiven = credentials != null))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateForm { it.copy(isTesting = false, error = ConnectError.Unknown) }
            }
        }
    }

    private inline fun updateForm(crossinline change: (ConnectFormState) -> ConnectFormState) {
        _state.update { current -> current.form?.let { current.copy(form = change(it)) } ?: current }
    }

    private fun CameraTarget.fallbackName(): String = when (this) {
        is CameraTarget.Device -> device.displayName
        is CameraTarget.Host -> host
        is CameraTarget.RtspUrl -> ""
    }
}

internal fun AppError.toConnectError(loginGiven: Boolean): ConnectError = when (this) {
    AppError.Unauthorized -> if (loginGiven) ConnectError.WrongLogin else ConnectError.LoginRequired
    AppError.Unreachable, AppError.NotFound -> ConnectError.Unreachable
    AppError.NoStreamFound -> ConnectError.NoStreamFound
    AppError.Timeout -> ConnectError.Timeout
    AppError.Offline -> ConnectError.Offline
    is AppError.Unknown -> ConnectError.Unknown
}
