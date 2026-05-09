package com.ubopod.ubokotlin.models

/** Status bar icon data for rendering. */
public data class StatusIconData(
    val symbol: String,
    val color: String,
)

/** Progress notification for status bar rendering. */
public data class ProgressNotificationData(
    val id: String,
    /** `null` = indeterminate (spinner); 0..1 = progress ring. */
    val progress: Float? = null,
    val color: String,
)

/**
 * All data needed to render the status bar (header + footer).
 *
 * Mirrors `Sources/UboSwift/Models/StatusBarData.swift`.
 */
public data class StatusBarData(
    // Header
    val title: String = "",
    val isRecording: Boolean = false,
    val isReplaying: Boolean = false,
    val isRecordingAudio: Boolean = false,
    val progressNotifications: List<ProgressNotificationData> = emptyList(),
    // Footer
    val clock: String = "",
    val temperature: Float? = null,
    val lightLevel: Float? = null,
    val icons: List<StatusIconData> = emptyList(),
) {
    public override fun toString(): String {
        val parts = buildList {
            if (title.isNotEmpty()) add("title: \"$title\"")
            if (clock.isNotEmpty()) add("clock: $clock")
            temperature?.let { add("temp: ${it.toInt()}C") }
            if (icons.isNotEmpty()) add("icons: ${icons.size}")
            if (progressNotifications.isNotEmpty()) add("progress: ${progressNotifications.size}")
        }
        return "StatusBar(${parts.joinToString(", ")})"
    }
}
