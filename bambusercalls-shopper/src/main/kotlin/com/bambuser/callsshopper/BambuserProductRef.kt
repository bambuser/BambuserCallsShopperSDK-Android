package com.bambuser.callsshopper

/**
 * Payload type for the `provide-product-data` event. The SDK owns
 * this type because it names the `bambuserId` you must echo back on
 * the reply — the rest of the product shape (what you send back) is
 * yours to model however you like and hand to the SDK as a
 * [BambuserFactorySpec].
 */
data class BambuserProductRef(
    /** Raw reference — a SKU, a URL, or a barcode depending on [kind]. */
    val ref: String,
    /** How the reference was captured on the agent side. */
    val kind: Kind,
    /**
     * The embed-generated id you MUST pass back to
     * `BambuserCallController.provideProductData(bambuserId, factorySpec)`
     * so the widget can associate the response with the pending
     * request.
     */
    val bambuserId: String,
    /**
     * The full unmodified JSON entry the embed sent for this ref.
     * Access new / unknown fields here — the SDK never drops data.
     */
    val raw: BambuserJSONValue = BambuserJSONValue.Null,
) {
    /**
     * How the agent added the product. `Unknown(raw)` covers any
     * future values Bambuser adds — the SDK never drops entries.
     */
    sealed class Kind(val rawValue: String) {
        object Url              : Kind("url")
        object ScannedCode      : Kind("scanned-code")
        object ProductReference : Kind("product-reference")
        data class Unknown(val raw: String) : Kind(raw)

        companion object {
            fun fromRaw(raw: String): Kind = when (raw) {
                "url"               -> Url
                "scanned-code"      -> ScannedCode
                "product-reference" -> ProductReference
                else                -> Unknown(raw)
            }
        }
    }

    companion object {
        /**
         * Decode a `provide-product-data` payload into a list of refs.
         * Never filters — every entry the web sent is preserved,
         * missing fields fall through as empty strings, unknown
         * `type` values are carried as `Kind.Unknown(rawValue)`.
         */
        fun decode(payload: BambuserJSONValue): List<BambuserProductRef> {
            val obj = payload as? BambuserJSONValue.Obj ?: return emptyList()
            val items = obj.entries["products"] as? BambuserJSONValue.Arr ?: return emptyList()

            return items.values.map { item ->
                val m = item as? BambuserJSONValue.Obj
                if (m == null) {
                    BambuserProductRef(
                        ref = "",
                        kind = Kind.Unknown(""),
                        bambuserId = "",
                        raw = item,
                    )
                } else {
                    BambuserProductRef(
                        ref = m.entries["ref"].asString() ?: "",
                        kind = Kind.fromRaw(m.entries["type"].asString() ?: ""),
                        bambuserId = m.entries["id"].asString() ?: "",
                        raw = item,
                    )
                }
            }
        }
    }
}

private fun BambuserJSONValue?.asString(): String? =
    (this as? BambuserJSONValue.Str)?.value
