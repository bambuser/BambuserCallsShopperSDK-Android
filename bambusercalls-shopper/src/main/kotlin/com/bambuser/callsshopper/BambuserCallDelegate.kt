package com.bambuser.callsshopper

/**
 * Fire-and-forget notification bridge. Assign a delegate to
 * `controller.delegate` and pattern-match on [BambuserCallEvent] for
 * the events you care about. [onError] is the error channel; it has a
 * no-op default so hosts implement it only when they want to log or
 * surface errors.
 *
 * For events where the SDK needs a value back (product hydration,
 * search results, cart resolution), use [BambuserCallHandlers].
 *
 * Mirrors the iOS `BambuserCallDelegate` protocol.
 */
interface BambuserCallDelegate {

    /**
     * Single stream. Every fire-and-forget event the SDK emits comes
     * through here as a [BambuserCallEvent] case.
     */
    fun onEmit(controller: BambuserCallController, event: BambuserCallEvent)

    /**
     * Non-fatal SDK errors (JS eval failures, malformed payloads,
     * etc.). Default: no-op.
     */
    fun onError(controller: BambuserCallController, error: BambuserCallError) {}
}
