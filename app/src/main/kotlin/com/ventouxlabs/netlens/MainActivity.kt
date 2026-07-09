package com.ventouxlabs.netlens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import com.ventouxlabs.netlens.core.billing.LocalProStatus
import com.ventouxlabs.netlens.core.billing.ProStatus
import com.ventouxlabs.netlens.navigation.resolveDeepLinkRoute
import com.ventouxlabs.netlens.ui.NetLensApp
import com.ventouxlabs.netlens.ui.theme.NetLensTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var proStatus: ProStatus

    private val viewModel: MainViewModel by viewModels()

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = intent?.resolveDeepLinkRoute()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalProStatus provides proStatus) {
                NetLensTheme(themeMode = themeMode) {
                    NetLensApp(
                        pendingRoute = pendingRoute,
                        onRouteConsumed = { pendingRoute = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.resolveDeepLinkRoute()
    }
}
