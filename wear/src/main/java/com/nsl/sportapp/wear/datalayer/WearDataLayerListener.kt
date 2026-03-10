package com.nsl.sportapp.wear.datalayer

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.nsl.sportapp.wear.data.db.entity.WearTrainingProgramEntity
import com.nsl.sportapp.wear.data.repository.WearWorkoutRepository
import com.nsl.sportapp.wear.service.WearWorkoutService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class WearDataLayerListener : WearableListenerService() {

    companion object {
        private const val TAG = "WearDataLayer"
        const val PATH_WORKOUT_START = "/workout/start"
        const val PATH_WORKOUT_STOP = "/workout/stop"
        const val PATH_INTERVAL_PHASE = "/workout/interval_phase"
        const val PATH_STATS = "/workout/stats"
        const val PATH_SYNC_PROGRAMS = "/sync/programs"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository by lazy { WearWorkoutRepository(this) }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "Message received: ${event.path}")
        when (event.path) {
            PATH_WORKOUT_START -> {
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
                Log.d(TAG, "Interval phase from phone (watch tracks independently)")
            }

            PATH_STATS -> {
                Log.d(TAG, "Stats from phone (watch is independent)")
            }

            PATH_SYNC_PROGRAMS -> {
                val json = String(event.data)
                Log.d(TAG, "Received programs from phone")
                scope.launch { saveProgramsFromPhone(json) }
            }
        }
    }

    private suspend fun saveProgramsFromPhone(json: String) {
        try {
            val array = JSONArray(json)
            val programs = mutableListOf<WearTrainingProgramEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                programs.add(
                    WearTrainingProgramEntity(
                        name = obj.getString("name"),
                        intervalConfigJson = obj.getString("configJson"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        syncedFromPhone = true
                    )
                )
            }
            repository.replaceAllPrograms(programs)
            Log.d(TAG, "Saved ${programs.size} programs from phone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save programs from phone", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
