package com.bambuser.callsshopper

/**
 * Data description of a Bambuser embed builder chain, so hosts can
 * drive any `oneToOneEmbed.someMethod(..., factory => ...)` flow —
 * present or future — without SDK changes.
 *
 * Every method call in a Bambuser factory chain is one of three
 * shapes:
 *
 *  • A leaf call with plain arguments:
 *      `.currency("USD")`
 *  • A call whose last argument is a builder callback:
 *      `.product(df -> df.name(...).sku(...))`
 *  • A call whose last argument is an "items callback" — the embed
 *    hands you a factory, you invoke it once per item and chain from
 *    the result:
 *      `.variations(vf -> arr.map(v -> vf().name(v.name)))`
 *      `.attributes(a  -> arr.map(x -> a(x.id).name(...)))`
 *
 * [BambuserFactorySpec] captures the three uniformly. The JS reducer
 * `window.__bambuserApplyFactorySpec` applies a spec to any factory
 * handed out by the embed.
 *
 * Mirrors the iOS `BambuserFactorySpec` type verbatim.
 */
data class BambuserFactorySpec(
    /**
     * Ordered list of builder method calls. Empty is legal — the JS
     * reducer returns the factory unchanged, which is useful when an
     * item factory is called with args (`attribute(id, optionId)`)
     * and needs no further chaining.
     */
    val calls: List<BambuserFactoryCall> = emptyList(),
) {
    /** Serialize the spec (with nested [calls]) to JSON. */
    fun toJsonString(): String = toJsonValue().toJsonString()

    fun toJsonValue(): BambuserJSONValue = jsonObject(
        "calls" to BambuserJSONValue.Arr(calls.map { it.toJsonValue() })
    )
}

/**
 * One method call on the current factory.
 *
 * @param method   Name of the builder method to invoke.
 * @param args     Plain args passed before any callback. Empty when
 *                 the method only takes a builder.
 * @param factory  When set, the last argument to [method] is a
 *                 builder callback:
 *                 `method(...args, f -> applySpec(f, factory))`.
 * @param items    When set, the last argument to [method] is an
 *                 "items callback":
 *                 `method(...args, itemFactory -> items.map(item -> applySpec(itemFactory(...item.factoryArgs), item.spec)))`.
 */
data class BambuserFactoryCall(
    val method: String,
    val args: List<BambuserJSONValue> = emptyList(),
    val factory: BambuserFactorySpec? = null,
    val items: List<BambuserFactoryItem>? = null,
) {
    fun toJsonValue(): BambuserJSONValue {
        val fields = linkedMapOf<String, BambuserJSONValue>(
            "method" to BambuserJSONValue.Str(method),
        )
        fields["args"] = BambuserJSONValue.Arr(args)
        factory?.let { fields["factory"] = it.toJsonValue() }
        items?.let { list ->
            fields["items"] = BambuserJSONValue.Arr(list.map { it.toJsonValue() })
        }
        return BambuserJSONValue.Obj(fields)
    }

    companion object {
        /** Leaf call — `factory.method(args...)`. */
        fun method(
            name: String,
            args: List<BambuserJSONValue> = emptyList(),
        ): BambuserFactoryCall = BambuserFactoryCall(name, args)

        /** Nested builder — `factory.method(args..., f -> applySpec(f, factory))`. */
        fun method(
            name: String,
            factory: BambuserFactorySpec,
            args: List<BambuserJSONValue> = emptyList(),
        ): BambuserFactoryCall = BambuserFactoryCall(name, args, factory = factory)

        /** Items builder — `factory.method(args..., iF -> items.map(...))`. */
        fun method(
            name: String,
            items: List<BambuserFactoryItem>,
            args: List<BambuserJSONValue> = emptyList(),
        ): BambuserFactoryCall = BambuserFactoryCall(name, args, items = items)
    }
}

/**
 * One entry of an "items callback" — see [BambuserFactoryCall.items].
 *
 * @param factoryArgs Args to invoke the item factory with. Empty means
 *                    `itemFactory()` (e.g. `variations()`); non-empty
 *                    means the factory is called with values
 *                    (e.g. `attribute(attributeId, optionId)`).
 * @param spec        Chain applied to the value returned by
 *                    `itemFactory(...factoryArgs)`. Empty when the
 *                    factory call itself is the entire item.
 */
data class BambuserFactoryItem(
    val factoryArgs: List<BambuserJSONValue> = emptyList(),
    val spec: BambuserFactorySpec = BambuserFactorySpec(),
) {
    fun toJsonValue(): BambuserJSONValue = jsonObject(
        "factoryArgs" to BambuserJSONValue.Arr(factoryArgs),
        "spec"        to spec.toJsonValue(),
    )
}
