package com.bambuser.callsshopper.internal

import com.bambuser.callsshopper.BambuserAgentsOnline
import com.bambuser.callsshopper.BambuserCallController
import com.bambuser.callsshopper.BambuserCallError
import com.bambuser.callsshopper.BambuserCallEvent
import com.bambuser.callsshopper.BambuserCartIntent
import com.bambuser.callsshopper.BambuserJSONValue
import com.bambuser.callsshopper.BambuserProductRef
import com.bambuser.callsshopper.BambuserQueueOpenState
import com.bambuser.callsshopper.BambuserQueueWaitingTime
import com.bambuser.callsshopper.BambuserReply
import com.bambuser.callsshopper.BambuserSearchRequest
import com.bambuser.callsshopper.BambuserTrackingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Routes JS-side events to the frozen handlers on the controller
 * (data sources) or the [BambuserCallDelegate] (fire-and-forget).
 * Mirrors iOS `EmbedEventRouter.swift`.
 *
 * Each incoming JSON payload is parsed to a [BambuserJSONValue] tree
 * (see [JsonParser]) so we can route by name and preserve unknown
 * fields via `raw`.
 */
internal class EmbedEventRouter(
    private val controller: BambuserCallController,
) {
    private val handlers get() = controller.activeHandlers
    private val delegate get() = controller.delegate

    // One shared scope for handler tasks. Runs on Main so state
    // observers (Compose mutableStateOf) see updates immediately.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun handle(rawMessage: String) {
        val root = JsonParser.parse(rawMessage) as? BambuserJSONValue.Obj ?: return
        val event = (root.entries["event"] as? BambuserJSONValue.Str)?.value ?: return
        val payload = root.entries["payload"] ?: BambuserJSONValue.Null

        when (event) {

            // -- Always subscribed by the SDK --

            "close" -> {
                controller.close()
                emit(BambuserCallEvent.Close)
            }

            "iframe-message" -> {
                val detail = (root.entries["detail"] as? BambuserJSONValue.Str)?.value ?: ""
                handleIframeMessage(detail)
            }

            "__async-response" -> controller.resolveAsyncInvoke(payload)

            // Call-state transitions come through the emitter override.
            "call-started", "call-starting", "call-connecting", "call-ringing" ->
                controller.setCallState(com.bambuser.callsshopper.CallState.Connecting)

            "call-connected", "call-active", "call-accepted", "connected" ->
                controller.setCallState(com.bambuser.callsshopper.CallState.Connected)

            "call-ended", "call-end", "call-disconnected", "call-rejected",
            "disconnected", "ended" ->
                controller.setCallState(com.bambuser.callsshopper.CallState.Ended)

            // -- Fire-and-forget events → delegate as BambuserCallEvent --

            "goto-checkout" -> emit(BambuserCallEvent.Checkout(payload))
            "goto-chat"     -> emit(BambuserCallEvent.ChatRequested)

            "surf-behind-to" -> {
                val obj = payload as? BambuserJSONValue.Obj
                val url = (obj?.entries?.get("url") as? BambuserJSONValue.Str)?.value
                if (!url.isNullOrEmpty()) emit(BambuserCallEvent.NavigateTo(url))
            }

            "queue-is-open"   -> emit(BambuserCallEvent.QueueOpened(parseQueueOpen(payload, isOpen = true)))
            "queue-is-closed" -> emit(BambuserCallEvent.QueueClosed(parseQueueOpen(payload, isOpen = false)))
            "agents-online"   -> emit(BambuserCallEvent.AgentsOnlineChanged(parseAgentsOnline(payload)))
            "queue-estimated-waiting-time" -> emit(BambuserCallEvent.WaitingTimeChanged(parseWaitingTime(payload)))
            "tracking-event"  -> emit(BambuserCallEvent.TrackingEvent(parseTrackingEvent(payload)))

            // -- Data-source events → handlers (need a reply) --

            "should-add-item-to-cart"    -> routeCartIntent(payload, add = true)
            "should-update-item-in-cart" -> routeCartIntent(payload, add = false)
            "provide-product-data"       -> routeProvideProductData(payload)
            "provide-search-data"        -> routeProvideSearchData(payload)

            // -- Catch-all — only fires when forwardUnknownEmbedEvents = true --

            else -> emit(BambuserCallEvent.Other(name = event, payload = payload))
        }
    }

    // MARK: - Async data-source dispatch

    private fun routeProvideProductData(payload: BambuserJSONValue) {
        val handler = handlers.provideProductData ?: return
        val refs = BambuserProductRef.decode(payload)
        for (ref in refs) {
            scope.launch {
                when (val result = handler(ref)) {
                    is BambuserReply.Reply -> controller.provideProductData(ref.bambuserId, result.payload)
                    is BambuserReply.Error -> controller.provideProductError(ref.bambuserId, result.message)
                    BambuserReply.Skip     -> Unit
                }
            }
        }
    }

    private fun routeProvideSearchData(payload: BambuserJSONValue) {
        val handler = handlers.provideSearchData ?: return
        val request = BambuserSearchRequest.decode(payload)
        if (request == null) {
            controller.reportError(
                BambuserCallError.InvalidEventPayload(
                    eventName = "provide-search-data",
                    reason = "missing callbackId — cannot reply",
                )
            )
            return
        }
        scope.launch {
            when (val result = handler(request)) {
                is BambuserReply.Reply -> controller.provideSearchData(request.callbackKey, result.payload)
                is BambuserReply.Error -> controller.provideSearchError(request.callbackKey, result.message)
                BambuserReply.Skip     -> Unit
            }
        }
    }

    private fun routeCartIntent(payload: BambuserJSONValue, add: Boolean) {
        val handler = if (add) handlers.shouldAddToCart else handlers.shouldUpdateCart
        handler ?: return

        val outer = payload as? BambuserJSONValue.Obj ?: run {
            controller.reportError(BambuserCallError.InvalidEventPayload(
                eventName = if (add) "should-add-item-to-cart" else "should-update-item-in-cart",
                reason = "payload not an object",
            ))
            return
        }
        val callbackKey = (outer.entries["callbackId"] as? BambuserJSONValue.Str)?.value ?: run {
            controller.reportError(BambuserCallError.InvalidEventPayload(
                eventName = if (add) "should-add-item-to-cart" else "should-update-item-in-cart",
                reason = "missing callbackId",
            ))
            return
        }
        val inner = outer.entries["payload"] as? BambuserJSONValue.Obj ?: run {
            controller.reportError(BambuserCallError.InvalidEventPayload(
                eventName = if (add) "should-add-item-to-cart" else "should-update-item-in-cart",
                reason = "missing payload envelope",
            ))
            return
        }

        val sku = (inner.entries["sku"] as? BambuserJSONValue.Str)?.value
            ?: ((inner.entries["item"] as? BambuserJSONValue.Obj)
                ?.entries?.get("sku") as? BambuserJSONValue.Str)?.value
            ?: ""
        val quantity = when (val q = inner.entries["count"] ?: inner.entries["quantity"]) {
            is BambuserJSONValue.Int    -> q.value
            is BambuserJSONValue.Long   -> q.value.toInt()
            is BambuserJSONValue.Double -> q.value.toInt()
            else -> 1
        }
        val previousQuantity = when (val q = inner.entries["previousQuantity"]) {
            is BambuserJSONValue.Int    -> q.value
            is BambuserJSONValue.Long   -> q.value.toInt()
            is BambuserJSONValue.Double -> q.value.toInt()
            else -> null
        }

        val intent = BambuserCartIntent(
            sku = sku,
            quantity = quantity,
            previousQuantity = previousQuantity,
            raw = inner,
        )

        scope.launch {
            when (val result = handler(intent)) {
                is BambuserReply.Reply -> controller.notify(callbackKey, result.payload.toJsonString())
                is BambuserReply.Error -> {
                    val obj: BambuserJSONValue = com.bambuser.callsshopper.jsonObject(
                        "success" to BambuserJSONValue.Bool(false),
                        "reason"  to BambuserJSONValue.Str(result.message),
                    )
                    controller.notify(callbackKey, obj.toJsonString())
                }
                BambuserReply.Skip -> Unit
            }
        }
    }

    // MARK: - Iframe message (viewport mode + forwarded events)

    private fun handleIframeMessage(detail: String) {
        val obj = JsonParser.parse(detail) as? BambuserJSONValue.Obj ?: return
        val eventType = (obj.entries["eventType"] as? BambuserJSONValue.Str)?.value ?: return

        when (eventType) {
            "viddget:viewport_mode" -> {
                val mode = (obj.entries["data"] as? BambuserJSONValue.Str)?.value ?: return
                when {
                    mode == "normal" || mode == "centerModal" -> controller.expand()
                    mode == "floatingLeftMinimized" || mode == "floatingRightMinimized" ->
                        controller.enterPiP(com.bambuser.callsshopper.PipPresentation.Minimized)
                    mode.startsWith("floating") ->
                        controller.enterPiP(com.bambuser.callsshopper.PipPresentation.Floating)
                }
            }
            "viddget:forwarded_event" -> {
                val forwarded = obj.entries["data"] as? BambuserJSONValue.Obj ?: return
                val name = (forwarded.entries["eventName"] as? BambuserJSONValue.Str)?.value ?: return
                if (!controller.activeSubscriptions.catchAllOther) return
                emit(BambuserCallEvent.Other(name = name, payload = forwarded.entries["data"] ?: BambuserJSONValue.Null))
            }
        }
    }

    // MARK: - Payload parsers

    private fun parseQueueOpen(payload: BambuserJSONValue, isOpen: Boolean): BambuserQueueOpenState {
        val obj = payload as? BambuserJSONValue.Obj
        return BambuserQueueOpenState(
            isOpen = (obj?.entries?.get("isOpen") as? BambuserJSONValue.Bool)?.value ?: isOpen,
            nextOpenTime  = (obj?.entries?.get("nextOpenTime")  as? BambuserJSONValue.Str)?.value,
            nextCloseTime = (obj?.entries?.get("nextCloseTime") as? BambuserJSONValue.Str)?.value,
            raw = payload,
        )
    }

    private fun parseAgentsOnline(payload: BambuserJSONValue): BambuserAgentsOnline {
        val obj = payload as? BambuserJSONValue.Obj
        val n = when (val v = obj?.entries?.get("numberOfAgentsOnline")) {
            is BambuserJSONValue.Int    -> v.value
            is BambuserJSONValue.Long   -> v.value.toInt()
            is BambuserJSONValue.Double -> v.value.toInt()
            else -> 0
        }
        return BambuserAgentsOnline(
            numberOfAgentsOnline = n,
            queueId = (obj?.entries?.get("queueId") as? BambuserJSONValue.Str)?.value,
            raw = payload,
        )
    }

    private fun parseWaitingTime(payload: BambuserJSONValue): BambuserQueueWaitingTime {
        val obj = payload as? BambuserJSONValue.Obj
        fun intField(key: String): Int = when (val v = obj?.entries?.get(key)) {
            is BambuserJSONValue.Int    -> v.value
            is BambuserJSONValue.Long   -> v.value.toInt()
            is BambuserJSONValue.Double -> v.value.toInt()
            else -> 0
        }
        return BambuserQueueWaitingTime(
            estimatedWaitingTime = intField("estimatedWaitingTime"),
            agents = intField("agents"),
            place  = intField("place"),
            queueId = (obj?.entries?.get("queueId") as? BambuserJSONValue.Str)?.value,
            raw = payload,
        )
    }

    private fun parseTrackingEvent(payload: BambuserJSONValue): BambuserTrackingEvent {
        val obj = payload as? BambuserJSONValue.Obj
        return BambuserTrackingEvent(
            eventName    = (obj?.entries?.get("eventName") as? BambuserJSONValue.Str)?.value,
            eventData    = obj?.entries?.get("eventData"),
            ga4Event     = (obj?.entries?.get("event")     as? BambuserJSONValue.Str)?.value,
            ga4Ecommerce = obj?.entries?.get("ecommerce"),
            raw = payload,
        )
    }

    // MARK: - Emit helper

    private fun emit(event: BambuserCallEvent) {
        delegate?.onEmit(controller, event)
    }
}
