package com.ubopod.ubokotlin.conversion

import com.google.protobuf.Any
import com.ubopod.ubokotlin.models.AudioSampleData
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
     */
    public sealed class CameraEvent {
        public data class StartViewfinder(val pattern: String?) : CameraEvent()
        public object StopViewfinder : CameraEvent()
    }

    // ---- Per-frame merge of SubscribeStore results ----

    /**
     * Merge the [results] of one [SubscribeStoreResponse][store.v1.Store.SubscribeStoreResponse]
     * frame into [previous] [SystemStats]. Each result's `Any` may carry
     * either a [Ubo.SystemState] (CPU / RAM / clock) or [Ubo.SensorsState]
     * (temperature / light); we union the fields and emit the new
     * snapshot.
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
            CameraEvent.StartViewfinder(if (payload.hasPattern()) payload.pattern else null)
        }
        Ubo.Event.EventCase.CAMERA_STOP_VIEWFINDER_EVENT -> CameraEvent.StopViewfinder
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
}
