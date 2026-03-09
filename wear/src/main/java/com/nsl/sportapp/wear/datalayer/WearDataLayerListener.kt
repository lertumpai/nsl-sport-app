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
                Log.d(TAG, "Interval phase from phone: $phase")
                // Phase changes from phone are informational only; watch now tracks independently
            }

            PATH_STATS -> {
                Log.d(TAG, "Stats from phone (watch now independent)")
            }
        }
    }
}
