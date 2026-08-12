package com.ubopod.ubokotlin.connection

import android.util.Log
import com.ubopod.ubokotlin.UboError
import com.ubopod.ubokotlin.conversion.ProtoFromAction
import com.ubopod.ubokotlin.conversion.ProtoToState
import com.ubopod.ubokotlin.conversion.ProtoToView
import com.ubopod.ubokotlin.models.PlaybackEvent
import com.ubopod.ubokotlin.models.StatusBarData
import com.ubopod.ubokotlin.models.SystemStats
import com.ubopod.ubokotlin.models.UboAction
import com.ubopod.ubokotlin.models.ViewData
import com.ubopod.ubokotlin.models.WebUIInputDescription
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import store.v1.Store
import store.v1.StoreServiceGrpcKt
import java.util.concurrent.TimeUnit

/**
 * Suspend-based gRPC actor. Owns the [ManagedChannel] and [storeClient]
 * lifetimes, serializes mutating calls through a [Mutex].
 *
 * Mirrors `Sources/UboSwift/Connection/UboConnection.swift` (a Swift
 * `actor`) — same public methods (`connect`, `disconnect`, `dispatchAction`,
 * `subscribeToStoreChanges`), same retry hooks, same `ViewData`/`StatusBarData`
 * decoding via [ProtoToView]. The Swift `AsyncThrowingStream` becomes a
 * Kotlin [Flow] here.
 */
public class UboConnection {

    private val mutex = Mutex()

    @Volatile
    private var channel: ManagedChannel? = null

    @Volatile
    private var storeClient: StoreServiceGrpcKt.StoreServiceCoroutineStub? = null

    @Volatile
    private var _state: ConnectionState = ConnectionState.DISCONNECTED

    public val state: ConnectionState get() = _state

    public var reconnectPolicy: ReconnectPolicy = ReconnectPolicy.Default

    /**
     * Open a gRPC connection to `host:port`. The new channel replaces any
     * previous one (which is shut down first).
     *
     * When [useTls] is `true` the channel negotiates TLS using the system
     * trust store (for reaching the device through a TLS-terminating reverse
     * proxy/tunnel); otherwise it connects in plaintext for a direct LAN
     * connection to the device's gRPC port.
     *
     * Mirrors Swift `UboConnection.connect(host:port:useTLS:)`.
     */
    public suspend fun connect(host: String, port: Int = 50051, useTls: Boolean = false) {
        Log.d(TAG, "connect($host:$port, tls=$useTls) start, thread=${Thread.currentThread().name}")
        mutex.withLock {
            Log.d(TAG, "  acquired mutex")
            channel?.shutdownNow()
            channel = null
            storeClient = null
            _state = ConnectionState.CONNECTING

            val newChannel = try {
                OkHttpChannelBuilder.forAddress(host, port)
                    .apply { if (!useTls) usePlaintext() }
                    // Confirmed root cause: the core's per-subscription
                    // event queue (store_service.py) overflows and drops
                    // AudioPlayAudioSequenceEvents under sustained TTS
                    // load even when this client's own read loop never
                    // blocks (verified via tracing — every AudioTrack
                    // write takes ~0ms). The bottleneck sits between the
                    // server's queue and bytes actually landing on this
                    // socket: `subscribe_event`'s `yield` blocks on HTTP/2
                    // flow control, which starves `queue.get()` on the
                    // next loop turn. OkHttpChannelBuilder's 64KiB default
                    // window was too small; iOS's grpc-swift/NIO and the
                    // browser's grpc-web negotiate something more
                    // generous by default, which is why only Android hit
                    // this. Widening the window here (verified via
                    // device testing: zero chunk loss, no reconnects,
                    // sub-second initial latency) fixed it.
                    .flowControlWindow(4 * 1024 * 1024)
                    .build()
                    .also { Log.d(TAG, "  channel built") }
            } catch (cancel: CancellationException) {
                Log.d(TAG, "  channel build cancelled")
                _state = ConnectionState.DISCONNECTED
                throw cancel
            } catch (t: Throwable) {
                Log.w(TAG, "  channel build failed", t)
                _state = ConnectionState.DISCONNECTED
                throw UboError.ConnectionFailed(t)
            }

            // OkHttp transport is lazy — verify the channel actually reaches
            // the server before flipping the state to CONNECTED. Otherwise
            // every subscription this client kicks off would individually
            // discover the connection is unreachable and emit duplicate
            // errors. Mirrors the Swift verifyConnection() retry loop.
            //
            // We deliberately let CancellationException propagate without
            // wrapping: structured concurrency uses it to signal "the caller
            // went away, abandon work" — wrapping would turn a benign
            // composition-leave into a fatal main-thread exception.
            // OkHttp transport's reported channel state flips to READY
            // optimistically (TCP-only). For an unreachable Ubo gRPC
            // server that's still listening on TCP — or DNS that resolves
            // but the server is dead — that lies. So instead of polling
            // the channel state, issue a real `SubscribeStore` RPC and
            // wait for the first frame within [PROBE_TIMEOUT_MS]. If the
            // server is reachable it sends `state.main.current_view`
            // immediately; if not, we time out or get UNAVAILABLE.
            val tempStub = StoreServiceGrpcKt.StoreServiceCoroutineStub(newChannel)
            try {
                Log.d(TAG, "  probing $host:$port")
                probeServer(tempStub)
                Log.d(TAG, "  probe ok")
            } catch (cancel: CancellationException) {
                Log.d(TAG, "  probe cancelled: ${cancel.message}")
                runCatching { newChannel.shutdownNow() }
                _state = ConnectionState.DISCONNECTED
                throw cancel
            } catch (t: Throwable) {
                Log.w(TAG, "  probe failed", t)
                runCatching { newChannel.shutdownNow() }
                _state = ConnectionState.DISCONNECTED
                if (t is UboError) throw t
                throw UboError.ConnectionFailed(t)
            }

            channel = newChannel
            storeClient = tempStub
            _state = ConnectionState.CONNECTED
            Log.d(TAG, "  state=CONNECTED")
        }
    }

    /**
     * Verify the server is reachable by issuing a real RPC. Subscribes
     * to `state.main.current_view` and waits for the first frame.
     *
     * - Reachable server → sends the current view immediately, function
     *   returns normally.
     * - Unreachable server → call errors with `UNAVAILABLE` (or some
     *   other gRPC status), or we hit the [PROBE_TIMEOUT_MS] deadline.
     *   Either way we throw [UboError.ConnectionFailed] (or
     *   [UboError.Timeout]).
     *
     * Cancellation propagates unchanged so structured concurrency works.
     */
    private suspend fun probeServer(
        stub: StoreServiceGrpcKt.StoreServiceCoroutineStub,
        timeoutMs: Long = PROBE_TIMEOUT_MS,
    ) {
        val request = Store.SubscribeStoreRequest.newBuilder()
            .addSelectors("state.main.current_view")
            .build()
        val frame = withTimeoutOrNull(timeoutMs) {
            stub.subscribeStore(request).first()
        }
        if (frame == null) throw UboError.Timeout
    }

    private companion object {
        private const val PROBE_TIMEOUT_MS: Long = 5_000
        private const val TAG: String = "UboConnection"
    }

    /** Mirror of Swift `UboConnection.disconnect()`. */
    public suspend fun disconnect() {
        mutex.withLock {
            channel?.shutdown()?.awaitTermination(2, TimeUnit.SECONDS)
            channel = null
            storeClient = null
            _state = ConnectionState.DISCONNECTED
        }
    }

    /** Dispatch a single [UboAction] as a unary `DispatchAction` RPC. */
    public suspend fun dispatchAction(action: UboAction) {
        val client = storeClient ?: throw UboError.NotConnected
        val request = Store.DispatchActionRequest.newBuilder()
            .setAction(ProtoFromAction.toProto(action))
            .build()
        try {
            client.dispatchAction(request)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            throw UboError.DispatchFailed(t)
        }
    }

    /**
     * Subscribe to store-state changes and stream decoded
     * `(ViewData, StatusBarData?)` tuples. Each emission corresponds to one
     * server frame.
     *
     * Mirrors Swift `UboConnection.subscribeToStoreChanges(selectors:)`.
     * The default selector list matches the Swift defaults.
     */
    public fun subscribeToStoreChanges(
        selectors: List<String> = listOf(
            "state.main.current_view",
            "state.main.status_bar",
        ),
    ): Flow<Pair<ViewData, StatusBarData?>> = flow {
        val client = storeClient ?: throw UboError.NotConnected
        val request = Store.SubscribeStoreRequest.newBuilder()
            .addAllSelectors(selectors)
            .build()

        try {
            client.subscribeStore(request).collect { response ->
                var view: ViewData? = null
                var status: StatusBarData? = null
                for (any in response.resultsList) {
                    if (view == null) {
                        ProtoToView.unpackViewData(any)?.let { view = it }
                    }
                    if (status == null) {
                        ProtoToView.unpackStatusBarData(any)?.let { status = it }
                    }
                }
                view?.let { emit(it to status) }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Log.w(TAG, "subscribeToStoreChanges: failed", t)
            throw UboError.SubscriptionFailed(t)
        }
    }

    /**
     * Subscribe to the device's `state.system` + `state.sensors` +
     * `state.localization` slices. Each emission is the full
     * [SystemStats] snapshot — partial updates are merged across frames
     * (the StatsHolder pattern from the Swift port). `clock` lives on
     * `state.localization`, not `state.system`.
     *
     * Mirrors `Sources/UboSwift/Connection/UboConnection.swift`
     * `subscribeToSystemStats()`.
     */
    public fun subscribeToSystemStats(): Flow<SystemStats> = flow {
        val client = storeClient ?: throw UboError.NotConnected
        val request = Store.SubscribeStoreRequest.newBuilder()
            .addAllSelectors(listOf("state.system", "state.sensors", "state.localization"))
            .build()
        var current: SystemStats? = null
        try {
            client.subscribeStore(request).collect { response ->
                val merged = ProtoToState.mergeSystemStats(current, response.resultsList)
                if (merged != null) {
                    current = merged
                    emit(merged)
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            throw UboError.SubscriptionFailed(t)
        }
    }

    /**
     * Subscribe to `state.web_ui.active_inputs`. Each emission is the
     * **current full list** — when an input demand resolves the server
     * sends an updated list with that entry removed.
     */
    public fun subscribeToActiveInputs(): Flow<List<WebUIInputDescription>> = flow {
        val client = storeClient ?: throw UboError.NotConnected
        val request = Store.SubscribeStoreRequest.newBuilder()
            .addAllSelectors(listOf("state.web_ui"))
            .build()
        try {
            client.subscribeStore(request).collect { response ->
                val inputs = ProtoToState.unpackActiveInputs(response.resultsList)
                if (inputs != null) emit(inputs)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            throw UboError.SubscriptionFailed(t)
        }
    }

    /**
     * Subscribe to camera viewfinder lifecycle events. The server filters
     * by event type using the `events` repeated field of
     * [SubscribeEventRequest][store.v1.Store.SubscribeEventRequest]; we
     * pass templated `Ubo.Event`s with the desired oneof case set.
     */
    public fun subscribeToCameraEvents(): Flow<ProtoToState.CameraEvent> = flow {
        val client = storeClient ?: throw UboError.NotConnected
        val filter = listOf(
            ubo.v1.Ubo.Event.newBuilder()
                .setCameraStartViewfinderEvent(ubo.v1.Ubo.CameraStartViewfinderEvent.getDefaultInstance())
                .build(),
            ubo.v1.Ubo.Event.newBuilder()
                .setCameraStopViewfinderEvent(ubo.v1.Ubo.CameraStopViewfinderEvent.getDefaultInstance())
                .build(),
            ubo.v1.Ubo.Event.newBuilder()
                .setCameraDetectAdvertiseEvent(ubo.v1.Ubo.CameraDetectAdvertiseEvent.getDefaultInstance())
                .build(),
        )
        val request = Store.SubscribeEventRequest.newBuilder()
            .addAllEvents(filter)
            .build()
        try {
            client.subscribeEvent(request).collect { response ->
                ProtoToState.convertCameraEvent(response.event)?.let { emit(it) }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            throw UboError.SubscriptionFailed(t)
        }
    }

    /**
     * Subscribe to the device's raw display render events. Each
     * emission carries one [com.ubopod.ubokotlin.models.DisplayRenderData]
     * frame — the rectangle, density, and packed RGB bytes the device
     * just pushed onto its panel.
     *
     * Mirrors the Swift `UboConnection.subscribeToDisplayRenderEvents()`.
     * Heavy: every frame the device redraws is forwarded, so callers
     * should treat this as an opt-in stream (off by default on
     * [com.ubopod.ubokotlin.UboClient.connect]).
     */
    public fun subscribeToDisplayRenderEvents(): Flow<com.ubopod.ubokotlin.models.DisplayRenderData> = flow {
        val client = storeClient ?: throw UboError.NotConnected
        val filter = listOf(
            ubo.v1.Ubo.Event.newBuilder()
                .setDisplayRenderEvent(ubo.v1.Ubo.DisplayRenderEvent.getDefaultInstance())
                .build(),
        )
        val request = Store.SubscribeEventRequest.newBuilder()
            .addAllEvents(filter)
            .build()
        try {
            client.subscribeEvent(request).collect { response ->
                ProtoToState.convertDisplayRenderEvent(response.event)?.let { emit(it) }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            throw UboError.SubscriptionFailed(t)
        }
    }

    /**
     * Subscribe to the device's audio playback event stream. Each
     * emission carries one playback action (sample, sequence chunk,
     * stop) ready to be routed through a local AudioTrack on the
     * client.
     */
    public fun subscribeToPlaybackEvents(): Flow<PlaybackEvent> = flow {
        val client = storeClient ?: throw UboError.NotConnected
        val filter = listOf(
            ubo.v1.Ubo.Event.newBuilder()
                .setAudioPlayAudioSampleEvent(ubo.v1.Ubo.AudioPlayAudioSampleEvent.getDefaultInstance())
                .build(),
            ubo.v1.Ubo.Event.newBuilder()
                .setAudioPlayAudioSequenceEvent(ubo.v1.Ubo.AudioPlayAudioSequenceEvent.getDefaultInstance())
                .build(),
            ubo.v1.Ubo.Event.newBuilder()
                .setAudioStopPlaybackEvent(ubo.v1.Ubo.AudioStopPlaybackEvent.getDefaultInstance())
                .build(),
        )
        val request = Store.SubscribeEventRequest.newBuilder()
            .addAllEvents(filter)
            .build()
        try {
            client.subscribeEvent(request).collect { response ->
                ProtoToState.convertPlaybackEvent(response.event)?.let { emit(it) }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            throw UboError.SubscriptionFailed(t)
        }
    }

    /**
     * Mark the connection as `RECONNECTING` from a long-lived subscription
     * loop. Public so the `runWithRetry` helper in the upcoming `UboClient`
     * can drive the state machine without exposing internals.
     */
    public suspend fun markReconnecting() {
        mutex.withLock {
            if (_state == ConnectionState.CONNECTED) {
                _state = ConnectionState.RECONNECTING
            }
        }
    }

    public suspend fun markConnected() {
        mutex.withLock {
            if (_state == ConnectionState.RECONNECTING) {
                _state = ConnectionState.CONNECTED
            }
        }
    }

    /**
     * Run [body] with exponential backoff (driven by [reconnectPolicy]) on
     * errors or graceful stream termination, until the consumer cancels or
     * `maxRetries` is exhausted. Mirrors the Swift `runWithRetry(...)`
     * helper used by every long-lived subscription.
     *
     * Errors that are NOT [CancellationException] count toward `maxRetries`
     * and trigger [onFinalError] when exhausted. A clean `body()` return is
     * a "soft retry" — the connection is still alive but the server stream
     * closed; back off once and resubscribe.
     */
    public suspend fun runWithRetry(
        body: suspend () -> Unit,
        onFinalError: (Throwable) -> Unit = {},
        onCancelled: () -> Unit = {},
    ): Unit = coroutineScope {
        var attempt = 0
        val policy = reconnectPolicy
        // A body that streamed healthily for this long resets the backoff.
        // Without the reset, sporadic blips over a long-lived session
        // accumulate towards maxRetries and permanently kill the
        // subscription even though every individual outage recovered.
        // Mirrors the fix applied to Swift's runWithRetry in cc0ab23.
        val healthyRunThresholdMs = 30_000L

        while (isActive) {
            val started = System.currentTimeMillis()
            try {
                body()
                // Body returned without error: server closed the stream.
                // Treat as a soft retry — back off then re-subscribe.
                if (System.currentTimeMillis() - started >= healthyRunThresholdMs) attempt = 0
                attempt += 1
                if (attempt >= policy.maxRetries) {
                    onFinalError(UboError.SubscriptionFailed(UboError.Timeout))
                    return@coroutineScope
                }
            } catch (cancel: CancellationException) {
                onCancelled()
                throw cancel
            } catch (t: Throwable) {
                if (System.currentTimeMillis() - started >= healthyRunThresholdMs) attempt = 0
                attempt += 1
                if (attempt >= policy.maxRetries) {
                    onFinalError(t)
                    return@coroutineScope
                }
                markReconnecting()
            }

            // ±20% jitter de-synchronises the parallel subscriptions' reconnect
            // attempts. A 0.2s floor stops a server that closes streams
            // immediately from driving a hot re-subscribe loop.
            val seconds = (policy.delaySeconds(attempt) * (0.8 + Math.random() * 0.4)).coerceAtLeast(0.2)
            try {
                delay((seconds * 1000.0).toLong())
            } catch (cancel: CancellationException) {
                onCancelled()
                throw cancel
            }
        }
        onCancelled()
    }
}
