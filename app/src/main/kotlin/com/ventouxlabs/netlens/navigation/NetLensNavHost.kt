package com.ventouxlabs.netlens.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ventouxlabs.netlens.feature.dns.DnsLookupScreen
import com.ventouxlabs.netlens.feature.dnsleak.DnsLeakScreen
import com.ventouxlabs.netlens.feature.history.HistoryScreen
import com.ventouxlabs.netlens.feature.httptester.HttpTesterScreen
import com.ventouxlabs.netlens.feature.ipinfo.IpInfoScreen
import com.ventouxlabs.netlens.feature.posture.PostureScreen
import com.ventouxlabs.netlens.feature.posture.model.PostureFactor
import com.ventouxlabs.netlens.feature.speedtest.SpeedTestScreen
import com.ventouxlabs.netlens.feature.lanscan.LanScanScreen
import com.ventouxlabs.netlens.feature.mdns.MdnsScreen
import com.ventouxlabs.netlens.feature.monitor.MonitorScreen
import com.ventouxlabs.netlens.feature.netlog.NetLogScreen
import com.ventouxlabs.netlens.feature.ping.PingScreen
import com.ventouxlabs.netlens.feature.traceroute.TracerouteScreen
import com.ventouxlabs.netlens.feature.portscan.PortScanScreen
import com.ventouxlabs.netlens.feature.tls.TlsScreen
import com.ventouxlabs.netlens.feature.vpnstatus.VpnStatusScreen
import com.ventouxlabs.netlens.feature.ipcalc.IpCalcScreen
import com.ventouxlabs.netlens.feature.whois.WhoisScreen
import com.ventouxlabs.netlens.feature.widgetsettings.WidgetSettingsScreen
import com.ventouxlabs.netlens.feature.wifi.WifiScreen
import com.ventouxlabs.netlens.feature.wifiaudit.WifiAuditScreen
import com.ventouxlabs.netlens.feature.celltower.CellTowerScreen
import com.ventouxlabs.netlens.feature.wol.WolScreen
import com.ventouxlabs.netlens.ui.home.HomeScreen
import com.ventouxlabs.netlens.ui.settings.SettingsScreen

@Composable
fun NetLensNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val navigateToTool: (String, String) -> Unit = { route, query ->
        val encoded = Uri.encode(query)
        navController.navigate("$route?query=$encoded") { launchSingleTop = true }
    }

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
                onSettingsClick = {
                    navController.navigate(ToolDestination.Settings.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(ToolDestination.Settings.route) {
            SettingsScreen(
                onBack = navController::popBackStack,
                onOpenWidgetSettings = {
                    navController.navigate(ToolDestination.WidgetSettings.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(ToolDestination.Posture.route) {
            PostureScreen(
                onBack = navController::popBackStack,
                onFactorClick = { factor ->
                    val route = when (factor) {
                        PostureFactor.Encryption -> ToolDestination.WifiAnalyzer.route
                        PostureFactor.DeviceCount -> ToolDestination.LanScan.route
                        PostureFactor.VpnStatus -> ToolDestination.VpnStatus.route
                    }
                    route?.let { navController.navigate(it) { launchSingleTop = true } }
                },
            )
        }
        composable(ToolDestination.VpnStatus.route) {
            VpnStatusScreen(
                onBack = navController::popBackStack,
                onOpenDnsLeak = {
                    navController.navigate(ToolDestination.DnsLeak.route) { launchSingleTop = true }
                },
            )
        }
        composable(ToolDestination.DnsLeak.route) { DnsLeakScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.IpInfo.route) { IpInfoScreen(onBack = navController::popBackStack, onNavigateToTool = navigateToTool) }
        composable(
            route = "${ToolDestination.LanScan.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            LanScanScreen(
                onBack = navController::popBackStack,
                initialCidr = entry.arguments?.getString("query")?.ifEmpty { null },
                onNavigateToTool = navigateToTool,
            )
        }
        composable(
            route = "${ToolDestination.PortScan.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            PortScanScreen(
                onBack = navController::popBackStack,
                initialHost = entry.arguments?.getString("query")?.ifEmpty { null },
                onNavigateToTool = navigateToTool,
            )
        }
        composable(
            route = "${ToolDestination.Dns.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            DnsLookupScreen(
                onBack = navController::popBackStack,
                initialDomain = entry.arguments?.getString("query")?.ifEmpty { null },
                onNavigateToTool = navigateToTool,
            )
        }
        composable(
            route = "${ToolDestination.Ping.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            PingScreen(
                onBack = navController::popBackStack,
                initialHost = entry.arguments?.getString("query")?.ifEmpty { null },
            )
        }
        composable(
            route = "${ToolDestination.Traceroute.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            TracerouteScreen(
                onBack = navController::popBackStack,
                initialHost = entry.arguments?.getString("query")?.ifEmpty { null },
                onNavigateToTool = navigateToTool,
            )
        }
        composable(ToolDestination.Wol.route) { WolScreen(onBack = navController::popBackStack) }
        composable(
            route = "${ToolDestination.Tls.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            TlsScreen(
                onBack = navController::popBackStack,
                initialHost = entry.arguments?.getString("query")?.ifEmpty { null },
                onNavigateToTool = navigateToTool,
            )
        }
        composable(
            route = "${ToolDestination.Whois.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            WhoisScreen(
                onBack = navController::popBackStack,
                initialQuery = entry.arguments?.getString("query")?.ifEmpty { null },
                onNavigateToTool = navigateToTool,
            )
        }
        composable(
            route = "${ToolDestination.HttpTester.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            HttpTesterScreen(
                onBack = navController::popBackStack,
                initialUrl = entry.arguments?.getString("query")?.ifEmpty { null },
            )
        }
        composable(
            route = "${ToolDestination.IpCalc.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            IpCalcScreen(
                onBack = navController::popBackStack,
                initialInput = entry.arguments?.getString("query")?.ifEmpty { null },
            )
        }
        composable(ToolDestination.Mdns.route) { MdnsScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.SpeedTest.route) { SpeedTestScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.WifiAnalyzer.route) { WifiScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.WifiAudit.route) { WifiAuditScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.CellTower.route) { CellTowerScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.NetLog.route) { NetLogScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Monitor.route) { MonitorScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.History.route) {
            HistoryScreen(
                onBack = navController::popBackStack,
                onNavigateToTool = navigateToTool,
            )
        }
        composable(ToolDestination.WidgetSettings.route) { WidgetSettingsScreen(onBack = navController::popBackStack) }
    }
}
