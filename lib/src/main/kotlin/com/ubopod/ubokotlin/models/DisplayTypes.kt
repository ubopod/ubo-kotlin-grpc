package com.ubopod.ubokotlin.models

/**
 * Display blank timeout options.
 *
 * Mirrors `Sources/UboSwift/Models/DisplayTypes.swift`.
 */
public enum class DisplayBlankTimeout(public val rawValue: String, public val protoValue: Int) {
    ONE_MINUTE("1min", 1),
    FIVE_MINUTES("5min", 2),
    TEN_MINUTES("10min", 3),
    THIRTY_MINUTES("30min", 4),
    ONE_HOUR("1hour", 5),
    OFF("off", 6);

    public companion object {
        public fun fromProto(protoValue: Int): DisplayBlankTimeout? =
            entries.firstOrNull { it.protoValue == protoValue }
    }
}

/** A subset of pixel data + the rectangle it occupies on the device's display. */
public data class DisplayRectangle(val y1: Int, val x1: Int, val y2: Int, val x2: Int) {
    public val width: Int get() = x2 - x1
    public val height: Int get() = y2 - y1
}

/** Display render event payload. */
public data class DisplayRenderData(
    val timestamp: Double,
    val data: ByteArray,
    val rectangle: DisplayRectangle,
    val density: Float,
) {
    public val width: Int get() = rectangle.width
    public val height: Int get() = rectangle.height

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DisplayRenderData
        return timestamp == other.timestamp &&
            data.contentEquals(other.data) &&
            rectangle == other.rectangle &&
            density == other.density
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + rectangle.hashCode()
        result = 31 * result + density.hashCode()
        return result
    }
}
