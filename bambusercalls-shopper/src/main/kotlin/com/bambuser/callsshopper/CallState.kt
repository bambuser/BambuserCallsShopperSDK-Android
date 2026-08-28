package com.bambuser.callsshopper

/**
 * Lifecycle of a Bambuser call as observed from the host side. The SDK
 * derives this from the widget's call-* events and exposes it as a
 * `StateFlow` on `BambuserCallController`.
 */
enum class CallState {
    /** No call in flight — overlay may still be visible on the pre-call widget UI. */
    Idle,

    /** Widget is placing/waiting on the call. */
    Connecting,

    /** Two-way audio/video is up. */
    Connected,

    /** Call ended (by either side). */
    Ended,
}
