package com.ubopod.ubokotlin.models

/**
 * System statistics from the Ubo device.
 *
 * Mirrors `Sources/UboSwift/Models/SystemStats.swift`.
 */
public data class SystemStats(
    val cpuPercent: Float = 0f,
    val ramPercent: Float = 0f,
    val clock: String = "",
    val temperature: Float? = null,
)
