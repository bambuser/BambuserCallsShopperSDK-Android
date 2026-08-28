package com.bambuser.callsshopper

/**
 * Payload type for the `provide-search-data` event. The SDK owns
 * this type because it names the `callbackKey` you must echo back on
 * the reply — the response shape is yours to model and hand to the
 * SDK as a [BambuserFactorySpec].
 */
data class BambuserSearchRequest(
    /** Free-text term the agent typed. */
    val term: String,
    /** 1-indexed page of results the agent is asking for. */
    val page: Int,
    /** Opaque callback id. Pass this back verbatim on the reply. */
    val callbackKey: String,
    /**
     * The full unmodified JSON payload from the embed. Access any
     * field the SDK doesn't surface as a typed property here —
     * including new fields Bambuser adds later.
     */
    val raw: BambuserJSONValue = BambuserJSONValue.Null,
) {
    companion object {
        /**
         * Decode a `provide-search-data` payload. Returns `null` only
         * when there is no `callbackId` — without it the SDK can't
         * reply. Missing `term` becomes `""`; missing / non-numeric
         * `page` becomes `1`.
         */
        fun decode(payload: BambuserJSONValue): BambuserSearchRequest? {
            val obj = payload as? BambuserJSONValue.Obj ?: return null
            val callbackId = (obj.entries["callbackId"] as? BambuserJSONValue.Str)?.value
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            val term = (obj.entries["term"] as? BambuserJSONValue.Str)?.value ?: ""

            val page: Int = when (val p = obj.entries["page"]) {
                is BambuserJSONValue.Int    -> p.value
                is BambuserJSONValue.Long   -> p.value.toInt()
                is BambuserJSONValue.Double -> p.value.toInt()
                is BambuserJSONValue.Str    -> p.value.toIntOrNull() ?: 1
                else                        -> 1
            }

            return BambuserSearchRequest(term = term, page = page, callbackKey = callbackId, raw = payload)
        }
    }
}
