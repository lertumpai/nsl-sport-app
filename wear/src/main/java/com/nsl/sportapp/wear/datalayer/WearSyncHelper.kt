package com.nsl.sportapp.wear.datalayer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.nsl.sportapp.wear.data.db.entity.WearTrainingProgramEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutSegmentEntity
import com.nsl.sportapp.wear.data.repository.WearWorkoutRepository
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

/**
 * Handles sync between the watch and the phone.
 *
 * Workouts (Watch → Phone): MessageClient, with GPS segment downsampling to stay
 * under the 100 KB per-message limit.
 *
 * Programs (Phone → Watch): DataClient. The phone writes programs as a DataItem that
 * persists and is automatically delivered when devices reconnect — no manual request needed.
 */
object WearSyncHelper {

    private const val TAG = "WearSyncHelper"
    const val PATH_SYNC_WORKOUT = "/sync/workout"

    /** DataClient path written by the phone. Watch reads it here or via onDataChanged(). */
    const val DATA_PATH_PROGRAMS = "/data/programs"

    /** Keep segments under MessageClient's 100 KB limit (~100 bytes/point). */
    private const val MAX_SEGMENTS = 400

    // ─── Programs (DataClient) ─────────────────────────────────────────────────

    /**
     * Reads programs from the phone's DataItem and saves them to the watch DB.
     * Returns true if programs were found and saved.
     */
    suspend fun pullProgramsFromDataLayer(context: Context): Boolean {
        return try {
            val uri = Uri.parse("wear://*$DATA_PATH_PROGRAMS")
            val dataItems = Wearable.getDataClient(context).getDataItems(uri).await()
            var saved = false
            for (item in dataItems) {
                val dataMap = DataMapItem.fromDataItem(item).dataMap
                val json = dataMap.getString("programsJson") ?: continue
                saveProgramsJson(context, json)
                saved = true
            }
            dataItems.release()
            if (!saved) Log.d(TAG, "pullProgramsFromDataLayer: no programs DataItem found yet")
            saved
        } catch (e: Exception) {
            Log.w(TAG, "pullProgramsFromDataLayer failed: ${e.message}")
            false
        }
    }

    /** Parses a programs JSON array and replaces the watch-side program list. */
    suspend fun saveProgramsJson(context: Context, json: String) {
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
            WearWorkoutRepository(context).replaceAllPrograms(programs)
            Log.d(TAG, "Saved ${programs.size} programs from phone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save programs", e)
        }
    }

    // ─── Workouts (MessageClient) ──────────────────────────────────────────────

    /**
     * Sends each unsynced workout to the phone via MessageClient.
     * Skips (and retries next time) any workout whose payload send fails.
     * Returns the count of workouts successfully synced.
     */
    suspend fun syncWorkoutsToPhone(context: Context): Int {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val nodeId = nodes.firstOrNull()?.id ?: return 0

            val repository = WearWorkoutRepository(context)
            val unsynced = repository.getUnsyncedWorkouts()
            if (unsynced.isEmpty()) return 0

            val messageClient = Wearable.getMessageClient(context)
            var count = 0
            for (workout in unsynced) {
                try {
                    val segments = repository.getSegments(workout.id)
                    val payload = buildSyncPayload(workout, segments)
                    val bytes = payload.toByteArray()
                    Log.d(TAG, "Sending workout ${workout.id}: ${bytes.size} bytes (${segments.size} → ${minOf(segments.size, MAX_SEGMENTS)} segments)")
                    messageClient.sendMessage(nodeId, PATH_SYNC_WORKOUT, bytes).await()
                    repository.markSynced(workout.id)
                    count++
                    Log.d(TAG, "Synced workout ${workout.id} to phone")
                } catch (e: Exception) {
                    // Don't mark as synced — will retry on next sync
                    Log.w(TAG, "Failed to sync workout ${workout.id}: ${e.message}")
                }
            }
            count
        } catch (e: Exception) {
            Log.w(TAG, "syncWorkoutsToPhone failed: ${e.message}")
            0
        }
    }

    fun buildSyncPayload(
        workout: WearWorkoutEntity,
        segments: List<WearWorkoutSegmentEntity>
    ): String {
        // Downsample to MAX_SEGMENTS so payload stays under MessageClient's 100 KB limit
        val sampled = if (segments.size <= MAX_SEGMENTS) {
            segments
        } else {
            val step = segments.size.toFloat() / MAX_SEGMENTS
            (0 until MAX_SEGMENTS).map { i -> segments[(i * step).toInt()] }
        }
        val segJson = sampled.joinToString(",") {
            """{"ts":${it.timestamp},"lat":${it.latitude},"lng":${it.longitude},"dist":${it.distanceFromStartMeters},"pace":${it.paceSecsPerKm},"hr":${it.heartRate}}"""
        }
        return """{"id":${workout.id},"startTime":${workout.startTime},"endTime":${workout.endTime},"durationMillis":${workout.durationMillis},"distanceMeters":${workout.totalDistanceMeters},"avgPace":${workout.avgPaceSecsPerKm},"avgHr":${workout.avgHeartRate},"maxHr":${workout.maxHeartRate},"segments":[${segJson}]}"""
    }
}
