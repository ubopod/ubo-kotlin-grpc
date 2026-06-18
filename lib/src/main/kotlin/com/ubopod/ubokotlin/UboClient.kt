package com.ubopod.ubokotlin

import com.ubopod.ubokotlin.connection.ConnectionState
import com.ubopod.ubokotlin.connection.ReconnectPolicy
import com.ubopod.ubokotlin.connection.UboConnection
import com.ubopod.ubokotlin.conversion.ProtoToState
import com.ubopod.ubokotlin.models.AudioDevice
import com.ubopod.ubokotlin.models.AudioSampleData
import com.ubopod.ubokotlin.models.Chime
import com.ubopod.ubokotlin.models.DisplayBlankTimeout
import com.ubopod.ubokotlin.models.DisplayRenderData
import com.ubopod.ubokotlin.models.Key
import com.ubopod.ubokotlin.models.PlaybackEvent
import com.ubopod.ubokotlin.models.StatusBarData
import com.ubopod.ubokotlin.models.SystemStats
import com.ubopod.ubokotlin.models.UboAction
import com.ubopod.ubokotlin.models.UboColor
import com.ubopod.ubokotlin.models.UboNotification
import com.ubopod.ubokotlin.models.ViewData
import com.ubopod.ubokotlin.models.WebUIInputDescription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Public client for interacting with Ubo devices via gRPC.
 *
 * Mirrors `Sources/UboSwift/UboClient.swift`. The Swift `@Published` Combine
 * properties become Kotlin [StateFlow]s; the Swift `Task<Void, Never>?`
 * subscription handles become [Job]s rooted in [scope]. Each public action
 * helper is a thin wrapper around [dispatch], which routes through the
 * underlying [UboConnection].
 *
 * Lifecycle: callers own the client. Call [disconnect] (or [close]) when
 * done so subscription jobs and the underlying gRPC channel shut down.
 *
 * ```
 * val client = UboClient()
 * client.connect("192.168.1.100")
 * client.startViewSubscription()
 * client.currentView.collect { view -> /* render */ }
 * client.pressKey(Key.UP)
 * client.disconnect()
 * ```
 */
public class UboClient(
    private val connection: UboConnection = UboConnection(),
    /**
     * Coroutine scope that owns all subscription jobs. Defaults to a
     * supervisor-rooted scope on [Dispatchers.Default]. Pass a custom scope
     * (e.g. an Android `viewModelScope`) to bind subscriptions to a host
     * lifecycle.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    public val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<UboError?>(null)
    public val lastError: StateFlow<UboError?> = _lastError.asStateFlow()

    private val _currentDisplay = MutableStateFlow<DisplayRenderData?>(null)

    /**
     * Latest raw display frame pushed by the device. Stays `null` unless
     * [startDisplaySubscription] (or `connect(..., subscribeToDisplay = true)`)
     * has been called.
     *
     * Mirrors Swift `UboClient.currentDisplay`.
     */
    public val currentDisplay: StateFlow<DisplayRenderData?> = _currentDisplay.asStateFlow()

    private val _currentView = MutableStateFlow<ViewData?>(null)
    public val currentView: StateFlow<ViewData?> = _currentView.asStateFlow()

    private val _statusBar = MutableStateFlow<StatusBarData?>(null)
    public val statusBar: StateFlow<StatusBarData?> = _statusBar.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    public val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _systemStats = MutableStateFlow<SystemStats?>(null)
    public val systemStats: StateFlow<SystemStats?> = _systemStats.asStateFlow()

    private val _isCameraViewfinderActive = MutableStateFlow(false)
    public val isCameraViewfinderActive: StateFlow<Boolean> = _isCameraViewfinderActive.asStateFlow()

    private val _cameraPattern = MutableStateFlow<String?>(null)
    public val cameraPattern: StateFlow<String?> = _cameraPattern.asStateFlow()

    private val _activeInputs = MutableStateFlow<List<WebUIInputDescription>>(emptyList())
    public val activeInputs: StateFlow<List<WebUIInputDescription>> = _activeInputs.asStateFlow()

    /**
     * This client's stable id for the camera-source registration protocol.
     * Set by the host app (typically derived from a per-install UUID); the
     * camera subscription uses it to filter [isCameraViewfinderActive]
     * transitions so only the selected source flips the flag. Empty
     * disables the filter for back-compat with pre-source-id devices.
     *
     * Mirrors the Swift `UboClient.cameraSourceId` property.
     */
    public var cameraSourceId: String = ""

    private val _cameraDetectAdvertise = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    /**
     * Fires when the device dispatches a `CameraDetectAdvertiseEvent`
     * (user tapped "Detect Cameras"). Subscribers should respond with
     * [registerAsCameraSource] so they are listed in the picker.
     *
     * Mirrors Swift `UboClient.cameraDetectAdvertiseSubject`.
     */
    public val cameraDetectAdvertise: SharedFlow<Unit> = _cameraDetectAdvertise.asSharedFlow()

    /** Tunable retry/backoff schedule for long-lived subscriptions. */
    public var reconnectPolicy: ReconnectPolicy
        get() = connection.reconnectPolicy
        set(value) {
            connection.reconnectPolicy = value
        }

    private var viewSubscriptionJob: Job? = null
    private var statsSubscriptionJob: Job? = null
    private var inputsSubscriptionJob: Job? = null
    private var cameraSubscriptionJob: Job? = null
    private var playbackSubscriptionJob: Job? = null
    private var displaySubscriptionJob: Job? = null

    public val isConnected: Boolean get() = _connectionState.value == ConnectionState.CONNECTED

    // ---- Connection management ----

    /**
     * Connect to an Ubo device.
     *
     * Mirrors Swift `connect(host:port:security:subscribeToDisplay:)`.
     * The Swift port defaults `subscribeToDisplay` to `true` so the
     * iPhone always mirrors the device's panel; Kotlin defaults it to
     * `false` because the Android phone-app doesn't consume
     * [currentDisplay] today and the stream is bandwidth-heavy. Set the
     * flag to `true` (or call [startDisplaySubscription] explicitly) to
     * enable raw frame mirroring.
     */
    public suspend fun connect(
        host: String,
        port: Int = 50051,
        subscribeToDisplay: Boolean = false,
    ): Unit = withContext(Dispatchers.IO) {
        // The readiness probe (and gRPC OkHttp's first-RPC transport
        // setup, which contains some synchronous work) must NOT run on
        // Dispatchers.Main, otherwise a slow connect blocks the UI
        // thread long enough to trigger an Android ANR ("Ubo isn't
        // responding"). withContext switches to IO regardless of where
        // the caller dispatched from.
        _connectionState.value = ConnectionState.CONNECTING
        _lastError.value = null
        try {
            connection.connect(host, port)
            _connectionState.value = ConnectionState.CONNECTED
            if (subscribeToDisplay) startDisplaySubscription()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            // Caller went away mid-probe — let structured concurrency
            // unwind cleanly. Don't surface this as a user-visible error.
            _connectionState.value = ConnectionState.DISCONNECTED
            throw cancel
        } catch (error: UboError) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _lastError.value = error
            throw error
        } catch (t: Throwable) {
            _connectionState.value = ConnectionState.DISCONNECTED
            val wrapped = UboError.ConnectionFailed(t)
            _lastError.value = wrapped
            throw wrapped
        }
    }

    /** Cancel all subscription jobs and shut down the gRPC channel. */
    public suspend fun disconnect() {
        viewSubscriptionJob?.cancel()
        viewSubscriptionJob = null
        statsSubscriptionJob?.cancel()
        statsSubscriptionJob = null
        inputsSubscriptionJob?.cancel()
        inputsSubscriptionJob = null
        cameraSubscriptionJob?.cancel()
        cameraSubscriptionJob = null
        playbackSubscriptionJob?.cancel()
        playbackSubscriptionJob = null
        displaySubscriptionJob?.cancel()
        displaySubscriptionJob = null

        connection.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        _currentView.value = null
        _currentDisplay.value = null
        _statusBar.value = null
        _systemStats.value = null
        _isCameraViewfinderActive.value = false
        _cameraPattern.value = null
        _activeInputs.value = emptyList()
    }

    /**
     * Cancel the client's coroutine [scope]. Use this when the client is
     * being disposed of; after [close] no further subscriptions can be
     * started.
     */
    public fun close() {
        scope.cancel()
    }

    // ---- Display pixel subscription ----

    /**
     * Subscribe to raw display render events; each frame populates
     * [currentDisplay]. Cancels any prior display subscription job.
     *
     * Mirrors Swift `UboClient.startDisplaySubscription()`. Off by
     * default on [connect] because forwarding every device-side redraw
     * costs bandwidth; callers that need a live screen mirror enable
     * it explicitly.
     */
    public fun startDisplaySubscription() {
        displaySubscriptionJob?.cancel()
        displaySubscriptionJob = scope.launch {
            connection.runWithRetry(
                body = {
                    connection.subscribeToDisplayRenderEvents().collect { frame ->
                        _currentDisplay.value = frame
                        connection.markConnected()
                    }
                },
                onFinalError = { t ->
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        _lastError.value = (t as? UboError) ?: UboError.SubscriptionFailed(t)
                    }
                },
            )
        }
    }

    public fun stopDisplaySubscription() {
        displaySubscriptionJob?.cancel()
        displaySubscriptionJob = null
        _currentDisplay.value = null
    }

    // ---- View subscription ----

    /**
     * Start subscribing to view-state changes (current view + status bar).
     * Cancels any prior view subscription job.
     */
    public fun startViewSubscription() {
        viewSubscriptionJob?.cancel()
        viewSubscriptionJob = scope.launch {
            connection.runWithRetry(
                body = {
                    connection.subscribeToStoreChanges().collect { (view, status) ->
                        _currentView.value = view
                        _statusBar.value = status
                        connection.markConnected()
                    }
                },
                onFinalError = { t ->
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        _lastError.value = (t as? UboError) ?: UboError.SubscriptionFailed(t)
                    }
                },
            )
        }
    }

    public fun stopViewSubscription() {
        viewSubscriptionJob?.cancel()
        viewSubscriptionJob = null
    }

    // ---- System stats / inputs / camera subscriptions ----

    public fun startStatsSubscription() {
        statsSubscriptionJob?.cancel()
        statsSubscriptionJob = scope.launch {
            connection.runWithRetry(
                body = {
                    connection.subscribeToSystemStats().collect { stats ->
                        _systemStats.value = stats
                        connection.markConnected()
                    }
                },
                onFinalError = { t ->
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        _lastError.value = (t as? UboError) ?: UboError.SubscriptionFailed(t)
                    }
                },
            )
        }
    }

    public fun stopStatsSubscription() {
        statsSubscriptionJob?.cancel()
        statsSubscriptionJob = null
    }

    public fun startInputsSubscription() {
        inputsSubscriptionJob?.cancel()
        inputsSubscriptionJob = scope.launch {
            connection.runWithRetry(
                body = {
                    connection.subscribeToActiveInputs().collect { inputs ->
                        _activeInputs.value = inputs
                        connection.markConnected()
                    }
                },
                onFinalError = { t ->
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        _lastError.value = (t as? UboError) ?: UboError.SubscriptionFailed(t)
                    }
                },
            )
        }
    }

    public fun stopInputsSubscription() {
        inputsSubscriptionJob?.cancel()
        inputsSubscriptionJob = null
    }

    public fun startCameraSubscription() {
        cameraSubscriptionJob?.cancel()
        cameraSubscriptionJob = scope.launch {
            connection.runWithRetry(
                body = {
                    connection.subscribeToCameraEvents().collect { event ->
                        when (event) {
                            is ProtoToState.CameraEvent.StartViewfinder -> {
                                // Honour the Pi's selection: empty sourceId
                                // (legacy devices) keeps old "any source"
                                // behaviour; otherwise only react when
                                // this client owns the registration.
                                if (event.sourceId.isEmpty() || event.sourceId == cameraSourceId) {
                                    _cameraPattern.value = event.pattern
                                    _isCameraViewfinderActive.value = true
                                }
                            }
                            ProtoToState.CameraEvent.StopViewfinder -> {
                                _isCameraViewfinderActive.value = false
                                _cameraPattern.value = null
                            }
                            ProtoToState.CameraEvent.DetectAdvertise -> {
                                _cameraDetectAdvertise.tryEmit(Unit)
                            }
                        }
                        connection.markConnected()
                    }
                },
                onFinalError = { t ->
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        _lastError.value = (t as? UboError) ?: UboError.SubscriptionFailed(t)
                    }
                },
            )
        }
    }

    public fun stopCameraSubscription() {
        cameraSubscriptionJob?.cancel()
        cameraSubscriptionJob = null
        _isCameraViewfinderActive.value = false
        _cameraPattern.value = null
    }

    /**
     * Cold [Flow] of audio playback events streamed from the device.
     *
     * **Prefer [startPlaybackSubscription]**: a raw collector on this
     * Flow has to handle gRPC errors itself, otherwise a transient
     * failure crashes the calling coroutine. The convenience helper
     * wraps the same Flow in [UboConnection.runWithRetry] so transient
     * failures auto-retry through [reconnectPolicy].
     *
     * Mirrors Swift `UboClient.playbackEvents()`.
     */
    public fun playbackEvents(): Flow<PlaybackEvent> = connection.subscribeToPlaybackEvents()

    /**
     * Subscribe to the device's audio playback events with the same
     * retry semantics as [startViewSubscription] / [startStatsSubscription]
     * etc. Each [PlaybackEvent] is delivered to [onEvent] in the order
     * it arrives. Cancels any prior playback subscription.
     */
    public fun startPlaybackSubscription(onEvent: suspend (PlaybackEvent) -> Unit) {
        playbackSubscriptionJob?.cancel()
        playbackSubscriptionJob = scope.launch {
            connection.runWithRetry(
                body = {
                    connection.subscribeToPlaybackEvents().collect { event ->
                        onEvent(event)
                        connection.markConnected()
                    }
                },
                onFinalError = { t ->
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        _lastError.value = (t as? UboError) ?: UboError.SubscriptionFailed(t)
                    }
                },
            )
        }
    }

    public fun stopPlaybackSubscription() {
        playbackSubscriptionJob?.cancel()
        playbackSubscriptionJob = null
    }

    // ---- Keypad / navigation helpers ----

    public suspend fun pressKey(key: Key, time: Double = 0.0): Unit =
        dispatch(UboAction.KeypadKeyPress(key, time))

    public suspend fun pressKeys(keys: Set<Key>, time: Double = 0.0): Unit =
        dispatch(UboAction.KeypadKeyPressMultiple(keys, time))

    public suspend fun releaseKey(key: Key): Unit =
        dispatch(UboAction.KeypadKeyRelease(key))

    public suspend fun pressL1(): Unit = pressKey(Key.L1)
    public suspend fun pressL2(): Unit = pressKey(Key.L2)
    public suspend fun pressL3(): Unit = pressKey(Key.L3)

    public suspend fun goBack(): Unit = dispatch(UboAction.MenuGoBack)
    public suspend fun goHome(): Unit = dispatch(UboAction.MenuGoHome)
    public suspend fun scrollUp(): Unit = dispatch(UboAction.MenuScrollUp)
    public suspend fun scrollDown(): Unit = dispatch(UboAction.MenuScrollDown)

    public suspend fun selectMenuItem(index: Int): Unit =
        dispatch(UboAction.MenuChooseByIndex(index))

    public suspend fun selectMenuItem(label: String): Unit =
        dispatch(UboAction.MenuChooseByLabel(label))

    public suspend fun selectMenuItemByIcon(icon: String): Unit =
        dispatch(UboAction.MenuChooseByIcon(icon))

    public suspend fun pushMenu(menuKey: String): Unit =
        dispatch(UboAction.StackPushMenu(menuKey))

    public suspend fun popStack(count: Int = 1): Unit =
        dispatch(UboAction.StackPop(count))

    public suspend fun popToRoot(): Unit = dispatch(UboAction.StackPopToRoot)

    // ---- Audio ----

    public suspend fun setVolume(level: Float, device: AudioDevice = AudioDevice.OUTPUT): Unit =
        dispatch(UboAction.AudioSetVolume(level, device))

    public suspend fun changeVolume(by: Float, device: AudioDevice = AudioDevice.OUTPUT): Unit =
        dispatch(UboAction.AudioChangeVolume(by, device))

    public suspend fun setMute(muted: Boolean, device: AudioDevice = AudioDevice.OUTPUT): Unit =
        dispatch(UboAction.AudioSetMute(muted, device))

    public suspend fun toggleMute(device: AudioDevice = AudioDevice.OUTPUT): Unit =
        dispatch(UboAction.AudioToggleMute(device))

    public suspend fun playChime(chime: Chime): Unit =
        dispatch(UboAction.AudioPlayChime(chime))

    public suspend fun startRecording() {
        dispatch(UboAction.AudioStartRecording)
        _isRecording.value = true
    }

    public suspend fun stopRecording() {
        dispatch(UboAction.AudioStopRecording)
        _isRecording.value = false
    }

    public suspend fun playRecording(): Unit = dispatch(UboAction.AudioPlayRecording)

    /**
     * [audioSource] must match the id passed to [startAssistantListening] for
     * this session, so the core accepts these samples and drops the device's
     * built-in mic. Empty means the on-device system mic.
     */
    public suspend fun reportAudioSample(
        timestamp: Float,
        data: ByteArray,
        channels: Int = 1,
        rate: Int = 16000,
        width: Int = 2,
        audioSource: String = "",
    ): Unit = dispatch(
        UboAction.AudioReportSample(
            timestamp = timestamp,
            sample = AudioSampleData(data, channels, rate, width),
            audioSource = audioSource,
        ),
    )

    // ---- Camera (frames → device) ----

    /**
     * Send a single camera frame to the device as a `CameraReportImageAction`.
     *
     * @param data Encoded frame bytes (typically JPEG; the device's camera
     *   service decodes whatever you upload).
     * @param width Frame width in pixels.
     * @param height Frame height in pixels.
     * @param timestamp Capture timestamp in seconds.
     *
     * Tags the outbound action with [cameraSourceId] so the Pi can route
     * the frame to the right pipeline (and drop it if some other source
     * is currently selected). Remote clients can never dispatch events
     * directly — events are emitted only from reducers.
     *
     * Mirrors Swift `UboClient.sendCameraFrame(data:width:height:timestamp:)`.
     */
    public suspend fun sendCameraFrame(
        data: ByteArray,
        width: Int,
        height: Int,
        timestamp: Float,
    ): Unit = dispatch(
        UboAction.CameraReportImage(
            timestamp = timestamp,
            data = data,
            width = width,
            height = height,
            sourceId = cameraSourceId,
        ),
    )

    /**
     * Register this client as a remote camera source on the device. The
     * device's camera picker will list it alongside its local USB /
     * picamera devices; selecting it makes the device dispatch
     * `CameraStartViewfinderEvent` here, at which point the host app
     * should begin pumping frames via [sendCameraFrame].
     *
     * Side-effect: also assigns [cameraSourceId] so subsequent
     * viewfinder events are correctly filtered for this client.
     *
     * Mirrors Swift `UboClient.registerAsCameraSource(id:label:)`.
     */
    public suspend fun registerAsCameraSource(id: String, label: String) {
        cameraSourceId = id
        dispatch(UboAction.CameraRegisterRemote(sourceId = id, label = label))
    }

    // ---- Display ----

    public suspend fun blankDisplay(): Unit = dispatch(UboAction.DisplayBlank)
    public suspend fun unblankDisplay(): Unit = dispatch(UboAction.DisplayUnblank)
    public suspend fun pauseDisplay(): Unit = dispatch(UboAction.DisplayPause)
    public suspend fun resumeDisplay(): Unit = dispatch(UboAction.DisplayResume)
    public suspend fun requestDisplayRedraw(): Unit = dispatch(UboAction.DisplayRedraw)

    public suspend fun setDisplayTimeout(timeout: DisplayBlankTimeout): Unit =
        dispatch(UboAction.DisplaySetBlankTimeout(timeout))

    // ---- RGB ring ----

    public suspend fun setLEDColor(color: UboColor): Unit =
        dispatch(UboAction.RgbRingSetAll(color))

    public suspend fun clearLEDs(): Unit = dispatch(UboAction.RgbRingBlank)

    public suspend fun setLEDBrightness(brightness: Float): Unit =
        dispatch(UboAction.RgbRingSetBrightness(brightness))

    public suspend fun setLEDEnabled(enabled: Boolean): Unit =
        dispatch(UboAction.RgbRingSetEnabled(enabled))

    public suspend fun pulseLEDs(color: UboColor, repetitions: Int = 3, wait: Double = 0.5): Unit =
        dispatch(UboAction.RgbRingPulse(color, repetitions, wait))

    public suspend fun blinkLEDs(color: UboColor, repetitions: Int = 3, wait: Double = 0.5): Unit =
        dispatch(UboAction.RgbRingBlink(color, repetitions, wait))

    public suspend fun rainbowLEDs(rounds: Int = 1, wait: Double = 0.05): Unit =
        dispatch(UboAction.RgbRingRainbow(rounds, wait))

    public suspend fun spinningWheelLEDs(
        color: UboColor,
        rounds: Int = 1,
        length: Int = 3,
        wait: Double = 0.05,
    ): Unit = dispatch(UboAction.RgbRingSpinningWheel(color, rounds, length, wait))

    public suspend fun progressWheelLEDs(color: UboColor, percentage: Int): Unit =
        dispatch(UboAction.RgbRingProgressWheel(color, percentage))

    // ---- Power ----

    public suspend fun powerOff(): Unit = dispatch(UboAction.PowerOff)
    public suspend fun reboot(): Unit = dispatch(UboAction.Reboot)

    // ---- Notifications ----

    public suspend fun addNotification(notification: UboNotification): Unit =
        dispatch(UboAction.NotificationAdd(notification))

    public suspend fun displayNotification(notification: UboNotification): Unit =
        dispatch(UboAction.NotificationDisplay(notification))

    public suspend fun notify(title: String, content: String, chime: Chime? = null): Unit =
        addNotification(UboNotification(title = title, content = content, chime = chime))

    public suspend fun removeNotification(id: String): Unit =
        dispatch(UboAction.NotificationRemove(id))

    public suspend fun clearAllNotifications(): Unit = dispatch(UboAction.NotificationClearAll)

    // ---- Assistant ----

    /**
     * Start assistant listening. Pass [audioSource] when this client also
     * streams its own microphone (see [reportAudioSample]): the core binds the
     * session to that id and ignores the device's built-in mic. Leave it empty
     * to use the on-device system mic. The same id must be set on every
     * [reportAudioSample].
     */
    public suspend fun startAssistantListening(audioSource: String = ""): Unit =
        dispatch(UboAction.AssistantStartListening(audioSource))
    public suspend fun stopAssistantListening(): Unit = dispatch(UboAction.AssistantStopListening)

    /** Toggle assistant listening. See [startAssistantListening] for [audioSource]. */
    public suspend fun toggleAssistantListening(audioSource: String = ""): Unit =
        dispatch(UboAction.AssistantToggleListening(audioSource))

    /**
     * Toggle playback of an audio chat bubble. On the device this is bound to
     * the bubble's L1/L2/L3 button; touch clients call this when the bubble is
     * tapped. The core flips the bubble's `is_playing` flag.
     */
    public suspend fun toggleChatAudio(messageId: String): Unit =
        dispatch(UboAction.ChatToggleAudioPlayback(messageId))

    // ---- Input demands ----

    public suspend fun provideInput(id: String, value: String): Unit =
        dispatch(UboAction.InputProvide(id, value))

    public suspend fun cancelInput(id: String): Unit =
        dispatch(UboAction.InputCancel(id))

    // ---- Raw dispatch ----

    /**
     * Dispatch an arbitrary [UboAction]. Most callers want one of the
     * typed helpers above; this is exposed for actions that aren't yet
     * wrapped in a helper.
     */
    public suspend fun dispatch(action: UboAction) {
        if (!isConnected) throw UboError.NotConnected
        try {
            connection.dispatchAction(action)
        } catch (error: UboError) {
            _lastError.value = error
            throw error
        } catch (t: Throwable) {
            val wrapped = UboError.DispatchFailed(t)
            _lastError.value = wrapped
            throw wrapped
        }
    }
}
