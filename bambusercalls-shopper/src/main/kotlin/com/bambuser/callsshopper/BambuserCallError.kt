package com.bambuser.callsshopper

/**
 * Non-fatal SDK errors surfaced through
 * [BambuserCallDelegate.onError]. Hosts can log or surface them;
 * otherwise the framework continues to no-op internally.
 */
sealed class BambuserCallError : Throwable() {

    /**
     * A native → JS `evaluateJavascript` call failed. [operation] is
     * a short tag identifying which outbound call
     * (e.g. `"notifyProductNavigation('1234')"`).
     */
    data class JavaScriptEvaluationFailed(
        val operation: String,
        val underlying: Throwable?,
    ) : BambuserCallError()

    /**
     * An incoming event from the embed couldn't be parsed into the
     * expected payload envelope. The event is dropped rather than
     * routed in a malformed form. Individual field-level nulls do NOT
     * trigger this — those flow through via the payload's `raw`
     * field.
     */
    data class InvalidEventPayload(
        val eventName: String,
        val reason: String,
    ) : BambuserCallError()

    override val message: String
        get() = when (this) {
            is JavaScriptEvaluationFailed -> "$operation failed: ${underlying?.message ?: "unknown"}"
            is InvalidEventPayload        -> "$eventName invalid payload: $reason"
        }
}
