package com.nsl.sportapp.datalayer

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.nsl.sportapp.data.repository.TrainingProgramRepository
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Pushes all training programs from the phone to every connected watch node.
 * Returns the number of nodes that received the data (0 if no watch is connected).
 */
object SyncHelper {

    private const val TAG = "SyncHelper"
    const val PATH_SYNC_PROGRAMS = "/sync/programs"

    suspend fun syncProgramsToWatch(context: Context): Int {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) return 0

            val programs = TrainingProgramRepository(context).getAllProgramsOnce()
            val items = programs.joinToString(",") { p ->
                """{"name":${JSONObject.quote(p.name)},"configJson":${JSONObject.quote(p.intervalConfigJson)},"createdAt":${p.createdAt}}"""
            }
            val json = "[$items]"
            val messageClient = Wearable.getMessageClient(context)

            nodes.forEach { node ->
                messageClient.sendMessage(node.id, PATH_SYNC_PROGRAMS, json.toByteArray()).await()
                Log.d(TAG, "Sent ${programs.size} programs to ${node.displayName}")
            }
            nodes.size
        } catch (e: Exception) {
            Log.w(TAG, "syncProgramsToWatch failed: ${e.message}")
            0
        }
    }
}
