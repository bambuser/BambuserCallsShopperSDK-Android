package com.bambuser.callsshopper

import android.util.Log
import android.webkit.WebView
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the embed lifecycle, observable state, and the JS bridge.
 * Host code typically holds one at the app / activity level, fills
 * in a [BambuserCallHandlers] with the closures it wants, drops a
 * `BambuserCallOverlay(controller = ...)` into its Compose tree, and
 * calls [show].
 *
 * The handler set is snapshotted at [show]; the SDK only wires the
 * JS `.on(...)` subscriptions for events that have a non-nil
 * handler, so unsubscribed events never cross the bridge.
 *
 * Mirrors the iOS `BambuserCallController` class 1:1.
 */
@Stable
class BambuserCallController(
    val configuration: BambuserCallConfiguration,
) {
    private val tag = "BambuserCall"

    // MARK: - Observable state

    var isVisible: Boolean by mutableStateOf(false); private set
    var isPiP: Boolean by mutableStateOf(false); private set
    var pipPresentation: PipPresentation by mutableStateOf(PipPresentation.Floating); private set
    var orgId: String? by mutableStateOf(null); private set
    var callState: CallState by mutableStateOf(CallState.Idle); private set
    var connectId: String? by mutableStateOf(null); private set
    var queue: String? by mutableStateOf(null); private set

    var environment: BambuserEnvironment by mutableStateOf(configuration.environment)

    /** Convenience — the embed script URL derived from [environment]. */
    internal val embedUrl: String get() = environment.embedUrl

    val isCallActive: Boolean
        get() = callState == CallState.Connecting || callState == CallState.Connected

    // MARK: - Handlers + delegate

    /**
     * Data-source closures. Frozen at [show] time — reassigning
     * mid-session logs a warning and takes effect on the next
     * [show].
     */
    var handlers: BambuserCallHandlers = BambuserCallHandlers()
        set(value) {
            field = value
            if (isVisible) {
                Log.w(tag, "handlers changed during an active session — changes take effect at next show()")
            }
        }

    /**
     * Fire-and-forget event stream. Assign a [BambuserCallDelegate]
     * to hear about widget lifecycle, queue status, checkout, chat,
     * navigation, tracking, and errors.
     *
     * Kotlin doesn't have `weak var` — hosts hold their own strong
     * reference and set this to `null` when releasing.
     */
    var delegate: BambuserCallDelegate? = null

    /** Frozen snapshot used by the router. Same value as [handlers] at [show]. */
    internal var activeHandlers: BambuserCallHandlers = BambuserCallHandlers()
        private set

    /** Which JS subscriptions the current HTML installed. Frozen at [show]. */
    internal var activeSubscriptions: BambuserCallSubscriptions =
        BambuserCallSubscriptions.from(BambuserCallHandlers(), forwardUnknownEmbedEvents = false)
        private set

    // MARK: - Wiring internals

    private var webView: WebView? = null

    // MARK: - Lifecycle

    /**
     * Mount the overlay and start an expert-chat session.
     * Freezes the current [handlers] snapshot for this session.
     */
    fun show(
        orgId: String? = null,
        connectId: String? = null,
        queue: String? = null,
    ) {
        this.orgId = orgId ?: configuration.orgId
        this.connectId = connectId
        this.queue = queue

        // Freeze the handler snapshot & derived subscription mask.
        activeHandlers = handlers
        activeSubscriptions = BambuserCallSubscriptions.from(
            handlers = handlers,
            forwardUnknownEmbedEvents = configuration.forwardUnknownEmbedEvents,
        )

        isVisible = true
        isPiP = false
    }

    fun close() {
        isVisible = false
        isPiP = false
        orgId = null
        connectId = null
        queue = null
        callState = CallState.Idle
        cancelPendingAsyncInvokes("call closed")
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

    // MARK: - Outbound (host → embed)

    /** Echo a host-side product navigation back into the embed. No-op when no call is active. */
    fun notifyProductNavigation(externalId: String) {
        if (!isCallActive) {
            Log.d(tag, "skipping notifyProductNavigation('$externalId') — call inactive (state=$callState)")
            return
        }
        val escaped = jsEscape(externalId)
        evaluate("window.notifyBambuserProductNavigation('$escaped')", "notifyProductNavigation('$externalId')")
    }

    // MARK: - Generic invoke (fire-and-forget)

    /**
     * Call a method on the live `oneToOneEmbed`, optionally appending
     * a builder callback derived from a [BambuserFactorySpec].
     */
    fun invoke(
        embedMethod: String,
        primaryArgs: List<BambuserJSONValue> = emptyList(),
        factorySpec: BambuserFactorySpec? = null,
    ) {
        if (webView == null) {
            Log.d(tag, "invoke(embedMethod: $embedMethod) — webView not ready")
            return
        }
        val argsJson = BambuserJSONValue.Arr(primaryArgs).toJsonString()
        val specJson = factorySpec?.toJsonString() ?: "null"
        val name = jsEscape(embedMethod)
        evaluate(
            "window.__bambuserInvokeEmbedMethod('$name', $argsJson, $specJson);",
            "invoke(embedMethod: $embedMethod)",
        )
    }

    /** Reply to a live callback the embed handed us. */
    fun invoke(
        callback: String,
        factorySpec: BambuserFactorySpec,
        deleteAfterUse: Boolean = true,
    ) {
        if (webView == null) {
            Log.d(tag, "invoke(callback: $callback) — webView not ready")
            return
        }
        val specJson = factorySpec.toJsonString()
        val key = jsEscape(callback)
        evaluate(
            "window.__bambuserApplyToCallback('$key', $specJson, ${deleteAfterUse});",
            "invoke(callback: $callback)",
        )
    }

    /**
     * Async variant for embed methods that return a Promise (queue
     * queries etc.). Awaits the JS-side resolution via a native ↔ web
     * response channel keyed by request id.
     */
    suspend fun invokeAsync(
        embedMethod: String,
        primaryArgs: List<BambuserJSONValue> = emptyList(),
    ): BambuserJSONValue = suspendCancellableCoroutine { cont ->
        if (webView == null) {
            cont.resumeWithException(
                BambuserCallError.JavaScriptEvaluationFailed(
                    "invokeAsync($embedMethod)",
                    IllegalStateException("webView not ready"),
                )
            )
            return@suspendCancellableCoroutine
        }
        asyncInvokeSeq += 1
        val requestId = "async_${asyncInvokeSeq}"
        pendingAsyncInvokes[requestId] = cont

        val argsJson = BambuserJSONValue.Arr(primaryArgs).toJsonString()
        val name = jsEscape(embedMethod)
        val id = jsEscape(requestId)
        evaluate(
            "window.__bambuserInvokeEmbedAsync('$id', '$name', $argsJson);",
            "invokeAsync($embedMethod)",
        ) { evalError ->
            if (evalError != null) {
                pendingAsyncInvokes.remove(requestId)?.resumeWithException(
                    BambuserCallError.JavaScriptEvaluationFailed("invokeAsync($embedMethod)", evalError)
                )
            }
        }

        cont.invokeOnCancellation {
            pendingAsyncInvokes.remove(requestId)
        }
    }

    // MARK: - Product / search convenience

    fun provideProductData(bambuserId: String, factorySpec: BambuserFactorySpec) {
        invoke(
            embedMethod = "updateProduct",
            primaryArgs = listOf(BambuserJSONValue.Str(bambuserId)),
            factorySpec = factorySpec,
        )
    }

    /**
     * Reply to `provide-product-data` with an error the agent sees.
     * Dispatches `updateProduct(id, () => { throw new Error(message) })` —
     * the documented "we don't sell that" pattern.
     */
    fun provideProductError(bambuserId: String, message: String) {
        invokeEmbedMethodWithError(
            embedMethod = "updateProduct",
            primaryArgs = listOf(BambuserJSONValue.Str(bambuserId)),
            errorMessage = message,
        )
    }

    fun provideSearchData(callbackKey: String, factorySpec: BambuserFactorySpec) {
        invoke(callback = callbackKey, factorySpec = factorySpec)
    }

    /** Reply to `provide-search-data` with an error — same throw-inside-callback pattern. */
    fun provideSearchError(callbackKey: String, message: String) {
        applyErrorToCallback(callbackKey = callbackKey, errorMessage = message)
    }

    // MARK: - Shopper activity, customer data, tracking tags

    fun notifyCustomerEvent(eventKey: String, payload: BambuserJSONValue) {
        if (webView == null) {
            Log.d(tag, "notifyCustomerEvent($eventKey) — webView not ready")
            return
        }
        val payloadJson = payload.toJsonString()
        val key = jsEscape(eventKey)
        evaluate(
            "window.__bambuserNotifyCustomerEvent('$key', $payloadJson);",
            "notifyCustomerEvent($eventKey)",
        )
    }

    fun updateData(data: BambuserJSONValue? = null) {
        if (data == null) {
            invoke(embedMethod = "updateData")
        } else {
            invoke(embedMethod = "updateData", primaryArgs = listOf(data))
        }
    }

    fun setTrackingTags(tags: List<BambuserTrackingTag>) {
        invoke(embedMethod = "setTrackingTags", primaryArgs = listOf(tags.toJsonArray()))
    }

    // MARK: - Queue

    /**
     * Update the queue for the active call. Named `updateQueue`
     * rather than `setQueue` so the JVM setter for the [queue]
     * observable property doesn't collide with the method.
     */
    fun updateQueue(queueTerm: String) {
        invoke(embedMethod = "setQueue", primaryArgs = listOf(BambuserJSONValue.Str(queueTerm)))
        this.queue = queueTerm
    }

    suspend fun areQueuesOpen(): BambuserQueueOpenState =
        decodeQueueOpenState(invokeAsync("areQueuesOpen"))

    suspend fun getNumberOfOnlineAgents(queue: String? = null): BambuserAgentsOnline {
        val args = queue?.let { listOf(BambuserJSONValue.Str(it)) } ?: emptyList()
        return decodeAgentsOnline(invokeAsync("getNumberOfOnlineAgents", args))
    }

    suspend fun getEstimatedWaitingTime(queue: String? = null): BambuserQueueWaitingTime {
        val args = queue?.let { listOf(BambuserJSONValue.Str(it)) } ?: emptyList()
        return decodeWaitingTime(invokeAsync("getEstimatedWaitingTime", args))
    }

    // MARK: - Widget control

    fun destroy() {
        invoke(embedMethod = "destroy")
        close()
    }

    fun floatAbove(url: String? = null) {
        val args = url?.let { listOf(BambuserJSONValue.Str(it)) } ?: emptyList()
        invoke(embedMethod = "floatAbove", primaryArgs = args)
    }

    // MARK: - Custom elements

    fun updateElement(element: BambuserElement, state: BambuserElementState?) {
        val payload = state?.toJsonValue() ?: BambuserJSONValue.Null
        invoke(
            embedMethod = "updateElement",
            primaryArgs = listOf(BambuserJSONValue.Str(element.rawValue), payload),
        )
    }

    // MARK: - Raw callback (cart, etc.)

    /**
     * Call a JS function registered on `window` by name, passing
     * [info] inline. Caller shapes [info] as a JS expression — string
     * literal, number, JSON object, etc.
     */
    fun notify(callbackKey: String, info: String) {
        val key = jsEscape(callbackKey)
        val js = """
            try {
              window['$key']($info);
            } catch (err) {
              throw err;
            }
        """.trimIndent()
        evaluate(js, "notify($callbackKey)")
    }

    // MARK: - Internal hooks (called by the router / WebView)

    internal fun attach(webView: WebView) {
        this.webView = webView
    }

    internal fun setCallState(newState: CallState) {
        if (callState == newState) return
        Log.d(tag, "callState: $callState → $newState")
        if (newState == CallState.Ended) {
            if (configuration.dismissOnCallEnd) close()
            else callState = newState
            emitEvent(BambuserCallEvent.CallStateChanged(CallState.Ended))
            return
        }
        callState = newState
        if (newState == CallState.Connected && isPiP && configuration.autoExpandOnConnect) {
            expand()
        }
        emitEvent(BambuserCallEvent.CallStateChanged(newState))
    }

    internal fun emitEvent(event: BambuserCallEvent) {
        delegate?.onEmit(this, event)
    }

    internal fun reportError(error: BambuserCallError) {
        delegate?.onError(this, error)
    }

    private fun emitPresentationChange() {
        emitEvent(BambuserCallEvent.PresentationChanged(isPiP = isPiP, presentation = pipPresentation))
    }

    // MARK: - Error-throwing JS helpers (used by provideProductError / provideSearchError)

    private fun invokeEmbedMethodWithError(
        embedMethod: String,
        primaryArgs: List<BambuserJSONValue>,
        errorMessage: String,
    ) {
        if (webView == null) {
            Log.d(tag, "invokeEmbedMethodWithError($embedMethod) — webView not ready")
            return
        }
        val argsJson = BambuserJSONValue.Arr(primaryArgs).toJsonString()
        val name = jsEscape(embedMethod)
        val msg = jsEscape(errorMessage)
        evaluate(
            "window.__bambuserInvokeEmbedMethodWithError('$name', $argsJson, '$msg');",
            "invokeEmbedMethodWithError($embedMethod)",
        )
    }

    private fun applyErrorToCallback(
        callbackKey: String,
        errorMessage: String,
        deleteAfterUse: Boolean = true,
    ) {
        if (webView == null) {
            Log.d(tag, "applyErrorToCallback($callbackKey) — webView not ready")
            return
        }
        val key = jsEscape(callbackKey)
        val msg = jsEscape(errorMessage)
        evaluate(
            "window.__bambuserApplyToCallbackWithError('$key', '$msg', ${deleteAfterUse});",
            "applyErrorToCallback($callbackKey)",
        )
    }

    // MARK: - Async invoke plumbing

    private var asyncInvokeSeq: Long = 0
    private val pendingAsyncInvokes: MutableMap<String, Continuation<BambuserJSONValue>> = mutableMapOf()

    /** Called by the router when a `__async-response` event arrives. */
    internal fun resolveAsyncInvoke(payload: BambuserJSONValue) {
        val obj = payload as? BambuserJSONValue.Obj ?: return
        val requestId = (obj.entries["requestId"] as? BambuserJSONValue.Str)?.value ?: return
        val cont = pendingAsyncInvokes.remove(requestId) ?: return
        val ok = (obj.entries["ok"] as? BambuserJSONValue.Bool)?.value ?: false
        if (ok) {
            cont.resume(obj.entries["value"] ?: BambuserJSONValue.Null)
        } else {
            val message = (obj.entries["error"] as? BambuserJSONValue.Str)?.value ?: "unknown error"
            cont.resumeWithException(
                BambuserCallError.JavaScriptEvaluationFailed(
                    "invokeAsync",
                    RuntimeException(message),
                )
            )
        }
    }

    private fun cancelPendingAsyncInvokes(reason: String) {
        if (pendingAsyncInvokes.isEmpty()) return
        val dead = pendingAsyncInvokes.toMap()
        pendingAsyncInvokes.clear()
        val err = BambuserCallError.JavaScriptEvaluationFailed(
            "invokeAsync",
            RuntimeException(reason),
        )
        dead.values.forEach { it.resumeWithException(err) }
    }

    // MARK: - JS helpers

    /**
     * Evaluate JS on the WebView's UI thread. [onResult] receives the
     * error (if any) once the WebView finishes the eval — mirrors iOS's
     * `evaluateJavaScript` completion.
     */
    private fun evaluate(
        js: String,
        operation: String,
        onResult: ((Throwable?) -> Unit)? = null,
    ) {
        val wv = webView
        if (wv == null) {
            onResult?.invoke(IllegalStateException("webView not ready"))
            return
        }
        wv.post {
            try {
                wv.evaluateJavascript(js) { _ -> onResult?.invoke(null) }
            } catch (t: Throwable) {
                Log.w(tag, "$operation failed: $t")
                reportError(BambuserCallError.JavaScriptEvaluationFailed(operation, t))
                onResult?.invoke(t)
            }
        }
    }

    private fun jsEscape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
}

// MARK: - Payload decoding helpers

internal fun decodeQueueOpenState(value: BambuserJSONValue): BambuserQueueOpenState {
    val obj = value as? BambuserJSONValue.Obj
        ?: return BambuserQueueOpenState(isOpen = false)
    val isOpen = (obj.entries["isOpen"] as? BambuserJSONValue.Bool)?.value ?: false
    return BambuserQueueOpenState(
        isOpen = isOpen,
        nextOpenTime  = (obj.entries["nextOpenTime"]  as? BambuserJSONValue.Str)?.value,
        nextCloseTime = (obj.entries["nextCloseTime"] as? BambuserJSONValue.Str)?.value,
        raw = value,
    )
}

internal fun decodeAgentsOnline(value: BambuserJSONValue): BambuserAgentsOnline {
    val obj = value as? BambuserJSONValue.Obj
        ?: return BambuserAgentsOnline(numberOfAgentsOnline = 0)
    val n = when (val v = obj.entries["numberOfAgentsOnline"]) {
        is BambuserJSONValue.Int    -> v.value
        is BambuserJSONValue.Long   -> v.value.toInt()
        is BambuserJSONValue.Double -> v.value.toInt()
        else -> 0
    }
    return BambuserAgentsOnline(
        numberOfAgentsOnline = n,
        queueId = (obj.entries["queueId"] as? BambuserJSONValue.Str)?.value,
        raw = value,
    )
}

internal fun decodeWaitingTime(value: BambuserJSONValue): BambuserQueueWaitingTime {
    val obj = value as? BambuserJSONValue.Obj
        ?: return BambuserQueueWaitingTime(estimatedWaitingTime = 0, agents = 0, place = 0)

    fun intField(key: String): Int = when (val v = obj.entries[key]) {
        is BambuserJSONValue.Int    -> v.value
        is BambuserJSONValue.Long   -> v.value.toInt()
        is BambuserJSONValue.Double -> v.value.toInt()
        else -> 0
    }
    return BambuserQueueWaitingTime(
        estimatedWaitingTime = intField("estimatedWaitingTime"),
        agents = intField("agents"),
        place  = intField("place"),
        queueId = (obj.entries["queueId"] as? BambuserJSONValue.Str)?.value,
        raw = value,
    )
}
