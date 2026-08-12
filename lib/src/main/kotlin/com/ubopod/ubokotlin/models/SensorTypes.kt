package com.ubopod.ubokotlin.models

/** Health of a connected sensor device, mirrors `Ubo.SensorStatus`. */
public enum class SensorDeviceStatus {
    ACTIVE,
    ERROR,
    UNSUPPORTED,
    AMBIGUOUS,
    UNSPECIFIED,
}

/** One reading from a sensor device's `entities` list. */
public data class SensorEntityReading(
    val key: String,
    val value: Float? = null,
    val name: String? = null,
    val unit: String? = null,
    val deviceClass: String? = null,
    val precision: Long? = null,
)

/**
 * One connected sensor device from `SensorsState.devices`.
 *
 * Mirrors `Sources/UboSwift/Models/SensorTypes.swift`.
 */
public data class SensorDeviceState(
    val id: String,
    val label: String,
    val status: SensorDeviceStatus,
    val entities: List<SensorEntityReading>,
)
