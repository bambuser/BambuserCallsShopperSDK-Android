package com.bambuser.callsshopper

/**
 * Single stream of events emitted by [BambuserCallController] via
 * [BambuserCallDelegate.onEvent]. Cases carrying a `callbackKey` expect the
 * host to call `controller.notify(callbackKey, info)` to resolve the
 * JS-side promise; the rest are fire-and-forget.
 */
sealed class BambuserCallEvent {

    /**
     * Embed product click. `externalId` is already unwrapped from its
     * three possible shapes (string, `{id, feedId}` dict, URL slug).
     */
    data class NavigateTo(val externalId: String) : BambuserCallEvent()

    /**
     * Embed close event. The overlay has already been dismissed by the
     * time this is delivered — purely for observation.
     */
    object Close : BambuserCallEvent()

    /**
     * Embed `goto-checkout` event. `cart` is whatever payload the embed
     * sent (typically a `{ items: [...] }` shape).
     */
    data class Checkout(val cart: Map<String, Any?>?) : BambuserCallEvent()

    data class CallStateChanged(val state: CallState) : BambuserCallEvent()

    /**
     * PiP / full-screen flip. `presentation` is meaningful only when
     * [isPiP] is true.
     */
    data class PresentationChanged(
        val isPiP: Boolean,
        val presentation: PipPresentation
    ) : BambuserCallEvent()

    /**
     * Resolve via `controller.notify(callbackKey, info)` — pass `true`
     * on success or e.g. `"{ success: false, reason: 'out-of-stock' }"`
     * on failure.
     */
    data class ShouldAddToCart(
        val sku: String,
        val quantity: Int,
        val callbackKey: String
    ) : BambuserCallEvent()

    /** Same callback contract as [ShouldAddToCart]. */
    data class ShouldUpdateCart(
        val sku: String,
        val quantity: Int,
        val callbackKey: String
    ) : BambuserCallEvent()

    /**
     * Any event the framework doesn't model explicitly — a forward-compat
     * hook and an analytics piggyback point.
     */
    data class Other(val name: String, val payload: Any?) : BambuserCallEvent()
}
