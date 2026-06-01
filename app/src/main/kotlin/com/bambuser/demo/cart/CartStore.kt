package com.bambuser.demo.cart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bambuser.demo.catalog.DemoProduct
import com.bambuser.demo.catalog.ProductCatalog

/**
 * Observable cart shared across the app. Tracks `[sku → quantity]`. Both
 * the manual "Add to cart" button and the Bambuser cart events mutate
 * this.
 */
class CartStore {

    private val lines = mutableStateMapOf<String, Int>()

    /** Compose-observable snapshot of the cart contents, sorted by
     *  catalog order so the rendering is deterministic. */
    var orderedLines: List<Pair<DemoProduct, Int>> by mutableStateOf(emptyList())
        private set

    private fun refreshOrderedLines() {
        orderedLines = ProductCatalog.all.mapNotNull { product ->
            val qty = lines[product.id] ?: return@mapNotNull null
            if (qty <= 0) null else product to qty
        }
    }

    fun quantity(forSku: String): Int = lines[forSku] ?: 0

    fun add(sku: String, quantity: Int = 1) {
        if (quantity <= 0) return
        lines[sku] = (lines[sku] ?: 0) + quantity
        refreshOrderedLines()
    }

    /** Replace the line's quantity. `0` removes it. */
    fun setQuantity(sku: String, quantity: Int) {
        if (quantity <= 0) lines.remove(sku) else lines[sku] = quantity
        refreshOrderedLines()
    }

    fun remove(sku: String) {
        lines.remove(sku)
        refreshOrderedLines()
    }

    fun clear() {
        lines.clear()
        refreshOrderedLines()
    }

    val totalItems: Int get() = lines.values.sum()

    val subtotal: Double
        get() = orderedLines.sumOf { (product, qty) -> product.price * qty }

    val currency: String
        get() = orderedLines.firstOrNull()?.first?.currency ?: "USD"
}
