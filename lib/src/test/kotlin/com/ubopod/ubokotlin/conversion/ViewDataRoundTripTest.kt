package com.ubopod.ubokotlin.conversion

import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.protobuf.ByteString
import com.google.protobuf.Message
import com.ubopod.ubokotlin.models.RenderKind
import com.ubopod.ubokotlin.models.RenderPropValue
import com.ubopod.ubokotlin.models.ViewData
import org.junit.jupiter.api.Test
import ubo.v1.Ubo

/**
 * Mirrors `Tests/UboSwiftTests/ViewDataRoundTripTests.swift`. For each of the
 * seven ViewData types this suite builds the corresponding proto message,
 * wraps it in `google.protobuf.Any`, and asserts that
 * [ProtoToView.unpackViewData] produces the matching Kotlin [ViewData] case
 * with non-empty fields.
 */
class ViewDataRoundTripTest {

    private fun anyFrom(msg: Message): Any = Any.pack(msg)

    @Test
    fun `home view round trips`() {
        val proto = Ubo.HomeViewData.newBuilder()
            .setType("home")
            .setShowStatusBar(true)
            .setCpuPercent(42f)
            .setRamPercent(17f)
            .setVolumeLevel(0.5f)
            .build()

        val unpacked = ProtoToView.unpackViewData(anyFrom(proto))
        assertThat(unpacked).isInstanceOf(ViewData.Home::class.java)
        val data = (unpacked as ViewData.Home).data
        assertThat(data.cpuPercent).isEqualTo(42f)
        assertThat(data.ramPercent).isEqualTo(17f)
        assertThat(data.volumeLevel).isEqualTo(0.5f)
    }

    @Test
    fun `menu view round trips with heading and pagination`() {
        val proto = Ubo.MenuViewData.newBuilder()
            .setType("menu")
            .setTitle("Settings")
            .setHeading("WiFi")
            .setPageIndex(1)
            .setTotalPages(3)
            .build()

        val unpacked = ProtoToView.unpackViewData(anyFrom(proto))
        assertThat(unpacked).isInstanceOf(ViewData.Menu::class.java)
        val data = (unpacked as ViewData.Menu).data
        assertThat(data.title).isEqualTo("Settings")
        assertThat(data.heading).isEqualTo("WiFi")
        assertThat(data.pageIndex).isEqualTo(1)
        assertThat(data.totalPages).isEqualTo(3)
    }

    @Test
    fun `notification view round trips`() {
        val proto = Ubo.NotificationViewData.newBuilder()
            .setType("notification")
            .setNotificationId("n-1")
            .setTitle("Hello")
            .setContent("World")
            .setIcon("󰂜")
            .build()

        val unpacked = ProtoToView.unpackViewData(anyFrom(proto))
        assertThat(unpacked).isInstanceOf(ViewData.Notification::class.java)
        val data = (unpacked as ViewData.Notification).data
        assertThat(data.notificationId).isEqualTo("n-1")
        assertThat(data.title).isEqualTo("Hello")
        assertThat(data.content).isEqualTo("World")
    }

    @Test
    fun `application view round trips`() {
        val proto = Ubo.ApplicationViewData.newBuilder()
            .setType("application")
            .setApplicationId("wifi:setup")
            .build()

        val unpacked = ProtoToView.unpackViewData(anyFrom(proto))
        assertThat(unpacked).isInstanceOf(ViewData.Application::class.java)
        assertThat((unpacked as ViewData.Application).data.applicationId).isEqualTo("wifi:setup")
    }

    @Test
    fun `instruction view round trips with spinner and timeout`() {
        val proto = Ubo.InstructionViewData.newBuilder()
            .setType("instruction")
            .setTitle("Pair")
            .setInstruction("Press the button")
            .setSpinner(true)
            .setTimeoutSeconds(30)
            .build()

        val unpacked = ProtoToView.unpackViewData(anyFrom(proto))
        assertThat(unpacked).isInstanceOf(ViewData.Instruction::class.java)
        val data = (unpacked as ViewData.Instruction).data
        assertThat(data.title).isEqualTo("Pair")
        assertThat(data.instruction).isEqualTo("Press the button")
        assertThat(data.spinner).isTrue()
        assertThat(data.timeoutSeconds).isEqualTo(30)
    }

    @Test
    fun `prompt view round trips with action items`() {
        val item = Ubo.MenuItemData.newBuilder()
            .setKey("yes")
            .setLabel("Yes")
            .setIcon("✅")
            .build()
        val items = Ubo.PromptViewData.Items.newBuilder().addItems(item).build()
        val proto = Ubo.PromptViewData.newBuilder()
            .setType("prompt")
            .setTitle("Confirm")
            .setPrompt("Reboot device?")
            .setItems(items)
            .build()

        val unpacked = ProtoToView.unpackViewData(anyFrom(proto))
        assertThat(unpacked).isInstanceOf(ViewData.Prompt::class.java)
        val data = (unpacked as ViewData.Prompt).data
        assertThat(data.prompt).isEqualTo("Reboot device?")
        assertThat(data.items).hasSize(1)
        assertThat(data.items.first().label).isEqualTo("Yes")
    }

    @Test
    fun `render view round trips with QR-code props`() {
        val basic = Ubo.BasicType.newBuilder().setString("https://example.com").build()
        val propValue = Ubo.RenderViewData.PropsValue.newBuilder().setBasicType(basic).build()
        val propsDict = Ubo.RenderViewData.PropsDict.newBuilder()
            .putItems("data", propValue)
            .build()
        val proto = Ubo.RenderViewData.newBuilder()
            .setType("render")
            .setKind("qr_code")
            .setTitle("Scan to pair")
            .setProps(propsDict)
            .build()

        val unpacked = ProtoToView.unpackViewData(anyFrom(proto))
        assertThat(unpacked).isInstanceOf(ViewData.Render::class.java)
        val data = (unpacked as ViewData.Render).data
        assertThat(data.kind).isEqualTo(RenderKind.QrCode)
        assertThat(data.title).isEqualTo("Scan to pair")
        val payload = data.props["data"]
        assertThat(payload).isInstanceOf(RenderPropValue.StringValue::class.java)
        assertThat((payload as RenderPropValue.StringValue).value).isEqualTo("https://example.com")
    }

    @Test
    fun `render kind falls back to unknown for unrecognised raw values`() {
        val kind = RenderKind.fromRawValue("future_kind_42")
        assertThat(kind).isInstanceOf(RenderKind.Unknown::class.java)
        assertThat((kind as RenderKind.Unknown).raw).isEqualTo("future_kind_42")
    }

    @Test
    fun `unknown type URL returns null`() {
        val random = Ubo.BasicType.newBuilder().setBytes(ByteString.copyFromUtf8("garbage")).build()
        val unpacked = ProtoToView.unpackViewData(anyFrom(random))
        assertThat(unpacked).isNull()
    }
}
