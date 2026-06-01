package com.bambuser.callsshopper

/**
 * Lifecycle of a one-to-one call. `Connecting` and `Connected` are both
 * "active" (an expert is or will be on the other end); `Idle` and `Ended`
 * are not.
 */
enum class CallState {
    Idle,
    Connecting,
    Connected,
    Ended
}
