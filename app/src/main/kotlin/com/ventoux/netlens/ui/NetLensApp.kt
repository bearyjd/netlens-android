package com.ventoux.netlens.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.ventoux.netlens.navigation.NetLensNavHost

@Composable
fun NetLensApp() {
    val navController = rememberNavController()
    NetLensNavHost(navController = navController)
}
