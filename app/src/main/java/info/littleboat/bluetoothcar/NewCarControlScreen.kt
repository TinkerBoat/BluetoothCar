package info.littleboat.bluetoothcar

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import info.littleboat.bluetoothcar.services.PairingStatus

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.result.ActivityResultLauncher

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box

import androidx.constraintlayout.compose.ConstraintLayout

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewCarControlScreen(viewModel: CarControlViewModel, onNavigateToDeviceList: () -> Unit) {
    val isConnecting by viewModel.isConnecting.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val pairingStatus by viewModel.pairingStatus.collectAsState()
    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsState()

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
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {
            val (disconnectButton, controlPanel, permissions) = createRefs()

            val isConnecting by viewModel.isConnecting.collectAsState()
            val isConnected by viewModel.isConnected.collectAsState()
            val hasLastConnectedDevice by viewModel.hasLastConnectedDevice.collectAsState()

            if (hasLastConnectedDevice || isConnected) {
                Button(
                    onClick = {
                        if (isConnected) {
                            viewModel.disconnect()
                            onNavigateToDeviceList()
                        } else {
                            viewModel.reconnectToLastDevice()
                        }
                    },
                    enabled = !isConnecting,
                    modifier = Modifier.constrainAs(disconnectButton) {
                        top.linkTo(parent.top, margin = 16.dp)
                        end.linkTo(parent.end, margin = 16.dp)
                    }
                ) {
                    Text(
                        when {
                            isConnected -> "Disconnect"
                            isConnecting -> "Connecting..."
                            else -> "Reconnect"
                        }
                    )
                }
            }

            if (permissionsState.allPermissionsGranted) {
                when {
                    isConnected -> {
                        ControlPanel(viewModel, speechRecognizerLauncher, onNavigateToDeviceList, modifier = Modifier.constrainAs(controlPanel) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        })
                    }
                    isConnecting || pairingStatus == PairingStatus.PAIRING -> {
                        CircularProgressIndicator(modifier = Modifier.constrainAs(controlPanel) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        })
                        Text(if (isConnecting) "Connecting..." else "Pairing...", modifier = Modifier.constrainAs(createRef()) {
                            top.linkTo(controlPanel.bottom, margin = 8.dp)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        })
                    }
                    else -> {
                        Button(onClick = onNavigateToDeviceList, modifier = Modifier.constrainAs(controlPanel) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        }) {
                            Text("Select Device")
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.constrainAs(permissions) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }) {
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

            if (connectionError != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearConnectionError() },
                    title = { Text("Connection Failed") },
                    text = { Text(connectionError!!) },
                    confirmButton = {
                        Button(onClick = { viewModel.clearConnectionError() }) {
                            Text("OK")
                        }
                    }
                )
            }

            if (pairingStatus == PairingStatus.FAILED) {
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
fun ControlPanel(viewModel: CarControlViewModel, speechRecognizerLauncher: ActivityResultLauncher<Intent>, onNavigateToDeviceList: () -> Unit, modifier: Modifier = Modifier) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val (dpad, actions) = createRefs()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.constrainAs(dpad) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(actions.start)
            }
        ) {
            PressAndHoldButton(onPress = { viewModel.startMovingForward() }, onRelease = { viewModel.stopMoving() }, modifier = Modifier.size(100.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Forward", modifier = Modifier.size(90.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                PressAndHoldButton(onPress = { viewModel.startTurningLeft() }, onRelease = { viewModel.stopMoving() }, modifier = Modifier.size(100.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Left", modifier = Modifier.size(90.dp))
                }
                Spacer(modifier = Modifier.width(40.dp))
                PressAndHoldButton(onPress = { viewModel.startTurningRight() }, onRelease = { viewModel.stopMoving() }, modifier = Modifier.size(100.dp)) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right", modifier = Modifier.size(90.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            PressAndHoldButton(onPress = { viewModel.startMovingBackward() }, onRelease = { viewModel.stopMoving() }, modifier = Modifier.size(100.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Backward", modifier = Modifier.size(90.dp))
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.constrainAs(actions) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(dpad.end)
                end.linkTo(parent.end)
            }
        ) {
            // Speed controls
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.setSpeed("high") }) { Text("High") }
                Button(onClick = { viewModel.setSpeed("medium") }) { Text("Medium") }
                Button(onClick = { viewModel.setSpeed("low") }) { Text("Low") }
            }

            // Light and horn controls
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var frontLightOn by remember { mutableStateOf(false) }
                var backLightOn by remember { mutableStateOf(false) }

                Button(onClick = {
                    frontLightOn = !frontLightOn
                    viewModel.toggleFrontLight(frontLightOn)
                }) { Text("Front Light") }

                Button(onClick = {
                    backLightOn = !backLightOn
                    viewModel.toggleBackLight(backLightOn)
                }) { Text("Back Light") }
            }

            PressAndHoldButton(onPress = { viewModel.startHorn() }, onRelease = { viewModel.stopHorn() }) { Text("Horn") }

            // Voice Command Button
            IconButton(onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command")
                }
                speechRecognizerLauncher.launch(intent)
            }) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice Command", modifier = Modifier.size(48.dp))
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
        Row {
            content()
        }
    }
}