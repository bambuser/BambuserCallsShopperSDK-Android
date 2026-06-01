package com.bambuser.callsshopper

import android.util.Log
import android.webkit.WebView
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.ref.WeakReference

/**
 * Owns the embed lifecycle, observable Compose state, and the JS bridge.
 * Host code typically holds one in a `ViewModel` or `remember { }`, drops
 * a [BambuserCallOverlay] in its composition, and assigns a
 * [BambuserCallDelegate].
 */
@Stable
class BambuserCallController(val configuration: BambuserCallConfiguration) {

    // Observable state — Compose recomposes consumers automatically.
    var isVisible: Boolean by mutableStateOf(false); private set
    var isPiP: Boolean by mutableStateOf(false); private set
    var pipPresentation: PipPresentation by mutableStateOf(PipPresentation.Floating); private set
    var orgId: String? by mutableStateOf(null); private set
    var callState: CallState by mutableStateOf(CallState.Idle); private set
    var connectId: String? by mutableStateOf(null); private set
    var queue: String? by mutableStateOf(null); private set
    var embedUrl: String by mutableStateOf(configuration.embedUrl)

    var delegate: BambuserCallDelegate? = null

    val isCallActive: Boolean
        get() = callState == CallState.Connecting || callState == CallState.Connected

    private var webViewRef: WeakReference<WebView>? = null

    // MARK: - Lifecycle

    /**
     * Mount the overlay and start an expert-chat session.
     *
     * @param orgId Optional override for `configuration.orgId`.
     * @param embedUrl Optional override for `configuration.embedUrl`.
     * @param connectId Optional Bambuser connect id for routing. Passed
     *   verbatim to the embed when set; omitted from the JS config when
     *   `null`.
     * @param queue Optional Bambuser queue name/id. Same nil-safe behavior
     *   as [connectId].
     */
    fun show(
        orgId: String? = null,
        embedUrl: String? = null,
        connectId: String? = null,
        queue: String? = null,
    ) {
        embedUrl?.let { this.embedUrl = it }
        this.orgId = orgId ?: configuration.orgId
        this.connectId = connectId
        this.queue = queue
        this.isVisible = true
        this.isPiP = false
    }

    fun close() {
        isVisible = false
        isPiP = false
        orgId = null
        connectId = null
        queue = null
        callState = CallState.Idle
    }

    fun expand() {
        val wasPiP = isPiP
        isPiP = false
        if (wasPiP) emitPresentationChange()
    }

    fun enterPiP(presentation: PipPresentation = PipPresentation.Floating) {
        val changed = !isPiP || pipPresentation != presentation
        pipPresentation = presentation
        isPiP = true
        if (changed) emitPresentationChange()
    }

    // MARK: - Outbound bridge

    /** Echo a host-side product navigation back into the embed. No-op when no call is active. */
    fun notifyProductNavigation(externalId: String) {
        if (!isCallActive) {
            Log.d(TAG, "skipping notifyProductNavigation('$externalId') — call inactive (state=$callState)")
            return
        }
        val escaped = JSEscape.escape(externalId)
        val js = "window.notifyBambuserProductNavigation('$escaped')"
        evaluate(js, operation = "notifyProductNavigation('$externalId')")
    }

    /**
     * Call a JS function registered on `window` by name, passing [info]
     * inline. The caller shapes [info] as a valid JS expression — string
     * literal, number, object literal, etc. The value is interpolated
     * verbatim; no quoting is applied.
     */
    fun notify(callbackKey: String, info: Any) {
        val escapedKey = JSEscape.escape(callbackKey)
        val js = """
            try {
              window['$escapedKey']($info);
            } catch (err) {
              throw err;
            }
        """.trimIndent()
        evaluate(js, operation = "notify('$callbackKey')")
    }

    // MARK: - Internal

    internal fun setCallState(newState: CallState) {
        if (callState == newState) return
        Log.d(TAG, "callState: $callState → $newState")
        if (newState == CallState.Ended) {
            if (configuration.dismissOnCallEnd) {
                close()
            } else {
                callState = newState
            }
            emit(BambuserCallEvent.CallStateChanged(CallState.Ended))
            return
        }
        callState = newState
        if (newState == CallState.Connected && isPiP && configuration.autoExpandOnConnect) {
            expand()
        }
        emit(BambuserCallEvent.CallStateChanged(newState))
    }

    internal fun attach(webView: WebView) {
        webViewRef = WeakReference(webView)
    }

    internal fun emit(event: BambuserCallEvent) {
        delegate?.onEvent(this, event)
    }

    internal fun emit(error: BambuserCallError) {
        delegate?.onError(this, error)
    }

    private fun emitPresentationChange() {
        emit(BambuserCallEvent.PresentationChanged(isPiP = isPiP, presentation = pipPresentation))
    }

    private fun evaluate(js: String, operation: String) {
        val webView = webViewRef?.get() ?: return
        webView.post {
            webView.evaluateJavascript(js) { result ->
                // WebView only reports parse failures via the result string here;
                // execution exceptions surface in the WebChromeClient onConsoleMessage,
                // not via this callback. Logging the result is enough for diagnostics.
                if (result == null) {
                    val error = BambuserCallError.JavaScriptEvaluationFailed(
                        operation = operation,
                        underlying = null,
                    )
                    Log.w(TAG, "$operation returned null result")
                    emit(error)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BambuserCall"
    }
}

internal object JSEscape {
    fun escape(s: String): String =
        s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
}
