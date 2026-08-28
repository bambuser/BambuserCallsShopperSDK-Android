package com.bambuser.callsshopper

/**
 * Identifier for the customizable cards on the widget's start
 * screen. Used with
 * [BambuserCallController.updateElement].
 */
enum class BambuserElement(val rawValue: String) {
    DropInCard("drop-in-card"),
    BookingCard("book-meeting-card");
}

/**
 * State overrides for [BambuserCallController.updateElement]. Every
 * field is optional — omitted fields fall back to the embed's
 * default.
 */
data class BambuserElementState(
    val disableCTA: Boolean? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val buttonText: String? = null,
) {
    internal fun toJsonValue(): BambuserJSONValue {
        val fields = linkedMapOf<String, BambuserJSONValue>()
        disableCTA?.let { fields["disableCTA"] = BambuserJSONValue.Bool(it) }

        val content = linkedMapOf<String, BambuserJSONValue>()
        title?.let      { content["title"]      = BambuserJSONValue.Str(it) }
        subtitle?.let   { content["subtitle"]   = BambuserJSONValue.Str(it) }
        buttonText?.let { content["buttonText"] = BambuserJSONValue.Str(it) }
        if (content.isNotEmpty()) {
            fields["content"] = BambuserJSONValue.Obj(content)
        }

        return BambuserJSONValue.Obj(fields)
    }
}
