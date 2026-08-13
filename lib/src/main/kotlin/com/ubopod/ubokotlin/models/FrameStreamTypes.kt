package com.ubopod.ubokotlin.models

/**
 * A single frame from a `frame_stream` `RenderViewData`. Packed RGB888
 * bytes (3 bytes per pixel, no padding), matching the server's
 * `FrameStreamDataEvent`.
 *
 * Mirrors `Sources/UboSwift/Connection/UboConnection.swift`'s
 * `FrameStreamFrame`.
 */
public data class FrameStreamFrame(
    val streamId: String,
    val data: ByteArray,
    val width: Int,
    val height: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrameStreamFrame
        return streamId == other.streamId &&
            width == other.width &&
            height == other.height &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = streamId.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + data.contentHashCode()
        return result
    }
}
