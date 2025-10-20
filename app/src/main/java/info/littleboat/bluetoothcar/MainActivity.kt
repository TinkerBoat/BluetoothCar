package info.littleboat.bluetoothcar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import info.littleboat.bluetoothcar.ui.theme.BluetoothCarTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val carControlViewModel: CarControlViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothCarTheme {
                CarControlScreen(viewModel = carControlViewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        carControlViewModel.disconnect()
    }
}