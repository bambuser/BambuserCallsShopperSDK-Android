package com.bambuser.callsshopper

/**
 * Host-side bridge for a [BambuserCallController]. Two methods:
 *
 *  * [onEvent] — the event stream. Required.
 *  * [onError] — the error channel. Optional (has a no-op default).
 */
interface BambuserCallDelegate {

    /**
     * Every event the framework recognizes is delivered here. See
     * [BambuserCallEvent] for the case list.
     */
    fun onEvent(controller: BambuserCallController, event: BambuserCallEvent)

    /** Errors the framework couldn't quietly absorb. */
    fun onError(controller: BambuserCallController, error: BambuserCallError) {}
}
