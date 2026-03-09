package com.nsl.sportapp.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
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
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer

class WearWorkoutService : LifecycleService() {

    companion object {
        private const val TAG = "WearWorkoutService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "wear_workout_channel"

        const val ACTION_START = "com.nsl.sportapp.wear.ACTION_START"
        const val ACTION_STOP = "com.nsl.sportapp.wear.ACTION_STOP"

        private val _heartRate = MutableStateFlow(0)
        val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

        private val _distanceMeters = MutableStateFlow(0f)
        val distanceMeters: StateFlow<Float> = _distanceMeters.asStateFlow()

        private val _paceSecsPerKm = MutableStateFlow(0f)
        val paceSecsPerKm: StateFlow<Float> = _paceSecsPerKm.asStateFlow()

        private val _currentPhase = MutableStateFlow("วิ่ง")
        val currentPhase: StateFlow<String> = _currentPhase.asStateFlow()

        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    }

    private lateinit var messageClient: MessageClient
    private var connectedNodeId: String? = null
    private var healthServicesRegistered = false

    private val passiveListenerCallback = object : PassiveListenerCallback {
        override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
            dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { point ->
                val hr = point.value.toInt()
                _heartRate.value = hr
                Log.d(TAG, "Heart rate: $hr bpm")
                sendHeartRateToPhone(hr)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        messageClient = Wearable.getMessageClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    fun startTracking() {
        _isActive.value = true
        startForeground(NOTIFICATION_ID, buildNotification("กำลังติดตามอัตราการเต้นหัวใจ..."))
        registerHeartRateMonitor()
        findConnectedNode()
    }

    fun stopTracking() {
        unregisterHeartRateMonitor()
        _isActive.value = false
        _heartRate.value = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerHeartRateMonitor() {
        if (healthServicesRegistered) return
        try {
            val healthClient = HealthServices.getClient(this@WearWorkoutService)
            val passiveClient = healthClient.passiveMonitoringClient
            val config = PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .build()
            passiveClient.setPassiveListenerCallback(config, passiveListenerCallback)
            healthServicesRegistered = true
            Log.d(TAG, "Heart rate monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register HR monitor", e)
        }
    }

    private fun unregisterHeartRateMonitor() {
        if (!healthServicesRegistered) return
        healthServicesRegistered = false
        Log.d(TAG, "Heart rate monitoring stopped")
    }

    private fun findConnectedNode() {
        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@WearWorkoutService).connectedNodes.await()
                connectedNodeId = nodes.firstOrNull()?.id
                Log.d(TAG, "Connected to phone node: $connectedNodeId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to find connected node", e)
            }
        }
    }

    private fun sendHeartRateToPhone(hr: Int) {
        val nodeId = connectedNodeId ?: return
        lifecycleScope.launch {
            try {
                val bytes = ByteBuffer.allocate(4).putInt(hr).array()
                messageClient.sendMessage(nodeId, "/workout/heartrate", bytes).await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send HR to phone: ${e.message}")
            }
        }
    }

    fun updateFromPhone(distMeters: Float, pace: Float, phase: String) {
        _distanceMeters.value = distMeters
        _paceSecsPerKm.value = pace
        _currentPhase.value = phase
    }

    fun vibratePhaseChange() {
        vibrate(longArrayOf(0, 200, 100, 200))
    }

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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout Monitoring",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NSL Sport Watch")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterHeartRateMonitor()
    }
}
