package com.nsl.sportapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.nsl.sportapp.MainActivity
import com.nsl.sportapp.data.db.entity.WorkoutEntity
import com.nsl.sportapp.data.db.entity.WorkoutSegmentEntity
import com.nsl.sportapp.data.model.IntervalConfig
import com.nsl.sportapp.data.model.IntervalMode
import com.nsl.sportapp.data.repository.WorkoutRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer

class WorkoutTrackingService : LifecycleService(), MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "WorkoutService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "workout_channel"
        private const val LOCATION_INTERVAL_MS = 2000L
        private const val PACE_ALERT_INTERVAL_MS = 5000L

        // Wearable message paths
        const val PATH_WORKOUT_START = "/workout/start"
        const val PATH_WORKOUT_STOP = "/workout/stop"
        const val PATH_HEARTRATE = "/workout/heartrate"
        const val PATH_INTERVAL_PHASE = "/workout/interval_phase"
        const val PATH_STATS = "/workout/stats"

        // Intent actions
        const val ACTION_START = "com.nsl.sportapp.ACTION_START"
        const val ACTION_STOP = "com.nsl.sportapp.ACTION_STOP"
        const val ACTION_PAUSE = "com.nsl.sportapp.ACTION_PAUSE"
        const val ACTION_RESUME = "com.nsl.sportapp.ACTION_RESUME"
        const val EXTRA_INTERVAL_CONFIG = "extra_interval_config"

        private val _state = MutableStateFlow(WorkoutState())
        val state: StateFlow<WorkoutState> = _state.asStateFlow()

        fun currentState() = _state.value
    }

    data class WorkoutState(
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val elapsedMillis: Long = 0L,
        val distanceMeters: Float = 0f,
        val currentPaceSecsPerKm: Float = 0f,
        val avgPaceSecsPerKm: Float = 0f,
        val heartRate: Int = 0,
        val currentActivityIndex: Int = 0,     // index ใน IntervalConfig.activities
        val currentActivityDistanceMeters: Float = 0f,  // ระยะทางใน activity นี้
        val currentRepetition: Int = 1,
        val totalRepetitions: Int = 0,
        val intervalConfig: IntervalConfig? = null,
        val isIntervalMode: Boolean = false
    ) {
        fun currentActivityLabel(): String =
            intervalConfig?.activityLabel(currentActivityIndex) ?: ""

        fun currentActivityTargetDistance(): Int =
            intervalConfig?.activityDistance(currentActivityIndex) ?: 0
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: WorkoutRepository
    private lateinit var messageClient: MessageClient

    private var lastLocation: Location? = null
    private var startTime: Long = 0L
    private var timerJob: Job? = null
    private var paceAlertJob: Job? = null
    private var connectedNodeId: String? = null

    // Pace rolling window (30 seconds)
    private val locationHistory = ArrayDeque<Pair<Long, Float>>(30) // (timestamp, distanceMeters)
    private val segmentBuffer = mutableListOf<WorkoutSegmentEntity>()

    // Interval tracking
    private var intervalConfig: IntervalConfig? = null
    private var currentActivityIndex = 0
    private var currentActivityDistance = 0f
    private var currentRepetition = 1
    private var totalActivitiesCompleted = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleNewLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = WorkoutRepository(this)
        messageClient = Wearable.getMessageClient(this)
        messageClient.addListener(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_INTERVAL_CONFIG)
                val config = configJson?.let {
                    try { Json.decodeFromString<IntervalConfig>(it) } catch (e: Exception) { null }
                }
                startWorkout(config)
            }
            ACTION_STOP -> stopWorkout()
            ACTION_PAUSE -> pauseWorkout()
            ACTION_RESUME -> resumeWorkout()
        }
        return START_STICKY
    }

    private fun startWorkout(config: IntervalConfig?) {
        intervalConfig = config
        currentActivityIndex = 0
        currentActivityDistance = 0f
        currentRepetition = 1
        totalActivitiesCompleted = 0
        startTime = System.currentTimeMillis()

        _state.value = WorkoutState(
            isRunning = true,
            isPaused = false,
            intervalConfig = config,
            isIntervalMode = config != null,
            totalRepetitions = config?.repetitions ?: 0,
            currentRepetition = 1
        )

        startForeground(NOTIFICATION_ID, buildNotification("กำลังวิ่ง..."))
        startLocationUpdates()
        startTimer()
        startPaceAlertMonitor()
        notifyWatchWorkoutStart()
    }

    private fun stopWorkout() {
        val state = _state.value
        if (!state.isRunning) return

        stopLocationUpdates()
        timerJob?.cancel()
        paceAlertJob?.cancel()

        lifecycleScope.launch {
            saveWorkoutToDb(state)
        }

        _state.value = WorkoutState(isRunning = false)
        notifyWatchWorkoutStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        startPaceAlertMonitor()
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

    private fun startPaceAlertMonitor() {
        val config = intervalConfig ?: return
        if (!config.paceAlertEnabled) return

        paceAlertJob = lifecycleScope.launch {
            while (true) {
                delay(PACE_ALERT_INTERVAL_MS)
                val currentPace = _state.value.currentPaceSecsPerKm
                if (currentPace > 0) {
                    val tooFast = currentPace < config.minPaceSecsPerKm
                    val tooSlow = currentPace > config.maxPaceSecsPerKm
                    if (tooFast || tooSlow) {
                        vibrate(longArrayOf(0, 150, 100, 150))
                    }
                }
            }
        }
    }

    private fun handleNewLocation(location: Location) {
        val prev = lastLocation
        lastLocation = location

        if (prev != null) {
            val delta = prev.distanceTo(location)
            if (delta < 0.5f) return  // filter noise

            val newTotal = _state.value.distanceMeters + delta
            currentActivityDistance += delta

            // Rolling pace (last 30 seconds)
            val now = System.currentTimeMillis()
            locationHistory.addLast(now to newTotal)
            while (locationHistory.size > 1 &&
                now - locationHistory.first().first > 30_000
            ) locationHistory.removeFirst()

            val pace = calculateRollingPace()
            val avgPace = calculateAvgPace(newTotal)

            // Check interval phase transition
            checkIntervalPhaseTransition(newTotal)

            _state.value = _state.value.copy(
                distanceMeters = newTotal,
                currentPaceSecsPerKm = pace,
                avgPaceSecsPerKm = avgPace,
                currentActivityDistanceMeters = currentActivityDistance,
                currentRepetition = currentRepetition
            )

            // Buffer GPS segment
            segmentBuffer.add(
                WorkoutSegmentEntity(
                    workoutId = 0,
                    timestamp = now,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    distanceFromStartMeters = newTotal,
                    paceSecsPerKm = pace,
                    heartRate = _state.value.heartRate
                )
            )

            // Send stats to watch every ~5 seconds
            if (segmentBuffer.size % 3 == 0) sendStatsToWatch(newTotal, pace)
        }
    }

    private fun calculateRollingPace(): Float {
        if (locationHistory.size < 2) return 0f
        val oldest = locationHistory.first()
        val newest = locationHistory.last()
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

    private fun checkIntervalPhaseTransition(totalDistance: Float) {
        val config = intervalConfig ?: return
        if (config.mode != IntervalMode.ACTIVITY_BASED) return
        if (config.activities.isEmpty()) return

        val actIndex = currentActivityIndex % config.activities.size
        val targetDist = config.activities[actIndex].distanceMeters

        if (currentActivityDistance >= targetDist) {
            currentActivityDistance -= targetDist
            currentActivityIndex++
            totalActivitiesCompleted++

            // Check if completed one full repetition cycle
            if (currentActivityIndex % config.activities.size == 0) {
                currentRepetition++
                if (currentRepetition > config.repetitions) {
                    // All reps done
                    vibrate(longArrayOf(0, 300, 200, 300, 200, 500))
                    stopWorkout()
                    return
                }
            }

            // Phase changed
            val nextIndex = currentActivityIndex % config.activities.size
            val nextPhase = config.activities[nextIndex].type.name
            vibrate(longArrayOf(0, 200, 100, 200))
            sendIntervalPhaseToWatch(nextPhase)
        }
    }

    // ── Watch Communication ──────────────────────────────────────────────

    private fun notifyWatchWorkoutStart() {
        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@WorkoutTrackingService).connectedNodes.await()
                nodes.firstOrNull()?.let { node ->
                    connectedNodeId = node.id
                    messageClient.sendMessage(node.id, PATH_WORKOUT_START, ByteArray(0)).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to notify watch: ${e.message}")
            }
        }
    }

    private fun notifyWatchWorkoutStop() {
        val nodeId = connectedNodeId ?: return
        lifecycleScope.launch {
            try {
                messageClient.sendMessage(nodeId, PATH_WORKOUT_STOP, ByteArray(0)).await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop watch: ${e.message}")
            }
        }
    }

    private fun sendIntervalPhaseToWatch(phase: String) {
        val nodeId = connectedNodeId ?: return
        lifecycleScope.launch {
            try {
                messageClient.sendMessage(nodeId, PATH_INTERVAL_PHASE, phase.toByteArray()).await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send phase: ${e.message}")
            }
        }
    }

    private fun sendStatsToWatch(distMeters: Float, paceSecsPerKm: Float) {
        val nodeId = connectedNodeId ?: return
        val data = "${distMeters.toInt()}:${paceSecsPerKm.toInt()}"
        lifecycleScope.launch {
            try {
                messageClient.sendMessage(nodeId, PATH_STATS, data.toByteArray()).await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send stats: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_HEARTRATE -> {
                val hr = ByteBuffer.wrap(event.data).int
                _state.value = _state.value.copy(heartRate = hr)
            }
        }
    }

    // ── Save to DB ───────────────────────────────────────────────────────

    private suspend fun saveWorkoutToDb(state: WorkoutState) {
        if (state.distanceMeters < 10f) return  // ignore very short workouts

        val config = intervalConfig
        val configJson = config?.let { Json.encodeToString(it) }

        val hrValues = segmentBuffer.map { it.heartRate }.filter { it > 0 }
        val avgHr = if (hrValues.isEmpty()) 0 else hrValues.average().toInt()
        val maxHr = hrValues.maxOrNull() ?: 0

        val paceValues = segmentBuffer.map { it.paceSecsPerKm }.filter { it > 0 }
        val maxPace = paceValues.maxOrNull() ?: 0f

        val endTime = System.currentTimeMillis()
        val workout = WorkoutEntity(
            startTime = startTime,
            endTime = endTime,
            durationMillis = state.elapsedMillis,
            totalDistanceMeters = state.distanceMeters,
            avgPaceSecsPerKm = state.avgPaceSecsPerKm,
            maxPaceSecsPerKm = maxPace,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            intervalConfigJson = configJson
        )

        try {
            repository.saveWorkout(workout, segmentBuffer.toList())
            Log.d(TAG, "Workout saved to DB")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save workout", e)
        }
    }

    // ── Notification ─────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "แสดงสถิติระหว่างวิ่ง"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NSL Sport กำลังทำงาน")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val state = _state.value
        val dist = if (state.distanceMeters >= 1000)
            String.format("%.2f km", state.distanceMeters / 1000f)
        else "${state.distanceMeters.toInt()} m"
        val pace = if (state.currentPaceSecsPerKm > 0) {
            val m = state.currentPaceSecsPerKm.toInt() / 60
            val s = state.currentPaceSecsPerKm.toInt() % 60
            "$m:${s.toString().padStart(2, '0')} /km"
        } else "--:--"
        val notif = buildNotification("$dist  |  Pace $pace")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notif)
    }

    // ── Vibrator ─────────────────────────────────────────────────────────

    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messageClient.removeListener(this)
        stopLocationUpdates()
        timerJob?.cancel()
        paceAlertJob?.cancel()
    }

}
