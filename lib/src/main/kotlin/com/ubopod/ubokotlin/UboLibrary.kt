package com.ubopod.ubokotlin

import store.v1.Store

/**
 * Stable identifier for the Kotlin Ubo bindings. Mirrors the version
 * string exposed by the Swift package at `Sources/UboSwift/UboSwift.swift`.
 */
public object UboLibrary {
    public const val VERSION: String = "0.1.0-SNAPSHOT"

    @Suppress("unused")
    internal fun _smokeStoreImport(): Class<Store.DispatchActionRequest> =
        Store.DispatchActionRequest::class.java
}
