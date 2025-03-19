package ru.toxyxd.freewave

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val SELECT_DEVICE_REQUEST = 0

@Composable
fun ScanScreen(
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel(),
    onDeviceSelected: (BluetoothDevice) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val context = LocalContext.current
    val deviceManager by lazy {
        getSystemService(context, CompanionDeviceManager::class.java) as CompanionDeviceManager
    }

    LaunchedEffect(selectedDevice) {
        if (selectedDevice != null) {
            onDeviceSelected(selectedDevice!!)
        }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            viewModel.onResult(SELECT_DEVICE_REQUEST, it.resultCode, it.data)
        }

    Column(modifier = modifier) {
        when (val state = uiState) {
            is ScanUiState.Initial -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        modifier = Modifier.align(Alignment.Center),
                        onClick = viewModel::startScan
                    ) {
                        Text("Select device")
                    }
                }
            }

            is ScanUiState.Selecting -> {
                LaunchedEffect(Unit) {
                    deviceManager.associate(
                        viewModel.pairingRequest,
                        object : CompanionDeviceManager.Callback() {
                            override fun onAssociationPending(intentSender: IntentSender) {
                                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                            }

                            override fun onFailure(p0: CharSequence?) {
                                viewModel.onResult(
                                    SELECT_DEVICE_REQUEST,
                                    Activity.RESULT_CANCELED,
                                    null
                                )
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                }
            }

            is ScanUiState.DeviceSelected -> {
                Text(text = "Device selected: ${state.name}")
            }

            is ScanUiState.Error -> {
                Text(text = "Error: ${state.message}")
            }
        }
    }
}

class ScanViewModel() : ViewModel() {
    val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
        .build()
    val pairingRequest: AssociationRequest = AssociationRequest.Builder()
        .addDeviceFilter(deviceFilter)
        .build()

    private val _uiState: MutableStateFlow<ScanUiState> = MutableStateFlow(ScanUiState.Initial)
    val uiState get() = _uiState.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val selectedDevice get() = _selectedDevice.asStateFlow()

    @SuppressLint("MissingPermission") // CDM takes care of it afaiu
    fun onResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            SELECT_DEVICE_REQUEST -> when (resultCode) {
                Activity.RESULT_OK -> {
                    if (data == null) {
                        _uiState.update { ScanUiState.Error("No data") }
                        return
                    }

                    val deviceToPair: BluetoothDevice? = IntentCompat.getParcelableExtra(
                        data,
                        CompanionDeviceManager.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                    if (deviceToPair == null) {
                        _uiState.update { ScanUiState.Error("No device to pair") }
                        return
                    }

                    deviceToPair.createBond()
                    _selectedDevice.update { deviceToPair }
                    _uiState.update { _ ->
                        ScanUiState.DeviceSelected(deviceToPair.name!!)
                    }
                }

                Activity.RESULT_CANCELED -> {
                    _uiState.update { ScanUiState.Initial }
                }
            }
        }
    }

    fun startScan() {
        _uiState.update { ScanUiState.Selecting }
    }
}

sealed class ScanUiState {
    data object Initial : ScanUiState()
    data object Selecting : ScanUiState()
    data class DeviceSelected(val name: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}