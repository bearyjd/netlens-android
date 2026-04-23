package us.beary.netlens.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import us.beary.netlens.navigation.NetLensNavHost

@Composable
fun NetLensApp() {
    val navController = rememberNavController()
    NetLensNavHost(navController = navController)
}
