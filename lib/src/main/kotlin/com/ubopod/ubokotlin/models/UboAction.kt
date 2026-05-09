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
}
