package com.bambuser.callsshopper

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Frozen-at-init configuration for a [BambuserCallController].
 *
 * Every embed constructor option is forwarded to JS as-is; nullable
 * options (`dropInEnabled`, `bookingsEnabled`, etc.) are only sent
 * when the host explicitly sets them, so the embed's own defaults
 * apply otherwise.
 *
 * See https://bambuser.com/docs/video-consultation/calls-widget-api-reference/#configuration-options
 */
data class BambuserCallConfiguration(

    // MARK: - Required

    /** Bambuser organization id. Hard-coded into the JS embed call. */
    val orgId: String,

    // MARK: - Region

    /** `US` or `EU`. Embed script URL is looked up from this. */
    val environment: BambuserEnvironment = BambuserEnvironment.US,

    // MARK: - Presentation (SDK-owned)

    /** Frame for the connected mini-player. Default 180×260 dp. */
    val floatingPipSize: DpSize = DpSize(180.dp, 260.dp),

    /** Whether the WebView is inspectable via Chrome DevTools. */
    val isInspectable: Boolean = defaultIsInspectable(),

    /**
     * When the call transitions to Connected while the user is in
     * PiP, auto-restore to full screen. Default `true`.
     */
    val autoExpandOnConnect: Boolean = true,

    /**
     * When the call ends, automatically dismiss the overlay. Default
     * `true`.
     */
    val dismissOnCallEnd: Boolean = true,

    // MARK: - Embed constructor forwards

    val locale: String? = null,
    val initialCustomerData: BambuserJSONValue? = null,
    val initialTrackingTags: List<BambuserTrackingTag> = emptyList(),
    val initialQueue: String? = null,

    val dropInEnabled: Boolean? = null,
    val bookingsEnabled: Boolean? = null,
    val openBookingPage: Boolean = false,
    val bookingServiceIds: List<String>? = null,
    val bookingResourceId: String? = null,
    val bookingIframeUrl: String? = null,

    val enableScanning: Boolean = false,
    val merchantBaseUrl: String? = null,
    val disableCoBrowsing: Boolean = false,
    val themeId: String? = null,
    val allowFirstPartyCookies: Boolean? = null,

    /**
     * Install the JS emitter override that forwards every embed event
     * to [BambuserCallEvent.Other]. Default `false` — the SDK only
     * crosses the bridge for events it (or data-source handlers)
     * explicitly model.
     */
    val forwardUnknownEmbedEvents: Boolean = false,
) {
    // MARK: - Internal defaults (not part of the public API)

    internal val triggers: List<String> = listOf("always", "connect-link")
    internal val floatingNavigationMode: String = "manual"
    internal val floatingFillMode: String = "cover"
    internal val minimizedPipSize: DpSize = DpSize(180.dp, 60.dp)
    internal val pipMarginDp: Int = 16
    internal val bottomReserveDp: Int = 0
    internal val transitionAnimationDurationMs: Int = 220
    internal val injectsLegacyGetUserMediaShim: Boolean = true
    internal val customUserAgent: String? = null

    /**
     * Always `true` for the mobile SDK — the merchant's page-level
     * GTM / GA data layer does not exist inside our WebView, so the
     * embed's data-layer interceptors have nothing to attach to.
     * Not part of the public API.
     */
    internal val disableDataLayerInterceptions: Boolean = true

    companion object {
        private fun defaultIsInspectable(): Boolean = true
    }
}
