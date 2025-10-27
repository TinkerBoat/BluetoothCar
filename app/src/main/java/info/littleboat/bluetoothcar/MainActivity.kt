package info.littleboat.bluetoothcar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import info.littleboat.bluetoothcar.ui.theme.BluetoothCarTheme

import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import android.bluetooth.BluetoothDevice
import info.littleboat.bluetoothcar.services.PairingStatus
import androidx.compose.runtime.Composable
import info.littleboat.bluetoothcar.di.IBluetoothService

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BluetoothCarTheme {
                val navController = rememberNavController()
                val viewModel: CarControlViewModel = viewModel()

                NavHost(navController = navController, startDestination = "carControl") {
                    composable("carControl") {
                        NewCarControlScreen(viewModel) {
                            navController.navigate("deviceList")
                        }
                    }
                    composable("deviceList") {
                        BluetoothDeviceListScreen(viewModel) {
                            navController.popBackStack()
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    BluetoothCarTheme {
        val navController = rememberNavController()
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

        NavHost(navController = navController, startDestination = "carControl") {
            composable("carControl") {
                NewCarControlScreen(viewModel) {
                    navController.navigate("deviceList")
                }
            }
            composable("deviceList") {
                BluetoothDeviceListScreen(viewModel) {
                    navController.popBackStack()
                }
            }
        }
    }
}