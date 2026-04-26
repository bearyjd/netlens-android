package com.ventoux.netlens.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ventoux.netlens.feature.dns.DnsLookupScreen
import com.ventoux.netlens.feature.history.HistoryScreen
import com.ventoux.netlens.feature.httptester.HttpTesterScreen
import com.ventoux.netlens.feature.ipinfo.IpInfoScreen
import com.ventoux.netlens.feature.posture.PostureScreen
import com.ventoux.netlens.feature.lanscan.LanScanScreen
import com.ventoux.netlens.feature.mdns.MdnsScreen
import com.ventoux.netlens.feature.monitor.MonitorScreen
import com.ventoux.netlens.feature.netlog.NetLogScreen
import com.ventoux.netlens.feature.ping.PingScreen
import com.ventoux.netlens.feature.traceroute.TracerouteScreen
import com.ventoux.netlens.feature.portscan.PortScanScreen
import com.ventoux.netlens.feature.tls.TlsScreen
import com.ventoux.netlens.feature.whois.WhoisScreen
import com.ventoux.netlens.feature.widgetsettings.WidgetSettingsScreen
import com.ventoux.netlens.feature.wol.WolScreen
import com.ventoux.netlens.ui.home.HomeScreen

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
        composable(ToolDestination.Posture.route) { PostureScreen(onBack = navController::popBackStack) }
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
