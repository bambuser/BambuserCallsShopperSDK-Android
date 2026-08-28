package com.bambuser.callsshopper

/**
 * One reply type for every data-source handler. The `Payload` is
 * specialised per handler:
 *
 *  - `provideProductData` / `provideSearchData` →
 *    `BambuserReply<BambuserFactorySpec>`
 *    (payload is a chained-builder description; SDK invokes the
 *    widget's factory callback with a builder derived from the spec)
 *
 *  - `shouldAddToCart` / `shouldUpdateCart` →
 *    `BambuserReply<BambuserJSONValue>`
 *    (payload is a raw JSON value the widget's callback accepts; the
 *    SDK passes it verbatim to `window[callback](value)`)
 *
 * Three cases:
 *  - [Reply] send the payload. Shape depends on the specialisation.
 *  - [Error] show `message` to the agent. Factory handlers dispatch
 *    `callback(() => { throw new Error(message) })` (the documented
 *    "we don't sell that" / "verify function" pattern). Cart handlers
 *    dispatch `callback({success: false, reason: message})`.
 *  - [Skip] send no reply. Widget times out after ~30s. Prefer
 *    [Error] in production so the agent sees something.
 */
sealed class BambuserReply<out Payload> {
    data class Reply<Payload>(val payload: Payload) : BambuserReply<Payload>()
    data class Error(val message: String) : BambuserReply<Nothing>()
    object Skip : BambuserReply<Nothing>()
}

// MARK: - Cart shorthand

/** Cart success — equivalent to `Reply(BambuserJSONValue.Bool(true))`. */
val cartSuccess: BambuserReply<BambuserJSONValue> =
    BambuserReply.Reply(BambuserJSONValue.Bool(true))

/**
 * Cart failure with an optional reason.
 *  - `null` reason → `Reply(BambuserJSONValue.Bool(false))` (`callback(false)`)
 *  - non-null reason → `Reply(.object([success:false, reason:...]))` — the
 *    widget surfaces on the product row.
 */
fun cartFailure(reason: String? = null): BambuserReply<BambuserJSONValue> =
    if (reason == null) {
        BambuserReply.Reply(BambuserJSONValue.Bool(false))
    } else {
        BambuserReply.Reply(
            jsonObject(
                "success" to BambuserJSONValue.Bool(false),
                "reason"  to BambuserJSONValue.Str(reason),
            )
        )
    }
