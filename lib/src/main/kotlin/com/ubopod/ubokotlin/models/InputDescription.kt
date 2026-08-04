package com.ubopod.ubokotlin.models

/**
 * Field types supported by [WebUIInputDescription].
 *
 * Mirrors `ubo_app.store.input.types.InputFieldType` and
 * `Sources/UboSwift/Models/InputDescription.swift`.
 */
public enum class InputFieldType(public val rawValue: String, public val protoValue: Int) {
    LONG("long", 1),
    TEXT("text", 2),
    PASSWORD("password", 3),
    NUMBER("number", 4),
    CHECKBOX("checkbox", 5),
    COLOR("color", 6),
    SELECT("select", 7),
    FILE("file", 8),
    DATE("date", 9),
    TIME("time", 10),
    RANGE("range", 11);

    public companion object {
        /**
         * Resolve from a proto value. Unknown values fall back to [TEXT] to
         * match the Swift `init(protoValue:)` behavior.
         */
        public fun fromProto(protoValue: Int): InputFieldType =
            entries.firstOrNull { it.protoValue == protoValue } ?: TEXT
    }
}

/** One field of a multi-field web/native input form. */
public data class InputFieldDescription(
    val name: String,
    val label: String,
    val type: InputFieldType,
    val description: String? = null,
    val title: String? = null,
    val fileMimetype: String? = null,
    val pattern: String? = null,
    val defaultValue: String? = null,
    val options: List<String> = emptyList(),
    val required: Boolean = false,
)

/**
 * A pending input demand of `input_method == WEB_DASHBOARD`. Each entry in
 * `state.web_ui.active_inputs` becomes one of these on the wire and one
 * dialog/sheet on the client.
 */
public data class WebUIInputDescription(
    val id: String,
    val title: String? = null,
    val prompt: String? = null,
    val fields: List<InputFieldDescription> = emptyList(),
)
