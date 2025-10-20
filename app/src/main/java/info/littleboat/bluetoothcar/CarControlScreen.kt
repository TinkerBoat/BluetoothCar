package info.littleboat.bluetoothcar

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.speech.RecognizerIntent
import android.content.Intent
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.activity.result.ActivityResultLauncher // Added this import

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CarControlScreen(viewModel: CarControlViewModel) {
    val isConnected by viewModel.isConnected.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    var isScanning by remember { mutableStateOf(false) }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
    }
    val permissionsState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText: String? = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.let { results ->
                results[0]
            }
            spokenText?.let { viewModel.processVoiceCommand(it) }
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.onPermissionsGranted()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (permissionsState.allPermissionsGranted) {
            when {
                isConnected -> ControlPanel(viewModel, speechRecognizerLauncher)
                isConnecting -> {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    Text("Connecting...")
                }
                else -> DeviceList(viewModel, discoveredDevices, isScanning) {
                    isScanning = it
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "This app requires Bluetooth, Location, and Microphone permissions to function correctly. Please grant them.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permissions")
                }
            }
        }
        ConnectionStatus(isConnecting, connectionError) {
            viewModel.clearConnectionError()
        }
    }
}

@Composable
fun ConnectionStatus(isConnecting: Boolean, error: String?, onErrorDismiss: () -> Unit) {
    if (isConnecting) {
        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
    } else if (error != null) {
        AlertDialog(
            onDismissRequest = onErrorDismiss,
            title = { Text("Connection Failed") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = onErrorDismiss) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun DeviceList(
    viewModel: CarControlViewModel,
    devices: List<BluetoothDevice>,
    isScanning: Boolean,
    onScanningChanged: (Boolean) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (isScanning) {
                viewModel.stopDiscovery()
            } else {
                viewModel.startDiscovery()
            }
            onScanningChanged(!isScanning)
        }) {
            Text(if (isScanning) "Stop Scan" else "Scan for Devices")
        }

        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        LazyColumn(modifier = Modifier.padding(16.dp)) {
            items(devices) { device ->
                Button(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { viewModel.connectToCar(device.address) }) {
                    Text(device.name ?: device.address)
                }
            }
        }
    }
}

@Composable
fun ControlPanel(viewModel: CarControlViewModel, speechRecognizerLauncher: ActivityResultLauncher<Intent>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Movement controls
        Box(modifier = Modifier.padding(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PressAndHoldButton(onPress = { viewModel.startMovingForward() }, onRelease = { viewModel.stopMoving() }) { Text("Forward") }
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    PressAndHoldButton(onPress = { viewModel.startTurningLeft() }, onRelease = { viewModel.stopMoving() }) { Text("Left") }
                    Spacer(modifier = Modifier.width(80.dp)) // Spacer for visual separation
                    PressAndHoldButton(onPress = { viewModel.startTurningRight() }, onRelease = { viewModel.stopMoving() }) { Text("Right") }
                }
                Spacer(modifier = Modifier.height(16.dp))
                PressAndHoldButton(onPress = { viewModel.startMovingBackward() }, onRelease = { viewModel.stopMoving() }) { Text("Backward") }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Light and horn controls
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            var frontLightOn by remember { mutableStateOf(false) }
            var backLightOn by remember { mutableStateOf(false) }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Front Light")
                Switch(checked = frontLightOn, onCheckedChange = { 
                    frontLightOn = it
                    viewModel.toggleFrontLight(it) 
                })
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Back Light")
                Switch(checked = backLightOn, onCheckedChange = { 
                    backLightOn = it
                    viewModel.toggleBackLight(it) 
                })
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        PressAndHoldButton(onPress = { viewModel.horn() }, onRelease = { /* No release action for horn */ }) { Text("Horn") }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { viewModel.disconnect() }) { Text("Disconnect") }

        // Voice Command Button
        Spacer(modifier = Modifier.height(16.dp))
        IconButton(onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command")
            }
            speechRecognizerLauncher.launch(intent)
        }) {
            Icon(Icons.Filled.Mic, contentDescription = "Voice Command")
        }
    }
}

@Composable
fun PressAndHoldButton(
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = { /* Handled by pointerInput */ },
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Press -> {
                            onPress()
                        }
                        PointerEventType.Release -> {
                            onRelease()
                        }
                    }
                }
            }
        }
    ) {
        content()
    }
}