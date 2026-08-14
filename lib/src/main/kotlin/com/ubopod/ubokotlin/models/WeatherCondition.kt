package com.ubopod.ubokotlin.models

/**
 * Current weather condition, sourced from `LocalizationState.weather`.
 *
 * Mirrors `Sources/UboSwift/Models/WeatherInfo.swift`. `symbolCode` follows
 * the MET Norway weather symbol vocabulary (e.g. `partlycloudy_day`),
 * matching what the device's own weather service
 * (`ubo_app/services/010-localization/weather.py`) fetches and what the
 * Web UI dashboard renders.
 */
public data class WeatherCondition(
    val symbolCode: String = "",
    val temperatureCelsius: Float = 0f,
    val windSpeedMps: Float? = null,
    /**
     * Already converted to the device's effective UnitSystem — see
     * `ubo_app/utils/units.py`. Clients display these, not [temperatureCelsius].
     */
    val temperatureDisplayValue: Float = 0f,
    val temperatureDisplayUnit: String = "°C",
    val windSpeedDisplayValue: Float? = null,
    val windSpeedDisplayUnit: String? = null,
)
