package com.bambuser.callsshopper

/**
 * Analytics tag attached to a session — max 20 per session; string
 * values capped at 1KB by the embed. Small helper stays in the SDK so
 * the `setTrackingTags` / `initialTrackingTags` APIs can enforce the
 * max-20 / last-key-wins contract at the type level.
 */
data class BambuserTrackingTag(
    val key: String,
    val value: BambuserJSONValue,
) {
    constructor(key: String, string: String) : this(key, BambuserJSONValue.Str(string))
    constructor(key: String, number: Double) : this(key, BambuserJSONValue.Double(number))
    constructor(key: String, bool: Boolean)   : this(key, BambuserJSONValue.Bool(bool))

    /** `{key, value}` object for the tags array. */
    fun toJsonValue(): BambuserJSONValue = jsonObject(
        "key"   to BambuserJSONValue.Str(key),
        "value" to value,
    )
}

/**
 * Deduplicate on key (last one wins, matching the embed contract)
 * and encode as a JSON array of objects.
 */
internal fun List<BambuserTrackingTag>.toJsonArray(): BambuserJSONValue {
    val seen = linkedMapOf<String, BambuserTrackingTag>()
    for (tag in this) seen[tag.key] = tag
    return BambuserJSONValue.Arr(seen.values.map { it.toJsonValue() })
}
