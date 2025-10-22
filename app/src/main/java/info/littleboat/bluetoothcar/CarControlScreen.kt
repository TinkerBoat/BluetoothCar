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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.bluetooth.BluetoothAdapter
import androidx.compose.material.icons.filled.Mic

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CarControlScreen(viewModel: CarControlViewModel) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val filteredDevices by viewModel.filteredDevices.collectAsState()
    val filterUnnamedDevices by viewModel.filterUnnamedDevices.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val pairingStatus by viewModel.pairingStatus.collectAsState()
    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

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
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
    }
    val permissionsState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText: String? =
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.let { results ->
                        results[0]
                    }
            spokenText?.let { viewModel.processVoiceCommand(it) }
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkBluetoothStatus()
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

    LaunchedEffect(pairingStatus) {
        if (pairingStatus == info.littleboat.bluetoothcar.services.PairingStatus.SUCCESS) {
            // viewModel.connectToCar(device.address) // We need the device here
            // For now, let's reset the status and let the user click again.
            viewModel.resetPairingStatus()
        }
    }

    if (!isBluetoothEnabled) {
        AlertDialog(
            onDismissRequest = { /* Do nothing */ },
            title = { Text("Bluetooth Disabled") },
            text = { Text("Please enable Bluetooth to use this app.") },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBluetoothLauncher.launch(intent)
                }) {
                    Text("Enable")
                }
            }
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (permissionsState.allPermissionsGranted) {
                when {
                    isConnected -> ControlPanel(viewModel, speechRecognizerLauncher)
                    isConnecting || pairingStatus == info.littleboat.bluetoothcar.services.PairingStatus.PAIRING -> {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        Text(if (isConnecting) "Connecting..." else "Pairing...")
                    }

                    else -> DeviceList(viewModel, filteredDevices, isScanning, filterUnnamedDevices)
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

            if (pairingStatus == info.littleboat.bluetoothcar.services.PairingStatus.FAILED) {
                AlertDialog(
                    onDismissRequest = { viewModel.resetPairingStatus() },
                    title = { Text("Pairing Failed") },
                    text = { Text("Could not pair with the selected device.") },
                    confirmButton = {
                        Button(onClick = { viewModel.resetPairingStatus() }) {
                            Text("OK")
                        }
                    }
                )
            }
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
    filterUnnamedDevices: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
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

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Switch(
                checked = !filterUnnamedDevices,
                onCheckedChange = { isChecked ->
                    viewModel.onFilterUnnamedDevicesChanged(!isChecked)
                }
            )
            Text("Show unnamed devices", modifier = Modifier.padding(start = 8.dp))
        }

        LazyColumn(modifier = Modifier.padding(16.dp)) {
            items(devices, key = { it.address }) { device ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                        LocalContext.current,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@items
                }
                Button(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { viewModel.pairDevice(device) }) {
                    Text(device.name ?: device.address)
                }
            }
        }
    }
}

@Composable
fun ControlPanel(viewModel: CarControlViewModel, speechRecognizerLauncher: ActivityResultLauncher<Intent>) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // D-Pad on the left
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            PressAndHoldButton(onPress = { viewModel.startMovingForward() }, onRelease = { viewModel.stopMoving() }) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Forward")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                PressAndHoldButton(onPress = { viewModel.startTurningLeft() }, onRelease = { viewModel.stopMoving() }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Left")
                }
                Spacer(modifier = Modifier.width(16.dp)) // Spacer for visual separation
                PressAndHoldButton(onPress = { viewModel.startTurningRight() }, onRelease = { viewModel.stopMoving() }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            PressAndHoldButton(onPress = { viewModel.startMovingBackward() }, onRelease = { viewModel.stopMoving() }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Backward")
            }
        }

        // Action buttons on the right
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            // Light and horn controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                var frontLightOn by remember { mutableStateOf(false) }
                var backLightOn by remember { mutableStateOf(false) }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Front Light")
                    Switch(checked = frontLightOn, onCheckedChange = {
                        frontLightOn = it
                        viewModel.toggleFrontLight(it)
                    })
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Back Light")
                    Switch(checked = backLightOn, onCheckedChange = {
                        backLightOn = it
                        viewModel.toggleBackLight(it)
                    })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            PressAndHoldButton(onPress = { viewModel.startHorn() }, onRelease = { viewModel.stopHorn() }) { Text("Horn") }

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