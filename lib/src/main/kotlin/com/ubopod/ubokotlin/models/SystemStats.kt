package com.ubopod.ubokotlin.models

/**
 * System statistics from the Ubo device.
 *
 * Mirrors `Sources/UboSwift/Models/SystemStats.swift`. The nullable
 * audio fields are populated from `state.audio` (see
 * `ProtoToState.mergeSystemStats`); they stay `null` on devices /
 * releases that don't yet report them so existing UIs degrade
 * gracefully rather than read a fabricated zero.
 */
public data class SystemStats(
    val cpuPercent: Float = 0f,
    val ramPercent: Float = 0f,
    val clock: String = "",
    val temperature: Float? = null,
    val playbackVolume: Float? = null,
    val isPlaybackMute: Boolean? = null,
    val isCaptureMute: Boolean? = null,
)
