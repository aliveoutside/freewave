package ru.toxyxd.freewave

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import ru.toxyxd.freewave.screen.MainScreen

@Composable
fun NavigationHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(modifier = modifier, navController = navController, startDestination = NavRoutes.Main) {
        composable<NavRoutes.BluetoothRequest> {
            BluetoothRequest(modifier = Modifier,
                onPermissionGranted = {
                    navController.navigate(NavRoutes.Main)
                }
            )
        }
        composable<NavRoutes.Main> {
            MainScreen()
        }
    }
}

@Serializable
sealed class NavRoutes {
    @Serializable
    data object BluetoothRequest : NavRoutes()
    @Serializable
    data object Main : NavRoutes()
}