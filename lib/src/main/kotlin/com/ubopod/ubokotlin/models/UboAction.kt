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
    /**
     * Report a captured mic sample. [audioSource] tags which mic the sample
     * came from (empty = on-device system mic; a remote client sets a unique
     * id so the core binds a listening session to that one source). It must
     * match the [audioSource] on the [AssistantStartListening] that opened the
     * session, or the core drops the sample.
     */
    public data class AudioReportSample(
        val timestamp: Float,
        val sample: AudioSampleData,
        val audioSource: String = "",
    ) : UboAction()
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

    /**
     * Execute a menu item's registered action handler directly by its
     * [actionId] (every `MenuItemData` carries one over the wire). Prefer
     * this over [MenuChooseByLabel]/[MenuChooseByIcon] — those depend on the
     * server's legacy label/icon lookup staying in sync with whatever the
     * client is showing, which it isn't for every screen (prompts,
     * notably). [menuKey] lets the reducer push the result onto the stack
     * when the handler returns a submenu.
     */
    public data class ExecuteMenuAction(val actionId: String, val menuKey: String? = null) : UboAction()

    public data class StackPushMenu(val menuKey: String) : UboAction()
    public data class StackPop(val count: Int = 1) : UboAction()
    public object StackPopToRoot : UboAction()

    // ---- Input ----

    /**
     * [value] is the scalar shown to single-field callers; [data] should
     * carry every field's name -> value for multi-field forms — server
     * handlers read `result.data`, not `value`, so a form with more than
     * one field silently no-ops without it.
     */
    public data class InputProvide(
        val id: String,
        val value: String,
        val data: Map<String, String> = emptyMap(),
    ) : UboAction()
    public data class InputCancel(val id: String) : UboAction()

    // ---- File Upload ----

    /**
     * Begin a chunked upload session. Prefer the high-level
     * `UboClient.uploadFile(id, filename, data)`, which drives this plus
     * the chunk/complete steps with retry — these three cases exist so
     * `ProtoFromAction` has something to match on.
     */
    public data class FileUploadStart(
        val uploadId: String,
        val filename: String,
        val totalSize: Long,
        val totalChunks: Long,
        val chunkSize: Long,
    ) : UboAction()

    /**
     * Send one chunk of an in-progress upload. [chunkIndex] is 0-based;
     * [data] must be exactly `chunkSize` bytes except for the final chunk.
     */
    public data class FileUploadChunk(
        val uploadId: String,
        val chunkIndex: Long,
        val data: ByteArray,
    ) : UboAction() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FileUploadChunk
            return uploadId == other.uploadId &&
                chunkIndex == other.chunkIndex &&
                data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = uploadId.hashCode()
            result = 31 * result + chunkIndex.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /** Signal that every chunk has been sent. */
    public data class FileUploadComplete(val uploadId: String) : UboAction()

    // ---- Assistant ----

    /**
     * Start assistant listening. [audioSource] selects which mic the session
     * consumes (empty = on-device system mic; a remote client sets a unique id
     * so the core listens only to that client's streamed samples and ignores
     * the device's built-in mic).
     *
     * [source] is a separate axis: it says *how* the session was triggered so
     * the core can pick a turn-completion policy. Leaving it `null` means no
     * policy applies and the device logs a warning.
     */
    public data class AssistantStartListening(
        val audioSource: String = "",
        val source: AssistantTriggerSource? = null,
    ) : UboAction()
    public object AssistantStopListening : UboAction()

    /** Toggle assistant listening. See [AssistantStartListening] for [audioSource]. */
    public data class AssistantToggleListening(val audioSource: String = "") : UboAction()

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
