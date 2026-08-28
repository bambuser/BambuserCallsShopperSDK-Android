package com.bambuser.callsshopper

/**
 * Type-safe JSON tree used everywhere in the SDK where a value has to
 * cross the native ↔ JavaScript boundary. Serializes to its natural
 * JSON form via [toJsonString].
 *
 * Mirrors the iOS `BambuserJSONValue` enum. Handy DSL entry points at
 * the bottom (`jsonObject`, `jsonArray`) — same ergonomics as the
 * Swift `ExpressibleBy*Literal` conformances.
 */
sealed class BambuserJSONValue {

    object Null : BambuserJSONValue()
    data class Bool(val value: Boolean) : BambuserJSONValue()
    data class Int(val value: kotlin.Int) : BambuserJSONValue()
    data class Long(val value: kotlin.Long) : BambuserJSONValue()
    data class Double(val value: kotlin.Double) : BambuserJSONValue()
    data class Str(val value: String) : BambuserJSONValue()
    data class Arr(val values: List<BambuserJSONValue>) : BambuserJSONValue()
    data class Obj(val entries: Map<String, BambuserJSONValue>) : BambuserJSONValue()

    /** Serialize to JSON. Result is also valid JavaScript. */
    fun toJsonString(): String {
        val sb = StringBuilder()
        appendJson(sb)
        return sb.toString()
    }

    private fun appendJson(sb: StringBuilder) {
        when (this) {
            is Null   -> sb.append("null")
            is Bool   -> sb.append(if (value) "true" else "false")
            is Int    -> sb.append(value)
            is Long   -> sb.append(value)
            is Double -> {
                if (value.isNaN() || value.isInfinite()) sb.append("null")
                else sb.append(value)
            }
            is Str    -> appendJsonString(sb, value)
            is Arr    -> {
                sb.append('[')
                values.forEachIndexed { idx, v ->
                    if (idx > 0) sb.append(',')
                    v.appendJson(sb)
                }
                sb.append(']')
            }
            is Obj    -> {
                sb.append('{')
                var first = true
                for ((k, v) in entries) {
                    if (!first) sb.append(',')
                    first = false
                    appendJsonString(sb, k)
                    sb.append(':')
                    v.appendJson(sb)
                }
                sb.append('}')
            }
        }
    }

    private fun appendJsonString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '\\'     -> sb.append("\\\\")
                '"'      -> sb.append("\\\"")
                '\b'     -> sb.append("\\b")
                '\n'     -> sb.append("\\n")
                '\r'     -> sb.append("\\r")
                '\t'     -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        sb.append("\\u")
                        sb.append(String.format("%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
    }

    companion object {
        fun stringOrNull(s: String?): BambuserJSONValue = s?.let(::Str) ?: Null
        fun intOrNull(i: kotlin.Int?): BambuserJSONValue = i?.let(::Int) ?: Null
        fun longOrNull(i: kotlin.Long?): BambuserJSONValue = i?.let(::Long) ?: Null
        fun doubleOrNull(d: kotlin.Double?): BambuserJSONValue = d?.let(::Double) ?: Null
        fun boolOrNull(b: Boolean?): BambuserJSONValue = b?.let(::Bool) ?: Null
    }
}

// MARK: - DSL

/** Object literal — `jsonObject("a" to jsonString("x"), "b" to jsonInt(2))`. */
fun jsonObject(vararg pairs: Pair<String, BambuserJSONValue>): BambuserJSONValue.Obj =
    BambuserJSONValue.Obj(linkedMapOf(*pairs))

/** Array literal — `jsonArray(jsonString("x"), jsonInt(2))`. */
fun jsonArray(vararg values: BambuserJSONValue): BambuserJSONValue.Arr =
    BambuserJSONValue.Arr(values.toList())

fun jsonString(s: String): BambuserJSONValue.Str = BambuserJSONValue.Str(s)
fun jsonInt(i: Int): BambuserJSONValue.Int = BambuserJSONValue.Int(i)
fun jsonLong(l: Long): BambuserJSONValue.Long = BambuserJSONValue.Long(l)
fun jsonDouble(d: Double): BambuserJSONValue.Double = BambuserJSONValue.Double(d)
fun jsonBool(b: Boolean): BambuserJSONValue.Bool = BambuserJSONValue.Bool(b)
