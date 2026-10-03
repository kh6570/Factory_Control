// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.discovery

import app.cash.turbine.test
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.domain.camera.AddCameraUseCase
import com.raghim.herz.core.domain.camera.ObserveCameraOverviewsUseCase
import com.raghim.herz.core.domain.camera.ScanForCamerasUseCase
import com.raghim.herz.core.domain.camera.TestCameraConnectionUseCase
import com.raghim.herz.core.model.CameraCredentials
import com.raghim.herz.core.model.CameraTarget
import com.raghim.herz.core.model.DiscoveryMethod
import com.raghim.herz.core.testing.FakeActiveCamerasRepository
import com.raghim.herz.core.testing.FakeCameraConnector
import com.raghim.herz.core.testing.FakeCameraDiscovery
import com.raghim.herz.core.testing.FakeCameraRepository
import com.raghim.herz.core.testing.MainDispatcherRule
import com.raghim.herz.core.testing.TestCameras
import com.raghim.herz.feature.discovery.model.ConnectError
import com.raghim.herz.feature.discovery.model.DiscoveryEffect
import com.raghim.herz.feature.discovery.model.DiscoveryIntent
import com.raghim.herz.feature.discovery.model.FormSource
import com.raghim.herz.feature.discovery.model.InputError
import com.raghim.herz.feature.discovery.model.ManualMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiscoveryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedCamera = TestCameras.camera(1)
    private val onvifDevice = TestCameras.device(1)
    private val portScanDevice = TestCameras.device(2, method = DiscoveryMethod.PORT_SCAN)

    private val cameras = FakeCameraRepository(listOf(savedCamera))
    private val active = FakeActiveCamerasRepository(cameras = cameras)
    private val discovery = FakeCameraDiscovery(listOf(onvifDevice, portScanDevice))
    private val connector = FakeCameraConnector()

    private fun viewModel() = DiscoveryViewModel(
        scanForCameras = ScanForCamerasUseCase(discovery),
        observeCameraOverviews = ObserveCameraOverviewsUseCase(cameras, active),
        testConnection = TestCameraConnectionUseCase(connector),
        addCamera = AddCameraUseCase(cameras, active),
    )

    private val DiscoveryViewModel.form get() = requireNotNull(state.value.form) { "form is not open" }

    @Test
    fun `scans on start and marks devices that are already saved`() = runTest {
        val viewModel = viewModel()

        val state = viewModel.state.value
        assertEquals(1, discovery.scans)
        assertFalse(state.isScanning)
        assertTrue(state.hasScanned)
        assertEquals(listOf(onvifDevice.host, portScanDevice.host), state.devices.map { it.device.host })
        assertEquals(listOf(true, false), state.devices.map { it.alreadyAdded })
        assertFalse(state.showNothingFound)
    }

    @Test
    fun `rescan starts a new scan`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(DiscoveryIntent.Rescan)

        assertEquals(2, discovery.scans)
        assertFalse(viewModel.state.value.isScanning)
        assertEquals(2, viewModel.state.value.devices.size)
    }

    @Test
    fun `empty scan shows help`() = runTest {
        discovery.devices = emptyList()
        val viewModel = viewModel()

        assertTrue(viewModel.state.value.showNothingFound)
    }

    @Test
    fun `selecting a device opens the form with its name`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(DiscoveryIntent.SelectDevice(onvifDevice))

        val form = viewModel.form
        assertEquals(FormSource.Device(onvifDevice), form.source)
        assertEquals(onvifDevice.displayName, form.name)

        viewModel.onIntent(DiscoveryIntent.DismissForm)
        assertNull(viewModel.state.value.form)
    }

    @Test
    fun `test and save stores the camera with credentials and starts it`() = runTest {
        connector.result = AppResult.Success(TestCameras.info(2))
        val viewModel = viewModel()

        viewModel.onIntent(DiscoveryIntent.SelectDevice(portScanDevice))
        viewModel.onIntent(DiscoveryIntent.NameChanged("Gate"))
        viewModel.onIntent(DiscoveryIntent.UsernameChanged("admin"))
        viewModel.onIntent(DiscoveryIntent.PasswordChanged("secret"))

        viewModel.effects.test {
            viewModel.onIntent(DiscoveryIntent.TestAndSave)
            val effect = awaitItem() as DiscoveryEffect.CameraAdded
            assertEquals("Gate", effect.name)

            val saved = cameras.state.value.single { it.id == effect.cameraId }
            assertEquals("Gate", saved.name)
            assertEquals(CameraCredentials("admin", "secret"), cameras.credentialsById[saved.id])
            assertTrue(active.state.value.any { it.cameraId == saved.id })
        }
        val credentials = CameraCredentials("admin", "secret")
        assertEquals(listOf(CameraTarget.Device(portScanDevice) to credentials), connector.calls)
        assertNull(viewModel.state.value.form)
        assertTrue(viewModel.state.value.devices.single { it.device.host == portScanDevice.host }.alreadyAdded)
    }

    @Test
    fun `unauthorized shows wrong login and does not save`() = runTest {
        connector.result = AppResult.Failure(AppError.Unauthorized)
        val viewModel = viewModel()

        viewModel.onIntent(DiscoveryIntent.SelectDevice(portScanDevice))
        viewModel.onIntent(DiscoveryIntent.UsernameChanged("admin"))
        viewModel.onIntent(DiscoveryIntent.PasswordChanged("wrong"))

        viewModel.effects.test {
            viewModel.onIntent(DiscoveryIntent.TestAndSave)
            expectNoEvents()
        }
        val form = viewModel.form
        assertEquals(ConnectError.WrongLogin, form.error)
        assertFalse(form.isTesting)
        assertEquals(listOf(savedCamera), cameras.state.value)
        assertTrue(cameras.credentialsById.isEmpty())

        viewModel.onIntent(DiscoveryIntent.PasswordChanged("again"))
        assertNull(viewModel.form.error)
    }

    @Test
    fun `unauthorized without username asks for a login`() = runTest {
        connector.result = AppResult.Failure(AppError.Unauthorized)
        val viewModel = viewModel()

        viewModel.onIntent(DiscoveryIntent.SelectDevice(portScanDevice))
        viewModel.onIntent(DiscoveryIntent.TestAndSave)

        assertEquals(ConnectError.LoginRequired, viewModel.form.error)
    }

    @Test
    fun `unreachable and no stream errors are mapped`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(DiscoveryIntent.SelectDevice(portScanDevice))

        connector.result = AppResult.Failure(AppError.Unreachable)
        viewModel.onIntent(DiscoveryIntent.TestAndSave)
        assertEquals(ConnectError.Unreachable, viewModel.form.error)

        connector.result = AppResult.Failure(AppError.NoStreamFound)
        viewModel.onIntent(DiscoveryIntent.TestAndSave)
        assertEquals(ConnectError.NoStreamFound, viewModel.form.error)
    }

    @Test
    fun `manual rtsp url must use rtsp scheme`() = runTest {
        connector.result = AppResult.Success(TestCameras.info(5))
        val viewModel = viewModel()

        viewModel.onIntent(DiscoveryIntent.AddManually(ManualMode.RtspUrl))
        viewModel.onIntent(DiscoveryIntent.MainUrlChanged("http://10.0.2.2:8554/cam01"))
        viewModel.onIntent(DiscoveryIntent.TestAndSave)

        assertEquals(InputError.InvalidMainUrl, viewModel.form.inputError)
        assertTrue(connector.calls.isEmpty())

        viewModel.onIntent(DiscoveryIntent.MainUrlChanged("rtsp://10.0.2.2:8554/cam01"))
        viewModel.onIntent(DiscoveryIntent.SubUrlChanged("ftp://10.0.2.2/sub"))
        viewModel.onIntent(DiscoveryIntent.TestAndSave)
        assertEquals(InputError.InvalidSubUrl, viewModel.form.inputError)

        viewModel.onIntent(DiscoveryIntent.SubUrlChanged(""))
        viewModel.onIntent(DiscoveryIntent.TestAndSave)
        assertEquals(CameraTarget.RtspUrl("rtsp://10.0.2.2:8554/cam01", null), connector.calls.single().first)
    }

    @Test
    fun `manual rtsp url with login inside is rejected`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(DiscoveryIntent.AddManually(ManualMode.RtspUrl))
        viewModel.onIntent(DiscoveryIntent.MainUrlChanged("rtsp://admin:secret@192.168.1.20/stream1"))
        viewModel.onIntent(DiscoveryIntent.TestAndSave)

        assertEquals(InputError.CredentialsInUrl, viewModel.form.inputError)
        assertTrue(connector.calls.isEmpty())
    }

    @Test
    fun `manual host validates host and port`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(DiscoveryIntent.AddManually(ManualMode.Host))

        viewModel.onIntent(DiscoveryIntent.TestAndSave)
        assertEquals(InputError.HostRequired, viewModel.form.inputError)

        viewModel.onIntent(DiscoveryIntent.HostChanged("192.168.1.50"))
        viewModel.onIntent(DiscoveryIntent.PortChanged("70000"))
        viewModel.onIntent(DiscoveryIntent.TestAndSave)
        assertEquals(InputError.InvalidPort, viewModel.form.inputError)

        viewModel.onIntent(DiscoveryIntent.PortChanged("0"))
        viewModel.onIntent(DiscoveryIntent.TestAndSave)
        assertEquals(InputError.InvalidPort, viewModel.form.inputError)
        assertTrue(connector.calls.isEmpty())
    }

    @Test
    fun `blank username passes no credentials`() = runTest {
        connector.result = AppResult.Success(TestCameras.info(5))
        val viewModel = viewModel()

        viewModel.onIntent(DiscoveryIntent.AddManually(ManualMode.Host))
        viewModel.onIntent(DiscoveryIntent.HostChanged(" 192.168.1.50 "))
        viewModel.onIntent(DiscoveryIntent.PortChanged("8554"))
        viewModel.onIntent(DiscoveryIntent.UsernameChanged("   "))
        viewModel.onIntent(DiscoveryIntent.PasswordChanged("ignored"))

        viewModel.effects.test {
            viewModel.onIntent(DiscoveryIntent.TestAndSave)
            assertTrue(awaitItem() is DiscoveryEffect.CameraAdded)
        }
        assertEquals(listOf(CameraTarget.Host("192.168.1.50", 8554) to null), connector.calls)
        assertTrue(cameras.credentialsById.isEmpty())
        assertEquals("Test T-5", cameras.state.value.last().name)
    }

    @Test
    fun `password never appears in state text`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(DiscoveryIntent.SelectDevice(portScanDevice))
        viewModel.onIntent(DiscoveryIntent.PasswordChanged("top-secret"))

        assertFalse("top-secret" in viewModel.state.value.toString())
        assertFalse("top-secret" in DiscoveryIntent.PasswordChanged("top-secret").toString())
    }
}
