package com.nsl.sportapp.datalayer

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
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
 *
 * Workouts (Watch → Phone): Received via MessageClient at /sync/workout.
 * Programs (Phone → Watch): Pushed via DataClient so they persist and auto-sync on reconnect.
 *   - pushProgramsToDataLayer() writes a DataItem the watch reads via onDataChanged().
 */
class PhoneDataLayerListener : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneDataLayer"
        const val PATH_SYNC_WORKOUT = "/sync/workout"
        /** Must match WearSyncHelper.DATA_PATH_PROGRAMS on the wear side. */
        private const val DATA_PATH_PROGRAMS = "/data/programs"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val programRepository by lazy { TrainingProgramRepository(this) }

    // ─── Messages from watch ───────────────────────────────────────────────────

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_SYNC_WORKOUT -> {
                val json = String(event.data)
                Log.d(TAG, "Received workout from watch (${event.data.size} bytes)")
                scope.launch { saveWatchWorkout(json) }
            }
        }
    }

    // ─── Peer connection ───────────────────────────────────────────────────────

    /** Called when the watch connects — push current programs to DataLayer. */
    override fun onPeerConnected(peer: Node) {
        Log.d(TAG, "Watch connected: ${peer.displayName} — pushing programs to DataLayer")
        pushProgramsToDataLayer()
    }

    // ─── Programs via DataClient ───────────────────────────────────────────────

    /**
     * Writes all training programs as a persistent DataItem at DATA_PATH_PROGRAMS.
     * The Wearable framework delivers it to the watch now if connected, or queues it
     * until the watch reconnects — no manual request from the watch needed.
     */
    private fun pushProgramsToDataLayer() {
        scope.launch {
            try {
                val programs = programRepository.getAllProgramsOnce()
                if (programs.isEmpty()) {
                    Log.d(TAG, "pushProgramsToDataLayer: no programs to push")
                    return@launch
                }
                val items = programs.joinToString(",") { p ->
                    """{"name":${JSONObject.quote(p.name)},"configJson":${JSONObject.quote(p.intervalConfigJson)},"createdAt":${p.createdAt}}"""
                }
                val json = "[$items]"
                val request = PutDataMapRequest.create(DATA_PATH_PROGRAMS).apply {
                    dataMap.putString("programsJson", json)
                    // Bump timestamp so GMS treats it as changed even if content is identical
                    dataMap.putLong("updatedAt", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(this@PhoneDataLayerListener).putDataItem(request).await()
                Log.d(TAG, "Pushed ${programs.size} programs to DataLayer (${json.length} bytes)")
            } catch (e: Exception) {
                Log.w(TAG, "pushProgramsToDataLayer failed: ${e.message}")
            }
        }
    }

    // ─── Save workout from watch ───────────────────────────────────────────────

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
            Log.d(TAG, "Watch workout saved to phone DB (dist=${workout.totalDistanceMeters}m, ${segments.size} segments)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse/save watch workout", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
