package com.nsl.sportapp.wear.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wear_workout_segments",
    foreignKeys = [ForeignKey(
        entity = WearWorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutId")]
)
data class WearWorkoutSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val distanceFromStartMeters: Float,
    val paceSecsPerKm: Float,
    val heartRate: Int
)
