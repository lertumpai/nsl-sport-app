package com.nsl.sportapp.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WearWorkoutDao {

    @Insert
    suspend fun insertWorkout(workout: WearWorkoutEntity): Long

    @Insert
    suspend fun insertSegments(segments: List<WearWorkoutSegmentEntity>)

    @Update
    suspend fun updateWorkout(workout: WearWorkoutEntity)

    @Query("SELECT * FROM wear_workouts ORDER BY startTime DESC")
    fun getAllWorkouts(): Flow<List<WearWorkoutEntity>>

    @Query("SELECT * FROM wear_workouts WHERE synced = 0 ORDER BY startTime ASC")
    suspend fun getUnsyncedWorkouts(): List<WearWorkoutEntity>

    @Query("SELECT * FROM wear_workout_segments WHERE workoutId = :workoutId ORDER BY timestamp ASC")
    suspend fun getSegments(workoutId: Long): List<WearWorkoutSegmentEntity>

    @Query("UPDATE wear_workouts SET synced = 1 WHERE id = :workoutId")
    suspend fun markSynced(workoutId: Long)

    @Transaction
    suspend fun saveWorkout(workout: WearWorkoutEntity, segments: List<WearWorkoutSegmentEntity>): Long {
        val id = insertWorkout(workout)
        if (segments.isNotEmpty()) {
            insertSegments(segments.map { it.copy(workoutId = id) })
        }
        return id
    }
}
