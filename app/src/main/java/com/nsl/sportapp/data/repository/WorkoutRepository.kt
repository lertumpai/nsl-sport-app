package com.nsl.sportapp.data.repository

import android.content.Context
import com.nsl.sportapp.data.db.AppDatabase
import com.nsl.sportapp.data.db.entity.WorkoutEntity
import com.nsl.sportapp.data.db.entity.WorkoutSegmentEntity
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).workoutDao()

    val allWorkouts: Flow<List<WorkoutEntity>> = dao.getAllWorkouts()

    suspend fun saveWorkout(
        workout: WorkoutEntity,
        segments: List<WorkoutSegmentEntity>
    ): Long {
        val workoutId = dao.insertWorkout(workout)
        val segsWithId = segments.map { it.copy(workoutId = workoutId) }
        dao.insertSegments(segsWithId)
        return workoutId
    }

    suspend fun getWorkoutById(id: Long): WorkoutEntity? = dao.getWorkoutById(id)

    suspend fun getSegmentsForWorkout(workoutId: Long): List<WorkoutSegmentEntity> =
        dao.getSegmentsForWorkout(workoutId)

    suspend fun deleteWorkout(id: Long) {
        dao.deleteSegmentsForWorkout(id)
        dao.deleteWorkout(id)
    }

    suspend fun getOverallStats(): OverallStats {
        return OverallStats(
            totalWorkouts = dao.getTotalWorkoutCount(),
            totalDistanceMeters = dao.getTotalDistance() ?: 0f,
            totalDurationMillis = dao.getTotalDuration() ?: 0L,
            avgHeartRate = dao.getOverallAvgHeartRate()?.toInt() ?: 0
        )
    }

    data class OverallStats(
        val totalWorkouts: Int,
        val totalDistanceMeters: Float,
        val totalDurationMillis: Long,
        val avgHeartRate: Int
    )
}
