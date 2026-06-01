package com.bambuser.callsshopper

/**
 * Errors the framework surfaces via [BambuserCallDelegate.onError]. Hosts
 * can log or surface them; otherwise the framework continues to no-op
 * internally.
 */
sealed class BambuserCallError {

    /**
     * A native → JS `evaluateJavascript` call failed. [operation] is a
     * short tag identifying which outbound call.
     */
    data class JavaScriptEvaluationFailed(
        val operation: String,
        val underlying: Throwable?
    ) : BambuserCallError()

    /**
     * An incoming event from the embed couldn't be parsed into the
     * expected payload shape. The event is dropped rather than emitted
     * in a malformed form.
     */
    data class InvalidEventPayload(
        val eventName: String,
        val reason: String
    ) : BambuserCallError()
}
