package com.ubopod.ubokotlin.conversion

import com.google.protobuf.Any
import com.ubopod.ubokotlin.models.AudioSampleData
import com.ubopod.ubokotlin.models.DisplayRectangle
import com.ubopod.ubokotlin.models.DisplayRenderData
import com.ubopod.ubokotlin.models.InputFieldDescription
import com.ubopod.ubokotlin.models.InputFieldType
import com.ubopod.ubokotlin.models.PlaybackEvent
import com.ubopod.ubokotlin.models.SystemStats
import com.ubopod.ubokotlin.models.WebUIInputDescription
import ubo.v1.Ubo

/**
 * Helpers for unpacking the state slices and event payloads streamed by
 * the Ubo gRPC server's `SubscribeStore` / `SubscribeEvent` RPCs into
 * Kotlin model types.
 *
 * Mirrors the Swift `convert*State`/`convert*Event` family in
 * `Sources/UboSwift/Connection/UboConnection.swift`. Dispatched by the
 * `google.protobuf.Any.typeUrl` suffix — same wire shape as the Swift
 * port, so frames round-trip without further translation.
 */
public object ProtoToState {

    // ---- Sealed event types ----

    /**
     * Camera viewfinder events. Driven by the Python core's
     * `state.camera`; the connected client starts / stops its local
     * camera capture in response.
     *
     * Mirrors the Swift `UboConnection.CameraEventType` enum.
     */
    public sealed class CameraEvent {
        /**
         * Pi has decided this source should start capturing.
         *
         * [sourceId] is the registered id of the chosen source.
         * Clients should ignore the event unless it matches their own
         * — except for empty, which means "any source" (back-compat
         * with pre-source-id devices).
         */
        public data class StartViewfinder(val pattern: String?, val sourceId: String) : CameraEvent()
        public object StopViewfinder : CameraEvent()

        /**
         * Pi tapped "Detect Cameras". Subscribed clients should respond
         * with a `CameraRegisterRemote` action so they are listed in
         * the picker.
         */
        public object DetectAdvertise : CameraEvent()
    }

    // ---- Per-frame merge of SubscribeStore results ----

    /**
     * Merge the [results] of one [SubscribeStoreResponse][store.v1.Store.SubscribeStoreResponse]
     * frame into [previous] [SystemStats]. Each result's `Any` may carry
     * a [Ubo.SystemState] (CPU / RAM), a [Ubo.LocalizationState] (clock),
     * or a [Ubo.SensorsState] (temperature / light); we union the fields
     * and emit the new snapshot.
     *
     * Returns `null` if no relevant fields were present in this frame.
     */
    public fun mergeSystemStats(previous: SystemStats?, results: List<Any>): SystemStats? {
        var current = previous ?: SystemStats()
        var changed = false
        // Match by the rightmost `.`-segment and parse `any.value` directly:
        // see [ProtoToView.unpackViewData] for the full explanation of the
        // Python betterproto `ubo_bindings.` prefix problem.
        for (any in results) {
            when (any.typeUrl.substringAfterLast('.')) {
                "SystemState" -> {
                    val s = Ubo.SystemState.parseFrom(any.value)
                    current = current.copy(
                        cpuPercent = if (s.hasCpuPercent()) s.cpuPercent else current.cpuPercent,
                        ramPercent = if (s.hasRamPercent()) s.ramPercent else current.ramPercent,
                    )
                    changed = true
                }
                "LocalizationState" -> {
                    val s = Ubo.LocalizationState.parseFrom(any.value)
                    current = current.copy(
                        clock = if (s.hasClock()) s.clock else current.clock,
                    )
                    changed = true
                }
                "SensorsState" -> {
                    val s = Ubo.SensorsState.parseFrom(any.value)
                    val t = if (s.hasTemperature() && s.temperature.hasValue()) s.temperature.value else current.temperature
                    current = current.copy(temperature = t)
                    changed = true
                }
                "AudioState" -> {
                    val s = Ubo.AudioState.parseFrom(any.value)
                    current = current.copy(
                        playbackVolume = if (s.hasPlaybackVolume()) s.playbackVolume else current.playbackVolume,
                        isPlaybackMute = if (s.hasIsPlaybackMute()) s.isPlaybackMute else current.isPlaybackMute,
                        isCaptureMute = if (s.hasIsCaptureMute()) s.isCaptureMute else current.isCaptureMute,
                    )
                    changed = true
                }
            }
        }
        return if (changed) current else null
    }

    // ---- WebUI active inputs ----

    public fun unpackActiveInputs(results: List<Any>): List<WebUIInputDescription>? {
        for (any in results) {
            if (any.typeUrl.substringAfterLast('.') != "WebUIState") continue
            val state = Ubo.WebUIState.parseFrom(any.value)
            return state.activeInputsList.map { convertWebUIInput(it) }
        }
        return null
    }

    public fun convertWebUIInput(p: Ubo.WebUIInputDescription): WebUIInputDescription = WebUIInputDescription(
        id = if (p.hasId()) p.id else "",
        title = if (p.hasTitle()) p.title else null,
        prompt = if (p.hasPrompt()) p.prompt else null,
        fields = if (p.hasFields()) p.fields.itemsList.map { convertInputField(it) } else emptyList(),
    )

    private fun convertInputField(p: Ubo.InputFieldDescription): InputFieldDescription = InputFieldDescription(
        name = p.name,
        label = p.label,
        type = mapInputFieldType(p.type),
        description = if (p.hasDescription()) p.description else null,
        title = if (p.hasTitle()) p.title else null,
        fileMimetype = if (p.hasFileMimetype()) p.fileMimetype else null,
        pattern = if (p.hasPattern()) p.pattern else null,
        defaultValue = if (p.hasDefaultValue()) p.defaultValue else null,
        options = if (p.hasOptions()) p.options.itemsList else emptyList(),
        required = if (p.hasRequired()) p.required else false,
    )

    private fun mapInputFieldType(p: Ubo.InputFieldType): InputFieldType = when (p) {
        Ubo.InputFieldType.INPUT_FIELD_TYPE_LONG -> InputFieldType.LONG
        Ubo.InputFieldType.INPUT_FIELD_TYPE_TEXT -> InputFieldType.TEXT
        Ubo.InputFieldType.INPUT_FIELD_TYPE_PASSWORD -> InputFieldType.PASSWORD
        Ubo.InputFieldType.INPUT_FIELD_TYPE_NUMBER -> InputFieldType.NUMBER
        Ubo.InputFieldType.INPUT_FIELD_TYPE_CHECKBOX -> InputFieldType.CHECKBOX
        Ubo.InputFieldType.INPUT_FIELD_TYPE_COLOR -> InputFieldType.COLOR
        Ubo.InputFieldType.INPUT_FIELD_TYPE_SELECT -> InputFieldType.SELECT
        Ubo.InputFieldType.INPUT_FIELD_TYPE_FILE -> InputFieldType.FILE
        Ubo.InputFieldType.INPUT_FIELD_TYPE_DATE -> InputFieldType.DATE
        Ubo.InputFieldType.INPUT_FIELD_TYPE_TIME -> InputFieldType.TIME
        else -> InputFieldType.TEXT
    }

    // ---- Camera events from SubscribeEvent frames ----

    public fun convertCameraEvent(event: Ubo.Event): CameraEvent? = when (event.eventCase) {
        Ubo.Event.EventCase.CAMERA_START_VIEWFINDER_EVENT -> {
            val payload = event.cameraStartViewfinderEvent
            CameraEvent.StartViewfinder(
                pattern = if (payload.hasPattern()) payload.pattern else null,
                sourceId = if (payload.hasSourceId()) payload.sourceId else "",
            )
        }
        Ubo.Event.EventCase.CAMERA_STOP_VIEWFINDER_EVENT -> CameraEvent.StopViewfinder
        Ubo.Event.EventCase.CAMERA_DETECT_ADVERTISE_EVENT -> CameraEvent.DetectAdvertise
        else -> null
    }

    // ---- Audio playback events from SubscribeEvent frames ----

    public fun convertPlaybackEvent(event: Ubo.Event): PlaybackEvent? = when (event.eventCase) {
        Ubo.Event.EventCase.AUDIO_PLAY_AUDIO_SAMPLE_EVENT -> {
            val p = event.audioPlayAudioSampleEvent
            PlaybackEvent.Sample(
                sample = convertAudioSample(p.sample),
                volume = p.volume,
            )
        }
        Ubo.Event.EventCase.AUDIO_PLAY_AUDIO_SEQUENCE_EVENT -> {
            val p = event.audioPlayAudioSequenceEvent
            PlaybackEvent.Sequence(
                id = p.id,
                index = p.index.toInt(),
                sample = if (p.hasSample()) convertAudioSample(p.sample) else null,
                volume = p.volume,
            )
        }
        Ubo.Event.EventCase.AUDIO_STOP_PLAYBACK_EVENT -> PlaybackEvent.Stop
        else -> null
    }

    private fun convertAudioSample(p: Ubo.AudioSample): AudioSampleData = AudioSampleData(
        data = p.data.toByteArray(),
        channels = p.channels.toInt(),
        rate = p.rate.toInt(),
        width = p.width.toInt(),
    )

    // ---- Display render events from SubscribeEvent frames ----

    /**
     * Convert a [Ubo.DisplayRenderEvent] into the public [DisplayRenderData]
     * model. The proto `rectangle` is a `repeated int64` of four entries
     * `[y1, x1, y2, x2]`; missing trailing entries default to `0` for
     * back-compat with the Swift port's safe-indexing logic.
     */
    public fun convertDisplayRenderEvent(event: Ubo.Event): DisplayRenderData? =
        if (event.eventCase == Ubo.Event.EventCase.DISPLAY_RENDER_EVENT) {
            val p = event.displayRenderEvent
            val rect = p.rectangleList
            DisplayRenderData(
                // `timestamp` and `density` are non-optional proto3 scalars
                // — no `hasX()` accessors. Treat their default (0f) as
                // "not provided" for [density] and surface a 1.0 default
                // so callers don't divide by zero.
                timestamp = p.timestamp.toDouble(),
                data = p.data.toByteArray(),
                rectangle = DisplayRectangle(
                    y1 = rect.getOrNull(0)?.toInt() ?: 0,
                    x1 = rect.getOrNull(1)?.toInt() ?: 0,
                    y2 = rect.getOrNull(2)?.toInt() ?: 0,
                    x2 = rect.getOrNull(3)?.toInt() ?: 0,
                ),
                density = if (p.density > 0f) p.density else 1f,
            )
        } else null
}
