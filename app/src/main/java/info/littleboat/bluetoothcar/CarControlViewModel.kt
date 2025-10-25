package info.littleboat.bluetoothcar

import android.Manifest
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.littleboat.bluetoothcar.di.IBluetoothService
import info.littleboat.bluetoothcar.services.PairingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CarControlViewModel @Inject constructor(
    private val bluetoothService: IBluetoothService
) : ViewModel() {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _filterUnnamedDevices = MutableStateFlow(true)
    val filterUnnamedDevices: StateFlow<Boolean> = _filterUnnamedDevices.asStateFlow()

    private val _filteredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val filteredDevices: StateFlow<List<BluetoothDevice>> = _filteredDevices.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _pairingStatus = MutableStateFlow(PairingStatus.IDLE)
    val pairingStatus: StateFlow<PairingStatus> = _pairingStatus.asStateFlow()

    private val _hasLastConnectedDevice = MutableStateFlow(false)
    val hasLastConnectedDevice: StateFlow<Boolean> = _hasLastConnectedDevice.asStateFlow()

    private var _deviceBeingPaired: BluetoothDevice? = null

    private val _isFrontLightOn = MutableStateFlow(false)
    val isFrontLightOn: StateFlow<Boolean> = _isFrontLightOn.asStateFlow()

    private val _isBackLightOn = MutableStateFlow(false)
    val isBackLightOn: StateFlow<Boolean> = _isBackLightOn.asStateFlow()

    // CarControlViewModel.kt

    val uiState: StateFlow<CarControlUiState> = combine(
        isConnecting,
        isConnected,
        connectionError,
        pairingStatus,
        isBluetoothEnabled,
        hasLastConnectedDevice,
        isFrontLightOn,
        isBackLightOn
    ) { values ->
        CarControlUiState(
            isConnecting = values[0] as Boolean,
            isConnected = values[1] as Boolean,
            connectionError = values[2] as? String,
            pairingStatus = values[3] as PairingStatus,
            isBluetoothEnabled = values[4] as Boolean,
            hasLastConnectedDevice = values[5] as Boolean,
            isFrontLightOn = values[6] as Boolean,
            isBackLightOn = values[7] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CarControlUiState()
    )


    init {
        checkBluetoothStatus()
        observePairingStatus()
        observeDeviceChanges()
        viewModelScope.launch {
            _hasLastConnectedDevice.value = bluetoothService.getLastConnectedDeviceAddress() != null
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun observeDeviceChanges() {
        viewModelScope.launch {
            bluetoothService.discoveredDevices.collect { devices ->
                _discoveredDevices.value = devices
                updateFilteredDevices(devices, _filterUnnamedDevices.value)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun onFilterUnnamedDevicesChanged(isChecked: Boolean) {
        _filterUnnamedDevices.value = isChecked
        updateFilteredDevices(discoveredDevices.value, isChecked)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun updateFilteredDevices(devices: List<BluetoothDevice>, filterUnnamed: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            // Sort devices: named devices first, then by recency (newest first)
            val sortedDevices = devices.withIndex()
                .filter { if (filterUnnamed) !it.value.name.isNullOrEmpty() else true }
                .sortedWith(compareByDescending<IndexedValue<BluetoothDevice>> { !it.value.name.isNullOrEmpty() }
                    .thenByDescending { it.index })
                .map { it.value }
            _filteredDevices.value = sortedDevices
        }
    }

    private fun observePairingStatus() {
        bluetoothService.pairingStatus.onEach { status ->
            _pairingStatus.value = status
            when (status) {
                PairingStatus.SUCCESS -> {
                    _deviceBeingPaired?.address?.let { address ->
                        connectToDevice(address)
                    }
                    _deviceBeingPaired = null
                }
                PairingStatus.FAILED -> {
                    _deviceBeingPaired = null
                }
                else -> {}
            }
        }.launchIn(viewModelScope)
    }

    fun checkBluetoothStatus() {
        _isBluetoothEnabled.value = bluetoothService.isBluetoothEnabled()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun onPermissionsGranted() {
        // Combine paired and discovered devices
        viewModelScope.launch {
            val paired = bluetoothService.getPairedDevices() ?: emptySet()
            val initialDevices = (paired + bluetoothService.discoveredDevices.value).distinctBy { it.address }
            _discoveredDevices.value = initialDevices
            updateFilteredDevices(initialDevices, _filterUnnamedDevices.value)
        }

        bluetoothService.discoveredDevices.onEach { discovered ->
            val paired = bluetoothService.getPairedDevices() ?: emptySet()
            val allDevices = (paired + discovered).distinctBy { it.address }
            _discoveredDevices.value = allDevices
            updateFilteredDevices(allDevices, _filterUnnamedDevices.value)
        }.launchIn(viewModelScope)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun pairDevice(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            connectToDevice(device.address)
        } else {
            _deviceBeingPaired = device // Store the device being paired
            viewModelScope.launch(Dispatchers.IO) {
                bluetoothService.pairDevice(device)
            }
        }
    }

    fun resetPairingStatus() {
        bluetoothService.resetPairingStatus()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            bluetoothService.startDiscovery()
        }
    }

    fun stopDiscovery() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = false
            bluetoothService.stopDiscovery()
        }
    }

    private fun connectToDevice(deviceAddress: String) {
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

    fun setSpeed(speed: String) {
        val command = when (speed) {
            "high" -> "1"
            "medium" -> "2"
            "low" -> "3"
            else -> ""
        }
        sendCommand(command)
    }

    fun reconnectToLastDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastDeviceAddress = bluetoothService.getLastConnectedDeviceAddress()
            if (lastDeviceAddress != null) {
                connectToDevice(lastDeviceAddress)
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

    private var movementJob: Job? = null

    // --- Button Actions ---
    fun startMovingForward() = startMoving("F")
    fun startMovingBackward() = startMoving("B")
    fun startTurningLeft() = startMoving("L")
    fun startTurningRight() = startMoving("R")

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
        viewModelScope.launch(Dispatchers.IO) {
            bluetoothService.sendCommand("S") // Send stop command
        }
    }

    fun toggleFrontLight(isOn: Boolean) {
        _isFrontLightOn.value = isOn
        sendCommand(if (isOn) "W" else "w")
    }

    fun toggleBackLight(isOn: Boolean) {
        _isBackLightOn.value = isOn
        sendCommand(if (isOn) "U" else "u")
    }

    fun horn() {
        // This function is no longer used directly for press/hold
    }

    private var hornJob: Job? = null

    fun startHorn() {
        hornJob?.cancel() // Cancel any existing horn job
        hornJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                bluetoothService.sendCommand("V")
                delay(100) // Send 'V' every 100ms
            }
        }
    }

    fun stopHorn() {
        hornJob?.cancel() // Stop sending 'V'
        viewModelScope.launch(Dispatchers.IO) {
            bluetoothService.sendCommand("v") // Send 'v' once on release
        }
    }

    // --- Button Actions ---
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

data class CarControlUiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val connectionError: String? = null,
    val pairingStatus: PairingStatus = PairingStatus.IDLE,
    val isBluetoothEnabled: Boolean = false,
    val hasLastConnectedDevice: Boolean = false,
    val isFrontLightOn: Boolean = false,
    val isBackLightOn: Boolean = false
)
