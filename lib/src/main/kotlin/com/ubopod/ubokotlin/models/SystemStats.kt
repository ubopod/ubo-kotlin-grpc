package com.ubopod.ubokotlin.models

/**
 * System statistics from the Ubo device.
 *
 * Mirrors `Sources/UboSwift/Models/SystemStats.swift`. The nullable
 * audio fields are populated from `state.audio` (see
 * `ProtoToState.mergeSystemStats`); they stay `null` on devices /
 * releases that don't yet report them so existing UIs degrade
 * gracefully rather than read a fabricated zero. Same for the rest of
 * the optional fields below, which stay `null` until their first
 * frame arrives.
 */
public data class SystemStats(
    val cpuPercent: Float = 0f,
    val ramPercent: Float = 0f,
    /**
     * Device temperature in Celsius. Sourced from
     * `SystemState.cpu_temperature_celsius` (previously read from the
     * onboard ambient sensor in `SensorsState.temperature`, which didn't
     * match what the Web UI's Processor card shows).
     */
    val temperature: Float? = null,
    /**
     * Already converted to the device's effective UnitSystem — see
     * `ubo_app/utils/units.py`. Clients display these, not [temperature].
     */
    val temperatureDisplayValue: Float? = null,
    val temperatureDisplayUnit: String? = null,
    val loadAverage1: Float? = null,
    val loadAverage5: Float? = null,
    val loadAverage15: Float? = null,
    /** Device boot time (epoch seconds). Used to derive uptime. */
    val bootTime: Float? = null,
    val diskTotalBytes: Long? = null,
    val diskUsedBytes: Long? = null,
    val diskPercent: Float? = null,
    val networkUploadBps: Float? = null,
    val networkDownloadBps: Float? = null,
    /** Current local date ("YYYY-MM-DD"), from `state.localization`. */
    val date: String = "",
    val clock: String = "",
    val weather: WeatherCondition? = null,
    /** City/country the device's location resolved to, from `LocalizationState.location`. */
    val locationCity: String? = null,
    val locationCountry: String? = null,
    /** Docker apps from `state.docker.service`, empty until first frame. */
    val dockerApps: List<DockerAppStatus> = emptyList(),
    /** Connected sensor devices from `state.sensors`, empty until first frame. */
    val sensorDevices: List<SensorDeviceState> = emptyList(),
    val playbackVolume: Float? = null,
    val isPlaybackMute: Boolean? = null,
    val isCaptureMute: Boolean? = null,
)
