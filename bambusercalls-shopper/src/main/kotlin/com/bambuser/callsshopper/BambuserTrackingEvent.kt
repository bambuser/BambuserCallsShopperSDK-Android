package com.bambuser.callsshopper

/**
 * Payload of the `tracking-event` stream. The embed emits two shapes:
 *
 *   • Bambuser call/product/cart events:  `{ eventName, eventData }`
 *   • GA4 ecommerce events:               `{ event, ecommerce }`
 *
 * Both are surfaced here — hosts route either into their own
 * analytics stack.
 *
 * See https://bambuser.com/docs/video-consultation/tracking-events/
 */
data class BambuserTrackingEvent(
    /** Bambuser-shaped event name (e.g. `"call_started"`). Null for GA4-shaped events. */
    val eventName: String?,
    /** Bambuser-shaped event payload. */
    val eventData: BambuserJSONValue?,
    /** GA4-shaped event name (e.g. `"add_to_cart"`). Null for Bambuser-shaped events. */
    val ga4Event: String?,
    /** GA4 ecommerce payload. */
    val ga4Ecommerce: BambuserJSONValue?,
    /** The full unmodified JSON payload. */
    val raw: BambuserJSONValue,
)
