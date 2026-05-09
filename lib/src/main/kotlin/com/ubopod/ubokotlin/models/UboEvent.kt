package com.ubopod.ubokotlin.models

/**
 * Events that can be received from an Ubo device.
 *
 * Mirrors `Sources/UboSwift/Models/UboEvent.swift`.
 */
public sealed class UboEvent {
    /** Display render event with pixel data. */
    public data class DisplayRender(val data: DisplayRenderData) : UboEvent()

    /** Display compressed render event. */
    public data class DisplayCompressedRender(
        val timestamp: Double,
        val compressedData: ByteArray,
        val rectangle: DisplayRectangle,
        val density: Float,
    ) : UboEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as DisplayCompressedRender
            return timestamp == other.timestamp &&
                compressedData.contentEquals(other.compressedData) &&
                rectangle == other.rectangle &&
                density == other.density
        }

        override fun hashCode(): Int {
            var result = timestamp.hashCode()
            result = 31 * result + compressedData.contentHashCode()
            result = 31 * result + rectangle.hashCode()
            result = 31 * result + density.hashCode()
            return result
        }
    }

    /** Display was blanked. */
    public object DisplayBlanked : UboEvent()

    /** Display was unblanked. */
    public object DisplayUnblanked : UboEvent()

    /** Display redraw requested. */
    public object DisplayRedraw : UboEvent()

    /** Audio sample reported. */
    public data class AudioSample(val timestamp: Double, val data: ByteArray) : UboEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AudioSample
            return timestamp == other.timestamp && data.contentEquals(other.data)
        }

        override fun hashCode(): Int = 31 * timestamp.hashCode() + data.contentHashCode()
    }

    /** Audio playback completed. */
    public object AudioPlaybackDone : UboEvent()

    /** Notification cleared. `id == null` means "all". */
    public data class NotificationCleared(val id: String?) : UboEvent()

    /** Notification displayed. */
    public data class NotificationDisplayed(val id: String) : UboEvent()

    /** Power off initiated. */
    public object PowerOff : UboEvent()

    /** Reboot initiated. */
    public object Reboot : UboEvent()

    /** Camera viewfinder started — device wants frames. */
    public data class CameraStartViewfinder(val pattern: String?) : UboEvent()

    /** Camera viewfinder stopped — device no longer needs frames. */
    public object CameraStopViewfinder : UboEvent()

    /** Generic / unknown event with the type-URL suffix the server sent. */
    public data class Unknown(val type: String) : UboEvent()
}
