package com.nsl.sportapp.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nsl.sportapp.wear.data.db.entity.WearTrainingProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WearTrainingProgramDao {
    @Query("SELECT * FROM wear_training_programs ORDER BY createdAt DESC")
    fun getAllPrograms(): Flow<List<WearTrainingProgramEntity>>

    @Query("SELECT * FROM wear_training_programs ORDER BY createdAt DESC")
    suspend fun getAllProgramsOnce(): List<WearTrainingProgramEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: WearTrainingProgramEntity): Long

    @Query("DELETE FROM wear_training_programs WHERE id = :id")
    suspend fun deleteProgram(id: Long)

    @Query("DELETE FROM wear_training_programs")
    suspend fun deleteAll()
}
