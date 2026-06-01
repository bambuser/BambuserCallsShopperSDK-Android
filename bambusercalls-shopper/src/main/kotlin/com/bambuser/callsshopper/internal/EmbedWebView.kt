package com.bambuser.callsshopper.internal

import android.annotation.SuppressLint
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.bambuser.callsshopper.BambuserCallConfiguration

/**
 * Compose wrapper around `android.webkit.WebView` that hosts the embed
 * HTML and wires the `BambuserAndroidBridge` JS interface.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun EmbedWebView(
    html: String,
    baseUrl: String?,
    configuration: BambuserCallConfiguration,
    onNativeMessage: (String) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    configuration.customUserAgent?.let { userAgentString = it }
                }

                // Auto-grant camera / mic so the embed's video call can
                // start without an extra WebView-level prompt. The host
                // app must have already secured CAMERA / RECORD_AUDIO at
                // the system level — otherwise this returns silently and
                // the embed fails.
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        request.grant(request.resources)
                    }
                }

                addJavascriptInterface(JsBridge(onNativeMessage), JsBridge.NAME)

                if (configuration.isInspectable) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }

                loadDataWithBaseURL(
                    baseUrl,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )

                onWebViewCreated(this)
            }
        },
    )
}
