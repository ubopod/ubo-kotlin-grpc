package com.ubopod.ubokotlin.models

/**
 * Audio chime sounds available on the Ubo device.
 *
 * Mirrors `Sources/UboSwift/Models/AudioTypes.swift`.
 */
public enum class Chime(public val rawValue: String, public val protoValue: Int) {
    ADD("add", 1),
    DONE("done", 2),
    FAILURE("failure", 3),
    VOLUME_CHANGE("volume_change", 4);

    public companion object {
        public fun fromProto(protoValue: Int): Chime? =
            entries.firstOrNull { it.protoValue == protoValue }
    }
}

/**
 * A raw audio sample captured by a connected client (or originating on the
 * device). Mirrors the `AudioSample` proto message.
 */
public data class AudioSampleData(
    val data: ByteArray,
    val channels: Int = 1,
    val rate: Int = 16000,
    val width: Int = 2,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioSampleData
        return data.contentEquals(other.data) &&
            channels == other.channels &&
            rate == other.rate &&
            width == other.width
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + channels
        result = 31 * result + rate
        result = 31 * result + width
        return result
    }
}

/**
 * One playback event from the device's `000-audio` service. Mirrors the
 * three event variants the Web UI subscribes to in `audio.ts`.
 */
public sealed class PlaybackEvent {
    /** One-shot PCM sample (chimes, alerts, short clips). */
    public data class Sample(val sample: AudioSampleData, val volume: Float) : PlaybackEvent()

    /**
     * One chunk of an indexed audio sequence (TTS, file playback). The device
     * may emit a final chunk with `sample == null` to mark end of stream;
     * listeners should treat that as a no-op.
     */
    public data class Sequence(
        val id: String,
        val index: Int,
        val sample: AudioSampleData?,
        val volume: Float,
    ) : PlaybackEvent()

    /** Device asked all clients to halt playback (e.g. "stop" UI button). */
    public object Stop : PlaybackEvent()
}

/** Audio device type (input/output). */
public enum class AudioDevice(public val rawValue: String, public val protoValue: Int) {
    INPUT("input", 1),
    OUTPUT("output", 2);

    public companion object {
        public fun fromProto(protoValue: Int): AudioDevice? =
            entries.firstOrNull { it.protoValue == protoValue }
    }
}
