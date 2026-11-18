package com.nexora.app.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nexora.app.ui.theme.NexoraColors

/**
 * Secure internal browser:
 * - JS enabled only inside the sandboxed WebView
 * - file access / content access DISABLED (untrusted-file protection)
 * - safe-browsing + https preferred, external intents limited (no app-hijack)
 * - credentials are NOT collected or exported (privacy command centre rule)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, onClose: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }

    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else onClose() }

    Column(Modifier.fillMaxSize().background(NexoraColors.Background)) {
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onClose) { Text("✕ Close", color = NexoraColors.TextSub) }
            Text(url, Modifier.weight(1f).padding(top = 14.dp), fontSize = 11.sp, color = NexoraColors.TextSub, maxLines = 1)
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.safeBrowsingMode = WebSettings.SAFE_BROWSING_ENABLED
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.userAgentString = settings.userAgentString + " NEXORA/1.0"
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val u = request.url
                            val scheme = u.scheme ?: ""
                            return when (scheme) {
                                "http", "https" -> false // render in-app
                                "mailto", "tel", "sms", "intent" -> try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, u)); true
                                } catch (e: Exception) { true }
                                else -> true // block unknown schemes (fail closed)
                            }
                        }
                        override fun onReceivedError(view: WebView, req: WebResourceRequest?, err: android.webkit.WebResourceError?) {
                            if (req?.isForMainFrame == true) failed = "This website could not be reached. Check the server status or try another server."
                        }
                    }
                    loadUrl(url)
                }.also { webView = it }
            },
            modifier = Modifier.weight(1f)
        )
        failed?.let { msg ->
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(msg, color = NexoraColors.Err, fontSize = 13.sp)
            }
        }
    }
}
