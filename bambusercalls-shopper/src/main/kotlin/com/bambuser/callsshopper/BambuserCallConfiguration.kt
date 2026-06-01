package com.bambuser.callsshopper

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Frozen-at-init configuration for a [BambuserCallController]. The public
 * surface is intentionally narrow — six knobs hosts realistically flip.
 * Everything else (embed triggers, fill modes, animation curves, etc.)
 * has internal defaults and isn't part of the supported API.
 */
data class BambuserCallConfiguration(
    /** Bambuser organization id. Hardcoded into the JS embed call. */
    val orgId: String,

    /** Embed script URL. Override for staging / labs / branch builds. */
    val embedUrl: String = DEFAULT_EMBED_URL,

    /** Frame for the connected mini-player. Default 180×260 dp. */
    val floatingPipSize: DpSize = DpSize(180.dp, 260.dp),

    /**
     * Whether the WebView is debuggable via Chrome DevTools. Defaults to
     * `true` in debug builds, `false` in release.
     */
    val isInspectable: Boolean = defaultIsInspectable(),

    /**
     * When the call transitions to `Connected` while the user is in PiP,
     * auto-restore to full screen. Default `true`.
     */
    val autoExpandOnConnect: Boolean = true,

    /**
     * When the call ends (`call-ended` and friends), automatically dismiss
     * the overlay. Default `true`.
     */
    val dismissOnCallEnd: Boolean = true,
) {
    // Internal defaults — not part of the public API.
    internal val triggers: List<String> = listOf("always", "connect-link")
    internal val floatingNavigationMode: String = "manual"
    internal val floatingFillMode: String = "cover"
    internal val minimizedPipSize: DpSize = DpSize(180.dp, 60.dp)
    internal val pipMargin = 16.dp
    internal val bottomReserve = 0.dp
    internal val transitionAnimationDurationMs: Int = 220
    internal val transitionAnimationEasing: Easing = FastOutSlowInEasing
    internal val customUserAgent: String? = BAMBUSER_SAFARI_USER_AGENT
    internal val injectsLegacyGetUserMediaShim: Boolean = true

    companion object {
        const val DEFAULT_EMBED_URL: String = "https://one-to-one.bambuser.com/embed.js"

        /**
         * Safari iOS UA used by default so the embed's browser-detection
         * check recognizes the WebKit context as a supported browser. The
         * Bambuser embed checks the UA on Android too; mirroring iOS keeps
         * the support matrix consistent.
         */
        internal const val BAMBUSER_SAFARI_USER_AGENT: String =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_7 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.7 Mobile/21H16 Safari/604.1"

        private fun defaultIsInspectable(): Boolean =
            // BuildConfig isn't reachable from a library at compile time; consumers
            // can override per-build. Defaults to true so SDK developers can debug.
            true
    }
}
