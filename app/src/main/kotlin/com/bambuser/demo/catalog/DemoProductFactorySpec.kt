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
 *   .product(p => p.name(...).sku(...).variations(v => [...]))
 * ```
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

private fun DemoProduct.productDetailSpec(): BambuserFactorySpec {
    val calls = mutableListOf(
        BambuserFactoryCall.method("name",        args = listOf(jsonString(name))),
        BambuserFactoryCall.method("sku",         args = listOf(jsonString(id))),
        BambuserFactoryCall.method("description", args = listOf(jsonString(description))),
    )
    calls.add(BambuserFactoryCall.method("variations", items = listOf(variationItem())))
    return BambuserFactorySpec(calls)
}

private fun DemoProduct.variationItem(): BambuserFactoryItem {
    val imageUrls: BambuserJSONValue = BambuserJSONValue.Arr(
        (listOf(imageUrl) + additionalImages).map { jsonString(it) }
    )
    val variationCalls = listOf(
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
 *   .products(f => matches.map(product => f().name(...).sku(...).price(...)))
 * ```
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
        BambuserFactoryItem(
            factoryArgs = emptyList(),
            spec = BambuserFactorySpec(
                calls = listOf(
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
            )
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
