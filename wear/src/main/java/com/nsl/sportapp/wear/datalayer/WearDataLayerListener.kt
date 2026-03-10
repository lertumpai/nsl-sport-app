package com.nsl.sportapp.wear.datalayer

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import com.nsl.sportapp.wear.service.WearWorkoutService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WearDataLayerListener : WearableListenerService() {

    companion object {
        private const val TAG = "WearDataLayer"
        const val PATH_WORKOUT_START = "/workout/start"
        const val PATH_WORKOUT_STOP = "/workout/stop"
        const val PATH_INTERVAL_PHASE = "/workout/interval_phase"
        const val PATH_STATS = "/workout/stats"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── Message API ───────────────────────────────────────────────────────────

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

            PATH_INTERVAL_PHASE -> Log.d(TAG, "Interval phase from phone (watch tracks independently)")
            PATH_STATS -> Log.d(TAG, "Stats from phone (watch is independent)")
        }
    }

    // ─── Data Layer (programs) ─────────────────────────────────────────────────

    /**
     * Called automatically when the phone writes/updates programs via DataClient.
     * This fires even if the watch was offline — GMS delivers the item on reconnect.
     */
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearSyncHelper.DATA_PATH_PROGRAMS
            ) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val json = dataMap.getString("programsJson") ?: continue
                Log.d(TAG, "onDataChanged: programs received (${json.length} bytes)")
                scope.launch { WearSyncHelper.saveProgramsJson(this@WearDataLayerListener, json) }
            }
        }
        dataEvents.release()
    }

    // ─── Peer connection ───────────────────────────────────────────────────────

    /** Auto-sync when phone comes into Bluetooth range. */
    override fun onPeerConnected(peer: Node) {
        Log.d(TAG, "Phone connected: ${peer.displayName} — auto-syncing")
        scope.launch {
            WearSyncHelper.syncWorkoutsToPhone(this@WearDataLayerListener)
            // Programs come via onDataChanged() automatically — pull as well in case
            // the DataItem was written before this listener was active.
            WearSyncHelper.pullProgramsFromDataLayer(this@WearDataLayerListener)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
