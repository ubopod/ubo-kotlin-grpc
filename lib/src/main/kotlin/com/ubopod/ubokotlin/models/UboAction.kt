package com.ubopod.ubokotlin.models

/**
 * Actions that can be dispatched to an Ubo device.
 *
 * Mirrors `Sources/UboSwift/Models/UboAction.swift`. Each subclass corresponds
 * to a single Swift `enum` case and maps 1:1 onto an oneof variant in the
 * generated `Ubo.Action` proto. Conversion to the proto wire form lives in
 * the conversion package.
 */
public sealed class UboAction {

    // ---- Keypad ----

    public data class KeypadKeyPress(val key: Key, val time: Double = 0.0) : UboAction()
    public data class KeypadKeyPressMultiple(val keys: Set<Key>, val time: Double = 0.0) : UboAction()
    public data class KeypadKeyRelease(val key: Key, val time: Double = 0.0) : UboAction()
    public data class KeypadKeyHold(val key: Key, val time: Double = 0.0) : UboAction()
    public data class KeypadKeyUnhold(val key: Key, val time: Double = 0.0) : UboAction()

    // ---- Audio ----

    public data class AudioSetVolume(val level: Float, val device: AudioDevice) : UboAction()
    public data class AudioChangeVolume(val change: Float, val device: AudioDevice) : UboAction()
    public data class AudioSetMute(val muted: Boolean, val device: AudioDevice) : UboAction()
    public data class AudioToggleMute(val device: AudioDevice) : UboAction()
    public data class AudioPlayChime(val chime: Chime) : UboAction()
    public data class AudioReportSample(val timestamp: Float, val sample: AudioSampleData) : UboAction()
    public object AudioStartRecording : UboAction()
    public object AudioStopRecording : UboAction()
    public object AudioPlayRecording : UboAction()

    // ---- Display ----

    public object DisplayBlank : UboAction()
    public object DisplayUnblank : UboAction()
    public object DisplayPause : UboAction()
    public object DisplayResume : UboAction()
    public object DisplayRedraw : UboAction()
    public data class DisplaySetBlankTimeout(val timeout: DisplayBlankTimeout) : UboAction()

    // ---- RGB Ring ----

    public data class RgbRingSetAll(val color: UboColor) : UboAction()
    public object RgbRingBlank : UboAction()
    public data class RgbRingSetBrightness(val level: Float) : UboAction()
    public data class RgbRingSetEnabled(val enabled: Boolean) : UboAction()
    public data class RgbRingPulse(val color: UboColor, val repetitions: Int, val wait: Double) : UboAction()
    public data class RgbRingBlink(val color: UboColor, val repetitions: Int, val wait: Double) : UboAction()
    public data class RgbRingRainbow(val rounds: Int, val wait: Double) : UboAction()
    public data class RgbRingSpinningWheel(
        val color: UboColor,
        val rounds: Int,
        val length: Int,
        val wait: Double,
    ) : UboAction()
    public data class RgbRingProgressWheel(val color: UboColor, val percentage: Int) : UboAction()

    // ---- Power ----

    public object PowerOff : UboAction()
    public object Reboot : UboAction()

    // ---- Notifications ----

    public data class NotificationAdd(val notification: UboNotification) : UboAction()
    public data class NotificationRemove(val id: String) : UboAction()
    public object NotificationClearAll : UboAction()
    public data class NotificationDisplay(val notification: UboNotification) : UboAction()

    // ---- Navigation ----

    public object MenuGoBack : UboAction()
    public object MenuGoHome : UboAction()
    public object MenuScrollUp : UboAction()
    public object MenuScrollDown : UboAction()
    public data class MenuChooseByIndex(val index: Int) : UboAction()
    public data class MenuChooseByLabel(val label: String) : UboAction()
    public data class MenuChooseByIcon(val icon: String) : UboAction()
    public data class StackPushMenu(val menuKey: String) : UboAction()
    public data class StackPop(val count: Int = 1) : UboAction()
    public object StackPopToRoot : UboAction()

    // ---- Input ----

    public data class InputProvide(val id: String, val value: String) : UboAction()
    public data class InputCancel(val id: String) : UboAction()

    // ---- Assistant ----

    public object AssistantStartListening : UboAction()
    public object AssistantStopListening : UboAction()
    public object AssistantToggleListening : UboAction()

    // ---- Camera ----

    /**
     * Register this client as a remote camera source on the device.
     * The Pi-side camera picker lists it alongside any local cameras;
     * selecting it triggers a `CameraStartViewfinderEvent` tagged with
     * the same [sourceId].
     */
    public data class CameraRegisterRemote(val sourceId: String, val label: String) : UboAction()

    /**
     * Report one camera frame to the device. The Pi-side reducer
     * forwards the frame to the QR decoder + viewfinder display only
     * when [sourceId] matches the currently-selected source; remote
     * clients can never dispatch events directly, so frames must go
     * through this action.
     */
    public data class CameraReportImage(
        val timestamp: Float,
        val data: ByteArray,
        val width: Int,
        val height: Int,
        val sourceId: String,
    ) : UboAction() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CameraReportImage
            return timestamp == other.timestamp &&
                width == other.width &&
                height == other.height &&
                sourceId == other.sourceId &&
                data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = timestamp.hashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + sourceId.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * Toggle playback of an audio chat bubble. The Pi-side chat reducer flips
     * the bubble's `is_playing` flag and starts/stops audio. On hardware this
     * is bound to the bubble's L1/L2/L3 button; touch clients dispatch it when
     * the bubble is tapped.
     */
    public data class ChatToggleAudioPlayback(val messageId: String) : UboAction()
}
