package info.littleboat.bluetoothcar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import info.littleboat.bluetoothcar.di.IBluetoothService
import info.littleboat.bluetoothcar.services.PairingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.bluetooth.BluetoothDevice
import androidx.activity.compose.LocalActivity


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDeviceListScreen(viewModel: CarControlViewModel, onNavigateBack: () -> Unit) {
    val filteredDevices by viewModel.filteredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    BackHandler(onBack = onNavigateBack)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    if (isScanning) {
                        viewModel.stopDiscovery()
                    } else {
                        viewModel.startDiscovery()
                    }
                }) {
                    Text(if (isScanning) "Stop Scan" else "Scan for Devices")
                }

                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 16.dp))
                }
            }

            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(filteredDevices, key = { it.address }) { device ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                            LocalContext.current,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return@items
                    }
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            viewModel.pairDevice(device)
                            onNavigateBack()
                        }
                    ) {
                        Text("${device.name} - ${device.address}")
                    }
                }
            }
        }

        val activity = LocalActivity.current
        IconButton(
            onClick = { activity?.finish() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Exit")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BluetoothDeviceListScreenPreview() {
    val bluetoothService = object : IBluetoothService {
        override val discoveredDevices: StateFlow<List<BluetoothDevice>> = MutableStateFlow(emptyList())
        override val pairingStatus: StateFlow<PairingStatus> = MutableStateFlow(PairingStatus.IDLE)
        override fun isBluetoothEnabled(): Boolean = true
        override fun startDiscovery() {}
        override fun stopDiscovery() {}
        override fun connectToDevice(deviceAddress: String): Boolean = true
        override fun disconnect() {}
        override fun sendCommand(command: String): Boolean = true
        override fun pairDevice(device: BluetoothDevice) {}
        override fun resetPairingStatus() {}
        override fun getPairedDevices(): Set<BluetoothDevice>? = null
        override fun getLastConnectedDeviceAddress(): String? = null
        override fun providePinAndRetryPairing(pin: String) {}
    }
    val viewModel = CarControlViewModel(bluetoothService)
    BluetoothDeviceListScreen(viewModel = viewModel, onNavigateBack = {})
}
