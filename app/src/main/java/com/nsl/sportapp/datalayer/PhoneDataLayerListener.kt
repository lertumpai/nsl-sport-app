package com.nsl.sportapp.datalayer

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.nsl.sportapp.data.db.entity.WorkoutEntity
import com.nsl.sportapp.data.db.entity.WorkoutSegmentEntity
import com.nsl.sportapp.data.repository.TrainingProgramRepository
import com.nsl.sportapp.data.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Listens for data synced from the WearOS watch.
 * - /sync/workout : saves workout to phone DB
 * Program sync is one-way (phone → watch only).
 * When watch connects, phone pushes its programs to the watch.
 */
class PhoneDataLayerListener : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneDataLayer"
        const val PATH_SYNC_WORKOUT = "/sync/workout"
        const val PATH_SYNC_PROGRAMS_TO_WATCH = "/sync/programs"
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

    /** Called when a node (watch) connects — push phone programs to it. */
    override fun onChannelOpened(channel: com.google.android.gms.wearable.Channel) {
        // Not used
    }

    override fun onPeerConnected(peer: com.google.android.gms.wearable.Node) {
        Log.d(TAG, "Watch connected: ${peer.displayName}")
        scope.launch { sendProgramsToWatch(peer.id) }
    }

    private suspend fun sendProgramsToWatch(nodeId: String) {
        try {
            val repository = TrainingProgramRepository(this)
            val programs = repository.getAllProgramsOnce()
            if (programs.isEmpty()) return
            val items = programs.joinToString(",") { p ->
                """{"name":${JSONObject.quote(p.name)},"configJson":${JSONObject.quote(p.intervalConfigJson)},"createdAt":${p.createdAt}}"""
            }
            val json = "[$items]"
            Wearable.getMessageClient(this)
                .sendMessage(nodeId, PATH_SYNC_PROGRAMS_TO_WATCH, json.toByteArray()).await()
            Log.d(TAG, "Sent ${programs.size} programs to watch")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send programs to watch: ${e.message}")
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
