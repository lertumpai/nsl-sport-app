package com.nsl.sportapp.datalayer

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.nsl.sportapp.data.db.entity.WorkoutEntity
import com.nsl.sportapp.data.db.entity.WorkoutSegmentEntity
import com.nsl.sportapp.data.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Listens for workout data synced from the WearOS watch.
 * When a workout is received via /sync/workout, it is saved to the phone's Room DB.
 */
class PhoneDataLayerListener : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneDataLayer"
        const val PATH_SYNC_WORKOUT = "/sync/workout"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_SYNC_WORKOUT -> {
                val json = String(event.data)
                Log.d(TAG, "Received workout sync from watch")
                scope.launch { saveWatchWorkout(json) }
            }
        }
    }

    private suspend fun saveWatchWorkout(json: String) {
        try {
            val obj = JSONObject(json)
            val repository = WorkoutRepository(this)

            val workout = WorkoutEntity(
                startTime = obj.getLong("startTime"),
                endTime = obj.getLong("endTime"),
                durationMillis = obj.getLong("durationMillis"),
                totalDistanceMeters = obj.getDouble("distanceMeters").toFloat(),
                avgPaceSecsPerKm = obj.getDouble("avgPace").toFloat(),
                maxPaceSecsPerKm = 0f,
                avgHeartRate = obj.optInt("avgHr", 0),
                maxHeartRate = obj.optInt("maxHr", 0),
                intervalConfigJson = null
            )

            val segmentsArray = obj.optJSONArray("segments")
            val segments = mutableListOf<WorkoutSegmentEntity>()
            if (segmentsArray != null) {
                for (i in 0 until segmentsArray.length()) {
                    val seg = segmentsArray.getJSONObject(i)
                    segments.add(
                        WorkoutSegmentEntity(
                            workoutId = 0,
                            timestamp = seg.getLong("ts"),
                            latitude = seg.getDouble("lat"),
                            longitude = seg.getDouble("lng"),
                            distanceFromStartMeters = seg.getDouble("dist").toFloat(),
                            paceSecsPerKm = seg.getDouble("pace").toFloat(),
                            heartRate = seg.optInt("hr", 0)
                        )
                    )
                }
            }

            repository.saveWorkout(workout, segments)
            Log.d(TAG, "Watch workout saved to phone DB (dist=${workout.totalDistanceMeters}m)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse/save watch workout", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
