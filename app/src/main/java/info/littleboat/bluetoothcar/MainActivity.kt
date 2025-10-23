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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val carControlViewModel: CarControlViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothCarTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "control_screen") {
                    composable("control_screen") {
                        NewCarControlScreen(viewModel = carControlViewModel, onNavigateToDeviceList = {
                            navController.navigate("device_list_screen")
                        })
                    }
                    composable("device_list_screen") {
                        BluetoothDeviceListScreen(viewModel = carControlViewModel, onNavigateBack = {
                            navController.popBackStack()
                        })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        carControlViewModel.disconnect()
    }
}