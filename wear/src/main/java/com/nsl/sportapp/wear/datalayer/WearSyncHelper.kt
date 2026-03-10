package com.nsl.sportapp.wear.datalayer

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutSegmentEntity
import com.nsl.sportapp.wear.data.repository.WearWorkoutRepository
import kotlinx.coroutines.tasks.await

/**
 * Syncs only the workouts that have NOT been synced yet (synced == false).
 * Already-synced workouts are skipped — no duplicates on the phone side.
 *
 * Returns the count of workouts successfully synced in this call.
 */
object WearSyncHelper {

    private const val TAG = "WearSyncHelper"
    const val PATH_SYNC_WORKOUT = "/sync/workout"
    const val PATH_REQUEST_PROGRAMS = "/sync/request_programs"

    /** Ask the phone to push its training programs to this watch. Returns true if request was sent. */
    suspend fun requestProgramsFromPhone(context: Context): Boolean {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val nodeId = nodes.firstOrNull()?.id ?: return false
            Wearable.getMessageClient(context)
                .sendMessage(nodeId, PATH_REQUEST_PROGRAMS, ByteArray(0)).await()
            Log.d(TAG, "Requested programs from phone")
            true
        } catch (e: Exception) {
            Log.w(TAG, "requestProgramsFromPhone failed: ${e.message}")
            false
        }
    }

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
                val segments = repository.getSegments(workout.id)
                val payload = buildSyncPayload(workout, segments)
                messageClient.sendMessage(nodeId, PATH_SYNC_WORKOUT, payload.toByteArray()).await()
                repository.markSynced(workout.id)
                count++
                Log.d(TAG, "Synced workout ${workout.id} to phone")
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
        val segJson = segments.joinToString(",") {
            """{"ts":${it.timestamp},"lat":${it.latitude},"lng":${it.longitude},"dist":${it.distanceFromStartMeters},"pace":${it.paceSecsPerKm},"hr":${it.heartRate}}"""
        }
        return """{"id":${workout.id},"startTime":${workout.startTime},"endTime":${workout.endTime},"durationMillis":${workout.durationMillis},"distanceMeters":${workout.totalDistanceMeters},"avgPace":${workout.avgPaceSecsPerKm},"avgHr":${workout.avgHeartRate},"maxHr":${workout.maxHeartRate},"segments":[${segJson}]}"""
    }
}
