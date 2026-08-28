package com.bambuser.callsshopper.internal

import android.webkit.JavascriptInterface

/**
 * The one JS-visible object. `window.__bambuserAndroidBridge.postMessage(json)`
 * on the JS side flows through here on a WebView background thread.
 *
 * The [onMessage] callback receives the raw JSON string as posted by
 * the embed. Parsing / dispatch happens in [EmbedEventRouter] on the
 * main thread — this class only marshals the string across the
 * bridge boundary.
 *
 * ProGuard: [JavascriptInterface] methods are kept via
 * `consumer-rules.pro`.
 */
internal class JsBridge(
    private val onMessage: (String) -> Unit,
) {

    @JavascriptInterface
    fun postMessage(json: String) {
        // Called from a WebView JS-thread. Dispatch to main happens
        // in the router; here we just forward the raw payload.
        onMessage(json)
    }

    companion object {
        /** Name exposed to JS: `window.__bambuserAndroidBridge`. */
        const val NAME = "__bambuserAndroidBridge"
    }
}
