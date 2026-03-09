package com.nsl.sportapp.wear.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wear_training_programs")
data class WearTrainingProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val intervalConfigJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedFromPhone: Boolean = false
)
