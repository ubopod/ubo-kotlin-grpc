package com.ubopod.ubokotlin.models

/** Data for rendering the home screen view. */
public data class HomeViewData(
    val type: String = "home",
    val showStatusBar: Boolean = true,
    val menuItems: List<MenuItemData> = emptyList(),
    val cpuPercent: Float = 0f,
    val ramPercent: Float = 0f,
    val volumeLevel: Float = 0f,
)

/** Data for rendering a menu view. */
public data class MenuViewData(
    val type: String = "menu",
    val showStatusBar: Boolean = true,
    val title: String = "",
    val heading: String? = null,
    val subHeading: String? = null,
    /** Items can be `null` to represent gaps in paginated menus. */
    val items: List<MenuItemData?> = emptyList(),
    val pageIndex: Int = 0,
    val totalPages: Int = 1,
)

/** Data for rendering a notification overlay view. */
public data class NotificationViewData(
    val type: String = "notification",
    val showStatusBar: Boolean = false,
    val notificationId: String = "",
    val title: String = "",
    val content: String = "",
    val icon: String = "",
    val color: String = "#ffffff",
    val items: List<MenuItemData?> = emptyList(),
    val extraInformation: String = "",
)

/** Data for rendering an application view. */
public data class ApplicationViewData(
    val type: String = "application",
    val showStatusBar: Boolean = false,
    val applicationId: String = "",
    val extraData: Map<String, String> = emptyMap(),
)

/**
 * Sub-kinds of [RenderViewData] that the core dispatches to clients.
 *
 * Mirrors the `kind` strings used by `ubo_app.store.core.types.view_data.RenderViewData`
 * (e.g. `'qr_code'`, `'frame_stream'`). New kinds added on the Python side without
 * a Kotlin counterpart fall through to [Unknown] so clients can degrade gracefully.
 */
public sealed class RenderKind(public val rawValue: String) {
    public object QrCode : RenderKind("qr_code")
    public object QrCodeCarousel : RenderKind("qr_code_carousel")
    public object TextViewer : RenderKind("text_viewer")
    public object ImageViewer : RenderKind("image_viewer")
    public object FrameStream : RenderKind("frame_stream")
    public object Status : RenderKind("status")
    public data class Unknown(val raw: String) : RenderKind(raw)

    public companion object {
        public fun fromRawValue(raw: String): RenderKind = when (raw) {
            "qr_code" -> QrCode
            "qr_code_carousel" -> QrCodeCarousel
            "text_viewer" -> TextViewer
            "image_viewer" -> ImageViewer
            "frame_stream" -> FrameStream
            "status" -> Status
            else -> Unknown(raw)
        }
    }
}

/**
 * A `props` value attached to a [RenderViewData] payload. Either a primitive
 * (string / int64 / float / bool / bytes) or a list of primitives — mirrors
 * the `BasicType | tuple[BasicType, ...] | list[BasicType]` union from Python.
 */
public sealed class RenderPropValue {
    public data class StringValue(val value: String) : RenderPropValue()
    public data class IntValue(val value: Long) : RenderPropValue()
    public data class FloatValue(val value: Float) : RenderPropValue()
    public data class BoolValue(val value: Boolean) : RenderPropValue()
    public data class BytesValue(val value: ByteArray) : RenderPropValue() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return value.contentEquals((other as BytesValue).value)
        }

        override fun hashCode(): Int = value.contentHashCode()
    }

    public data class ListValue(val value: List<RenderPropValue>) : RenderPropValue()
}

/**
 * Data for rendering a generic widget such as a QR code, image/text viewer,
 * status page, or live frame stream. The sub-kind is carried in [kind] and
 * the renderer-specific arguments live in [props].
 */
public data class RenderViewData(
    val type: String = "render",
    val showStatusBar: Boolean = false,
    val kind: RenderKind,
    val title: String = "",
    val props: Map<String, RenderPropValue> = emptyMap(),
    val items: List<MenuItemData> = emptyList(),
    val streamId: String = "",
)

/**
 * Data for rendering an instruction / waiting view (e.g. "Press the button
 * on your device", "Scan this QR code"). Optionally shows a spinner and a
 * timeout countdown.
 */
public data class InstructionViewData(
    val type: String = "instruction",
    val showStatusBar: Boolean = false,
    val title: String = "",
    val instruction: String = "",
    val icon: String = "",
    val spinner: Boolean = false,
    val timeoutSeconds: Int = 0,
    val progressText: String = "",
    val footerText: String = "",
)

/**
 * Data for rendering a confirmation / prompt view (e.g. Yes/Cancel,
 * Connect/Delete). Items carry the action-button labels and `actionId`s.
 */
public data class PromptViewData(
    val type: String = "prompt",
    val showStatusBar: Boolean = false,
    val title: String = "",
    val prompt: String = "",
    val icon: String = "",
    val items: List<MenuItemData> = emptyList(),
)

/**
 * Union type for all view data variants. Mirrors the Swift `enum ViewData`
 * with seven cases.
 */
public sealed class ViewData {
    public abstract val type: String
    public abstract val showStatusBar: Boolean

    public data class Home(val data: HomeViewData) : ViewData() {
        override val type: String get() = data.type
        override val showStatusBar: Boolean get() = data.showStatusBar
    }

    public data class Menu(val data: MenuViewData) : ViewData() {
        override val type: String get() = data.type
        override val showStatusBar: Boolean get() = data.showStatusBar
    }

    public data class Notification(val data: NotificationViewData) : ViewData() {
        override val type: String get() = data.type
        override val showStatusBar: Boolean get() = data.showStatusBar
    }

    public data class Application(val data: ApplicationViewData) : ViewData() {
        override val type: String get() = data.type
        override val showStatusBar: Boolean get() = data.showStatusBar
    }

    public data class Instruction(val data: InstructionViewData) : ViewData() {
        override val type: String get() = data.type
        override val showStatusBar: Boolean get() = data.showStatusBar
    }

    public data class Prompt(val data: PromptViewData) : ViewData() {
        override val type: String get() = data.type
        override val showStatusBar: Boolean get() = data.showStatusBar
    }

    public data class Render(val data: RenderViewData) : ViewData() {
        override val type: String get() = data.type
        override val showStatusBar: Boolean get() = data.showStatusBar
    }

    public val isHome: Boolean get() = this is Home
    public val isMenu: Boolean get() = this is Menu
    public val isNotification: Boolean get() = this is Notification
    public val isApplication: Boolean get() = this is Application
    public val isInstruction: Boolean get() = this is Instruction
    public val isPrompt: Boolean get() = this is Prompt
    public val isRender: Boolean get() = this is Render

    public override fun toString(): String = when (this) {
        is Home -> "HomeView(items: ${data.menuItems.size}, cpu: ${data.cpuPercent.toInt()}%, ram: ${data.ramPercent.toInt()}%)"
        is Menu -> "MenuView(title: \"${data.title}\", items: ${data.items.size}, page: ${data.pageIndex + 1}/${data.totalPages})"
        is Notification -> "NotificationView(title: \"${data.title}\")"
        is Application -> "ApplicationView(id: \"${data.applicationId}\")"
        is Instruction -> "InstructionView(title: \"${data.title}\", spinner: ${data.spinner})"
        is Prompt -> "PromptView(title: \"${data.title}\", items: ${data.items.size})"
        is Render -> "RenderView(kind: ${data.kind.rawValue}, title: \"${data.title}\")"
    }
}
