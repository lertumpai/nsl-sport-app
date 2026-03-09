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

        private const val PACE_ALERT_INTERVAL_MS = 5000L

        // Paths for messages from phone
        const val PATH_WORKOUT_START = "/workout/start"
        const val PATH_WORKOUT_STOP = "/workout/stop"
        const val PATH_HEARTRATE = "/workout/heartrate"

        private val _state = MutableStateFlow(WorkoutState())
        val state: StateFlow<WorkoutState> = _state.asStateFlow()

        // Convenience accessors kept for backward compatibility
        val heartRate: StateFlow<Int> get() = MutableStateFlow(_state.value.heartRate).also { it }
        val isActive: StateFlow<Boolean> get() = MutableStateFlow(_state.value.isRunning).also { it }
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
        val paceInRange: Boolean = true
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val minPace = intent.getIntExtra(EXTRA_MIN_PACE, 0)
                val maxPace = intent.getIntExtra(EXTRA_MAX_PACE, 0)
                startWorkout(minPace, maxPace)
            }
            ACTION_STOP -> stopWorkout()
            ACTION_PAUSE -> pauseWorkout()
            ACTION_RESUME -> resumeWorkout()
        }
        return START_NOT_STICKY
    }

    private fun startWorkout(minPace: Int = 0, maxPace: Int = 0) {
        startTime = System.currentTimeMillis()
        locationHistory.clear()
        segmentBuffer.clear()
        lastLocation = null

        val paceAlertEnabled = minPace > 0 && maxPace > 0
        _state.value = WorkoutState(
            isRunning = true, isPaused = false,
            minPaceSecsPerKm = minPace, maxPaceSecsPerKm = maxPace,
            paceAlertEnabled = paceAlertEnabled
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
        _state.value = _state.value.copy(isPaused = true)
    }

    private fun resumeWorkout() {
        if (!_state.value.isRunning || !_state.value.isPaused) return
        _state.value = _state.value.copy(isPaused = false)
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
                val elapsed = baseElapsed + (System.currentTimeMillis() - startTs)
                _state.value = _state.value.copy(elapsedMillis = elapsed)
                updateNotification()
            }
        }
    }

    private fun handleNewLocation(location: Location) {
        val prev = lastLocation
        lastLocation = location

        if (prev != null) {
            val delta = prev.distanceTo(location)
            if (delta < 0.5f) return

            val newTotal = _state.value.distanceMeters + delta
            val now = System.currentTimeMillis()

            locationHistory.addLast(now to newTotal)
            while (locationHistory.size > 1 && now - locationHistory.first().first > 30_000)
                locationHistory.removeFirst()

            val pace = calculateRollingPace()
            val avgPace = calculateAvgPace(newTotal)

            _state.value = _state.value.copy(
                distanceMeters = newTotal,
                currentPaceSecsPerKm = pace,
                avgPaceSecsPerKm = avgPace,
                gpsActive = true
            )

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
        if (state.distanceMeters < 10f) return

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
            } catch (e: Exception) {
                Log.w(TAG, "No phone connected: ${e.message}")
            }
        }
    }

    private fun triggerSyncToPhone() {
        lifecycleScope.launch {
            val nodeId = connectedNodeId ?: return@launch
            try {
                val unsynced = repository.getUnsyncedWorkouts()
                for (workout in unsynced) {
                    val segments = repository.getSegments(workout.id)
                    val payload = buildSyncPayload(workout, segments)
                    messageClient.sendMessage(nodeId, "/sync/workout", payload.toByteArray()).await()
                    repository.markSynced(workout.id)
                    Log.d(TAG, "Synced workout ${workout.id} to phone")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed: ${e.message}")
            }
        }
    }

    private fun buildSyncPayload(
        workout: WearWorkoutEntity,
        segments: List<WearWorkoutSegmentEntity>
    ): String {
        val segJson = segments.joinToString(",") {
            """{"ts":${it.timestamp},"lat":${it.latitude},"lng":${it.longitude},"dist":${it.distanceFromStartMeters},"pace":${it.paceSecsPerKm},"hr":${it.heartRate}}"""
        }
        return """{"id":${workout.id},"startTime":${workout.startTime},"endTime":${workout.endTime},"durationMillis":${workout.durationMillis},"distanceMeters":${workout.totalDistanceMeters},"avgPace":${workout.avgPaceSecsPerKm},"avgHr":${workout.avgHeartRate},"maxHr":${workout.maxHeartRate},"segments":[${segJson}]}"""
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
