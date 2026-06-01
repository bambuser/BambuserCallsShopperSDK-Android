package com.bambuser.demo.catalog

/**
 * Flat product model used across the demo. Each SKU is a single-variation
 * product, so we don't need the colour/size tree the SDK can hydrate — `id`
 * is the SKU at every level.
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
)
