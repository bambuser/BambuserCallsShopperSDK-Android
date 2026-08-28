package com.bambuser.callsshopper

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bambuser.callsshopper.internal.EmbedEventRouter
import com.bambuser.callsshopper.internal.EmbedHTMLBuilder
import com.bambuser.callsshopper.internal.EmbedWebView

/**
 * Compose surface for a [BambuserCallController]. Renders the embed
 * as a full-screen sheet or, when [BambuserCallController.isPiP] is
 * true, as a draggable floating mini-player. Drop one into a `Box`
 * at the root of your screen.
 */
@Composable
fun BambuserCallOverlay(
    controller: BambuserCallController,
    modifier: Modifier = Modifier,
) {
    if (!controller.isVisible) return
    val orgId = controller.orgId ?: return

    val router = remember(controller) { EmbedEventRouter(controller) }
    val density = LocalDensity.current
    val cfg = controller.configuration
    val isPiP = controller.isPiP

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthDp = maxWidth
        val containerHeightDp = maxHeight

        val pipSize = when (controller.pipPresentation) {
            PipPresentation.Minimized -> cfg.minimizedPipSize
            PipPresentation.Floating  -> cfg.floatingPipSize
        }

        val animSpec = tween<androidx.compose.ui.unit.Dp>(
            durationMillis = cfg.transitionAnimationDurationMs,
        )
        val frameWidth by animateDpAsState(
            targetValue = if (isPiP) pipSize.width else containerWidthDp,
            animationSpec = animSpec,
            label = "frame-width",
        )
        val frameHeight by animateDpAsState(
            targetValue = if (isPiP) pipSize.height else containerHeightDp,
            animationSpec = animSpec,
            label = "frame-height",
        )

        // Cumulative drag offset from the default (bottom-right) PiP origin.
        var dragOffsetXPx by remember { mutableStateOf(0f) }
        var dragOffsetYPx by remember { mutableStateOf(0f) }

        LaunchedEffect(isPiP) {
            dragOffsetXPx = 0f
            dragOffsetYPx = 0f
        }

        val (offsetXDp, offsetYDp) = with(density) {
            if (!isPiP) {
                0.dp to 0.dp
            } else {
                val margin = cfg.pipMarginDp.dp
                val bottomReserve = cfg.bottomReserveDp.dp
                val baseX = containerWidthDp - pipSize.width - margin
                val baseY = containerHeightDp - pipSize.height - bottomReserve - margin
                (baseX + dragOffsetXPx.toDp()) to (baseY + dragOffsetYPx.toDp())
            }
        }

        val dragModifier = if (isPiP) {
            Modifier.pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    dragOffsetXPx += dragAmount.x
                    dragOffsetYPx += dragAmount.y
                }
            }
        } else Modifier

        Box(
            Modifier
                .offset(x = offsetXDp, y = offsetYDp)
                .size(width = frameWidth, height = frameHeight)
                .then(
                    if (isPiP)
                        Modifier
                            .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                    else Modifier
                )
                .background(Color.Transparent)
                .then(dragModifier)
        ) {
            EmbedWebView(
                html = EmbedHTMLBuilder.makeHTML(makeEmbedOptions(controller, orgId)),
                baseUrl = EmbedHTMLBuilder.baseUrl(controller.embedUrl),
                configuration = cfg,
                onNativeMessage = { raw -> router.handle(raw) },
                onWebViewCreated = { webView -> controller.attach(webView) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun makeEmbedOptions(
    controller: BambuserCallController,
    orgId: String,
): EmbedHTMLBuilder.Options {
    val cfg = controller.configuration
    return EmbedHTMLBuilder.Options(
        orgId = orgId,
        embedUrl = controller.embedUrl,
        connectId = controller.connectId,
        queue = controller.queue ?: cfg.initialQueue,
        triggers = cfg.triggers,
        floatingNavigationMode = cfg.floatingNavigationMode,
        floatingFillMode = cfg.floatingFillMode,
        locale = cfg.locale,
        data = cfg.initialCustomerData,
        trackingTags = if (cfg.initialTrackingTags.isEmpty()) null
                       else cfg.initialTrackingTags.toJsonArray(),
        dropInEnabled = cfg.dropInEnabled,
        bookingsEnabled = cfg.bookingsEnabled,
        openBookingPage = cfg.openBookingPage,
        bookingServiceIds = cfg.bookingServiceIds,
        bookingResourceId = cfg.bookingResourceId,
        bookingIframeUrl = cfg.bookingIframeUrl,
        enableScanning = cfg.enableScanning,
        merchantBaseUrl = cfg.merchantBaseUrl,
        disableCoBrowsing = cfg.disableCoBrowsing,
        themeId = cfg.themeId,
        allowFirstPartyCookies = cfg.allowFirstPartyCookies,
        disableDataLayerInterceptions = cfg.disableDataLayerInterceptions,
        subscriptions = controller.activeSubscriptions,
    )
}
