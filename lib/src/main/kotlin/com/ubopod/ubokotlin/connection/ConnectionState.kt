package com.ubopod.ubokotlin.connection

/**
 * Connection state to an Ubo device.
 *
 * Mirrors `Sources/UboSwift/Connection/ConnectionState.swift`.
 */
public enum class ConnectionState {
    /** Not connected to any device. */
    DISCONNECTED,

    /** Currently attempting an initial connection. */
    CONNECTING,

    /** Successfully connected to the device. */
    CONNECTED,

    /** Connection was lost; auto-retry loop is running. */
    RECONNECTING;

    public val isConnected: Boolean get() = this == CONNECTED
    public val isConnecting: Boolean get() = this == CONNECTING || this == RECONNECTING
}
