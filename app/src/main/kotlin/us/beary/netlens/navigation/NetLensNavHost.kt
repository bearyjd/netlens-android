package us.beary.netlens.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import us.beary.netlens.feature.dns.DnsLookupScreen
import us.beary.netlens.feature.ipinfo.IpInfoScreen
import us.beary.netlens.feature.lanscan.LanScanScreen
import us.beary.netlens.feature.ping.PingScreen
import us.beary.netlens.feature.portscan.PortScanScreen
import us.beary.netlens.feature.wol.WolScreen

@Composable
fun NetLensNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.IpInfo.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.IpInfo.route) { IpInfoScreen() }
        composable(TopLevelDestination.LanScan.route) { LanScanScreen() }
        composable(TopLevelDestination.PortScan.route) { PortScanScreen() }
        composable(TopLevelDestination.Dns.route) { DnsLookupScreen() }
        composable(TopLevelDestination.Ping.route) { PingScreen() }
        composable(TopLevelDestination.Wol.route) { WolScreen() }
    }
}
