package com.bambuser.callsshopper

/**
 * Single event stream delivered via
 * [BambuserCallDelegate.onEmit]. Fire-and-forget — the SDK
 * doesn't need a reply for any of these. Data-source events (product
 * hydration, search, cart) that DO need a reply live on
 * [BambuserCallHandlers] instead.
 *
 * Mirrors the iOS `BambuserCallEvent` enum. Kotlin sealed class so a
 * future SDK case is a compile-time surfaced addition on `when`
 * exhaustiveness checks.
 */
sealed class BambuserCallEvent {

    // MARK: - Lifecycle

    /**
     * Widget was closed (by the user or by `destroy()`). The overlay
     * has already been dismissed natively.
     */
    object Close : BambuserCallEvent()

    /** Call moved between `Idle` / `Connecting` / `Connected` / `Ended`. */
    data class CallStateChanged(val state: CallState) : BambuserCallEvent()

    /**
     * Overlay flipped between full-screen and PiP. [presentation] is
     * meaningful only when [isPiP] is true.
     */
    data class PresentationChanged(
        val isPiP: Boolean,
        val presentation: PipPresentation,
    ) : BambuserCallEvent()

    // MARK: - Widget actions

    /**
     * Shopper tapped the checkout button. [cart] is the raw payload
     * the embed sent (typically `{ items: [...] }`).
     */
    data class Checkout(val cart: BambuserJSONValue) : BambuserCallEvent()

    /**
     * Shopper picked "I prefer to chat" instead of the call. The
     * widget has already closed by the time this fires.
     */
    object ChatRequested : BambuserCallEvent()

    /**
     * Agent asked to send the shopper to a [url]. The single point
     * every agent-driven navigation passes through in MANUAL
     * floatingPlayer mode (checkout too). Route into your navigation
     * stack.
     */
    data class NavigateTo(val url: String) : BambuserCallEvent()

    // MARK: - Queue

    data class QueueOpened(val state: BambuserQueueOpenState) : BambuserCallEvent()
    data class QueueClosed(val state: BambuserQueueOpenState) : BambuserCallEvent()
    data class AgentsOnlineChanged(val online: BambuserAgentsOnline) : BambuserCallEvent()
    data class WaitingTimeChanged(val time: BambuserQueueWaitingTime) : BambuserCallEvent()

    // MARK: - Analytics

    data class TrackingEvent(val event: BambuserTrackingEvent) : BambuserCallEvent()

    // MARK: - Firehose (forward-compat)

    /**
     * Any event the SDK doesn't model explicitly. Only fires when
     * [BambuserCallConfiguration.forwardUnknownEmbedEvents] is true.
     */
    data class Other(
        val name: String,
        val payload: BambuserJSONValue,
    ) : BambuserCallEvent()
}
