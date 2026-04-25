package us.beary.netlens.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import us.beary.netlens.feature.dns.DnsLookupScreen
import us.beary.netlens.feature.history.HistoryScreen
import us.beary.netlens.feature.httptester.HttpTesterScreen
import us.beary.netlens.feature.ipinfo.IpInfoScreen
import us.beary.netlens.feature.lanscan.LanScanScreen
import us.beary.netlens.feature.mdns.MdnsScreen
import us.beary.netlens.feature.monitor.MonitorScreen
import us.beary.netlens.feature.netlog.NetLogScreen
import us.beary.netlens.feature.ping.PingScreen
import us.beary.netlens.feature.traceroute.TracerouteScreen
import us.beary.netlens.feature.portscan.PortScanScreen
import us.beary.netlens.feature.tls.TlsScreen
import us.beary.netlens.feature.whois.WhoisScreen
import us.beary.netlens.feature.widgetsettings.WidgetSettingsScreen
import us.beary.netlens.feature.wol.WolScreen
import us.beary.netlens.ui.home.HomeScreen

@Composable
fun NetLensNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier,
    ) {
        composable("home") {
            HomeScreen(
                onToolClick = { tool ->
                    navController.navigate(tool.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(ToolDestination.IpInfo.route) { IpInfoScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.LanScan.route) { LanScanScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.PortScan.route) { PortScanScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Dns.route) { DnsLookupScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Ping.route) { PingScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Traceroute.route) { TracerouteScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Wol.route) { WolScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Tls.route) { TlsScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Whois.route) { WhoisScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.HttpTester.route) { HttpTesterScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Mdns.route) { MdnsScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.NetLog.route) { NetLogScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Monitor.route) { MonitorScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.History.route) { HistoryScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.WidgetSettings.route) { WidgetSettingsScreen(onBack = navController::popBackStack) }
    }
}
