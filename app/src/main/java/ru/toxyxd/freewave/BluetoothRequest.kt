package ru.toxyxd.freewave

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothRequest(modifier: Modifier, onPermissionGranted: () -> Unit) {
    val state = rememberPermissionState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.Manifest.permission.BLUETOOTH_CONNECT
        else android.Manifest.permission.BLUETOOTH
    )
        when {
            state.status.isGranted -> onPermissionGranted()
            else -> {
                LaunchedEffect(state) {
                    state.launchPermissionRequest()
                }
            }
        }
}