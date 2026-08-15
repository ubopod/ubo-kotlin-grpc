package com.ubopod.ubokotlin.connection

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ubopod.ubokotlin.UboError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** A device discovered on the local network via mDNS / DNS-SD. */
public data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int,
)

/**
 * `NsdManager`-based mDNS browser for Ubo devices on the local network.
 *
 * Mirrors `Sources/UboSwift/Connection/Discovery.swift`. The Python core
 * does not yet publish a `_uborpc._tcp` Bonjour/DNS-SD record; once it
 * does, callers see freshly-resolved devices in the [Flow] without
 * touching the consumer code. Until then [browse] simply emits an empty
 * snapshot when no peers are visible.
 *
 * Each emission is the **current set** of devices — appearances and
 * disappearances both yield a fresh snapshot, so consumers can render
 * the latest list directly without diffing.
 *
 * Usage:
 * ```
 * UboDiscovery.browse(context).collect { devices -> updateUi(devices) }
 * ```
 */
public object UboDiscovery {

    /** Bonjour/DNS-SD service type advertised by an Ubo device's gRPC server. */
    public const val DEFAULT_SERVICE_TYPE: String = "_uborpc._tcp."

    /**
     * Browse continuously for Ubo devices on the local network.
     *
     * @param context Android [Context] used to obtain [NsdManager]. The
     *   application context is sufficient.
     * @param serviceType DNS-SD service type, default [DEFAULT_SERVICE_TYPE].
     * @param resolveTimeoutMs Per-device resolve timeout. After a service
     *   is found, the platform must resolve its host/port; if the resolve
     *   doesn't complete in time the device is skipped from the snapshot.
     */
    public fun browse(
        context: Context,
        serviceType: String = DEFAULT_SERVICE_TYPE,
        resolveTimeoutMs: Long = 4_000,
    ): Flow<Set<DiscoveredDevice>> {
        val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        // Resolved devices, keyed by service name, carried across emissions
        // so a re-announcement of an already-known device (mDNS does this
        // periodically) doesn't trigger another NsdManager resolve. Each
        // resolve registers a system-level listener, and Android caps how
        // many an app can have outstanding at once — re-resolving devices
        // we already know about was burning through that budget for no
        // reason and could starve out real discovery.
        val resolvedByName = ConcurrentHashMap<String, DiscoveredDevice>()
        return rawSnapshots(nsdManager, serviceType)
            .map { infos -> resolveNew(nsdManager, infos, resolvedByName, resolveTimeoutMs) }
            .distinctUntilChanged()
    }

    /**
     * Inner `Flow<List<NsdServiceInfo>>` — one emission per discovery delta.
     * Each emission is the *current* visible set, which the public [browse]
     * Flow then resolves into [DiscoveredDevice]s.
     */
    private fun rawSnapshots(
        nsdManager: NsdManager,
        serviceType: String,
    ): Flow<List<NsdServiceInfo>> = callbackFlow {
        // Track currently-visible service infos by their fully-qualified name
        // so we can emit a complete snapshot on each delta.
        val visible = ConcurrentHashMap<String, NsdServiceInfo>()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e("UboDiscovery", "onStartDiscoveryFailed type=$serviceType errorCode=$errorCode")
                close(UboError.SubscriptionFailed(IllegalStateException("NSD discovery start failed: $errorCode")))
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w("UboDiscovery", "onStopDiscoveryFailed type=$serviceType errorCode=$errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d("UboDiscovery", "onDiscoveryStarted type=$serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d("UboDiscovery", "onDiscoveryStopped type=$serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d("UboDiscovery", "onServiceFound name=${serviceInfo.serviceName} type=${serviceInfo.serviceType}")
                visible[serviceInfo.serviceName] = serviceInfo
                trySend(visible.values.toList())
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("UboDiscovery", "onServiceLost name=${serviceInfo.serviceName}")
                visible.remove(serviceInfo.serviceName)
                trySend(visible.values.toList())
            }
        }

        // Seed the consumer with an empty snapshot so it can render an
        // immediate "searching…" state rather than waiting for the first
        // service-found callback.
        trySend(emptyList())

        Log.d("UboDiscovery", "discoverServices type=$serviceType")
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (_: IllegalArgumentException) {
                // Listener may already be unregistered.
            }
        }
    }

    private suspend fun resolveNew(
        nsdManager: NsdManager,
        infos: List<NsdServiceInfo>,
        resolvedByName: ConcurrentHashMap<String, DiscoveredDevice>,
        timeoutMs: Long,
    ): Set<DiscoveredDevice> {
        val currentNames = infos.mapNotNull { it.serviceName }.toSet()
        resolvedByName.keys.retainAll(currentNames)

        for (info in infos) {
            val name = info.serviceName ?: continue
            if (resolvedByName.containsKey(name)) continue
            val device = resolveService(nsdManager, info, timeoutMs) ?: continue
            resolvedByName[name] = device
        }
        return resolvedByName.values.toSet()
    }

    private suspend fun resolveService(
        nsdManager: NsdManager,
        info: NsdServiceInfo,
        timeoutMs: Long,
    ): DiscoveredDevice? {
        val resolved: NsdServiceInfo? = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    registerResolveCallbackApi34(nsdManager, info, cont)
                } else {
                    registerResolveListenerLegacy(nsdManager, info, cont)
                }
            }
        }
        if (resolved == null) return null

        val host = resolved.host?.hostAddress ?: resolved.host?.hostName ?: return null
        return DiscoveredDevice(
            name = resolved.serviceName ?: info.serviceName ?: "",
            host = host,
            port = resolved.port,
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun registerResolveCallbackApi34(
        nsdManager: NsdManager,
        info: NsdServiceInfo,
        cont: kotlinx.coroutines.CancellableContinuation<NsdServiceInfo?>,
    ) {
        val callback = object : NsdManager.ServiceInfoCallback {
            private var fired = false
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.e("UboDiscovery", "resolve(34) registrationFailed name=${info.serviceName} errorCode=$errorCode")
                if (!fired) { fired = true; cont.resume(null) }
            }
            override fun onServiceUpdated(info: NsdServiceInfo) {
                Log.d("UboDiscovery", "resolve(34) updated name=${info.serviceName} host=${info.host} port=${info.port}")
                if (!fired) {
                    fired = true; cont.resume(info)
                }
                runCatching { nsdManager.unregisterServiceInfoCallback(this) }
            }
            override fun onServiceLost() {
                Log.d("UboDiscovery", "resolve(34) lost name=${info.serviceName}")
                if (!fired) { fired = true; cont.resume(null) }
            }
            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        try {
            nsdManager.registerServiceInfoCallback(info, Runnable::run, callback)
        } catch (t: Throwable) {
            Log.e("UboDiscovery", "resolve(34) registerServiceInfoCallback threw for name=${info.serviceName}", t)
            cont.resume(null)
        }
        cont.invokeOnCancellation {
            runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
        }
    }

    // resolveService is the only resolve API available pre-Upside-Down-Cake;
    // it is officially "deprecated" in API 34+ but still supported.
    @Suppress("DEPRECATION")
    private fun registerResolveListenerLegacy(
        nsdManager: NsdManager,
        info: NsdServiceInfo,
        cont: kotlinx.coroutines.CancellableContinuation<NsdServiceInfo?>,
    ) {
        val listener = object : NsdManager.ResolveListener {
            private var fired = false
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e("UboDiscovery", "resolve(legacy) failed name=${serviceInfo?.serviceName} errorCode=$errorCode")
                if (!fired) { fired = true; cont.resume(null) }
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(
                    "UboDiscovery",
                    "resolve(legacy) resolved name=${serviceInfo.serviceName} host=${serviceInfo.host} port=${serviceInfo.port}",
                )
                if (!fired) { fired = true; cont.resume(serviceInfo) }
            }
        }
        try {
            nsdManager.resolveService(info, listener)
        } catch (t: Throwable) {
            Log.e("UboDiscovery", "resolve(legacy) resolveService threw for name=${info.serviceName}", t)
            cont.resume(null)
        }
    }
}
