package info.littleboat.bluetoothcar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import info.littleboat.bluetoothcar.services.BluetoothService
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any as anyString

@ExperimentalCoroutinesApi
class CarControlViewModelTest {

    @Mock
    private lateinit var mockBluetoothService: BluetoothService

    private lateinit var viewModel: CarControlViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        viewModel = CarControlViewModel(mockBluetoothService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `checkBluetoothStatus updates isBluetoothEnabled`() = runTest {
        whenever(mockBluetoothService.isBluetoothEnabled()).thenReturn(true)
        viewModel.checkBluetoothStatus()
        assert(viewModel.isBluetoothEnabled.value)

        whenever(mockBluetoothService.isBluetoothEnabled()).thenReturn(false)
        viewModel.checkBluetoothStatus()
        assert(!viewModel.isBluetoothEnabled.value)
    }

    @Test
    fun `connectToCar updates isConnected state`() = runTest {
        whenever(mockBluetoothService.connectToDevice(anyString())).thenReturn(true)
        viewModel.connectToCar("AA:BB:CC:DD:EE:FF")
        testDispatcher.scheduler.advanceUntilIdle()
        assert(viewModel.isConnected.value)
    }

    @Test
    fun `sendCommand calls bluetoothService sendCommand when connected`() = runTest {
        whenever(mockBluetoothService.connectToDevice(anyString())).thenReturn(true)
        viewModel.connectToCar("AA:BB:CC:DD:EE:FF")
        testDispatcher.scheduler.advanceUntilIdle()

        whenever(mockBluetoothService.sendCommand(anyString())).thenReturn(true)
        viewModel.sendCommand("TEST_COMMAND")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(mockBluetoothService).sendCommand("TEST_COMMAND")
    }

    @Test
    fun `disconnect calls bluetoothService disconnect`() = runTest {
        // Ensure we are in a connected state first, so disconnect has something to do
        whenever(mockBluetoothService.connectToDevice(anyString())).thenReturn(true)
        viewModel.connectToCar("AA:BB:CC:DD:EE:FF")
        testDispatcher.scheduler.advanceUntilIdle()
        assert(viewModel.isConnected.value) // Verify connected state

        // No need to stub disconnect as it's a Unit function
        viewModel.disconnect()
        testDispatcher.scheduler.advanceUntilIdle()
        verify(mockBluetoothService).disconnect()
        assert(!viewModel.isConnected.value) // Verify disconnected state
    }

    @Test
    fun `startDiscovery calls bluetoothService startDiscovery`() = runTest {
        // No need to stub startDiscovery as it's a Unit function
        viewModel.startDiscovery()
        testDispatcher.scheduler.advanceUntilIdle()
        verify(mockBluetoothService).startDiscovery()
    }

    @Test
    fun `stopDiscovery calls bluetoothService stopDiscovery`() = runTest {
        // No need to stub stopDiscovery as it's a Unit function
        viewModel.stopDiscovery()
        testDispatcher.scheduler.advanceUntilIdle()
        verify(mockBluetoothService).stopDiscovery()
    }

    @Test
    fun `moveForward sends correct command`() = runTest {
        whenever(mockBluetoothService.connectToDevice(anyString())).thenReturn(true)
        viewModel.connectToCar("AA:BB:CC:DD:EE:FF")
        testDispatcher.scheduler.advanceUntilIdle()

        whenever(mockBluetoothService.sendCommand(anyString())).thenReturn(true)
        viewModel.moveForward(true)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(mockBluetoothService).sendCommand("FORWARD_ON")

        viewModel.moveForward(false)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(mockBluetoothService).sendCommand("MOVEMENT_OFF")
    }

    @Test
    fun `toggleFrontLight sends correct command`() = runTest {
        whenever(mockBluetoothService.connectToDevice(anyString())).thenReturn(true)
        viewModel.connectToCar("AA:BB:CC:DD:EE:FF")
        testDispatcher.scheduler.advanceUntilIdle()

        whenever(mockBluetoothService.sendCommand(anyString())).thenReturn(true)
        viewModel.toggleFrontLight(true)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(mockBluetoothService).sendCommand("FRONT_LIGHT_ON")

        viewModel.toggleFrontLight(false)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(mockBluetoothService).sendCommand("FRONT_LIGHT_OFF")
    }

    @Test
    fun `horn sends correct command`() = runTest {
        whenever(mockBluetoothService.connectToDevice(anyString())).thenReturn(true)
        viewModel.connectToCar("AA:BB:CC:DD:EE:FF")
        testDispatcher.scheduler.advanceUntilIdle()

        whenever(mockBluetoothService.sendCommand(anyString())).thenReturn(true)
        viewModel.horn(true)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(mockBluetoothService).sendCommand("HORN_ON")

        viewModel.horn(false)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(mockBluetoothService).sendCommand("HORN_OFF")
    }
}