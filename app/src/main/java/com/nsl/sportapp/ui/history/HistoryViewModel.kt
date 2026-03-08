package com.nsl.sportapp.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nsl.sportapp.data.db.entity.WorkoutEntity
import com.nsl.sportapp.data.db.entity.WorkoutSegmentEntity
import com.nsl.sportapp.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = WorkoutRepository(app)

    val workouts: StateFlow<List<WorkoutEntity>> = repository.allWorkouts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteWorkout(id: Long) {
        viewModelScope.launch {
            repository.deleteWorkout(id)
        }
    }

    suspend fun getWorkoutWithSegments(id: Long): Pair<WorkoutEntity?, List<WorkoutSegmentEntity>> {
        val workout = repository.getWorkoutById(id)
        val segments = if (workout != null) repository.getSegmentsForWorkout(id) else emptyList()
        return workout to segments
    }

    suspend fun getOverallStats() = repository.getOverallStats()
}
