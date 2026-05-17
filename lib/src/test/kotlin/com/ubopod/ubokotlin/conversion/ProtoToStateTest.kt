package com.ubopod.ubokotlin.conversion

import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.ubopod.ubokotlin.models.SystemStats
import org.junit.jupiter.api.Test
import ubo.v1.Ubo

/**
 * Covers the slice-merge / event-conversion behaviour added in the
 * feature-parity push: AudioState contribution to SystemStats, camera
 * StartViewfinder carrying sourceId, and CameraDetectAdvertiseEvent
 * mapping to the new sealed-class variant.
 */
class ProtoToStateTest {

    private fun anyOf(typeUrl: String, message: com.google.protobuf.Message): Any =
        Any.newBuilder().setTypeUrl(typeUrl).setValue(message.toByteString()).build()

    @Test
    fun `audio state populates playback volume + mute fields`() {
        val audio = Ubo.AudioState.newBuilder()
            .setPlaybackVolume(0.42f)
            .setIsPlaybackMute(false)
            .setIsCaptureMute(true)
            .build()
        val merged = ProtoToState.mergeSystemStats(
            previous = null,
            results = listOf(anyOf("type.googleapis.com/ubo.v1.AudioState", audio)),
        )
        assertThat(merged).isNotNull()
        assertThat(merged!!.playbackVolume).isEqualTo(0.42f)
        assertThat(merged.isPlaybackMute).isEqualTo(false)
        assertThat(merged.isCaptureMute).isEqualTo(true)
    }

    @Test
    fun `audio state preserves prior cpu and temperature`() {
        val prior = SystemStats(cpuPercent = 25f, ramPercent = 50f, temperature = 42f)
        val audio = Ubo.AudioState.newBuilder().setPlaybackVolume(0.9f).build()
        val merged = ProtoToState.mergeSystemStats(
            previous = prior,
            results = listOf(anyOf("type.googleapis.com/ubo.v1.AudioState", audio)),
        )
        assertThat(merged).isNotNull()
        assertThat(merged!!.cpuPercent).isEqualTo(25f)
        assertThat(merged.ramPercent).isEqualTo(50f)
        assertThat(merged.temperature).isEqualTo(42f)
        assertThat(merged.playbackVolume).isEqualTo(0.9f)
    }

    @Test
    fun `camera start viewfinder event yields sourceId`() {
        val event = Ubo.Event.newBuilder()
            .setCameraStartViewfinderEvent(
                Ubo.CameraStartViewfinderEvent.newBuilder()
                    .setPattern("qr")
                    .setSourceId("phone-abc")
                    .build(),
            )
            .build()
        val converted = ProtoToState.convertCameraEvent(event)
        assertThat(converted).isInstanceOf(ProtoToState.CameraEvent.StartViewfinder::class.java)
        val start = converted as ProtoToState.CameraEvent.StartViewfinder
        assertThat(start.pattern).isEqualTo("qr")
        assertThat(start.sourceId).isEqualTo("phone-abc")
    }

    @Test
    fun `camera start viewfinder without sourceId yields empty string`() {
        val event = Ubo.Event.newBuilder()
            .setCameraStartViewfinderEvent(Ubo.CameraStartViewfinderEvent.getDefaultInstance())
            .build()
        val converted = ProtoToState.convertCameraEvent(event)
        val start = converted as ProtoToState.CameraEvent.StartViewfinder
        assertThat(start.sourceId).isEqualTo("")
        assertThat(start.pattern).isNull()
    }

    @Test
    fun `camera detect advertise event maps to DetectAdvertise`() {
        val event = Ubo.Event.newBuilder()
            .setCameraDetectAdvertiseEvent(Ubo.CameraDetectAdvertiseEvent.getDefaultInstance())
            .build()
        val converted = ProtoToState.convertCameraEvent(event)
        assertThat(converted).isEqualTo(ProtoToState.CameraEvent.DetectAdvertise)
    }

    @Test
    fun `camera stop viewfinder still maps to StopViewfinder`() {
        val event = Ubo.Event.newBuilder()
            .setCameraStopViewfinderEvent(Ubo.CameraStopViewfinderEvent.getDefaultInstance())
            .build()
        val converted = ProtoToState.convertCameraEvent(event)
        assertThat(converted).isEqualTo(ProtoToState.CameraEvent.StopViewfinder)
    }

    @Test
    fun `display render event maps to DisplayRenderData`() {
        val bytes = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val event = Ubo.Event.newBuilder()
            .setDisplayRenderEvent(
                Ubo.DisplayRenderEvent.newBuilder()
                    .setTimestamp(2.5f)
                    .setData(com.google.protobuf.ByteString.copyFrom(bytes))
                    .addAllRectangle(listOf(10L, 20L, 30L, 40L))
                    .setDensity(2f)
                    .build(),
            )
            .build()
        val converted = ProtoToState.convertDisplayRenderEvent(event)
        assertThat(converted).isNotNull()
        assertThat(converted!!.timestamp).isEqualTo(2.5)
        assertThat(converted.data).isEqualTo(bytes)
        assertThat(converted.rectangle.y1).isEqualTo(10)
        assertThat(converted.rectangle.x1).isEqualTo(20)
        assertThat(converted.rectangle.y2).isEqualTo(30)
        assertThat(converted.rectangle.x2).isEqualTo(40)
        assertThat(converted.density).isEqualTo(2f)
    }

    @Test
    fun `display render event with missing rectangle entries defaults to zero`() {
        val event = Ubo.Event.newBuilder()
            .setDisplayRenderEvent(Ubo.DisplayRenderEvent.getDefaultInstance())
            .build()
        val converted = ProtoToState.convertDisplayRenderEvent(event)
        assertThat(converted).isNotNull()
        assertThat(converted!!.rectangle).isEqualTo(
            com.ubopod.ubokotlin.models.DisplayRectangle(0, 0, 0, 0),
        )
        // density defaults to 1.0f when the wire field is 0 — see ProtoToState.
        assertThat(converted.density).isEqualTo(1f)
    }

    @Test
    fun `non display event yields null`() {
        val event = Ubo.Event.newBuilder()
            .setCameraStopViewfinderEvent(Ubo.CameraStopViewfinderEvent.getDefaultInstance())
            .build()
        assertThat(ProtoToState.convertDisplayRenderEvent(event)).isNull()
    }
}
