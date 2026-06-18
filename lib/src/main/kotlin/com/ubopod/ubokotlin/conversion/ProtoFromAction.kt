package com.ubopod.ubokotlin.conversion

import com.google.protobuf.ByteString
import com.ubopod.ubokotlin.models.AudioDevice
import com.ubopod.ubokotlin.models.AudioSampleData
import com.ubopod.ubokotlin.models.Chime
import com.ubopod.ubokotlin.models.DisplayBlankTimeout
import com.ubopod.ubokotlin.models.Key
import com.ubopod.ubokotlin.models.NotificationImportance
import com.ubopod.ubokotlin.models.UboAction
import com.ubopod.ubokotlin.models.UboColor
import com.ubopod.ubokotlin.models.UboNotification
import ubo.v1.Ubo

/**
 * Translate a [UboAction] sealed-class instance into the matching
 * [Ubo.Action] proto oneof variant. Mirrors the Swift `buildProtoAction(_:)`
 * function in `Sources/UboSwift/Connection/UboConnection.swift`.
 *
 * Each branch sets exactly one oneof field so the server-side dispatcher
 * picks the right reducer.
 */
public object ProtoFromAction {

    public fun toProto(action: UboAction): Ubo.Action {
        val builder = Ubo.Action.newBuilder()
        when (action) {
            // ---- Keypad ----
            is UboAction.KeypadKeyPress -> builder.setKeypadKeyPressAction(
                Ubo.KeypadKeyPressAction.newBuilder()
                    .setKey(toProtoKey(action.key))
                    .setTime(action.time.toFloat())
                    .build(),
            )
            is UboAction.KeypadKeyPressMultiple -> builder.setKeypadKeyPressAction(
                Ubo.KeypadKeyPressAction.newBuilder()
                    .apply {
                        action.keys.firstOrNull()?.let { setKey(toProtoKey(it)) }
                        action.keys.forEach { addPressedKeys(toProtoKey(it)) }
                        setTime(action.time.toFloat())
                    }
                    .build(),
            )
            is UboAction.KeypadKeyRelease -> builder.setKeypadKeyReleaseAction(
                Ubo.KeypadKeyReleaseAction.newBuilder()
                    .setKey(toProtoKey(action.key))
                    .setTime(action.time.toFloat())
                    .build(),
            )
            is UboAction.KeypadKeyHold -> builder.setKeypadKeyHoldAction(
                Ubo.KeypadKeyHoldAction.newBuilder()
                    .setKey(toProtoKey(action.key))
                    .setTime(action.time.toFloat())
                    .build(),
            )
            is UboAction.KeypadKeyUnhold -> builder.setKeypadKeyUnholdAction(
                Ubo.KeypadKeyUnholdAction.newBuilder()
                    .setKey(toProtoKey(action.key))
                    .setTime(action.time.toFloat())
                    .build(),
            )

            // ---- Audio ----
            is UboAction.AudioSetVolume -> builder.setAudioSetVolumeAction(
                Ubo.AudioSetVolumeAction.newBuilder()
                    .setVolume(action.level)
                    .setDevice(toProtoAudioDevice(action.device))
                    .build(),
            )
            is UboAction.AudioChangeVolume -> builder.setAudioChangeVolumeAction(
                Ubo.AudioChangeVolumeAction.newBuilder()
                    .setAmount(action.change)
                    .setDevice(toProtoAudioDevice(action.device))
                    .build(),
            )
            is UboAction.AudioSetMute -> builder.setAudioSetMuteStatusAction(
                Ubo.AudioSetMuteStatusAction.newBuilder()
                    .setIsMute(action.muted)
                    .setDevice(toProtoAudioDevice(action.device))
                    .build(),
            )
            is UboAction.AudioToggleMute -> builder.setAudioToggleMuteStatusAction(
                Ubo.AudioToggleMuteStatusAction.newBuilder()
                    .setDevice(toProtoAudioDevice(action.device))
                    .build(),
            )
            is UboAction.AudioPlayChime -> builder.setAudioPlayChimeAction(
                Ubo.AudioPlayChimeAction.newBuilder()
                    .setName(action.chime.rawValue)
                    .build(),
            )
            is UboAction.AudioReportSample -> builder.setAudioReportSampleAction(
                Ubo.AudioReportSampleAction.newBuilder()
                    .setTimestamp(action.timestamp)
                    // The assistant pipeline consumes `sample_speech_recognition`
                    // (raw PCM16 bytes), NOT the `sample` AudioSample (that feeds
                    // the recording path). The Web UI sets this; we must too, or
                    // the core pushes empty frames and nothing reaches the assistant.
                    .setSampleSpeechRecognition(ByteString.copyFrom(action.sample.data))
                    .setSample(toProtoAudioSample(action.sample))
                    .setAudioSource(action.audioSource)
                    .build(),
            )
            UboAction.AudioStartRecording -> builder.setAudioStartRecordingAction(
                Ubo.AudioStartRecordingAction.getDefaultInstance(),
            )
            UboAction.AudioStopRecording -> builder.setAudioStopRecordingAction(
                Ubo.AudioStopRecordingAction.getDefaultInstance(),
            )
            UboAction.AudioPlayRecording -> builder.setAudioPlayRecordingAction(
                Ubo.AudioPlayRecordingAction.getDefaultInstance(),
            )

            // ---- Display ----
            UboAction.DisplayBlank -> builder.setDisplayBlankAction(
                Ubo.DisplayBlankAction.getDefaultInstance(),
            )
            UboAction.DisplayUnblank -> builder.setDisplayUnblankAction(
                Ubo.DisplayUnblankAction.getDefaultInstance(),
            )
            UboAction.DisplayPause -> builder.setDisplayPauseAction(
                Ubo.DisplayPauseAction.getDefaultInstance(),
            )
            UboAction.DisplayResume -> builder.setDisplayResumeAction(
                Ubo.DisplayResumeAction.getDefaultInstance(),
            )
            UboAction.DisplayRedraw -> builder.setDisplayRedrawAction(
                Ubo.DisplayRedrawAction.getDefaultInstance(),
            )
            is UboAction.DisplaySetBlankTimeout -> builder.setDisplaySetBlankTimeoutAction(
                Ubo.DisplaySetBlankTimeoutAction.newBuilder()
                    .setTimeout(toProtoBlankTimeout(action.timeout))
                    .build(),
            )

            // ---- RGB Ring ----
            is UboAction.RgbRingSetAll -> builder.setRgbRingSetAllAction(
                Ubo.RgbRingSetAllAction.newBuilder()
                    .setColor(toProtoRgbColor(action.color))
                    .build(),
            )
            UboAction.RgbRingBlank -> builder.setRgbRingBlankAction(
                Ubo.RgbRingBlankAction.getDefaultInstance(),
            )
            is UboAction.RgbRingSetBrightness -> builder.setRgbRingSetBrightnessAction(
                Ubo.RgbRingSetBrightnessAction.newBuilder()
                    .setBrightness(action.level)
                    .build(),
            )
            is UboAction.RgbRingSetEnabled -> builder.setRgbRingSetEnabledAction(
                Ubo.RgbRingSetEnabledAction.newBuilder()
                    .setEnabled(action.enabled)
                    .build(),
            )
            is UboAction.RgbRingPulse -> builder.setRgbRingPulseAction(
                Ubo.RgbRingPulseAction.newBuilder()
                    .setColor(toProtoRgbColor(action.color))
                    .setRepetitions(action.repetitions.toLong())
                    .setWait(action.wait.toLong())
                    .build(),
            )
            is UboAction.RgbRingBlink -> builder.setRgbRingBlinkAction(
                Ubo.RgbRingBlinkAction.newBuilder()
                    .setColor(toProtoRgbColor(action.color))
                    .setRepetitions(action.repetitions.toLong())
                    .setWait(action.wait.toLong())
                    .build(),
            )
            is UboAction.RgbRingRainbow -> builder.setRgbRingRainbowAction(
                Ubo.RgbRingRainbowAction.newBuilder()
                    .setRounds(action.rounds.toLong())
                    .setWait(action.wait.toLong())
                    .build(),
            )
            is UboAction.RgbRingSpinningWheel -> builder.setRgbRingSpinningWheelAction(
                Ubo.RgbRingSpinningWheelAction.newBuilder()
                    .setColor(toProtoRgbColor(action.color))
                    .setRepetitions(action.rounds.toLong())
                    .setLength(action.length.toLong())
                    .setWait(action.wait.toLong())
                    .build(),
            )
            is UboAction.RgbRingProgressWheel -> builder.setRgbRingProgressWheelAction(
                Ubo.RgbRingProgressWheelAction.newBuilder()
                    .setColor(toProtoRgbColor(action.color))
                    .setPercentage(action.percentage.toFloat())
                    .build(),
            )

            // ---- Power ----
            UboAction.PowerOff -> builder.setPowerOffAction(Ubo.PowerOffAction.getDefaultInstance())
            UboAction.Reboot -> builder.setRebootAction(Ubo.RebootAction.getDefaultInstance())

            // ---- Notifications ----
            is UboAction.NotificationAdd -> builder.setNotificationsAddAction(
                Ubo.NotificationsAddAction.newBuilder()
                    .setNotification(toProtoNotification(action.notification))
                    .build(),
            )
            is UboAction.NotificationRemove -> builder.setNotificationsClearByIdAction(
                Ubo.NotificationsClearByIdAction.newBuilder()
                    .setId(action.id)
                    .build(),
            )
            UboAction.NotificationClearAll -> builder.setNotificationsClearAllAction(
                Ubo.NotificationsClearAllAction.getDefaultInstance(),
            )
            is UboAction.NotificationDisplay -> builder.setNotificationsDisplayAction(
                Ubo.NotificationsDisplayAction.newBuilder()
                    .setNotification(toProtoNotification(action.notification))
                    .build(),
            )

            // ---- Navigation ----
            UboAction.MenuGoBack -> builder.setMenuGoBackAction(
                Ubo.MenuGoBackAction.getDefaultInstance(),
            )
            UboAction.MenuGoHome -> builder.setMenuGoHomeAction(
                Ubo.MenuGoHomeAction.getDefaultInstance(),
            )
            UboAction.MenuScrollUp -> builder.setMenuScrollAction(
                Ubo.MenuScrollAction.newBuilder()
                    .setDirection(Ubo.MenuScrollDirection.MENU_SCROLL_DIRECTION_UP)
                    .build(),
            )
            UboAction.MenuScrollDown -> builder.setMenuScrollAction(
                Ubo.MenuScrollAction.newBuilder()
                    .setDirection(Ubo.MenuScrollDirection.MENU_SCROLL_DIRECTION_DOWN)
                    .build(),
            )
            is UboAction.MenuChooseByIndex -> builder.setMenuChooseByIndexAction(
                Ubo.MenuChooseByIndexAction.newBuilder()
                    .setIndex(action.index.toLong())
                    .build(),
            )
            is UboAction.MenuChooseByLabel -> builder.setMenuChooseByLabelAction(
                Ubo.MenuChooseByLabelAction.newBuilder()
                    .setLabel(action.label)
                    .build(),
            )
            is UboAction.MenuChooseByIcon -> builder.setMenuChooseByIconAction(
                Ubo.MenuChooseByIconAction.newBuilder()
                    .setIcon(action.icon)
                    .build(),
            )
            is UboAction.StackPushMenu -> builder.setStackPushMenuAction(
                Ubo.StackPushMenuAction.newBuilder()
                    .setMenuKey(action.menuKey)
                    .build(),
            )
            is UboAction.StackPop -> builder.setStackPopAction(
                Ubo.StackPopAction.newBuilder()
                    .setCount(action.count.toLong())
                    .build(),
            )
            UboAction.StackPopToRoot -> builder.setStackPopToRootAction(
                Ubo.StackPopToRootAction.getDefaultInstance(),
            )

            // ---- Input ----
            is UboAction.InputProvide -> builder.setInputProvideAction(
                Ubo.InputProvideAction.newBuilder()
                    .setId(action.id)
                    .setValue(action.value)
                    .build(),
            )
            is UboAction.InputCancel -> builder.setInputCancelAction(
                Ubo.InputCancelAction.newBuilder()
                    .setId(action.id)
                    .build(),
            )

            // ---- Assistant ----
            is UboAction.AssistantStartListening -> builder.setAssistantStartListeningAction(
                Ubo.AssistantStartListeningAction.newBuilder()
                    .setAudioSource(action.audioSource)
                    .build(),
            )
            UboAction.AssistantStopListening -> builder.setAssistantStopListeningAction(
                Ubo.AssistantStopListeningAction.getDefaultInstance(),
            )
            is UboAction.AssistantToggleListening -> builder.setAssistantToggleListeningAction(
                Ubo.AssistantToggleListeningAction.newBuilder()
                    .setAudioSource(action.audioSource)
                    .build(),
            )

            // ---- Camera ----
            is UboAction.CameraRegisterRemote -> builder.setCameraRegisterRemoteAction(
                Ubo.CameraRegisterRemoteAction.newBuilder()
                    .setSourceId(action.sourceId)
                    .setLabel(action.label)
                    .build(),
            )
            is UboAction.CameraReportImage -> builder.setCameraReportImageAction(
                Ubo.CameraReportImageAction.newBuilder()
                    .setTimestamp(action.timestamp)
                    .setData(ByteString.copyFrom(action.data))
                    .setWidth(action.width.toLong())
                    .setHeight(action.height.toLong())
                    .setSourceId(action.sourceId)
                    .build(),
            )
            is UboAction.ChatToggleAudioPlayback -> builder.setChatToggleAudioPlaybackAction(
                Ubo.ChatToggleAudioPlaybackAction.newBuilder()
                    .setMessageId(action.messageId)
                    .build(),
            )
        }
        return builder.build()
    }

    // -------- Enum mappers --------

    private fun toProtoKey(key: Key): Ubo.Key = when (key) {
        Key.BACK -> Ubo.Key.KEY_BACK
        Key.HOME -> Ubo.Key.KEY_HOME
        Key.UP -> Ubo.Key.KEY_UP
        Key.DOWN -> Ubo.Key.KEY_DOWN
        Key.L1 -> Ubo.Key.KEY_L1
        Key.L2 -> Ubo.Key.KEY_L2
        Key.L3 -> Ubo.Key.KEY_L3
    }

    private fun toProtoAudioDevice(device: AudioDevice): Ubo.AudioDevice = when (device) {
        AudioDevice.INPUT -> Ubo.AudioDevice.AUDIO_DEVICE_INPUT
        AudioDevice.OUTPUT -> Ubo.AudioDevice.AUDIO_DEVICE_OUTPUT
    }

    @Suppress("unused")
    private fun toProtoChime(chime: Chime): Ubo.Chime = when (chime) {
        Chime.ADD -> Ubo.Chime.CHIME_ADD
        Chime.DONE -> Ubo.Chime.CHIME_DONE
        Chime.FAILURE -> Ubo.Chime.CHIME_FAILURE
        Chime.VOLUME_CHANGE -> Ubo.Chime.CHIME_VOLUME_CHANGE
    }

    private fun toProtoBlankTimeout(timeout: DisplayBlankTimeout): Ubo.DisplayBlankTimeout = when (timeout) {
        DisplayBlankTimeout.ONE_MINUTE -> Ubo.DisplayBlankTimeout.DISPLAY_BLANK_TIMEOUT_ONE_MINUTE
        DisplayBlankTimeout.FIVE_MINUTES -> Ubo.DisplayBlankTimeout.DISPLAY_BLANK_TIMEOUT_FIVE_MINUTES
        DisplayBlankTimeout.TEN_MINUTES -> Ubo.DisplayBlankTimeout.DISPLAY_BLANK_TIMEOUT_TEN_MINUTES
        DisplayBlankTimeout.THIRTY_MINUTES -> Ubo.DisplayBlankTimeout.DISPLAY_BLANK_TIMEOUT_THIRTY_MINUTES
        DisplayBlankTimeout.ONE_HOUR -> Ubo.DisplayBlankTimeout.DISPLAY_BLANK_TIMEOUT_ONE_HOUR
        DisplayBlankTimeout.OFF -> Ubo.DisplayBlankTimeout.DISPLAY_BLANK_TIMEOUT_OFF
    }

    private fun toProtoImportance(level: NotificationImportance): Ubo.Importance = when (level) {
        NotificationImportance.CRITICAL -> Ubo.Importance.IMPORTANCE_CRITICAL
        NotificationImportance.HIGH -> Ubo.Importance.IMPORTANCE_HIGH
        NotificationImportance.MEDIUM -> Ubo.Importance.IMPORTANCE_MEDIUM
        NotificationImportance.LOW -> Ubo.Importance.IMPORTANCE_LOW
    }

    // -------- Aggregate value mappers --------

    private fun toProtoRgbColor(color: UboColor): Ubo.RgbColor =
        Ubo.RgbColor.newBuilder()
            .addItems(rgbInt(color.red))
            .addItems(rgbInt(color.green))
            .addItems(rgbInt(color.blue))
            .addItems(rgbInt(color.alpha))
            .build()

    private fun rgbInt(value: Int): Ubo.RgbColorElement =
        Ubo.RgbColorElement.newBuilder().setInt64(value.toLong()).build()

    private fun toProtoAudioSample(sample: AudioSampleData): Ubo.AudioSample =
        Ubo.AudioSample.newBuilder()
            .setData(ByteString.copyFrom(sample.data))
            .setChannels(sample.channels.toLong())
            .setRate(sample.rate.toLong())
            .setWidth(sample.width.toLong())
            .build()

    private fun toProtoNotification(n: UboNotification): Ubo.Notification {
        val b = Ubo.Notification.newBuilder()
            .setId(n.id)
            .setTitle(n.title)
            .setContent(n.content)
            .setImportance(toProtoImportance(n.importance))
        n.icon?.let { b.setIcon(it) }
        n.chime?.let { b.setChime(toProtoChime(it)) }
        return b.build()
    }
}
