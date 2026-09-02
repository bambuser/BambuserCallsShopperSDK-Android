package com.bambuser.demo.catalog

/**
 * Flat product model used across the demo. Each SKU is a single-variation
 * product, so we don't need the colour/size tree the SDK can hydrate — `id`
 * is the SKU at every level.
 *
 * Adapt this: swap for your own product type. The SDK doesn't care what
 * shape your product model has — only [DemoProductFactorySpec] does, and
 * that's demo code you're expected to rewrite for your catalog. Bambuser
 * only requires that the SKU you emit via `.sku(...)` matches the id the
 * agent's carousel sends back on `navigate-to` / cart events.
 */
data class DemoProduct(
    val id: String,
    val name: String,
    val brand: String,
    val category: String,
    val price: Double,
    val currency: String,
    val inStock: Boolean,
    val description: String,
    val imageUrl: String,
    val additionalImages: List<String> = emptyList(),
    /**
     * Product-page URL on demo.bambuser.shop, when the product is
     * actually live on the demo site. `null` for products that were
     * retired from the demo catalog (agent's carousel will just skip
     * the link).
     */
    val url: String? = null,
)
