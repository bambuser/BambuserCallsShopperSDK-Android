package com.bambuser.callsshopper.internal

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Native side of the `BambuserAndroidBridge.postMessage(...)` channel
 * referenced by [EmbedHTMLBuilder]'s JS. Every message the embed posts
 * comes through here on a WebView worker thread; we hop to the main
 * thread before handing it to the router.
 */
internal class JsBridge(private val onMessage: (String) -> Unit) {

    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postMessage(raw: String) {
        // WebView invokes @JavascriptInterface methods on a private background
        // thread; bounce to the main thread so the controller's Compose state
        // mutations are safe.
        main.post { onMessage(raw) }
    }

    companion object {
        const val NAME = "BambuserAndroidBridge"
    }
}
