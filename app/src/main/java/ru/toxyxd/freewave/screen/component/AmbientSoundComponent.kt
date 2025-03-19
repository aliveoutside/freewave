package ru.toxyxd.freewave.screen.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.toxyxd.freewave.bluetooth.BluetoothController
import ru.toxyxd.freewave.bluetooth.protocol.requests.AmbientControl

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun AmbientSoundComponent(
    viewModel: AmbientSoundViewModel = viewModel(),
) {
    val uiState by viewModel.ambientSoundUiState.collectAsStateWithLifecycle()
    var dropdownExpanded by remember { mutableStateOf(false) }

    var sliderPosition by remember { mutableIntStateOf(uiState.ambientSound) }
    val sliderInteraction = remember { MutableInteractionSource() }
    val isSliderDragging by sliderInteraction.collectIsDraggedAsState()

    LaunchedEffect(uiState.ambientSound) {
        if (!isSliderDragging) {
            sliderPosition = uiState.ambientSound
        }
    }

    val sliderMovementFlow = remember {
        snapshotFlow { sliderPosition }
            .filter { it in 1..20 }
            .sample(150)
    }

    val sliderReleaseFlow = remember {
        snapshotFlow { sliderPosition }
            .filter { it in 1..20 }
            .debounce(100)
    }
    LaunchedEffect(Unit) {
        launch {
            sliderMovementFlow.collect { viewModel.onAmbientSoundChange(it) }
        }
        launch {
            sliderReleaseFlow.collect { viewModel.onAmbientSoundChange(it) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            OutlinedTextField(
                value = "Mode: ${uiState.mode.name.replace('_', ' ')}",
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Mode dropdown"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = RoundedCornerShape(8.dp),
            )
            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                shape = RoundedCornerShape(8.dp),
            ) {
                AmbientControl.Mode.entries.forEach { modeOption ->
                    DropdownMenuItem(text = { Text(modeOption.name.replace('_', ' ')) }, onClick = {
                        viewModel.onModeChange(modeOption)
                        dropdownExpanded = false
                    }, contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Text(text = "Ambient Sound Level: ${uiState.ambientSound}")
        Slider(
            value = sliderPosition.toFloat(),
            onValueChange = { newValue ->
                sliderPosition = newValue.toInt()
            },
            valueRange = 1f..20f,
            enabled = uiState.mode == AmbientControl.Mode.AMBIENT_SOUND,
            steps = 20,
            interactionSource = sliderInteraction,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Focus on Voice")
            Switch(
                checked = uiState.focusOnVoice,
                onCheckedChange = { viewModel.onFocusOnVoiceChange(it) },
                enabled = uiState.mode == AmbientControl.Mode.AMBIENT_SOUND,
            )
        }
    }
}

class AmbientSoundViewModel : ViewModel() {
    private val controller: BluetoothController = BluetoothController

    private val _ambientSoundUiState: MutableStateFlow<AmbientSoundUiState> =
        MutableStateFlow(AmbientSoundUiState())
    val ambientSoundUiState = _ambientSoundUiState.asStateFlow()

    init {
        viewModelScope.apply {
            launch {
                controller.eventsFlow.filterIsInstance(AmbientControl::class).collect { event ->
                    _ambientSoundUiState.update {
                        AmbientSoundUiState(
                            mode = event.mode,
                            focusOnVoice = event.focusOnVoice,
                            ambientSound = event.ambientSound
                        )
                    }
                }
            }
            launch {
                controller.get(AmbientControl)
            }
        }
    }

    fun onAmbientSoundChange(ambientSound: Int) {
        setAmbientControl(
            mode = ambientSoundUiState.value.mode,
            focusOnVoice = ambientSoundUiState.value.focusOnVoice,
            ambientSound = ambientSound
        )
    }

    fun onModeChange(mode: AmbientControl.Mode) {
        setAmbientControl(
            mode = mode,
            focusOnVoice = ambientSoundUiState.value.focusOnVoice,
            ambientSound = ambientSoundUiState.value.ambientSound
        )
    }

    fun onFocusOnVoiceChange(focusOnVoice: Boolean) {
        setAmbientControl(
            mode = ambientSoundUiState.value.mode,
            focusOnVoice = focusOnVoice,
            ambientSound = ambientSoundUiState.value.ambientSound
        )
    }

    private fun setAmbientControl(
        mode: AmbientControl.Mode,
        focusOnVoice: Boolean,
        ambientSound: Int,
    ) {
        viewModelScope.launch {
            controller.set(
                AmbientControl(
                    mode = mode,
                    focusOnVoice = focusOnVoice,
                    ambientSound = ambientSound
                )
            )
        }
    }
}

data class AmbientSoundUiState(
    val mode: AmbientControl.Mode = AmbientControl.Mode.OFF,
    val focusOnVoice: Boolean = false,
    val ambientSound: Int = 0,
)
