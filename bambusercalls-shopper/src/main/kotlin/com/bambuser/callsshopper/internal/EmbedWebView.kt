package com.bambuser.callsshopper.internal

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.bambuser.callsshopper.BambuserCallConfiguration

private const val TAG = "BambuserCall.WebView"

/**
 * Compose wrapper around a Chromium `WebView`. Mounts once per
 * `show()` cycle; recomposes with a stable identity so the WebView
 * itself isn't re-created on parent-recomposition.
 *
 * The WebView is aggressively configured for the Bambuser embed:
 *   - JavaScript enabled
 *   - Media playback without user gesture
 *   - DOM storage + third-party cookies (embed's session flow)
 *   - Auto-grant camera / mic on `onPermissionRequest`
 *   - `onCreateWindow` handled inline so consent popups stay in the
 *     same WebView instead of falling through to a browser intent
 *   - JS console lines surface to logcat at `TAG` for debugging
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
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = {
            WebView(context).apply {
                Log.d(TAG, "factory: creating WebView")

                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(true)
                    configuration.customUserAgent?.let { userAgentString = it }
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                if (configuration.isInspectable) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }

                // Bridge: `window.__bambuserAndroidBridge.postMessage(json)`
                // in JS lands in JsBridge, which hops to Main for the
                // router.
                val handler = Handler(Looper.getMainLooper())
                val bridge = JsBridge { message ->
                    handler.post { onNativeMessage(message) }
                }
                addJavascriptInterface(bridge, JsBridge.NAME)

                webChromeClient = object : WebChromeClient() {

                    override fun onPermissionRequest(request: PermissionRequest) {
                        Log.d(TAG, "onPermissionRequest: ${request.resources.joinToString()}")
                        // Auto-grant. Host app must already hold the
                        // system-level CAMERA / RECORD_AUDIO permissions —
                        // WebView-level grant only forwards to that.
                        request.grant(request.resources)
                    }

                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?,
                    ): Boolean {
                        val parent = view ?: return false
                        val child = WebView(parent.context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                        }
                        parent.addView(
                            child,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        )
                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                        transport?.webView = child
                        resultMsg?.sendToTarget()
                        Log.d(TAG, "onCreateWindow: opened child WebView")
                        return true
                    }

                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        val level = when (message.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR   -> "E"
                            ConsoleMessage.MessageLevel.WARNING -> "W"
                            ConsoleMessage.MessageLevel.DEBUG   -> "D"
                            else                                -> "I"
                        }
                        Log.d(TAG, "JS[$level] ${message.sourceId()}:${message.lineNumber()} ${message.message()}")
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(TAG, "onPageFinished: $url")
                        view?.evaluateJavascript("(function(){return typeof __bambuserAndroidBridge;})();") { r ->
                            Log.d(TAG, "__bambuserAndroidBridge typeof = $r")
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        Log.w(TAG, "onReceivedError: ${error?.errorCode} ${error?.description} for ${request?.url}")
                    }
                }

                // Document-start shim. Runs before the embed's async
                // script, so PiP iframe-messages and legacy
                // getUserMedia are covered from the very first frame.
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    val shim = EmbedHTMLBuilder.makeDocumentStartShim(
                        injectsLegacyGetUserMediaShim = configuration.injectsLegacyGetUserMediaShim
                    )
                    val origin = baseUrl?.let { setOf(it) } ?: setOf("*")
                    WebViewCompat.addDocumentStartJavaScript(this, shim, origin)
                }

                loadDataWithBaseURL(
                    baseUrl,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )

                onWebViewCreated(this)
                Log.d(TAG, "factory: WebView ready, handed to controller.attach")
            }
        },
    )
}
