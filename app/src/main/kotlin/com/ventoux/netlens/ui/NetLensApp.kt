package com.ventoux.netlens.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.rememberNavController
import com.ventoux.netlens.navigation.NetLensNavHost

@Composable
fun NetLensApp(
    pendingRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    NetLensNavHost(navController = navController)

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null && pendingRoute != "home") {
            navController.navigate(pendingRoute) { launchSingleTop = true }
            onRouteConsumed()
        }
    }
}
