package com.ubopod.ubokotlin.conversion

import com.google.common.truth.Truth.assertThat
import com.ubopod.ubokotlin.models.AudioDevice
import com.ubopod.ubokotlin.models.AudioSampleData
import com.ubopod.ubokotlin.models.Chime
import com.ubopod.ubokotlin.models.DisplayBlankTimeout
import com.ubopod.ubokotlin.models.Key
import com.ubopod.ubokotlin.models.UboAction
import com.ubopod.ubokotlin.models.UboColor
import com.ubopod.ubokotlin.models.UboNotification
import org.junit.jupiter.api.Test
import ubo.v1.Ubo

/**
 * Mirrors `Tests/UboSwiftTests/ActionBuildTests.swift`. Each test builds an
 * [UboAction] subclass and asserts that [ProtoFromAction.toProto] produces an
 * [Ubo.Action] with the matching oneof case set.
 */
class ActionBuildTest {

    private fun build(action: UboAction): Ubo.Action = ProtoFromAction.toProto(action)

    @Test
    fun `keypad key press encodes key and time`() {
        val proto = build(UboAction.KeypadKeyPress(Key.UP, time = 1.5))
        assertThat(proto.actionCase).isEqualTo(Ubo.Action.ActionCase.KEYPAD_KEY_PRESS_ACTION)
        val payload = proto.keypadKeyPressAction
        assertThat(payload.key).isEqualTo(Ubo.Key.KEY_UP)
        assertThat(payload.time).isEqualTo(1.5f)
    }

    @Test
    fun `audio set volume encodes level and device`() {
        val proto = build(UboAction.AudioSetVolume(0.75f, AudioDevice.OUTPUT))
        assertThat(proto.actionCase).isEqualTo(Ubo.Action.ActionCase.AUDIO_SET_VOLUME_ACTION)
        assertThat(proto.audioSetVolumeAction.volume).isEqualTo(0.75f)
        assertThat(proto.audioSetVolumeAction.device).isEqualTo(Ubo.AudioDevice.AUDIO_DEVICE_OUTPUT)
    }

    @Test
    fun `audio play chime encodes chime name`() {
        val proto = build(UboAction.AudioPlayChime(Chime.DONE))
        assertThat(proto.actionCase).isEqualTo(Ubo.Action.ActionCase.AUDIO_PLAY_CHIME_ACTION)
        assertThat(proto.audioPlayChimeAction.name).isEqualTo("done")
    }

    @Test
    fun `audio report sample encodes timestamp + sample bytes`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val proto = build(
            UboAction.AudioReportSample(
                timestamp = 1.5f,
                sample = AudioSampleData(bytes, channels = 1, rate = 16000, width = 2),
            ),
        )
        assertThat(proto.actionCase).isEqualTo(Ubo.Action.ActionCase.AUDIO_REPORT_SAMPLE_ACTION)
        val payload = proto.audioReportSampleAction
        assertThat(payload.timestamp).isEqualTo(1.5f)
        assertThat(payload.sample.data.toByteArray()).isEqualTo(bytes)
        assertThat(payload.sample.channels).isEqualTo(1L)
        assertThat(payload.sample.rate).isEqualTo(16000L)
        assertThat(payload.sample.width).isEqualTo(2L)
    }

    @Test
    fun `display set blank timeout encodes enum`() {
        val proto = build(UboAction.DisplaySetBlankTimeout(DisplayBlankTimeout.FIVE_MINUTES))
        assertThat(proto.actionCase).isEqualTo(Ubo.Action.ActionCase.DISPLAY_SET_BLANK_TIMEOUT_ACTION)
        assertThat(proto.displaySetBlankTimeoutAction.timeout)
            .isEqualTo(Ubo.DisplayBlankTimeout.DISPLAY_BLANK_TIMEOUT_FIVE_MINUTES)
    }

    @Test
    fun `rgb ring set all encodes color components`() {
        val proto = build(UboAction.RgbRingSetAll(UboColor(255, 128, 64, 200)))
        assertThat(proto.actionCase).isEqualTo(Ubo.Action.ActionCase.RGB_RING_SET_ALL_ACTION)
        val items = proto.rgbRingSetAllAction.color.itemsList.map { it.int64 }
        assertThat(items).containsExactly(255L, 128L, 64L, 200L).inOrder()
    }

    @Test
    fun `notification add carries title and content`() {
        val proto = build(
            UboAction.NotificationAdd(
                UboNotification(id = "n-1", title = "Hi", content = "World"),
            ),
        )
        assertThat(proto.actionCase).isEqualTo(Ubo.Action.ActionCase.NOTIFICATIONS_ADD_ACTION)
        val n = proto.notificationsAddAction.notification
        assertThat(n.id).isEqualTo("n-1")
        assertThat(n.title).isEqualTo("Hi")
        assertThat(n.content).isEqualTo("World")
    }

    @Test
    fun `menu navigation maps to the matching oneof`() {
        assertThat(build(UboAction.MenuGoBack).actionCase)
            .isEqualTo(Ubo.Action.ActionCase.MENU_GO_BACK_ACTION)
        assertThat(build(UboAction.MenuGoHome).actionCase)
            .isEqualTo(Ubo.Action.ActionCase.MENU_GO_HOME_ACTION)
        assertThat(build(UboAction.MenuScrollUp).menuScrollAction.direction)
            .isEqualTo(Ubo.MenuScrollDirection.MENU_SCROLL_DIRECTION_UP)
        assertThat(build(UboAction.MenuScrollDown).menuScrollAction.direction)
            .isEqualTo(Ubo.MenuScrollDirection.MENU_SCROLL_DIRECTION_DOWN)
    }

    @Test
    fun `stack push menu carries menuKey`() {
        val proto = build(UboAction.StackPushMenu("wifi"))
        assertThat(proto.actionCase).isEqualTo(Ubo.Action.ActionCase.STACK_PUSH_MENU_ACTION)
        assertThat(proto.stackPushMenuAction.menuKey).isEqualTo("wifi")
    }

    @Test
    fun `stack pop carries count`() {
        val proto = build(UboAction.StackPop(count = 3))
        assertThat(proto.actionCase).isEqualTo(Ubo.Action.ActionCase.STACK_POP_ACTION)
        assertThat(proto.stackPopAction.count).isEqualTo(3L)
    }

    @Test
    fun `input provide carries id and value`() {
        val proto = build(UboAction.InputProvide(id = "abc", value = "secret"))
        assertThat(proto.actionCase).isEqualTo(Ubo.Action.ActionCase.INPUT_PROVIDE_ACTION)
        assertThat(proto.inputProvideAction.id).isEqualTo("abc")
        assertThat(proto.inputProvideAction.value).isEqualTo("secret")
    }

    @Test
    fun `power and reboot use empty oneof variants`() {
        assertThat(build(UboAction.PowerOff).actionCase)
            .isEqualTo(Ubo.Action.ActionCase.POWER_OFF_ACTION)
        assertThat(build(UboAction.Reboot).actionCase)
            .isEqualTo(Ubo.Action.ActionCase.REBOOT_ACTION)
    }
}
