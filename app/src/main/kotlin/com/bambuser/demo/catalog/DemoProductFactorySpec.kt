package com.bambuser.demo.catalog

import com.bambuser.callsshopper.BambuserFactoryCall
import com.bambuser.callsshopper.BambuserFactoryItem
import com.bambuser.callsshopper.BambuserFactorySpec
import com.bambuser.callsshopper.BambuserJSONValue
import com.bambuser.callsshopper.jsonBool
import com.bambuser.callsshopper.jsonDouble
import com.bambuser.callsshopper.jsonInt
import com.bambuser.callsshopper.jsonString

/**
 * Build the exact factory-chain the JS embed expects for
 * `provideProductData`. Mirrors the iOS demo's
 * `BambuserProduct+FactorySpec.swift` — kept in the demo target
 * because the SDK is intentionally agnostic to product shape.
 *
 * Corresponds to the JS chain:
 *
 * ```
 * factory
 *   .currency("USD")
 *   .locale("en-US")
 *   .product(p => p
 *     .name(...).sku(...).description(...).url(...)
 *     .variations(v => [ v().name(...).sku(...).price(...)... ]))
 * ```
 *
 * ### The `url` rule
 *
 * The agent's tool looks for the product's external landing page on
 * the **product** level (`detailFactory.url(...)`), matching
 * Bambuser's canonical `bambuserHoodie.js` mock. Variations don't
 * carry `.url()`.
 *
 * When wiring a data model that keeps the URL on the variation
 * (some catalogs work that way), always promote the first
 * variation's URL up to the product level via
 * [resolveProductLevelUrl] — never emit `.url()` inside the
 * variation builder. That's the invariant the widget relies on.
 */
fun DemoProduct.toProductFactorySpec(
    locale: String = "en-US",
): BambuserFactorySpec = BambuserFactorySpec(
    calls = listOf(
        BambuserFactoryCall.method("currency", args = listOf(jsonString(currency))),
        BambuserFactoryCall.method("locale",   args = listOf(jsonString(locale))),
        BambuserFactoryCall.method(
            name = "product",
            factory = productDetailSpec(),
        ),
    )
)

/**
 * Resolve the URL to emit at the product level. The product-level
 * `.url()` is **mandatory** for the agent's tool to render a
 * "View page" link and fire co-browse events, so we require one.
 * Falls back to the first non-null variation URL if the product-level
 * [DemoProduct.url] is not set.
 */
private fun DemoProduct.resolveProductLevelUrl(): String? =
    url ?: variationUrls().firstOrNull()

/**
 * Variation-level URLs, in order. Variations **may** carry their own
 * `.url()` when a specific colour/size has a different landing page —
 * emit it when available, skip when null. The demo has one variation
 * per SKU that inherits the product URL.
 */
private fun DemoProduct.variationUrls(): List<String?> = listOf(url)

private fun DemoProduct.productDetailSpec(): BambuserFactorySpec {
    val calls = mutableListOf(
        BambuserFactoryCall.method("name",        args = listOf(jsonString(name))),
        BambuserFactoryCall.method("sku",         args = listOf(jsonString(id))),
        BambuserFactoryCall.method("description", args = listOf(jsonString(description))),
    )
    // Product-level `.url()` — mandatory for agent-side "View page" +
    // navigate-to co-browse to work.
    resolveProductLevelUrl()?.let {
        calls.add(BambuserFactoryCall.method("url", args = listOf(jsonString(it))))
    }
    calls.add(BambuserFactoryCall.method("variations", items = listOf(variationItem())))
    return BambuserFactorySpec(calls)
}

private fun DemoProduct.variationItem(): BambuserFactoryItem {
    val imageUrls: BambuserJSONValue = BambuserJSONValue.Arr(
        (listOf(imageUrl) + additionalImages).map { jsonString(it) }
    )
    val variationCalls = mutableListOf(
        BambuserFactoryCall.method("name",      args = listOf(jsonString(name))),
        BambuserFactoryCall.method("sku",       args = listOf(jsonString(id))),
        BambuserFactoryCall.method("subtitle",  args = listOf(jsonString(brand))),
        BambuserFactoryCall.method("inStock",   args = listOf(jsonBool(inStock))),
        BambuserFactoryCall.method("imageUrls", args = listOf(imageUrls)),
        BambuserFactoryCall.method(
            name = "price",
            factory = BambuserFactorySpec(
                calls = listOf(
                    BambuserFactoryCall.method("current", args = listOf(jsonDouble(price))),
                )
            ),
        ),
        BambuserFactoryCall.method(
            name = "comparableAttributes",
            items = listOf(
                comparableAttributeItem("category", "Category", category),
                comparableAttributeItem("brand",    "Brand",    brand),
            ),
        ),
    )
    // Optional variation-level `.url()` — emitted only when present.
    // Useful when a specific colour/size has its own landing page;
    // otherwise the agent's tool falls back to the product-level URL.
    url?.let {
        variationCalls.add(BambuserFactoryCall.method("url", args = listOf(jsonString(it))))
    }
    return BambuserFactoryItem(
        factoryArgs = emptyList(),
        spec = BambuserFactorySpec(variationCalls),
    )
}

private fun comparableAttributeItem(
    id: String,
    name: String,
    value: String,
): BambuserFactoryItem = BambuserFactoryItem(
    factoryArgs = listOf(jsonString(id), jsonString(value)),
    spec = BambuserFactorySpec(
        calls = listOf(BambuserFactoryCall.method("name", args = listOf(jsonString(name))))
    ),
)

/**
 * Search response factory chain — the reply for
 * `provideSearchData`. Mirrors the shape iOS's `BambuserSearchResponse`
 * builds.
 *
 * ```
 * factory
 *   .currency(...)
 *   .locale(...)
 *   .pagination(p => p.totalPages(...).totalMatches(...).currentPageIndex(...))
 *   .products(f => matches.map(product => f()
 *     .name(...).sku(...).imageUrl(...).price(...).url(...)))
 * ```
 *
 * Search items are flat product cards, so `.url()` lives directly on
 * each product (there's no variation nesting to worry about). Emitted
 * only when the source [DemoProduct.url] is non-null.
 */
fun buildSearchResponseFactorySpec(
    products: List<DemoProduct>,
    currency: String,
    locale: String = "en-US",
    currentPageIndex: Int,
    totalPages: Int,
    totalMatches: Int,
): BambuserFactorySpec {
    val paginationSpec = BambuserFactorySpec(
        calls = listOf(
            BambuserFactoryCall.method("totalPages",       args = listOf(jsonInt(totalPages))),
            BambuserFactoryCall.method("totalMatches",     args = listOf(jsonInt(totalMatches))),
            BambuserFactoryCall.method("currentPageIndex", args = listOf(jsonInt(currentPageIndex))),
        )
    )
    val productItems = products.map { p ->
        val calls = mutableListOf(
            BambuserFactoryCall.method("name",     args = listOf(jsonString(p.name))),
            BambuserFactoryCall.method("sku",      args = listOf(jsonString(p.id))),
            BambuserFactoryCall.method("imageUrl", args = listOf(jsonString(p.imageUrl))),
            BambuserFactoryCall.method(
                name = "price",
                factory = BambuserFactorySpec(
                    calls = listOf(
                        BambuserFactoryCall.method("current", args = listOf(jsonDouble(p.price))),
                    )
                ),
            ),
        )
        p.url?.let {
            calls.add(BambuserFactoryCall.method("url", args = listOf(jsonString(it))))
        }
        BambuserFactoryItem(
            factoryArgs = emptyList(),
            spec = BambuserFactorySpec(calls),
        )
    }
    return BambuserFactorySpec(
        calls = listOf(
            BambuserFactoryCall.method("currency",   args = listOf(jsonString(currency))),
            BambuserFactoryCall.method("locale",     args = listOf(jsonString(locale))),
            BambuserFactoryCall.method("pagination", factory = paginationSpec),
            BambuserFactoryCall.method("products",   items = productItems),
        )
    )
}
