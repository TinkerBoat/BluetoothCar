package info.littleboat.bluetoothcar.services

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.StateFlow
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow


enum class PairingStatus {
    IDLE,
    PAIRING,
    SUCCESS,
    FAILED,
    NEEDS_USER_INPUT
}

class BluetoothService @Inject constructor(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val _pairingStatus = MutableStateFlow(PairingStatus.IDLE)
    val pairingStatus: StateFlow<PairingStatus> = _pairingStatus.asStateFlow()

    private val _pinsToTry = MutableStateFlow<List<String>>(emptyList())
    private var _deviceToPair: BluetoothDevice? = null

    private var isDiscoveryReceiverRegistered = false

    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")


    private val bluetoothDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val device: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

            device?.let { updatedDevice ->
                // Check permission if needed (API 31+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) return

                val currentList = _discoveredDevices.value.toMutableList()
                val index = currentList.indexOfFirst { it.address == updatedDevice.address }

                when (action) {
                    BluetoothDevice.ACTION_FOUND,
                    BluetoothDevice.ACTION_NAME_CHANGED -> {

                        if (!updatedDevice.name.isNullOrEmpty()) {
                            if (index == -1) {
                                currentList.add(updatedDevice) // new device
                            } else {
                                currentList[index] = updatedDevice // update existing
                            }
                            _discoveredDevices.value = currentList
                        }

                        Log.d(
                            "BluetoothServiceX",
                            "Device update ($action): ${updatedDevice.name} (${updatedDevice.address})"
                        )
                    }
                }
            }
        }
    }

    private val pairingRequestReceiver = object : BroadcastReceiver() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADMIN])
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_PAIRING_REQUEST) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (_pinsToTry.value.isNotEmpty()) {
                        val pinToUse = _pinsToTry.value.first()
                        _pinsToTry.value = _pinsToTry.value.drop(1)
                        val pin = pinToUse.toByteArray()
                        try {
                            Log.d("BluetoothService", "Setting PIN for device: ${it.address} with PIN: $pinToUse")
                            it.setPin(pin)
                            abortBroadcast()
                        } catch (e: SecurityException) {
                            Log.e("BluetoothService", "Failed to set PIN for device: ${it.address}", e)
                            e.printStackTrace()
                        }
                    } else {
                        Log.d("BluetoothService", "No more automatic PINs to try. User input required.")
                    }
                }
            }
        }
    }

    private val bondStateReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val bondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val previousBondState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                    BluetoothDevice.ERROR
                )

                when (bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        _pairingStatus.value = PairingStatus.SUCCESS
                        context.unregisterReceiver(this)
                        context.unregisterReceiver(pairingRequestReceiver)
                        _pinsToTry.value = emptyList()
                        _deviceToPair = null
                    }

                    BluetoothDevice.BOND_BONDING -> {
                        _pairingStatus.value = PairingStatus.PAIRING
                    }

                    BluetoothDevice.BOND_NONE -> {
                        if (previousBondState == BluetoothDevice.BOND_BONDING) {
                            if (_pinsToTry.value.isNotEmpty()) {
                                Log.d("BluetoothService", "Pairing failed with current PIN, trying next...")
                                context.unregisterReceiver(this)
                                context.unregisterReceiver(pairingRequestReceiver)
                                _deviceToPair?.let { device ->
                                    startPairingProcess(device, _pinsToTry.value)
                                }
                            } else {
                                _pairingStatus.value = PairingStatus.NEEDS_USER_INPUT
                                context.unregisterReceiver(this)
                                context.unregisterReceiver(pairingRequestReceiver)
                                _deviceToPair = null
                            }
                        }
                    }

                    BluetoothDevice.ERROR -> {
                        _pairingStatus.value = PairingStatus.FAILED
                        context.unregisterReceiver(this)
                        context.unregisterReceiver(pairingRequestReceiver)
                        _pinsToTry.value = emptyList()
                        _deviceToPair = null
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery() {
        // Permission check (BLUETOOTH_SCAN on API 31+, ADMIN otherwise)
        val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.BLUETOOTH_ADMIN
        }

        if (ActivityCompat.checkSelfPermission(context, scanPermission)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // Register receivers only once
        if (!isDiscoveryReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            }
            context.registerReceiver(bluetoothDeviceReceiver, filter)
            isDiscoveryReceiverRegistered = true
        }

        bluetoothAdapter?.startDiscovery()
    }

    fun stopDiscovery() {
        if (isDiscoveryReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothDeviceReceiver)
            } catch (ignored: IllegalArgumentException) {
            } finally {
                isDiscoveryReceiverRegistered = false
            }
        }

        val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.BLUETOOTH_ADMIN
        }

        if (ActivityCompat.checkSelfPermission(context, scanPermission)
            != PackageManager.PERMISSION_GRANTED
        ) return

        bluetoothAdapter?.cancelDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun pairDevice(device: BluetoothDevice) {
        _deviceToPair = device
        _pinsToTry.value = listOf("1234", "0000")
        startPairingProcess(device, _pinsToTry.value)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startPairingProcess(device: BluetoothDevice, pins: List<String>) {
        try {
            val pairingFilter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
            context.registerReceiver(pairingRequestReceiver, pairingFilter)
            val bondFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            context.registerReceiver(bondStateReceiver, bondFilter)
            device.createBond()
        } catch (e: SecurityException) {
            _pairingStatus.value = PairingStatus.FAILED
        }
    }

    fun providePinAndRetry(pin: String) {
        _deviceToPair?.let { device ->
            _pinsToTry.value = listOf(pin)
            _pairingStatus.value = PairingStatus.PAIRING
            startPairingProcess(device, _pinsToTry.value)
        } ?: run {
            Log.e("BluetoothService", "No device to pair with when providing PIN.")
        }
    }

    fun resetPairingStatus() {
        _pairingStatus.value = PairingStatus.IDLE
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getPairedDevices(): Set<BluetoothDevice>? {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptySet()
        }
        return bluetoothAdapter?.bondedDevices
    }

    fun connectToDevice(deviceAddress: String): Boolean {
        if (bluetoothAdapter == null || !isBluetoothEnabled()) {
            return false
        }
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        if (ActivityCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            return true
        } catch (e: SecurityException) {
            e.printStackTrace()
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                bluetoothSocket?.close()
            } catch (closeException: Exception) {
                closeException.printStackTrace()
            }
            return false
        }
    }

    fun sendCommand(command: String): Boolean {
        Log.d("BluetoothService", "Attempting to send command: $command")
        return try {
            outputStream?.write(command.toByteArray())
            Log.d("BluetoothService", "Command sent successfully: $command")
            true
        } catch (e: Exception) {
            Log.e("BluetoothService", "Failed to send command: $command", e)
            e.printStackTrace()
            false
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            outputStream = null
            bluetoothSocket = null
        }
    }
}