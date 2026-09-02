package com.bambuser.demo

import android.util.Log
import com.bambuser.callsshopper.BambuserCallController
import com.bambuser.callsshopper.BambuserCallDelegate
import com.bambuser.callsshopper.BambuserCallError
import com.bambuser.callsshopper.BambuserCallEvent
import com.bambuser.callsshopper.BambuserCallHandlers
import com.bambuser.callsshopper.BambuserJSONValue
import com.bambuser.callsshopper.BambuserProductRef
import com.bambuser.callsshopper.BambuserReply
import com.bambuser.callsshopper.CallState
import com.bambuser.callsshopper.cartFailure
import com.bambuser.callsshopper.cartSuccess
import com.bambuser.callsshopper.jsonInt
import com.bambuser.callsshopper.jsonObject
import com.bambuser.callsshopper.jsonString
import com.bambuser.demo.cart.CartStore
import com.bambuser.demo.catalog.ProductCatalog
import com.bambuser.demo.catalog.buildSearchResponseFactorySpec
import com.bambuser.demo.catalog.toProductFactorySpec
import com.bambuser.demo.root.NavigationBridge
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Bridges the SDK to the demo app.
 *
 *   1. [BambuserCallDelegate] — fire-and-forget events (navigation,
 *      checkout, chat, queue, tracking, close). Logged; navigate +
 *      checkout drive the demo UI.
 *   2. [buildHandlers] — data-source closures the SDK awaits a reply
 *      from (product hydration, catalog search, cart intents).
 *
 * Adapt this: replace [CartStore] and [ProductCatalog] with your own
 * cart/catalog, and rewrite the URL/SKU parsing in [extractSku] /
 * [resolveSku] to match your product URL scheme. Everything else here
 * (the delegate wiring, the handler shape, the reply pattern) is the
 * canonical way to integrate — copy it as-is.
 */
class BambuserCallBridge(
    private val cart: CartStore,
    private val controller: BambuserCallController,
) : BambuserCallDelegate {

    // -----------------------------------------------------------------
    // Delegate — fire-and-forget events
    // -----------------------------------------------------------------

    override fun onEmit(controller: BambuserCallController, event: BambuserCallEvent) {
        when (event) {
            is BambuserCallEvent.NavigateTo -> handleNavigate(event.url)
            is BambuserCallEvent.Checkout   -> NavigationBridge.onSwitchToCart?.invoke()

            is BambuserCallEvent.CallStateChanged -> {
                Log.d(TAG, "event: $event")
                if (event.state == CallState.Connected) syncCartToWidget(controller)
            }

            BambuserCallEvent.Close,
            BambuserCallEvent.ChatRequested,
            is BambuserCallEvent.PresentationChanged,
            is BambuserCallEvent.QueueOpened,
            is BambuserCallEvent.QueueClosed,
            is BambuserCallEvent.AgentsOnlineChanged,
            is BambuserCallEvent.WaitingTimeChanged,
            is BambuserCallEvent.TrackingEvent,
            is BambuserCallEvent.Other      -> Log.d(TAG, "event: $event")
        }
    }

    /**
     * On call connect, tell the agent's widget what's already in the
     * shopper's local cart. The embed's `notifyCustomerEvent` takes
     * one item per call, so we fire once per line. Each payload must
     * carry both `sku` and `name` — the embed rejects the event
     * otherwise (`fail('requires a non-empty "name"')`).
     * No-op when the cart is empty.
     *
     * Fires only on the Connected transition — the widget's cart
     * tool doesn't accept events before that.
     */
    private fun syncCartToWidget(controller: BambuserCallController) {
        val lines = cart.orderedLines
        if (lines.isEmpty()) return
        Log.d(TAG, "syncCartToWidget: replaying ${lines.size} line(s)")
        lines.forEach { (product, quantity) ->
            controller.notifyCustomerEvent(
                eventKey = "ADDED_TO_CART",
                payload = jsonObject(
                    "sku"      to jsonString(product.id),
                    "name"     to jsonString(product.name),
                    "price"    to BambuserJSONValue.Double(product.price),
                    "currency" to jsonString(product.currency),
                    "quantity" to jsonInt(quantity),
                ),
            )
        }
    }

    override fun onError(controller: BambuserCallController, error: BambuserCallError) {
        Log.w(TAG, "BambuserCall error: $error")
    }

    private fun handleNavigate(url: String) {
        val sku = extractSku(url) ?: run {
            Log.w(TAG, "no product for navigate-to url='$url'")
            return
        }
        if (ProductCatalog.product(forSku = sku) == null) {
            Log.w(TAG, "unknown sku '$sku' from navigate-to url='$url'")
            return
        }
        NavigationBridge.productsNav?.navigate("detail/$sku") { launchSingleTop = true }
        // Echo the co-browse hop back so the agent's carousel highlights
        // this SKU. The PDP's LaunchedEffect(product.id) also fires this
        // once it composes — sending both is idempotent and covers the
        // path where the shopper navigates on their own (no agent
        // `navigate-to`).
        controller.notifyProductNavigation(externalId = sku)
    }

    /**
     * Best-effort SKU extraction — the embed may hand us a full URL,
     * a slug, or the raw external id.
     *
     * Adapt this: match your own product-URL scheme. The default treats
     * the last non-empty path segment as the SKU (works for
     * `demo.bambuser.shop/product/{sku}/{slug}/` after trailing-slash
     * trim) and falls back to the raw string. If your URLs encode the
     * id in a query param or a different segment, decode it here.
     */
    private fun extractSku(url: String): String? {
        if (url.isEmpty()) return null
        val trimmed = url.trimEnd('/')
        val tail = trimmed.substringAfterLast('/', missingDelimiterValue = trimmed)
        return when {
            ProductCatalog.bySku.containsKey(tail) -> tail
            ProductCatalog.bySku.containsKey(url)  -> url
            tail.isNotEmpty()                      -> tail
            else                                   -> null
        }
    }

    // -----------------------------------------------------------------
    // Data-source handlers
    // -----------------------------------------------------------------

    fun buildHandlers(): BambuserCallHandlers = BambuserCallHandlers(
        provideProductData = { ref ->
            Log.d(TAG, "provideProductData ref='${ref.ref}' kind=${ref.kind.rawValue} bambuserId=${ref.bambuserId}")
            val sku = resolveSku(ref)
            when {
                sku == null -> {
                    Log.w(TAG, "  → reject: could not resolve SKU from ref='${ref.ref}'")
                    BambuserReply.Error("Unrecognised product reference: '${ref.ref}'")
                }
                else -> {
                    val product = ProductCatalog.product(forSku = sku)
                    if (product == null) {
                        Log.w(TAG, "  → reject: SKU '$sku' not in ProductCatalog")
                        BambuserReply.Error("We don't sell '$sku'")
                    } else {
                        Log.d(TAG, "  → reply: hydrating '${product.name}' (sku=$sku)")
                        BambuserReply.Reply(product.toProductFactorySpec())
                    }
                }
            }
        },

        provideSearchData = { request ->
            Log.d(TAG, "provideSearchData term='${request.term}' page=${request.page}")
            val query = request.term.trim().lowercase()
            if (query.length > 100) {
                BambuserReply.Error("Search term too long")
            } else {
                val matches = if (query.isEmpty()) {
                    ProductCatalog.all
                } else {
                    ProductCatalog.all.filter { p ->
                        p.name.lowercase().contains(query) ||
                        p.brand.lowercase().contains(query) ||
                        p.category.lowercase().contains(query) ||
                        p.id.lowercase().contains(query)
                    }
                }
                val pageSize = 10
                val totalMatches = matches.size
                val totalPages = max(1, ceil(totalMatches / pageSize.toDouble()).toInt())
                val pageIndex = max(1, min(request.page, totalPages))
                val start = (pageIndex - 1) * pageSize
                val end = min(start + pageSize, totalMatches)
                val pageItems = if (totalMatches == 0) emptyList() else matches.subList(start, end)
                val currency = pageItems.firstOrNull()?.currency ?: "USD"
                BambuserReply.Reply(
                    buildSearchResponseFactorySpec(
                        products = pageItems,
                        currency = currency,
                        currentPageIndex = pageIndex,
                        totalPages = totalPages,
                        totalMatches = totalMatches,
                    )
                )
            }
        },

        // Adapt this: hook these into your real cart repository /
        // network calls. Return `cartFailure(reason)` so the widget
        // surfaces a reason on the agent's row (e.g. "out-of-stock",
        // "region-restricted"). Both handlers are `suspend`, so awaiting
        // a network call is fine — the widget keeps the row pending
        // until the reply lands.
        shouldAddToCart = { intent ->
            if (intent.sku.isEmpty()) cartFailure(reason = "missing-sku")
            else {
                cart.setQuantity(sku = intent.sku, quantity = intent.quantity)
                cartSuccess
            }
        },

        shouldUpdateCart = { intent ->
            if (intent.sku.isEmpty()) cartFailure(reason = "missing-sku")
            else {
                cart.setQuantity(sku = intent.sku, quantity = intent.quantity)
                cartSuccess
            }
        },
    )

    // Adapt this: the embed hands us the agent's raw ref plus a `kind`
    // hint. URL refs need the SKU pulled out of a path; scanned barcodes
    // and product-reference codes come in as bare ids we can use
    // directly. Rewrite for your own barcode / URL / id conventions.
    private fun resolveSku(ref: BambuserProductRef): String? = when (ref.kind) {
        is BambuserProductRef.Kind.Url -> ref.ref.substringAfterLast('/', "").ifEmpty { null }
        is BambuserProductRef.Kind.ScannedCode,
        is BambuserProductRef.Kind.ProductReference,
        is BambuserProductRef.Kind.Unknown -> ref.ref.ifEmpty { null }
    }

    companion object { private const val TAG = "BambuserCallBridge" }
}
