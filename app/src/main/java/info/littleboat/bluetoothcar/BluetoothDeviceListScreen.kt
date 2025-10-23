package info.littleboat.bluetoothcar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDeviceListScreen(viewModel: CarControlViewModel, onNavigateBack: () -> Unit) {
    val filteredDevices by viewModel.filteredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val filterUnnamedDevices by viewModel.filterUnnamedDevices.collectAsState()

    BackHandler(onBack = onNavigateBack)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxHeight()
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Switch(
                checked = !filterUnnamedDevices,
                onCheckedChange = { isChecked ->
                    viewModel.onFilterUnnamedDevicesChanged(!isChecked)
                }
            )
            Text("Show unnamed devices", modifier = Modifier.padding(start = 8.dp))
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
                    Text(device.name ?: device.address)
                }
            }
        }
    }
}