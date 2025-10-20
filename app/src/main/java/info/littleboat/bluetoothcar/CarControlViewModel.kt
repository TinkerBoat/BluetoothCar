package info.littleboat.bluetoothcar

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import info.littleboat.bluetoothcar.services.BluetoothService
import javax.inject.Inject

@HiltViewModel
class CarControlViewModel @Inject constructor(
    private val bluetoothService: BluetoothService
) : ViewModel() {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    init {
        checkBluetoothStatus()
    }

    fun checkBluetoothStatus() {
        _isBluetoothEnabled.value = bluetoothService.isBluetoothEnabled()
    }

    fun onPermissionsGranted() {
        // Combine paired and discovered devices
        bluetoothService.discoveredDevices.onEach { discovered ->
            val paired = bluetoothService.getPairedDevices() ?: emptySet()
            val allDevices = (paired + discovered).distinctBy { it.address }
            _discoveredDevices.value = allDevices
        }.launchIn(viewModelScope)
    }

    fun startDiscovery() {
        viewModelScope.launch(Dispatchers.IO) {
            bluetoothService.startDiscovery()
        }
    }

    fun stopDiscovery() {
        if (!_isConnecting.value) {
            viewModelScope.launch(Dispatchers.IO) {
                bluetoothService.stopDiscovery()
            }
        }
    }

    fun connectToCar(deviceAddress: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isConnecting.value = true
            _connectionError.value = null
            stopDiscovery() // Stop discovery before connecting
            val success = bluetoothService.connectToDevice(deviceAddress)
            withContext(Dispatchers.Main) {
                _isConnected.value = success
                if (!success) {
                    _connectionError.value = "Failed to connect to device"
                }
                _isConnecting.value = false
            }
        }
    }

    fun clearConnectionError() {
        _connectionError.value = null
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            bluetoothService.disconnect()
            withContext(Dispatchers.Main) {
                _isConnected.value = false
            }
        }
    }

    fun sendCommand(command: String) {
        if (_isConnected.value) {
            viewModelScope.launch(Dispatchers.IO) {
                bluetoothService.sendCommand(command)
            }
        }
    }

    private var movementJob: kotlinx.coroutines.Job? = null

    // --- Button Actions ---
    fun startMovingForward() = startMoving("F")
    fun startMovingBackward() = startMoving("B")
    fun startTurningLeft() = startMoving("L")
    fun startTurningRight() = startMoving("R")
    fun startMovingForwardLeft() = startMoving("G")
    fun startMovingForwardRight() = startMoving("I")
    fun startMovingBackwardLeft() = startMoving("H")
    fun startMovingBackwardRight() = startMoving("J")

    private fun startMoving(command: String) {
        movementJob?.cancel() // Cancel any existing movement job
        movementJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) { // Keep sending command while coroutine is active
                bluetoothService.sendCommand(command)
                delay(100) // Send command every 100ms
            }
        }
    }

    fun stopMoving() {
        movementJob?.cancel() // Stop sending movement commands
    }

    fun toggleFrontLight(isOn: Boolean) {
        sendCommand(if (isOn) "W" else "w")
    }

    fun toggleBackLight(isOn: Boolean) {
        sendCommand(if (isOn) "U" else "u")
    }

    fun horn() {
        sendCommand("v") // Send 'V' once for a momentary horn
    }

    // --- Voice Command Processing ---
    fun processVoiceCommand(commandText: String) {
        val lowerCaseCommand = commandText.lowercase().replace(" ","")
        when {
            lowerCaseCommand.contains("forward") || lowerCaseCommand.contains("gostraight") -> startMovingForward()
            lowerCaseCommand.contains("backward") || lowerCaseCommand.contains("reverse") -> startMovingBackward()
            lowerCaseCommand.contains("left") -> startTurningLeft()
            lowerCaseCommand.contains("right") -> startTurningRight()
            lowerCaseCommand.contains("stop") -> {
                stopMoving()
            }
            lowerCaseCommand.contains("frontlighton") -> toggleFrontLight(true)
            lowerCaseCommand.contains("frontlightoff") -> toggleFrontLight(false)
            lowerCaseCommand.contains("backlighton") -> toggleBackLight(true)
            lowerCaseCommand.contains("backlightoff") -> toggleBackLight(false)
            lowerCaseCommand.contains("horn") || lowerCaseCommand.contains("beep") -> {
                horn()
            }
            else -> {
                // Command not recognized
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
    }
}