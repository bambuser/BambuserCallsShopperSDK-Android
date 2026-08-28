package com.bambuser.callsshopper

/**
 * The intent the agent sent from their tool. [raw] is the untouched
 * payload from the web — inspect it if you need fields the SDK
 * didn't surface.
 */
data class BambuserCartIntent(
    val sku: String,
    val quantity: Int,
    /** Only meaningful on `should-update-item-in-cart`. Null for adds. */
    val previousQuantity: Int?,
    val raw: BambuserJSONValue,
)
