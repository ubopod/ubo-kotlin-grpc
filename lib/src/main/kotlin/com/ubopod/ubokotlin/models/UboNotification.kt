package com.ubopod.ubokotlin.models

import java.util.UUID

/** Importance level for notifications. */
public enum class NotificationImportance(public val rawValue: String, public val protoValue: Int) {
    CRITICAL("critical", 1),
    HIGH("high", 2),
    MEDIUM("medium", 3),
    LOW("low", 4);
}

/** Display type for notifications. */
public enum class NotificationDisplayType(public val rawValue: String, public val protoValue: Int) {
    NOT_SET("notSet", 1),
    BACKGROUND("background", 2),
    FLASH("flash", 3),
    STICKY("sticky", 4);
}

/**
 * A notification to display on the Ubo device.
 *
 * Mirrors `Sources/UboSwift/Models/UboNotification.swift`.
 */
public data class UboNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val icon: String? = null,
    val color: UboColor? = null,
    val importance: NotificationImportance = NotificationImportance.MEDIUM,
    val displayType: NotificationDisplayType = NotificationDisplayType.FLASH,
    val chime: Chime? = null,
    val dismissable: Boolean = true,
    val progress: Float? = null,
)
