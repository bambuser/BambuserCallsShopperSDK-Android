package com.bambuser.callsshopper.internal

import com.bambuser.callsshopper.BambuserCallController
import com.bambuser.callsshopper.BambuserCallError
import com.bambuser.callsshopper.BambuserCallEvent
import com.bambuser.callsshopper.CallState
import com.bambuser.callsshopper.PipPresentation
import org.json.JSONException
import org.json.JSONObject

internal class EmbedEventRouter(private val controller: BambuserCallController) {

    fun handle(rawMessage: String) {
        val json = parseJson(rawMessage) ?: return
        val event = json.optString("event").takeIf { it.isNotEmpty() } ?: return

        when (event) {
            "close" -> {
                controller.close()
                emit(BambuserCallEvent.Close)
            }
            "goto-checkout" -> {
                val cart = json.opt("payload") as? JSONObject
                emit(BambuserCallEvent.Checkout(cart = cart?.toMap()))
            }

            // Call lifecycle — match permissively so a small SDK rename
            // doesn't silently break gating.
            "call-started", "call-starting", "call-connecting", "call-ringing" ->
                controller.setCallState(CallState.Connecting)

            "call-connected", "call-active", "call-accepted", "connected" ->
                controller.setCallState(CallState.Connected)

            "call-ended", "call-end", "call-disconnected", "call-rejected",
            "disconnected", "ended" ->
                controller.setCallState(CallState.Ended)

            "should-add-item-to-cart" -> handleCartIntent(json.opt("payload"), event, add = true)
            "should-update-item-in-cart" -> handleCartIntent(json.opt("payload"), event, add = false)

            "iframe-message" -> handleIframeMessage(json.optString("detail", ""))

            else -> emit(BambuserCallEvent.Other(name = event, payload = json.opt("payload")))
        }
    }

    private fun handleIframeMessage(detail: String) {
        if (detail.isEmpty()) return
        val json = parseJson(detail) ?: return
        when (val type = json.optString("eventType")) {
            "viddget:viewport_mode" -> {
                val mode = json.optString("data")
                when {
                    mode == "normal" || mode == "centerModal" -> controller.expand()
                    mode == "floatingLeftMinimized" || mode == "floatingRightMinimized" ->
                        controller.enterPiP(PipPresentation.Minimized)
                    mode.startsWith("floating") ->
                        controller.enterPiP(PipPresentation.Floating)
                }
            }
            "viddget:forwarded_event" -> {
                val forwarded = json.optJSONObject("data") ?: return
                val name = forwarded.optString("eventName").takeIf { it.isNotEmpty() } ?: return
                handleForwardedEvent(name, forwarded.opt("data"))
            }
            else -> {
                // ignore — only the two iframe channels above are meaningful
                _ignore(type)
            }
        }
    }

    private fun handleForwardedEvent(name: String, data: Any?) {
        when (name) {
            "navigate-to" -> {
                val payload = data as? JSONObject
                if (payload == null) {
                    emitError(
                        BambuserCallError.InvalidEventPayload(name, "payload not a dictionary")
                    )
                    return
                }
                val externalId = unwrapExternalId(payload)
                if (externalId.isNullOrEmpty()) {
                    emitError(
                        BambuserCallError.InvalidEventPayload(name, "no externalId / url found")
                    )
                    return
                }
                emit(BambuserCallEvent.NavigateTo(externalId))
            }
            else -> emit(BambuserCallEvent.Other(name = name, payload = data))
        }
    }

    /**
     * The embed sends `externalId` in three shapes — a bare string, an
     * object with an `id` field, or only a `url` (we fall back to the
     * trailing slug).
     */
    private fun unwrapExternalId(payload: JSONObject): String? {
        val direct = payload.optString("externalId", "").takeIf { it.isNotEmpty() }
        if (direct != null) return direct

        val obj = payload.optJSONObject("externalId")
        if (obj != null) {
            val idString = obj.optString("id", "").takeIf { it.isNotEmpty() }
            if (idString != null) return idString
            val idInt = obj.optInt("id", Int.MIN_VALUE)
            if (idInt != Int.MIN_VALUE) return idInt.toString()
        }

        val urlString = payload.optString("url", "").takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { android.net.Uri.parse(urlString) }.getOrNull() ?: return null
        val segments = uri.pathSegments ?: return null
        return segments.asReversed().firstOrNull { it.isNotEmpty() }
    }

    // MARK: - Cart

    private fun handleCartIntent(payload: Any?, eventName: String, add: Boolean) {
        val emoji = if (add) "🛒" else "🔄"
        android.util.Log.d("BambuserCall", "$emoji $eventName raw payload: $payload")

        val dict = payload as? JSONObject ?: run {
            emitError(BambuserCallError.InvalidEventPayload(eventName, "payload not a dictionary"))
            return
        }
        val callbackKey = dict.optString("callbackId", "").takeIf { it.isNotEmpty() } ?: run {
            emitError(BambuserCallError.InvalidEventPayload(eventName, "missing callbackId"))
            return
        }
        val inner = dict.optJSONObject("payload") ?: run {
            emitError(BambuserCallError.InvalidEventPayload(eventName, "missing payload envelope"))
            return
        }

        val sku = inner.optString("sku", "").ifEmpty {
            inner.optJSONObject("item")?.optString("sku", "").orEmpty()
        }
        val quantity = inner.optInt("count", Int.MIN_VALUE).let {
            if (it != Int.MIN_VALUE) it else inner.optInt("quantity", 1)
        }

        android.util.Log.d(
            "BambuserCall",
            "$emoji $eventName sku=$sku quantity=$quantity callbackKey=$callbackKey"
        )

        emit(
            if (add) BambuserCallEvent.ShouldAddToCart(sku, quantity, callbackKey)
            else BambuserCallEvent.ShouldUpdateCart(sku, quantity, callbackKey)
        )
    }

    // MARK: - Helpers

    private fun emit(event: BambuserCallEvent) = controller.emit(event)
    private fun emitError(error: BambuserCallError) = controller.emit(error)

    private fun parseJson(s: String): JSONObject? =
        try { JSONObject(s) } catch (_: JSONException) { null }

    @Suppress("FunctionName", "UNUSED_PARAMETER")
    private fun _ignore(unused: String) { /* no-op, present so the when stays exhaustive */ }
}

private fun JSONObject.toMap(): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        result[key] = opt(key)
    }
    return result
}
