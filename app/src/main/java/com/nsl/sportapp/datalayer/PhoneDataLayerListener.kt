package com.nsl.sportapp.datalayer

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.nsl.sportapp.data.db.AppDatabase
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
 * Workouts (Watch → Phone): Received via DataClient at /sync/workout/{id}.
 * The watch writes a persistent DataItem that GMS delivers even after reconnect.
 * After saving, this listener deletes the DataItem to acknowledge receipt and
 * prevent re-delivery.
 *
 * Programs (Phone → Watch): Pushed via DataClient so they persist and auto-sync on reconnect.
 *   - pushProgramsToDataLayer() writes a DataItem the watch reads via onDataChanged().
 */
class PhoneDataLayerListener : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneDataLayer"
        /** Must match WearSyncHelper.PATH_SYNC_WORKOUT_PREFIX on the wear side. */
        private const val PATH_SYNC_WORKOUT_PREFIX = "/sync/workout/"
        /** Must match WearSyncHelper.DATA_PATH_PROGRAMS on the wear side. */
        private const val DATA_PATH_PROGRAMS = "/data/programs"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val programRepository by lazy { TrainingProgramRepository(this) }

    // ─── DataClient (workouts from watch + programs to watch) ─────────────────

    /**
     * Called when the watch writes a workout DataItem or any other watched path changes.
     * Workouts arrive at /sync/workout/{id} — we save them and then delete the DataItem
     * to acknowledge receipt.
     */
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            val path = event.dataItem.uri.path ?: continue
            if (event.type == DataEvent.TYPE_CHANGED && path.startsWith(PATH_SYNC_WORKOUT_PREFIX)) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val json = dataMap.getString("workoutJson") ?: continue
                val itemUri = event.dataItem.uri
                Log.d(TAG, "Received workout DataItem from watch: $path (${json.length} bytes)")
                scope.launch {
                    saveWatchWorkout(json)
                    // Delete the DataItem so GMS doesn't re-deliver it on future reconnects
                    try {
                        Wearable.getDataClient(this@PhoneDataLayerListener)
                            .deleteDataItems(itemUri).await()
                        Log.d(TAG, "Deleted workout DataItem: $path")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete workout DataItem: ${e.message}")
                    }
                }
            }
        }
        dataEvents.release()
    }

    // ─── Messages from watch (legacy / real-time path) ────────────────────────

    override fun onMessageReceived(event: MessageEvent) {
        // No message-based workout sync any more; keeping for future use
        Log.d(TAG, "Message received: ${event.path}")
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
            val startTime = obj.getLong("startTime")
            val dao = AppDatabase.getInstance(this).workoutDao()

            // Deduplicate: skip if a workout with the same start time already exists
            if (dao.countByStartTime(startTime) > 0) {
                Log.d(TAG, "Skipping duplicate watch workout (startTime=$startTime)")
                return
            }

            val repository = WorkoutRepository(this)

            val workout = WorkoutEntity(
                startTime = startTime,
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
