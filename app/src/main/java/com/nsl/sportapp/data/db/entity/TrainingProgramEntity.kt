package com.nsl.sportapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "training_programs")
data class TrainingProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val intervalConfigJson: String,  // serialized IntervalConfig
    val createdAt: Long = System.currentTimeMillis()
)
