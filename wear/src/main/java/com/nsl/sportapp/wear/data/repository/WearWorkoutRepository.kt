package com.nsl.sportapp.wear.data.repository

import android.content.Context
import com.nsl.sportapp.wear.data.db.WearWorkoutDatabase
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutSegmentEntity
import kotlinx.coroutines.flow.Flow

class WearWorkoutRepository(context: Context) {

    private val dao = WearWorkoutDatabase.getInstance(context).workoutDao()

    val allWorkouts: Flow<List<WearWorkoutEntity>> = dao.getAllWorkouts()

    suspend fun saveWorkout(workout: WearWorkoutEntity, segments: List<WearWorkoutSegmentEntity>): Long =
        dao.saveWorkout(workout, segments)

    suspend fun getUnsyncedWorkouts(): List<WearWorkoutEntity> = dao.getUnsyncedWorkouts()

    suspend fun getSegments(workoutId: Long): List<WearWorkoutSegmentEntity> = dao.getSegments(workoutId)

    suspend fun markSynced(workoutId: Long) = dao.markSynced(workoutId)
}
