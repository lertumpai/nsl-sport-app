package com.nsl.sportapp.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.nsl.sportapp.data.model.IntervalConfig
import com.nsl.sportapp.wear.datalayer.WearSyncHelper
import com.nsl.sportapp.data.model.IntervalMode
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutSegmentEntity
import com.nsl.sportapp.wear.data.repository.WearWorkoutRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer

class WearWorkoutService : LifecycleService(), MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "WearWorkoutService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "wear_workout_channel"
        private const val LOCATION_INTERVAL_MS = 2000L

        const val ACTION_START = "com.nsl.sportapp.wear.ACTION_START"
        const val ACTION_STOP = "com.nsl.sportapp.wear.ACTION_STOP"
        const val ACTION_PAUSE = "com.nsl.sportapp.wear.ACTION_PAUSE"
        const val ACTION_RESUME = "com.nsl.sportapp.wear.ACTION_RESUME"

        // Pace alert extras (seconds per km, 0 = disabled)
        const val EXTRA_MIN_PACE = "extra_min_pace"
        const val EXTRA_MAX_PACE = "extra_max_pace"
        // IntervalConfig as JSON (optional)
        const val EXTRA_INTERVAL_CONFIG = "extra_interval_config"

        private const val PACE_ALERT_INTERVAL_MS = 5000L
        private val json = Json { ignoreUnknownKeys = true }

        // GPS quality thresholds
        private const val GPS_WARMUP_FIXES = 4       // Skip first N fixes while GPS settles
        private const val MAX_ACCURACY_METERS = 50f  // Reject fixes worse than this
        private const val MIN_MOVEMENT_METERS = 3.0f // Minimum distance per update (reduces jitter)
        private const val MIN_SPEED_MPS = 0.4f       // ~1.4 km/h — below is stationary
        private const val MAX_SPEED_MPS = 12f        // ~43 km/h — above is GPS glitch/teleport

        // Paths for messages from phone
        const val PATH_WORKOUT_START = "/workout/start"
        const val PATH_WORKOUT_STOP = "/workout/stop"
        const val PATH_HEARTRATE = "/workout/heartrate"

        private val _state = MutableStateFlow(WorkoutState())
        val state: StateFlow<WorkoutState> = _state.asStateFlow()

        // Convenience accessors derived from main state flow
        val heartRate: Int get() = _state.value.heartRate
        val isActive: Boolean get() = _state.value.isRunning
    }

    data class WorkoutState(
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val elapsedMillis: Long = 0L,
        val distanceMeters: Float = 0f,
        val currentPaceSecsPerKm: Float = 0f,
        val avgPaceSecsPerKm: Float = 0f,
        val heartRate: Int = 0,
        val gpsActive: Boolean = false,
        // Pace alert: 0 = disabled
        val minPaceSecsPerKm: Int = 0,
        val maxPaceSecsPerKm: Int = 0,
        val paceAlertEnabled: Boolean = false,
        val paceInRange: Boolean = true,
        // Interval mode
        val intervalConfig: IntervalConfig? = null,
        val currentActivityLabel: String = "",
        val currentActivityIndex: Int = 0,
        val currentRepetition: Int = 1,
        val activityDistanceStart: Float = 0f,
        // Bluetooth connection
        val phoneConnected: Boolean = false
    )

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: WearWorkoutRepository
    private lateinit var messageClient: MessageClient

    private var lastLocation: Location? = null
    private var startTime: Long = 0L
    private var timerJob: Job? = null
    private var paceAlertJob: Job? = null
    private var connectedNodeId: String? = null
    private var healthServicesRegistered = false
    // Track last real movement so we can zero pace when stationary
    private var lastMovementTime: Long = 0L
    // GPS warmup counter — first N fixes are discarded while GPS settles on actual position
    private var gpsFixCount = 0

    private val locationHistory = ArrayDeque<Pair<Long, Float>>(30)
    private val segmentBuffer = mutableListOf<WearWorkoutSegmentEntity>()

    private val passiveListenerCallback = object : PassiveListenerCallback {
        override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
            if (!healthServicesRegistered) return
            dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { point ->
                val hr = point.value.toInt()
                _state.value = _state.value.copy(heartRate = hr)
                sendHeartRateToPhone(hr)
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleNewLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = WearWorkoutRepository(this)
        messageClient = Wearable.getMessageClient(this)
        messageClient.addListener(this)
        createNotificationChannel()
        findConnectedNode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val minPace = intent.getIntExtra(EXTRA_MIN_PACE, 0)
                val maxPace = intent.getIntExtra(EXTRA_MAX_PACE, 0)
                val configJson = intent.getStringExtra(EXTRA_INTERVAL_CONFIG)
                val config = configJson?.let {
                    runCatching { json.decodeFromString<IntervalConfig>(it) }.getOrNull()
                }
                startWorkout(minPace, maxPace, config)
            }
            ACTION_STOP -> stopWorkout()
            ACTION_PAUSE -> pauseWorkout()
            ACTION_RESUME -> resumeWorkout()
        }
        return START_NOT_STICKY
    }

    private fun startWorkout(minPace: Int = 0, maxPace: Int = 0, config: IntervalConfig? = null) {
        startTime = System.currentTimeMillis()
        locationHistory.clear()
        segmentBuffer.clear()
        lastLocation = null
        lastMovementTime = System.currentTimeMillis()
        gpsFixCount = 0

        // Determine pace from config if provided
        val effectiveMin = if (config != null) config.minPaceSecsPerKm else minPace
        val effectiveMax = if (config != null) config.maxPaceSecsPerKm else maxPace
        val paceAlertEnabled = config?.paceAlertEnabled ?: (effectiveMin > 0 && effectiveMax > 0)

        val firstActivityLabel = config?.takeIf { it.mode == IntervalMode.ACTIVITY_BASED }
            ?.activities?.firstOrNull()?.type?.label ?: ""

        _state.value = WorkoutState(
            isRunning = true, isPaused = false,
            minPaceSecsPerKm = effectiveMin, maxPaceSecsPerKm = effectiveMax,
            paceAlertEnabled = paceAlertEnabled,
            intervalConfig = config,
            currentActivityLabel = firstActivityLabel,
            currentActivityIndex = 0, currentRepetition = 1, activityDistanceStart = 0f
        )

        startForeground(NOTIFICATION_ID, buildNotification("กำลังวิ่ง..."))
        startLocationUpdates()
        startTimer()
        if (paceAlertEnabled) startPaceAlertMonitor()
        registerHeartRateMonitor()
        findConnectedNode()
        vibrate(longArrayOf(0, 100, 100, 100))
    }

    private fun stopWorkout() {
        val snapshot = _state.value
        if (!snapshot.isRunning) return

        stopLocationUpdates()
        timerJob?.cancel()
        paceAlertJob?.cancel()
        unregisterHeartRateMonitor()

        _state.value = WorkoutState(isRunning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        vibrate(longArrayOf(0, 200, 100, 200, 100, 400))

        lifecycleScope.launch {
            saveWorkoutToDb(snapshot)
            triggerSyncToPhone()
            stopSelf()
        }
    }

    private fun pauseWorkout() {
        if (!_state.value.isRunning || _state.value.isPaused) return
        stopLocationUpdates()
        timerJob?.cancel()
        paceAlertJob?.cancel()
        // Clear pace history so stale data doesn't affect pace calculation on resume
        locationHistory.clear()
        _state.value = _state.value.copy(isPaused = true, currentPaceSecsPerKm = 0f)
    }

    private fun resumeWorkout() {
        if (!_state.value.isRunning || !_state.value.isPaused) return
        // Re-warm GPS after pause so first few fixes don't skew distance/pace
        gpsFixCount = 0
        lastMovementTime = System.currentTimeMillis()
        _state.value = _state.value.copy(isPaused = false, currentPaceSecsPerKm = 0f)
        startLocationUpdates()
        startTimer()
        if (_state.value.paceAlertEnabled) startPaceAlertMonitor()
    }

    private fun startPaceAlertMonitor() {
        paceAlertJob = lifecycleScope.launch {
            while (true) {
                delay(PACE_ALERT_INTERVAL_MS)
                val s = _state.value
                val pace = s.currentPaceSecsPerKm.toInt()
                if (pace > 0 && s.paceAlertEnabled) {
                    val inRange = pace in s.minPaceSecsPerKm..s.maxPaceSecsPerKm
                    _state.value = s.copy(paceInRange = inRange)
                    if (!inRange) vibrate(longArrayOf(0, 150, 100, 150))
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(1000)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startTimer() {
        val baseElapsed = _state.value.elapsedMillis
        val startTs = System.currentTimeMillis()
        timerJob = lifecycleScope.launch {
            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                val elapsed = baseElapsed + (now - startTs)
                // Zero pace when no movement for more than 5 seconds
                val isStationary = lastMovementTime > 0L && (now - lastMovementTime) > 5_000L
                _state.value = _state.value.copy(
                    elapsedMillis = elapsed,
                    currentPaceSecsPerKm = if (isStationary) 0f else _state.value.currentPaceSecsPerKm,
                    paceInRange = if (isStationary) true else _state.value.paceInRange
                )
                updateNotification()
            }
        }
    }

    private fun handleNewLocation(location: Location) {
        val s = _state.value
        if (!s.isRunning || s.isPaused) return

        // 1. Reject poor-accuracy fixes (GPS not yet settled, indoors, urban canyon)
        if (location.accuracy > MAX_ACCURACY_METERS) {
            _state.value = s.copy(gpsActive = false)
            return
        }

        // 2. GPS warmup: accept position but don't count distance for first N fixes.
        //    The first few fixes drift from last-known/cached position to actual position,
        //    which would appear as false movement of 100s of metres.
        gpsFixCount++
        if (gpsFixCount <= GPS_WARMUP_FIXES) {
            lastLocation = location
            _state.value = s.copy(gpsActive = true)
            return
        }

        val prev = lastLocation ?: run { lastLocation = location; return }
        val delta = prev.distanceTo(location)
        val now = System.currentTimeMillis()

        // 3. Teleport guard: if computed speed exceeds a runner's max, it's a GPS glitch.
        //    Update lastLocation to new position but don't count the distance.
        val timeDeltaSecs = ((now - (locationHistory.lastOrNull()?.first ?: (now - LOCATION_INTERVAL_MS))) / 1000f)
            .coerceAtLeast(0.5f)
        val computedSpeedMs = if (timeDeltaSecs > 0) delta / timeDeltaSecs else 0f
        if (computedSpeedMs > MAX_SPEED_MPS) {
            Log.w(TAG, "GPS teleport detected (${computedSpeedMs.toInt()} m/s) — skipping")
            lastLocation = location
            return
        }

        // 4. Movement validation: use GPS chip speed when available (more reliable than
        //    distance deltas for low speeds / stationary).
        val isMoving: Boolean = if (location.hasSpeed() && location.speed >= 0f) {
            location.speed >= MIN_SPEED_MPS
        } else {
            delta >= MIN_MOVEMENT_METERS
        }

        lastLocation = location

        if (!isMoving) return

        val newTotal = s.distanceMeters + delta
        lastMovementTime = now

        locationHistory.addLast(now to newTotal)
        while (locationHistory.size > 1 && now - locationHistory.first().first > 30_000)
            locationHistory.removeFirst()

        val pace = calculateRollingPace()
        val avgPace = calculateAvgPace(newTotal)

        var updatedState = s.copy(
            distanceMeters = newTotal,
            currentPaceSecsPerKm = pace,
            avgPaceSecsPerKm = avgPace,
            gpsActive = true
        )
        updatedState = checkIntervalTransition(updatedState, newTotal)
        _state.value = updatedState

        segmentBuffer.add(
            WearWorkoutSegmentEntity(
                workoutId = 0,
                timestamp = now,
                latitude = location.latitude,
                longitude = location.longitude,
                distanceFromStartMeters = newTotal,
                paceSecsPerKm = pace,
                heartRate = _state.value.heartRate
            )
        )
    }

    private fun checkIntervalTransition(state: WorkoutState, totalDistance: Float): WorkoutState {
        val config = state.intervalConfig ?: return state
        if (config.mode != IntervalMode.ACTIVITY_BASED || config.activities.isEmpty()) return state

        val activityDist = config.activityDistance(state.currentActivityIndex)
        val traveledInActivity = totalDistance - state.activityDistanceStart
        if (traveledInActivity < activityDist) return state

        // Advance to next activity
        val nextIndex = (state.currentActivityIndex + 1) % config.activities.size
        val completedCycle = nextIndex == 0
        val nextRep = if (completedCycle) state.currentRepetition + 1 else state.currentRepetition

        if (completedCycle && nextRep > config.repetitions) {
            // All intervals complete
            vibrate(longArrayOf(0, 300, 100, 300, 100, 600))
            return state.copy(
                currentActivityLabel = "เสร็จแล้ว!",
                intervalConfig = null
            )
        }

        vibrate(longArrayOf(0, 200, 100, 200))
        return state.copy(
            currentActivityIndex = nextIndex,
            currentRepetition = nextRep,
            activityDistanceStart = totalDistance,
            currentActivityLabel = config.activityLabel(nextIndex)
        )
    }

    private fun calculateRollingPace(): Float {
        if (locationHistory.size < 2) return 0f
        val oldest = locationHistory.first(); val newest = locationHistory.last()
        val timeSecs = (newest.first - oldest.first) / 1000f
        val distKm = (newest.second - oldest.second) / 1000f
        if (distKm <= 0) return 0f
        return timeSecs / distKm
    }

    private fun calculateAvgPace(totalDistanceMeters: Float): Float {
        val elapsedSecs = _state.value.elapsedMillis / 1000f
        val distKm = totalDistanceMeters / 1000f
        if (distKm <= 0) return 0f
        return elapsedSecs / distKm
    }

    private suspend fun saveWorkoutToDb(state: WorkoutState) {
        // Always save — even 0 m workouts (e.g. stationary HR sessions)
        val hrValues = segmentBuffer.map { it.heartRate }.filter { it > 0 }
        val avgHr = if (hrValues.isEmpty()) 0 else hrValues.average().toInt()
        val maxHr = hrValues.maxOrNull() ?: 0

        val workout = WearWorkoutEntity(
            startTime = startTime,
            endTime = System.currentTimeMillis(),
            durationMillis = state.elapsedMillis,
            totalDistanceMeters = state.distanceMeters,
            avgPaceSecsPerKm = state.avgPaceSecsPerKm,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            synced = false
        )
        try {
            repository.saveWorkout(workout, segmentBuffer.toList())
            Log.d(TAG, "Workout saved to watch DB")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save workout", e)
        }
    }

    // ── Sync to Phone ─────────────────────────────────────────────────────

    private fun findConnectedNode() {
        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@WearWorkoutService).connectedNodes.await()
                connectedNodeId = nodes.firstOrNull()?.id
                _state.value = _state.value.copy(phoneConnected = connectedNodeId != null)
            } catch (e: Exception) {
                Log.w(TAG, "No phone connected: ${e.message}")
                _state.value = _state.value.copy(phoneConnected = false)
            }
        }
    }

    private suspend fun triggerSyncToPhone() {
        try {
            val count = WearSyncHelper.syncWorkoutsToPhone(this@WearWorkoutService)
            if (count > 0) Log.d(TAG, "triggerSyncToPhone: synced $count workout(s)")
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed: ${e.message}")
        }
    }

    // ── Heart Rate Monitor ────────────────────────────────────────────────

    private fun registerHeartRateMonitor() {
        if (healthServicesRegistered) return
        try {
            val passiveClient = HealthServices.getClient(this).passiveMonitoringClient
            val config = PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .build()
            passiveClient.setPassiveListenerCallback(config, passiveListenerCallback)
            healthServicesRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register HR monitor", e)
        }
    }

    private fun unregisterHeartRateMonitor() {
        if (!healthServicesRegistered) return
        healthServicesRegistered = false
    }

    private fun sendHeartRateToPhone(hr: Int) {
        val nodeId = connectedNodeId ?: return
        lifecycleScope.launch {
            try {
                val bytes = ByteBuffer.allocate(4).putInt(hr).array()
                messageClient.sendMessage(nodeId, PATH_HEARTRATE, bytes).await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send HR: ${e.message}")
            }
        }
    }

    // ── Messages from Phone ───────────────────────────────────────────────

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_WORKOUT_START -> if (!_state.value.isRunning) startWorkout()
            PATH_WORKOUT_STOP -> stopWorkout()
        }
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Workout Tracking", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NSL Sport Watch")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    private fun updateNotification() {
        val s = _state.value
        val dist = if (s.distanceMeters >= 1000) String.format("%.2f km", s.distanceMeters / 1000f)
                   else "${s.distanceMeters.toInt()} m"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(dist))
    }

    // ── Vibrator ──────────────────────────────────────────────────────────

    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(pattern, -1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messageClient.removeListener(this)
        stopLocationUpdates()
        timerJob?.cancel()
        paceAlertJob?.cancel()
        unregisterHeartRateMonitor()
    }
}
