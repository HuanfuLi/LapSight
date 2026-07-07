package com.huanfuli.lapsight.glasses

import android.util.Log
import androidx.annotation.GuardedBy
import com.huanfuli.lapsight.glasses.hud.HudRenderer
import com.huanfuli.lapsight.shared.GpsFixStatus
import com.huanfuli.lapsight.shared.SpeedUnit
import com.huanfuli.lapsight.shared.glasses.GlassesConnectionState
import com.huanfuli.lapsight.shared.glasses.GlassesDeviceSummary
import com.huanfuli.lapsight.shared.glasses.GlassesGpsState
import com.huanfuli.lapsight.shared.glasses.HudModel
import com.huanfuli.lapsight.shared.glasses.HudPage
import com.huanfuli.lapsight.shared.session.SessionController
import com.huanfuli.lapsight.shared.session.TimingRunSnapshot
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the DAT session lifecycle that connects a display-capable Meta glasses
 * device (Phase 7 MR-01/MR-03): register-gated `createSession` -> session
 * `STARTED` -> `addDisplay()` -> `DisplayState.STARTED`.
 *
 * KMP boundary (ARCH-01): every `com.meta.wearable.*` import lives HERE, in
 * `androidApp`. The bridge maps DAT session/device state into the
 * platform-free [GlassesConnectionState] / [GlassesDeviceSummary] shared types
 * exposed as [connectionState] / [devices] for 07-05's shared UI — shared code
 * never sees a DAT type. It reads the hoisted [SessionController] (the MR-01
 * seam) for timing state in a later slice of this plan (the ~2 Hz render
 * loop); this constructor already accepts it so that seam is wired end to end.
 *
 * Lifecycle mirrors the cloned SDK's DisplayAccess sample
 * (`DisplayViewModel`/`WearablesRepository`): `Wearables.devices` /
 * `devicesMetadata` are monitored continuously (read-only) so the device
 * picker (07-05) always has a fresh list; [connect] starts a session for a
 * user-picked device id, gates `addDisplay()` on `DeviceSessionState.STARTED`,
 * and gates the render loop on `DisplayState.STARTED`. A terminal `STOPPED`
 * NEVER reuses the old session — [connect] always builds a fresh one — and,
 * unless [stop] was called intentionally, the bridge silently recreates the
 * session in the background (D-11): no user-facing alert, and
 * [SessionController] is never touched, so phone timing keeps running.
 *
 * Once the display is `STARTED`, a single uniform coroutine (D-06) polls
 * [SessionController.timingRunSnapshot] at ~2 Hz (D-05), builds the pure
 * [HudModel], and pushes it via `sendContent` — with a frame-dedupe skip for
 * byte-identical models to bound BLE traffic (RESEARCH Pitfall 3). There is no
 * separate immediate-event path; lap completion / sector flashes simply appear
 * on the next beat.
 *
 * One instance is owned by `MainActivity` for the Activity's lifetime and
 * stopped via [stop] in `onDestroy`.
 */
class GlassesBridge(
    private val sessionController: SessionController,
    private val scope: CoroutineScope,
    private val idleGpsState: () -> GlassesGpsState = { GlassesGpsState.idle() },
    private val speedUnit: () -> SpeedUnit = { SpeedUnit.KilometersPerHour },
) {
    private companion object {
        private const val TAG = "GlassesBridge"

        /** ~2 Hz uniform render-and-push cadence (D-05); tunable per RESEARCH Pitfall 3. */
        private const val POLL_INTERVAL_MILLIS = 500L
    }

    /** Guards every mutable session/display/job field below (mirrors DisplayViewModel's `sessionLock`). */
    private val lock = Any()

    @GuardedBy("lock") private var session: DeviceSession? = null
    @GuardedBy("lock") private var display: Display? = null
    @GuardedBy("lock") private var sessionStateJob: Job? = null
    @GuardedBy("lock") private var sessionErrorJob: Job? = null
    @GuardedBy("lock") private var displayStateJob: Job? = null
    @GuardedBy("lock") private var renderLoopJob: Job? = null
    @GuardedBy("lock") private var targetDeviceId: DeviceIdentifier? = null
    @GuardedBy("lock") private var intentionalStop: Boolean = false

    @Volatile private var displayReady: Boolean = false
    private var lastSentModel: HudModel? = null

    private val deviceMonitoringJobs = mutableMapOf<DeviceIdentifier, Job>()
    private val _deviceMetadata = MutableStateFlow<Map<DeviceIdentifier, Device>>(emptyMap())

    private val _connectionState = MutableStateFlow<GlassesConnectionState>(GlassesConnectionState.Idle)

    /** Platform-free connection state for 07-05's shared UI (`collectAsState`). */
    val connectionState: StateFlow<GlassesConnectionState> = _connectionState.asStateFlow()

    private val _devices = MutableStateFlow<List<GlassesDeviceSummary>>(emptyList())

    /** Platform-free, continuously-updated device list for 07-05's device picker. */
    val devices: StateFlow<List<GlassesDeviceSummary>> = _devices.asStateFlow()

    /** Active HUD page (D-01); mutated by the phone-side page selector (07-05). */
    @Volatile var page: HudPage = HudPage.FOCUSED

    private val glassesInput = GlassesInput(
        currentPage = { page },
        selectPage = { selectedPage ->
            Log.i(TAG, "Glasses display click selected HUD page: $selectedPage")
            page = selectedPage
        },
    )

    /** Sector-flash window end (D-04); set by lap-crossing detection wired in a later plan. */
    @Volatile var flashUntilEpochMs: Long? = null

    init {
        // Read-only device monitoring (mirrors WearablesRepository.startMonitoring): populates
        // `devices` for the picker regardless of whether a session is active.
        scope.launch {
            Wearables.devices.collect { identifiers -> updateDeviceIdentifiers(identifiers) }
        }
        scope.launch {
            _deviceMetadata.collect { metadata -> publishDeviceSummaries(metadata) }
        }
    }

    /**
     * Connect to the device identified by the opaque [deviceId] (a
     * [GlassesDeviceSummary.id]). Always builds a NEW session — never reuses a
     * prior terminal one.
     */
    fun connect(deviceId: String) {
        synchronized(lock) { intentionalStop = false }
        startSession(DeviceIdentifier(deviceId))
    }

    /** Tear down the bridge for good (`MainActivity.onDestroy`); no further auto-reconnect. */
    fun stop() {
        synchronized(lock) { intentionalStop = true }
        teardownSession()
        _connectionState.value = GlassesConnectionState.Idle
    }

    private fun startSession(identifier: DeviceIdentifier) {
        synchronized(lock) { targetDeviceId = identifier }
        teardownSession()
        _connectionState.value = GlassesConnectionState.Connecting

        Wearables.createSession(SpecificDeviceSelector(identifier)).fold(
            onSuccess = { newSession ->
                synchronized(lock) { session = newSession }

                val stateJob = scope.launch {
                    newSession.state.collect { state -> handleSessionState(identifier, newSession, state) }
                }
                val errorJob = scope.launch {
                    newSession.errors.collect { error ->
                        handleSessionError(error)
                    }
                }
                synchronized(lock) {
                    sessionStateJob?.cancel()
                    sessionStateJob = stateJob
                    sessionErrorJob?.cancel()
                    sessionErrorJob = errorJob
                }

                newSession.start()
            },
            onFailure = { error, _ ->
                Log.e(TAG, "createSession failed: ${error.description}")
                _connectionState.value = GlassesConnectionState.Error(
                    message = error.description,
                    datAppUpdateRequired = error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED,
                )
            },
        )
    }

    private fun handleSessionState(
        identifier: DeviceIdentifier,
        session: DeviceSession,
        state: DeviceSessionState,
    ) {
        Log.i(TAG, "Session state: $state")
        when (state) {
            DeviceSessionState.STARTED -> {
                val alreadyAttached = synchronized(lock) { display != null }
                if (!alreadyAttached) attachDisplay(session)
            }
            DeviceSessionState.STOPPED -> handleSessionStopped(identifier)
            else -> Unit
        }
    }

    private fun attachDisplay(session: DeviceSession) {
        session.addDisplay().fold(
            onSuccess = { newDisplay ->
                synchronized(lock) { display = newDisplay }
                val stateJob = scope.launch {
                    newDisplay.state.collect { state -> handleDisplayState(state) }
                }
                synchronized(lock) {
                    displayStateJob?.cancel()
                    displayStateJob = stateJob
                }
            },
            onFailure = { error, _ ->
                Log.e(TAG, "addDisplay failed: ${error.description}")
                synchronized(lock) { intentionalStop = true }
                _connectionState.value = GlassesConnectionState.Error(error.description)
            },
        )
    }

    private fun handleSessionError(error: DeviceSessionError) {
        Log.e(TAG, "Session error: ${error.description}")
        if (error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) {
            // This is a terminal capability/version problem, not a transient link
            // drop. Do not enter the silent reconnect loop; Settings owns the
            // update action and the user can retry casting after updating.
            synchronized(lock) { intentionalStop = true }
            _connectionState.value = GlassesConnectionState.Error(
                message = error.description,
                datAppUpdateRequired = true,
            )
        }
    }

    private fun handleDisplayState(state: DisplayState) {
        Log.i(TAG, "Display state: $state")
        when (state) {
            DisplayState.STARTED -> {
                displayReady = true
                _connectionState.value = GlassesConnectionState.Connected
                startRenderLoop()
            }
            DisplayState.STOPPED, DisplayState.CLOSED -> {
                displayReady = false
                synchronized(lock) {
                    renderLoopJob?.cancel()
                    renderLoopJob = null
                }
            }
            else -> Unit
        }
    }

    private fun handleSessionStopped(identifier: DeviceIdentifier) {
        displayReady = false
        synchronized(lock) {
            renderLoopJob?.cancel()
            renderLoopJob = null
            displayStateJob?.cancel()
            displayStateJob = null
            display = null
            session = null
        }

        val shouldReconnect = synchronized(lock) { !intentionalStop && targetDeviceId == identifier }
        if (shouldReconnect) {
            // Silent auto-reconnect (D-11): only the glasses session is recreated
            // here, in a FRESH coroutine (never inline in this state collector,
            // which `startSession`'s teardown would otherwise try to cancel).
            // `sessionController` is never touched — phone timing keeps running.
            _connectionState.value = GlassesConnectionState.Reconnecting()
            scope.launch { startSession(identifier) }
        }
    }

    private fun startRenderLoop() {
        synchronized(lock) { renderLoopJob?.cancel() }
        val loopJob = scope.launch {
            while (isActive && displayReady) {
                val currentDisplay = synchronized(lock) { display }
                if (currentDisplay != null) {
                    pushFrame(currentDisplay)
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
        synchronized(lock) { renderLoopJob = loopJob }
    }

    /**
     * One render beat (D-06): poll [SessionController.timingRunSnapshot], build
     * the pure [HudModel], and push the full 3-page HUD renderer. Frame-dedupe
     * (D-06 discretion) skips `sendContent` for a byte-identical model to bound
     * BLE traffic (RESEARCH Pitfall 3).
     */
    private suspend fun pushFrame(display: Display) {
        val run = sessionController.timingRunSnapshot()
        val gps = gpsStateFor(run)
        val model = HudModel.from(
            run = run,
            gps = gps,
            page = page,
            nowEpochMs = System.currentTimeMillis(),
            flashUntilEpochMs = flashUntilEpochMs,
            speedUnit = speedUnit(),
        )
        if (model == lastSentModel) return

        display.sendContent {
            HudRenderer.render(
                scope = this,
                model = model,
                onClick = { glassesInput.handle(GlassesInputAction.DisplayClick) },
            )
        }.onFailure { error, _ -> Log.e(TAG, "sendContent failed: ${error.description}") }

        lastSentModel = model
    }

    /**
     * GPS/ready state for the [HudModel] (D-13/D-15). While a run is active,
     * reuse the snapshot's own accuracy/rate (already sampled from the live
     * feed) rather than probing `LocationSampleProvider` a second time (07-02's
     * documented fallback — see 07-02-SUMMARY.md). Pre-timing, defer to
     * [idleGpsState].
     */
    private fun gpsStateFor(run: TimingRunSnapshot): GlassesGpsState = if (run.isActive) {
        GlassesGpsState.from(
            fixStatus = GpsFixStatus.Live,
            accuracyMeters = run.accuracyMeters,
            sampleRateHz = run.sampleRateHz,
        )
    } else {
        idleGpsState()
    }

    private fun teardownSession() {
        val (jobsToCancel, sessionToStop) = synchronized(lock) {
            val jobs = listOfNotNull(sessionStateJob, sessionErrorJob, displayStateJob, renderLoopJob)
            sessionStateJob = null
            sessionErrorJob = null
            displayStateJob = null
            renderLoopJob = null
            val current = session
            session = null
            display = null
            jobs to current
        }
        jobsToCancel.forEach { it.cancel() }
        displayReady = false
        lastSentModel = null
        sessionToStop?.let { activeSession ->
            runCatching { activeSession.removeDisplay() }
                .onFailure { Log.e(TAG, "removeDisplay threw", it) }
            activeSession.stop()
        }
    }

    private fun updateDeviceIdentifiers(identifiers: Set<DeviceIdentifier>) {
        val current = _deviceMetadata.value
        val removed = current.keys - identifiers
        val added = identifiers - current.keys

        if (removed.isNotEmpty()) {
            removed.forEach { deviceMonitoringJobs.remove(it)?.cancel() }
            _deviceMetadata.update { it - removed }
        }

        added.forEach { id ->
            deviceMonitoringJobs[id] = scope.launch {
                Wearables.devicesMetadata[id]?.collect { device ->
                    _deviceMetadata.update { it + (id to device) }
                }
            }
        }
    }

    private fun publishDeviceSummaries(metadata: Map<DeviceIdentifier, Device>) {
        _devices.value = metadata.map { (id, device) ->
            GlassesDeviceSummary(
                id = id.toString(),
                name = device.name,
                type = device.deviceType.description,
                isDisplayCapable = device.isDisplayCapable(),
                requiresFirmwareUpdate = device.compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED,
            )
        }
    }
}
