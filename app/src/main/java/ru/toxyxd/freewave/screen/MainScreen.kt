package ru.toxyxd.freewave.screen

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.toxyxd.freewave.bluetooth.BluetoothController
import ru.toxyxd.freewave.bluetooth.protocol.requests.AmbientControl
import ru.toxyxd.freewave.bluetooth.protocol.requests.Init
import ru.toxyxd.freewave.screen.component.AmbientSoundComponent
import ru.toxyxd.freewave.ui.component.Button

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bluetoothControllerState by viewModel.bluetoothControllerState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        when (val state = uiState) {
            is MainUiState.Loading -> {
                Text(text = "Loading...")
            }

            is MainUiState.Error -> {
                Text(text = "Error: ${(uiState as MainUiState.Error).message}")
            }

            is MainUiState.Success -> {
                Text(text = "Name: ${state.name}")
                Text(text = "MAC: ${state.mac}")

                when (bluetoothControllerState) {
                    is BluetoothController.ConnectionState.Connected -> {
                        Text(text = "Connected")
                        AmbientSoundComponent()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
//                            Button(onClick = { viewModel.sendInit() }) {
//                                Text(text = "Send Init")
//                            }
//                            Button(onClick = { viewModel.getAmbientControl() }) {
//                                Text(text = "Get Ambient Control")
//                            }
                        }
                    }

                    is BluetoothController.ConnectionState.Disconnected -> {
                        Text(text = "Disconnected")
                        Button(onClick = { viewModel.connect() }) {
                            Text(text = "Reconnect")
                        }
                    }

                    is BluetoothController.ConnectionState.Connecting -> {
                        Text(text = "Connecting...")
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _bluetoothControllerState =
        MutableStateFlow<BluetoothController.ConnectionState>(BluetoothController.ConnectionState.Disconnected)
    val bluetoothControllerState = _bluetoothControllerState.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothDevice: BluetoothDevice? = null
    private var bluetoothController: BluetoothController

    init {
        bluetoothDevice = bluetoothAdapter.bondedDevices.firstOrNull { device ->
            device.name == "WH-1000XM4"
        }
        if (bluetoothDevice == null) {
            _uiState.update { MainUiState.Error("Bluetooth device is null") }
        }
        bluetoothController = BluetoothController.also {
            viewModelScope.launch {
                it.connect(bluetoothDevice!!)
                launch {
                    collectBluetoothState(it)
                }
                _uiState.update {
                    MainUiState.Success(
                        name = bluetoothDevice!!.name,
                        mac = bluetoothDevice!!.address,
                    )
                }
            }
        }
    }

    private suspend fun collectBluetoothState(controller: BluetoothController) {
        controller.connectionState.collect { state ->
            _bluetoothControllerState.update { state }
        }
    }

    fun connect() {
        viewModelScope.launch(Dispatchers.IO) {
            bluetoothController.connect(bluetoothDevice!!)
        }
    }

    fun sendInit() {
        viewModelScope.launch(Dispatchers.IO) {
            bluetoothController.get(Init)
        }
    }

    fun getAmbientControl() {
        viewModelScope.launch(Dispatchers.IO) {
            bluetoothController.get(AmbientControl)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothController.disconnect()
    }
}

sealed class MainUiState {
    object Loading : MainUiState()
    class Error(val message: String) : MainUiState()
    data class Success(
        val name: String = "WH-1000XM4",
        val mac: String = "00:00:00:00:00:00",
    ) : MainUiState()
}