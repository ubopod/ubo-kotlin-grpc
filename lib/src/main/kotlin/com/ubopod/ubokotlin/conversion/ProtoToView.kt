package com.ubopod.ubokotlin.conversion

import com.google.protobuf.Any
import com.google.protobuf.InvalidProtocolBufferException
import com.ubopod.ubokotlin.models.ApplicationViewData
import com.ubopod.ubokotlin.models.HomeViewData
import com.ubopod.ubokotlin.models.InstructionViewData
import com.ubopod.ubokotlin.models.MenuItemData
import com.ubopod.ubokotlin.models.MenuViewData
import com.ubopod.ubokotlin.models.NotificationViewData
import com.ubopod.ubokotlin.models.ProgressNotificationData
import com.ubopod.ubokotlin.models.PromptViewData
import com.ubopod.ubokotlin.models.RenderKind
import com.ubopod.ubokotlin.models.RenderPropValue
import com.ubopod.ubokotlin.models.RenderViewData
import com.ubopod.ubokotlin.models.StatusBarData
import com.ubopod.ubokotlin.models.StatusIconData
import com.ubopod.ubokotlin.models.SystemStats
import com.ubopod.ubokotlin.models.ViewData
import ubo.v1.Ubo

/**
 * Helpers for converting `google.protobuf.Any` payloads streamed by the
 * Ubo gRPC server into Kotlin model types.
 *
 * Mirrors the Swift `unpackViewData(from:)` / `convert*ViewData(_:)` family
 * of functions in `Sources/UboSwift/Connection/UboConnection.swift`. The
 * dispatch is by type-URL suffix — same wire shape as the Swift port, so
 * a server frame that round-trips through Swift round-trips through Kotlin
 * without any further translation.
 */
public object ProtoToView {

    /**
     * Unpack a [com.google.protobuf.Any] message and return the matching
     * [ViewData] subtype, or `null` for an unrecognized type URL.
     */
    public fun unpackViewData(any: Any): ViewData? {
        // The Python core's betterproto generator emits typeUrls with a
        // `ubo_bindings.` prefix
        // (e.g. `type.googleapis.com/ubo_bindings.ubo.v1.MenuViewData`),
        // while the standard Java protoc only sees `ubo.v1.MenuViewData`.
        // Match by the rightmost `.`-segment so both shapes resolve, and
        // parse `any.value` directly with `parseFrom` instead of
        // `Any.unpack(Class)` — `unpack` enforces a strict typeUrl match
        // against the class's full name and would reject the betterproto
        // variant outright.
        return when (any.typeUrl.substringAfterLast('.')) {
            "HomeViewData" ->
                ViewData.Home(convertHomeViewData(Ubo.HomeViewData.parseFrom(any.value)))
            "MenuViewData" ->
                ViewData.Menu(convertMenuViewData(Ubo.MenuViewData.parseFrom(any.value)))
            "NotificationViewData" ->
                ViewData.Notification(convertNotificationViewData(Ubo.NotificationViewData.parseFrom(any.value)))
            "ApplicationViewData" ->
                ViewData.Application(convertApplicationViewData(Ubo.ApplicationViewData.parseFrom(any.value)))
            "InstructionViewData" ->
                ViewData.Instruction(convertInstructionViewData(Ubo.InstructionViewData.parseFrom(any.value)))
            "PromptViewData" ->
                ViewData.Prompt(convertPromptViewData(Ubo.PromptViewData.parseFrom(any.value)))
            "RenderViewData" ->
                ViewData.Render(convertRenderViewData(Ubo.RenderViewData.parseFrom(any.value)))
            else -> null
        }
    }

    /** Unpack a status-bar payload, returning `null` on the wrong type URL. */
    public fun unpackStatusBarData(any: Any): StatusBarData? = try {
        when (any.typeUrl.substringAfterLast('.')) {
            "StatusBarData" ->
                convertStatusBarData(Ubo.StatusBarData.parseFrom(any.value))
            else -> null
        }
    } catch (_: InvalidProtocolBufferException) {
        null
    }

    /**
     * Try to interpret an [Any] as one of the system-stats fields the device
     * publishes (CPU%, RAM%, clock string). Mirrors the Swift `StatsHolder`
     * accumulation pattern but is exposed as a parser only — the connection
     * layer is responsible for merging multiple frames into a single
     * [SystemStats].
     */
    public fun mergeIntoSystemStats(any: Any, into: SystemStats): SystemStats {
        // The Python store streams individual scalar fields; a complete
        // SystemStats is built up by overlaying successive frames. Each
        // frame carries one of: float (cpu/ram), string (clock).
        return when (any.typeUrl.substringAfterLast('/')) {
            "google.protobuf.FloatValue" -> into // not currently used; placeholder for future precision
            else -> into
        }
    }

    // -------- View conversions --------

    public fun convertHomeViewData(p: Ubo.HomeViewData): HomeViewData = HomeViewData(
        type = if (p.hasType()) p.type else "home",
        showStatusBar = if (p.hasShowStatusBar()) p.showStatusBar else true,
        menuItems = if (p.hasMenuItems()) p.menuItems.itemsList.map { convertMenuItem(it) } else emptyList(),
        cpuPercent = if (p.hasCpuPercent()) p.cpuPercent else 0f,
        ramPercent = if (p.hasRamPercent()) p.ramPercent else 0f,
        volumeLevel = if (p.hasVolumeLevel()) p.volumeLevel else 0f,
    )

    public fun convertMenuViewData(p: Ubo.MenuViewData): MenuViewData = MenuViewData(
        type = if (p.hasType()) p.type else "menu",
        showStatusBar = if (p.hasShowStatusBar()) p.showStatusBar else true,
        title = if (p.hasTitle()) p.title else "",
        heading = if (p.hasHeading()) p.heading else null,
        subHeading = if (p.hasSubHeading()) p.subHeading else null,
        items = if (p.hasItems()) {
            p.items.itemsList.map { wrapper ->
                if (wrapper.hasItems()) convertMenuItem(wrapper.items) else null
            }
        } else emptyList(),
        pageIndex = if (p.hasPageIndex()) p.pageIndex.toInt() else 0,
        totalPages = if (p.hasTotalPages()) p.totalPages.toInt() else 1,
    )

    public fun convertNotificationViewData(p: Ubo.NotificationViewData): NotificationViewData = NotificationViewData(
        type = if (p.hasType()) p.type else "notification",
        showStatusBar = if (p.hasShowStatusBar()) p.showStatusBar else false,
        notificationId = if (p.hasNotificationId()) p.notificationId else "",
        title = if (p.hasTitle()) p.title else "",
        content = if (p.hasContent()) p.content else "",
        icon = if (p.hasIcon()) p.icon else "",
        color = if (p.hasColor()) p.color else "#ffffff",
        items = if (p.hasItems()) {
            p.items.itemsList.map { wrapper ->
                if (wrapper.hasItems()) convertMenuItem(wrapper.items) else null
            }
        } else emptyList(),
        extraInformation = if (p.hasExtraInformation()) p.extraInformation else "",
    )

    public fun convertApplicationViewData(p: Ubo.ApplicationViewData): ApplicationViewData = ApplicationViewData(
        type = if (p.hasType()) p.type else "application",
        showStatusBar = if (p.hasShowStatusBar()) p.showStatusBar else false,
        applicationId = if (p.hasApplicationId()) p.applicationId else "",
        extraData = if (p.hasExtraData()) {
            p.extraData.itemsMap.mapValues { (_, value) ->
                // ApplicationViewData.extraData on the Swift side is `[String: String]`,
                // i.e. the basic-type primitive collapsed to a string. Match that.
                when {
                    value.hasBasicType() -> renderPropPrimitiveAsString(value.basicType)
                    else -> ""
                }
            }
        } else emptyMap(),
    )

    public fun convertInstructionViewData(p: Ubo.InstructionViewData): InstructionViewData = InstructionViewData(
        type = if (p.hasType()) p.type else "instruction",
        showStatusBar = if (p.hasShowStatusBar()) p.showStatusBar else false,
        title = if (p.hasTitle()) p.title else "",
        instruction = if (p.hasInstruction()) p.instruction else "",
        icon = if (p.hasIcon()) p.icon else "",
        spinner = if (p.hasSpinner()) p.spinner else false,
        timeoutSeconds = if (p.hasTimeoutSeconds()) p.timeoutSeconds.toInt() else 0,
        progressText = if (p.hasProgressText()) p.progressText else "",
        footerText = if (p.hasFooterText()) p.footerText else "",
    )

    public fun convertPromptViewData(p: Ubo.PromptViewData): PromptViewData = PromptViewData(
        type = if (p.hasType()) p.type else "prompt",
        showStatusBar = if (p.hasShowStatusBar()) p.showStatusBar else false,
        title = if (p.hasTitle()) p.title else "",
        prompt = if (p.hasPrompt()) p.prompt else "",
        icon = if (p.hasIcon()) p.icon else "",
        items = if (p.hasItems()) p.items.itemsList.map { convertMenuItem(it) } else emptyList(),
    )

    public fun convertRenderViewData(p: Ubo.RenderViewData): RenderViewData = RenderViewData(
        type = if (p.hasType()) p.type else "render",
        showStatusBar = if (p.hasShowStatusBar()) p.showStatusBar else false,
        kind = RenderKind.fromRawValue(if (p.hasKind()) p.kind else ""),
        title = if (p.hasTitle()) p.title else "",
        props = if (p.hasProps()) {
            p.props.itemsMap.mapValues { (_, value) -> convertPropValue(value) }
        } else emptyMap(),
        items = if (p.hasItems()) p.items.itemsList.map { convertMenuItem(it) } else emptyList(),
        streamId = if (p.hasStreamId()) p.streamId else "",
    )

    // -------- Status bar --------

    public fun convertStatusBarData(p: Ubo.StatusBarData): StatusBarData = StatusBarData(
        title = if (p.hasTitle()) p.title else "",
        isRecording = if (p.hasIsRecording()) p.isRecording else false,
        isReplaying = if (p.hasIsReplaying()) p.isReplaying else false,
        isRecordingAudio = if (p.hasIsRecordingAudio()) p.isRecordingAudio else false,
        progressNotifications = if (p.hasProgressNotifications()) {
            p.progressNotifications.itemsList.map { convertProgressNotification(it) }
        } else emptyList(),
        clock = if (p.hasClock()) p.clock else "",
        temperature = if (p.hasTemperature()) p.temperature else null,
        lightLevel = if (p.hasLightLevel()) p.lightLevel else null,
        icons = if (p.hasIcons()) {
            p.icons.itemsList.map { convertStatusIcon(it) }
        } else emptyList(),
    )

    private fun convertStatusIcon(p: Ubo.StatusIconData): StatusIconData =
        StatusIconData(symbol = p.symbol, color = p.color)

    private fun convertProgressNotification(p: Ubo.ProgressNotificationData): ProgressNotificationData =
        ProgressNotificationData(
            id = p.id,
            progress = if (p.hasProgress()) p.progress else null,
            color = p.color,
        )

    // -------- Menu items + render props --------

    private fun convertMenuItem(p: Ubo.MenuItemData): MenuItemData = MenuItemData(
        key = p.key,
        label = p.label,
        icon = p.icon,
        color = if (p.hasColor()) p.color else "#ffffff",
        backgroundColor = if (p.hasBackgroundColor()) p.backgroundColor else null,
        isShort = if (p.hasIsShort()) p.isShort else false,
        actionId = if (p.hasActionId()) p.actionId else null,
    )

    private fun convertPropValue(p: Ubo.RenderViewData.PropsValue): RenderPropValue = when {
        p.hasBasicType() -> convertBasicTypeTyped(p.basicType)
        p.hasList() -> RenderPropValue.ListValue(p.list.itemsList.map { convertBasicTypeTyped(it) })
        else -> RenderPropValue.StringValue("")
    }

    private fun convertBasicTypeTyped(p: Ubo.BasicType): RenderPropValue {
        // Use explicit Java getters: the proto fields `bool`, `bytes`, `float`,
        // `int64` collide with type-like names that confuse Kotlin's property
        // accessor synthesis; `getXxx()` resolves cleanly.
        return when (p.basicTypeCase) {
            Ubo.BasicType.BasicTypeCase.STRING -> RenderPropValue.StringValue(p.getString())
            Ubo.BasicType.BasicTypeCase.INT64 -> RenderPropValue.IntValue(p.getInt64())
            Ubo.BasicType.BasicTypeCase.FLOAT -> RenderPropValue.FloatValue(p.getFloat())
            Ubo.BasicType.BasicTypeCase.BOOL -> RenderPropValue.BoolValue(p.getBool())
            Ubo.BasicType.BasicTypeCase.BYTES -> RenderPropValue.BytesValue(p.getBytes().toByteArray())
            Ubo.BasicType.BasicTypeCase.BASICTYPE_NOT_SET -> RenderPropValue.StringValue("")
            null -> RenderPropValue.StringValue("")
        }
    }

    private fun renderPropPrimitiveAsString(p: Ubo.BasicType): String {
        return when (p.basicTypeCase) {
            Ubo.BasicType.BasicTypeCase.STRING -> p.getString()
            Ubo.BasicType.BasicTypeCase.INT64 -> p.getInt64().toString()
            Ubo.BasicType.BasicTypeCase.FLOAT -> p.getFloat().toString()
            Ubo.BasicType.BasicTypeCase.BOOL -> p.getBool().toString()
            Ubo.BasicType.BasicTypeCase.BYTES -> ""
            Ubo.BasicType.BasicTypeCase.BASICTYPE_NOT_SET -> ""
            null -> ""
        }
    }
}
