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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject

class BluetoothService @Inject constructor(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private var isReceiverRegistered = false

    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val receiver = object : BroadcastReceiver() {
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
                    device?.let {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        val currentList = _discoveredDevices.value.toMutableList()
                        if (!currentList.any { d -> d.address == it.address }) {
                            currentList.add(it)
                            _discoveredDevices.value = currentList
                        }
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!isReceiverRegistered) {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(receiver, filter)
            isReceiverRegistered = true
        }
        bluetoothAdapter?.startDiscovery()
    }

    fun stopDiscovery() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
                isReceiverRegistered = false
            } catch (e: IllegalArgumentException) {
                // Receiver not registered, ignore
            }
        }
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothAdapter?.cancelDiscovery()
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
        return try {
            outputStream?.write(command.toByteArray())
            true
        } catch (e: Exception) {
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