package com.nsl.sportapp.ui.workout

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nsl.sportapp.data.model.IntervalConfig
import com.nsl.sportapp.service.WorkoutTrackingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutViewModel : ViewModel() {

    val workoutState = WorkoutTrackingService.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkoutTrackingService.currentState())

    fun startWorkout(context: Context, intervalConfig: IntervalConfig? = null) {
        val intent = Intent(context, WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_START
            intervalConfig?.let {
                putExtra(
                    WorkoutTrackingService.EXTRA_INTERVAL_CONFIG,
                    Json.encodeToString(it)
                )
            }
        }
        context.startForegroundService(intent)
    }

    fun stopWorkout(context: Context) {
        context.startService(
            Intent(context, WorkoutTrackingService::class.java).apply {
                action = WorkoutTrackingService.ACTION_STOP
            }
        )
    }

    fun pauseWorkout(context: Context) {
        context.startService(
            Intent(context, WorkoutTrackingService::class.java).apply {
                action = WorkoutTrackingService.ACTION_PAUSE
            }
        )
    }

    fun resumeWorkout(context: Context) {
        context.startService(
            Intent(context, WorkoutTrackingService::class.java).apply {
                action = WorkoutTrackingService.ACTION_RESUME
            }
        )
    }
}
