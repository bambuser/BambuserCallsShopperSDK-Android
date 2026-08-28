package com.bambuser.callsshopper

/**
 * How the overlay renders when `controller.isPiP` is true.
 */
enum class PipPresentation {
    /** Draggable mini-player, default 180×260 dp (`configuration.floatingPipSize`). */
    Floating,

    /** Compact pill, 180×60 dp. */
    Minimized,
}
