package com.nsl.sportapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nsl.sportapp.data.db.entity.WorkoutEntity
import com.nsl.sportapp.data.db.entity.WorkoutSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // ── Workout ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: Long): WorkoutEntity?

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun getTotalWorkoutCount(): Int

    @Query("SELECT SUM(totalDistanceMeters) FROM workouts")
    suspend fun getTotalDistance(): Float?

    @Query("SELECT SUM(durationMillis) FROM workouts")
    suspend fun getTotalDuration(): Long?

    @Query("SELECT AVG(avgHeartRate) FROM workouts WHERE avgHeartRate > 0")
    suspend fun getOverallAvgHeartRate(): Float?

    // ── Segments ─────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<WorkoutSegmentEntity>)

    @Query("SELECT * FROM workout_segments WHERE workoutId = :workoutId ORDER BY timestamp ASC")
    suspend fun getSegmentsForWorkout(workoutId: Long): List<WorkoutSegmentEntity>

    @Query("DELETE FROM workout_segments WHERE workoutId = :workoutId")
    suspend fun deleteSegmentsForWorkout(workoutId: Long)
}
