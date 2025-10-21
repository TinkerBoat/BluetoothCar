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
    FAILED
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

    private var isDiscoveryReceiverRegistered = false

    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action!!
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    device?.let { device ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }

                        val currentList = _discoveredDevices.value.toMutableList()
                        if (currentList.none { it.address == device.address }) {
                            currentList.add(device)
                            _discoveredDevices.value = currentList
                            Log.d(
                                "BluetoothService",
                                "Device found: ${device.name} - ${device.address}"
                            )
                        }
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
                    }

                    BluetoothDevice.BOND_BONDING -> {
                        _pairingStatus.value = PairingStatus.PAIRING
                    }

                    BluetoothDevice.BOND_NONE -> {
                        if (previousBondState == BluetoothDevice.BOND_BONDING) {
                            _pairingStatus.value = PairingStatus.FAILED
                        }
                        context.unregisterReceiver(this)
                    }

                    BluetoothDevice.ERROR -> {
                        _pairingStatus.value = PairingStatus.FAILED
                        context.unregisterReceiver(this)
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.BLUETOOTH_ADMIN
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (!isDiscoveryReceiverRegistered) {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(discoveryReceiver, filter)
            isDiscoveryReceiverRegistered = true
        }
        if (bluetoothAdapter == null) {
            return
        }
        bluetoothAdapter.startDiscovery()
    }

    fun stopDiscovery() {
        if (isDiscoveryReceiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
                isDiscoveryReceiverRegistered = false
            } catch (e: IllegalArgumentException) {
                // Receiver not registered, ignore
            }
        }

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.BLUETOOTH_ADMIN
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        bluetoothAdapter?.cancelDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun pairDevice(device: BluetoothDevice) {
        try {
            val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            context.registerReceiver(bondStateReceiver, filter)
            device.createBond()
        } catch (e: SecurityException) {
            _pairingStatus.value = PairingStatus.FAILED
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