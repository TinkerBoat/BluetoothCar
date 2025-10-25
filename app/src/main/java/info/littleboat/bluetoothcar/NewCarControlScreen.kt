package info.littleboat.bluetoothcar

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import info.littleboat.bluetoothcar.services.PairingStatus

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NewCarControlScreen(viewModel: CarControlViewModel, onNavigateToDeviceList: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    // --- Permissions Handling ---
    val permissionsToRequest = getPermissionsToRequest()
    val permissionsState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

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

    // --- Activity Result Launchers ---
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            spokenText?.let { viewModel.processVoiceCommand(it) }
        }
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkBluetoothStatus()
    }

    // --- UI Rendering ---
    Box(modifier = Modifier.fillMaxSize()) {
        if (!uiState.isBluetoothEnabled) {
            BluetoothDisabledDialog {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(intent)
            }
        } else if (!permissionsState.allPermissionsGranted) {
            PermissionsNotGrantedContent {
                permissionsState.launchMultiplePermissionRequest()
            }
        } else {
            // Main content when Bluetooth and permissions are ready
            ConstraintLayout(modifier = Modifier.fillMaxSize()) {
                val (reconnectButton, mainContent) = createRefs()

                if (uiState.hasLastConnectedDevice || uiState.isConnected) {
                    ReconnectButton(
                        uiState = uiState,
                        onClick = {
                            if (uiState.isConnected) {
                                viewModel.disconnect()
                                onNavigateToDeviceList()
                            } else {
                                viewModel.reconnectToLastDevice()
                            }
                        },
                        modifier = Modifier.constrainAs(reconnectButton) {
                            top.linkTo(parent.top, margin = 16.dp)
                            end.linkTo(parent.end, margin = 16.dp)
                        }
                    )
                }

                Box(modifier = Modifier.constrainAs(mainContent) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }) {
                    when {
                        uiState.isConnected -> ControlPanel(
                            isFrontLightOn = uiState.isFrontLightOn,
                            isBackLightOn = uiState.isBackLightOn,
                            onStartMovingForward = viewModel::startMovingForward,
                            onStartMovingBackward = viewModel::startMovingBackward,
                            onStartTurningLeft = viewModel::startTurningLeft,
                            onStartTurningRight = viewModel::startTurningRight,
                            onStopMoving = viewModel::stopMoving,
                            onStartHorn = viewModel::startHorn,
                            onStopHorn = viewModel::stopHorn,
                            onToggleFrontLight = { viewModel.toggleFrontLight(!uiState.isFrontLightOn) },
                            onToggleBackLight = { viewModel.toggleBackLight(!uiState.isBackLightOn) },
                            onSetSpeed = viewModel::setSpeed,
                            onVoiceCommandClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command")
                                }
                                speechRecognizerLauncher.launch(intent)
                            }
                        )
                        uiState.isConnecting || uiState.pairingStatus == PairingStatus.PAIRING -> {
                            ConnectingIndicator(isConnecting = uiState.isConnecting)
                        }
                        else -> {
                            SelectDeviceButton(onClick = onNavigateToDeviceList)
                        }
                    }
                }
            }
        }

        // --- Dialogs for Errors ---
        uiState.connectionError?.let { error ->
            ErrorDialog(
                title = "Connection Failed",
                text = error,
                onDismiss = { viewModel.clearConnectionError() }
            )
        }

        if (uiState.pairingStatus == PairingStatus.FAILED) {
            ErrorDialog(
                title = "Pairing Failed",
                text = "Could not pair with the selected device.",
                onDismiss = { viewModel.resetPairingStatus() }
            )
        }
    }
}

// --- Stateless UI Components ---

@Composable
private fun ControlPanel(
    isFrontLightOn: Boolean,
    isBackLightOn: Boolean,
    onStartMovingForward: () -> Unit,
    onStartMovingBackward: () -> Unit,
    onStartTurningLeft: () -> Unit,
    onStartTurningRight: () -> Unit,
    onStopMoving: () -> Unit,
    onStartHorn: () -> Unit,
    onStopHorn: () -> Unit,
    onToggleFrontLight: () -> Unit,
    onToggleBackLight: () -> Unit,
    onSetSpeed: (String) -> Unit,
    onVoiceCommandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val (dpad, actions) = createRefs()

        // D-Pad Controls
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
            PressAndHoldButton(onPress = onStartMovingForward, onRelease = onStopMoving, modifier = Modifier.size(100.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "Forward", modifier = Modifier.size(90.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row {
                PressAndHoldButton(onPress = onStartTurningLeft, onRelease = onStopMoving, modifier = Modifier.size(100.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left", modifier = Modifier.size(90.dp))
                }
                Spacer(Modifier.width(40.dp))
                PressAndHoldButton(onPress = onStartTurningRight, onRelease = onStopMoving, modifier = Modifier.size(100.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right", modifier = Modifier.size(90.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            PressAndHoldButton(onPress = onStartMovingBackward, onRelease = onStopMoving, modifier = Modifier.size(100.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "Backward", modifier = Modifier.size(90.dp))
            }
        }

        // Action Buttons
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSetSpeed("high") }) { Text("High") }
                Button(onClick = { onSetSpeed("medium") }) { Text("Medium") }
                Button(onClick = { onSetSpeed("low") }) { Text("Low") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleFrontLight) { Text(if (isFrontLightOn) "Front Off" else "Front On") }
                Button(onClick = onToggleBackLight) { Text(if (isBackLightOn) "Back Off" else "Back On") }
            }
            PressAndHoldButton(onPress = onStartHorn, onRelease = onStopHorn) { Text("Horn") }
            IconButton(onClick = onVoiceCommandClick) {
                Icon(Icons.Filled.Mic, "Voice Command", modifier = Modifier.size(48.dp))
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
        onClick = { /* Clicks are disabled, interaction is handled by pointerInput */ },
        modifier = modifier.pointerInput(onPress, onRelease) {
            detectTapGestures(
                onPress = {
                    onPress()
                    try {
                        awaitRelease()
                    } finally {
                        onRelease()
                    }
                }
            )
        }
    ) {
        Row {
            content()
        }
    }
}

// --- Helper Composables for Screen States ---

@Composable
private fun BluetoothDisabledDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Bluetooth Disabled") },
        text = { Text("Please enable Bluetooth to use this app.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Enable") } }
    )
}

@Composable
private fun PermissionsNotGrantedContent(onGrantClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "This app requires Bluetooth, Location, and Microphone permissions to function correctly. Please grant them.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrantClick) {
            Text("Grant Permissions")
        }
    }
}

@Composable
private fun ReconnectButton(uiState: CarControlUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = !uiState.isConnecting,
        modifier = modifier
    ) {
        Text(
            when {
                uiState.isConnected -> "Disconnect"
                uiState.isConnecting -> "Connecting..."
                else -> "Reconnect"
            }
        )
    }
}

@Composable
private fun ConnectingIndicator(isConnecting: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(8.dp))
        Text(if (isConnecting) "Connecting..." else "Pairing...")
    }
}

@Composable
private fun SelectDeviceButton(onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Button(onClick = onClick) {
            Text("Select Device")
        }
    }
}

@Composable
private fun ErrorDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } }
    )
}

// --- Helper Functions ---

private fun getPermissionsToRequest(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
}

// --- Preview ---

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun ControlPanelPreview() {
    ControlPanel(
        isFrontLightOn = false,
        isBackLightOn = true,
        onStartMovingForward = {},
        onStartMovingBackward = {},
        onStartTurningLeft = {},
        onStartTurningRight = {},
        onStopMoving = {},
        onStartHorn = {},
        onStopHorn = {},
        onToggleFrontLight = {},
        onToggleBackLight = {},
        onSetSpeed = {},
        onVoiceCommandClick = {}
    )
}