package com.nsl.sportapp.wear.datalayer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
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
 * Workouts (Watch → Phone): DataClient, using persistent DataItems so workouts are delivered
 * even when the phone was out of Bluetooth range during the workout. GMS queues the DataItem
 * and delivers it automatically when the devices reconnect.
 *
 * Programs (Phone → Watch): DataClient. The phone writes programs as a DataItem that
 * persists and is automatically delivered when devices reconnect — no manual request needed.
 */
object WearSyncHelper {

    private const val TAG = "WearSyncHelper"

    /** DataClient path for watch→phone workout sync. Each workout gets its own path. */
    const val PATH_SYNC_WORKOUT_PREFIX = "/sync/workout/"

    /** DataClient path written by the phone. Watch reads it here or via onDataChanged(). */
    const val DATA_PATH_PROGRAMS = "/data/programs"

    /** Keep segments under DataClient's ~100 KB DataItem limit (~100 bytes/point). */
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

    // ─── Workouts (DataClient) ─────────────────────────────────────────────────

    /**
     * Sends each unsynced workout to the phone via DataClient DataItems.
     *
     * Unlike MessageClient, DataClient persists the data and guarantees delivery
     * even if the phone is currently out of Bluetooth range — GMS will deliver
     * the DataItem automatically when the devices reconnect.
     *
     * Each workout is written to its own path "/sync/workout/{workoutId}" so
     * multiple unsynced workouts can coexist without overwriting each other.
     * The phone deletes each DataItem after saving it, which also acts as an
     * implicit acknowledgment.
     *
     * Returns the count of workouts successfully queued for sync.
     */
    suspend fun syncWorkoutsToPhone(context: Context): Int {
        return try {
            val repository = WearWorkoutRepository(context)
            val unsynced = repository.getUnsyncedWorkouts()
            if (unsynced.isEmpty()) return 0

            val dataClient = Wearable.getDataClient(context)
            var count = 0
            for (workout in unsynced) {
                try {
                    val segments = repository.getSegments(workout.id)
                    val payload = buildSyncPayload(workout, segments)
                    val path = "$PATH_SYNC_WORKOUT_PREFIX${workout.id}"
                    val request = PutDataMapRequest.create(path).apply {
                        dataMap.putString("workoutJson", payload)
                        // Bump timestamp so GMS treats it as changed on each retry attempt
                        dataMap.putLong("ts", System.currentTimeMillis())
                    }.asPutDataRequest().setUrgent()

                    dataClient.putDataItem(request).await()
                    // Mark synced after successful DataItem write — GMS guarantees delivery
                    repository.markSynced(workout.id)
                    count++
                    Log.d(TAG, "DataClient queued workout ${workout.id} (${payload.length} bytes, path=$path)")
                } catch (e: Exception) {
                    // Don't mark as synced — will retry on next sync attempt
                    Log.w(TAG, "Failed to queue workout ${workout.id}: ${e.message}")
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
        // Downsample to MAX_SEGMENTS so payload stays under DataClient's ~100 KB DataItem limit
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
