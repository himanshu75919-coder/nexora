package com.nexora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.nexora.app.data.api.AppCard
import com.nexora.app.ui.browser.WebViewScreen
import com.nexora.app.ui.dashboard.DashboardScreen
import com.nexora.app.ui.server.ServerSelectionSheet
import com.nexora.app.ui.theme.NexoraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexoraTheme {
                // Phase 1 navigation: dashboard -> server sheet -> internal browser
                var activeCard by remember { mutableStateOf<AppCard?>(null) }
                var openUrl by remember { mutableStateOf<String?>(null) }

                openUrl?.let { url ->
                    WebViewScreen(url, onClose = { openUrl = null })
                    return@setContent
                }
                activeCard?.let { card ->
                    ServerSelectionSheet(
                        card = card,
                        onOpen = { url -> openUrl = url },
                        onDismiss = { activeCard = null }
                    )
                }
                DashboardScreen(onOpenCard = { activeCard = it })
            }
        }
    }
}
