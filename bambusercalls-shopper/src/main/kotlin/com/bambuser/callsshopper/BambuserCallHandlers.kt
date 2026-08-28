package com.bambuser.callsshopper

/**
 * Data-source contracts. Only fill in the closures you actually
 * implement — null handlers translate to "no JS subscription
 * installed", so the widget won't waste time waiting for a reply you
 * can't give.
 *
 * Every non-null handler returns a value the SDK dispatches to the
 * embed. For fire-and-forget notifications (checkout, chat, queue,
 * tracking, navigation, close, errors), implement
 * [BambuserCallDelegate] on the controller.
 *
 * All closures are `suspend` — you can call into your network / db /
 * cache layers without blocking. They run on the coroutine scope the
 * controller uses (main dispatcher by default).
 *
 * Mirrors the iOS `BambuserCallHandlers` struct.
 */
data class BambuserCallHandlers(

    /**
     * Agent added a product in their tool. Return:
     *  - `Reply(spec)` with a [BambuserFactorySpec] for the product,
     *  - `Error(message)` to signal "we don't sell that" (widget
     *    calls `updateProduct` with a callback that throws),
     *  - `Skip` to send no reply (agent tool times out; prefer
     *    `Error` in production).
     */
    val provideProductData: (suspend (BambuserProductRef) -> BambuserReply<BambuserFactorySpec>)? = null,

    /**
     * Agent is searching the catalog. Return:
     *  - `Reply(spec)` with a [BambuserFactorySpec] for the results,
     *  - `Error(message)` for a validation / backend error (the
     *    "verify function" pattern from the JS docs,
     *    `if (!validSearch(...)) throw ...`),
     *  - `Skip` to send no reply.
     */
    val provideSearchData: (suspend (BambuserSearchRequest) -> BambuserReply<BambuserFactorySpec>)? = null,

    /**
     * Agent adds an item to the cart. Return `cartSuccess`,
     * `cartFailure(...)`, a custom `Reply(json)`, `Error(message)`,
     * or `Skip`.
     */
    val shouldAddToCart: (suspend (BambuserCartIntent) -> BambuserReply<BambuserJSONValue>)? = null,

    /**
     * Agent updates a cart line (or removes with `quantity == 0`).
     * Same reply shape as [shouldAddToCart].
     */
    val shouldUpdateCart: (suspend (BambuserCartIntent) -> BambuserReply<BambuserJSONValue>)? = null,
)

/**
 * Which JS `.on(...)` subscriptions to install for a given session.
 * Data-source events gate on individual handlers; fire-and-forget
 * events (delegate-routed) are always installed. The catch-all
 * emitter override is gated on `forwardUnknownEmbedEvents`.
 *
 * Frozen at `show()` time; the HTML is templated from this.
 */
internal data class BambuserCallSubscriptions(
    val provideProductData: Boolean = false,
    val provideSearchData: Boolean = false,
    val shouldAddToCart: Boolean = false,
    val shouldUpdateCart: Boolean = false,
    val catchAllOther: Boolean = false,
) {
    companion object {
        fun from(
            handlers: BambuserCallHandlers,
            forwardUnknownEmbedEvents: Boolean,
        ): BambuserCallSubscriptions = BambuserCallSubscriptions(
            provideProductData = handlers.provideProductData != null,
            provideSearchData  = handlers.provideSearchData != null,
            shouldAddToCart    = handlers.shouldAddToCart != null,
            shouldUpdateCart   = handlers.shouldUpdateCart != null,
            catchAllOther      = forwardUnknownEmbedEvents,
        )
    }
}
