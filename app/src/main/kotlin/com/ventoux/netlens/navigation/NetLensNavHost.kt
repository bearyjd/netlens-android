package com.ventoux.netlens.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
        composable(
            route = "${ToolDestination.LanScan.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            LanScanScreen(
                onBack = navController::popBackStack,
                initialCidr = entry.arguments?.getString("query")?.ifEmpty { null },
            )
        }
        composable(
            route = "${ToolDestination.PortScan.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            PortScanScreen(
                onBack = navController::popBackStack,
                initialHost = entry.arguments?.getString("query")?.ifEmpty { null },
            )
        }
        composable(
            route = "${ToolDestination.Dns.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            DnsLookupScreen(
                onBack = navController::popBackStack,
                initialDomain = entry.arguments?.getString("query")?.ifEmpty { null },
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
        composable(ToolDestination.Traceroute.route) { TracerouteScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Wol.route) { WolScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Tls.route) { TlsScreen(onBack = navController::popBackStack) }
        composable(
            route = "${ToolDestination.Whois.route}?query={query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            WhoisScreen(
                onBack = navController::popBackStack,
                initialQuery = entry.arguments?.getString("query")?.ifEmpty { null },
            )
        }
        composable(ToolDestination.HttpTester.route) { HttpTesterScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Mdns.route) { MdnsScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.NetLog.route) { NetLogScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.Monitor.route) { MonitorScreen(onBack = navController::popBackStack) }
        composable(ToolDestination.History.route) {
            HistoryScreen(
                onBack = navController::popBackStack,
                onNavigateToTool = { route, query ->
                    val encoded = Uri.encode(query)
                    navController.navigate("$route?query=$encoded") { launchSingleTop = true }
                },
            )
        }
        composable(ToolDestination.WidgetSettings.route) { WidgetSettingsScreen(onBack = navController::popBackStack) }
    }
}
