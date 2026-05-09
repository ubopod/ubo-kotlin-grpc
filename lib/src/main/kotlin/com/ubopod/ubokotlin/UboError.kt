package com.ubopod.ubokotlin

/**
 * Errors that can occur when interacting with Ubo devices.
 *
 * Mirrors `Sources/UboSwift/UboError.swift`. Each variant carries the same
 * payload as its Swift counterpart so call sites translate 1:1.
 */
public sealed class UboError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Not connected to any device. */
    public object NotConnected : UboError("Not connected to any Ubo device")

    /** Connection to device failed. */
    public class ConnectionFailed(cause: Throwable) :
        UboError("Connection failed: ${cause.message ?: cause::class.simpleName}", cause)

    /** Event subscription failed. */
    public class SubscriptionFailed(cause: Throwable) :
        UboError("Event subscription failed: ${cause.message ?: cause::class.simpleName}", cause)

    /** Action dispatch failed. */
    public class DispatchFailed(cause: Throwable) :
        UboError("Action dispatch failed: ${cause.message ?: cause::class.simpleName}", cause)

    /** Invalid response from device. */
    public class InvalidResponse(detail: String) : UboError("Invalid response: $detail")

    /** Connection timed out. */
    public object Timeout : UboError("Connection timed out")

    /** gRPC channel is not available. */
    public object ChannelUnavailable : UboError("gRPC channel is not available")
}
