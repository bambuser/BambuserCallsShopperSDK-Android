package com.bambuser.callsshopper.internal

import com.bambuser.callsshopper.BambuserJSONValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parse a JSON string into a [BambuserJSONValue] tree. Uses the
 * Android `org.json` classes as the actual tokenizer; this file only
 * converts their output into our typed tree.
 *
 * Returns [BambuserJSONValue.Null] if the input isn't parseable —
 * the SDK is deliberately tolerant here (see the forward-compat
 * contract).
 */
internal object JsonParser {

    fun parse(json: String): BambuserJSONValue {
        return try {
            // Wrap in a container so both objects and arrays parse
            // through the same code path. `org.json` doesn't have a
            // universal parse entry point.
            val trimmed = json.trim()
            when {
                trimmed.isEmpty()         -> BambuserJSONValue.Null
                trimmed.startsWith("{")   -> fromObject(JSONObject(trimmed))
                trimmed.startsWith("[")   -> fromArray(JSONArray(trimmed))
                trimmed == "null"         -> BambuserJSONValue.Null
                trimmed == "true"         -> BambuserJSONValue.Bool(true)
                trimmed == "false"        -> BambuserJSONValue.Bool(false)
                else -> {
                    // Primitive: number or quoted string.
                    val i = trimmed.toIntOrNull()
                    if (i != null) return BambuserJSONValue.Int(i)
                    val l = trimmed.toLongOrNull()
                    if (l != null) return BambuserJSONValue.Long(l)
                    val d = trimmed.toDoubleOrNull()
                    if (d != null) return BambuserJSONValue.Double(d)
                    if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                        return BambuserJSONValue.Str(
                            trimmed.substring(1, trimmed.length - 1)
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                                .replace("\\n", "\n")
                                .replace("\\r", "\r")
                                .replace("\\t", "\t")
                        )
                    }
                    BambuserJSONValue.Null
                }
            }
        } catch (_: Throwable) {
            BambuserJSONValue.Null
        }
    }

    fun fromAny(value: Any?): BambuserJSONValue = when (value) {
        null                -> BambuserJSONValue.Null
        JSONObject.NULL     -> BambuserJSONValue.Null
        is Boolean          -> BambuserJSONValue.Bool(value)
        is Int              -> BambuserJSONValue.Int(value)
        is Long             -> BambuserJSONValue.Long(value)
        is Double           -> BambuserJSONValue.Double(value)
        is Float            -> BambuserJSONValue.Double(value.toDouble())
        is Number           -> BambuserJSONValue.Double(value.toDouble())
        is String           -> BambuserJSONValue.Str(value)
        is JSONObject       -> fromObject(value)
        is JSONArray        -> fromArray(value)
        else                -> BambuserJSONValue.Str(value.toString())
    }

    private fun fromObject(o: JSONObject): BambuserJSONValue.Obj {
        val map = linkedMapOf<String, BambuserJSONValue>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = fromAny(o.opt(k))
        }
        return BambuserJSONValue.Obj(map)
    }

    private fun fromArray(a: JSONArray): BambuserJSONValue.Arr {
        val list = ArrayList<BambuserJSONValue>(a.length())
        for (i in 0 until a.length()) {
            list.add(fromAny(a.opt(i)))
        }
        return BambuserJSONValue.Arr(list)
    }
}
