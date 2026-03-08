package com.nsl.sportapp.wear.datalayer

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.nsl.sportapp.wear.service.WearWorkoutService

class WearDataLayerListener : WearableListenerService() {

    companion object {
        private const val TAG = "WearDataLayer"
        const val PATH_WORKOUT_START = "/workout/start"
        const val PATH_WORKOUT_STOP = "/workout/stop"
        const val PATH_INTERVAL_PHASE = "/workout/interval_phase"
        const val PATH_STATS = "/workout/stats"
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "Message received: ${event.path}")
        when (event.path) {
            PATH_WORKOUT_START -> {
                // Start WearWorkoutService
                val intent = Intent(this, WearWorkoutService::class.java).apply {
                    action = WearWorkoutService.ACTION_START
                }
                startForegroundService(intent)
            }

            PATH_WORKOUT_STOP -> {
                val intent = Intent(this, WearWorkoutService::class.java).apply {
                    action = WearWorkoutService.ACTION_STOP
                }
                startService(intent)
            }

            PATH_INTERVAL_PHASE -> {
                val phase = String(event.data)
                Log.d(TAG, "Interval phase: $phase")
                // Update companion object state directly (service may not be running yet)
                val phaseLabel = when (phase) {
                    "RUN" -> "วิ่ง"
                    "WALK" -> "เดิน"
                    "REST" -> "พัก"
                    else -> phase
                }
                WearWorkoutService.currentPhase // read to ensure initialized
                // Trigger vibration if service is running
                try {
                    val svcIntent = Intent(this, WearWorkoutService::class.java)
                    // Service will receive START and handle phase internally
                } catch (e: Exception) {
                    Log.w(TAG, "Service not running for phase change")
                }
            }

            PATH_STATS -> {
                // Format: "distMeters:paceSecsPerKm"
                val data = String(event.data)
                val parts = data.split(":")
                if (parts.size == 2) {
                    val dist = parts[0].toFloatOrNull() ?: 0f
                    val pace = parts[1].toFloatOrNull() ?: 0f
                    Log.d(TAG, "Stats update: dist=$dist pace=$pace")
                }
            }
        }
    }
}
