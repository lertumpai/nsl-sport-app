package com.nsl.sportapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nsl.sportapp.data.db.entity.TrainingProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingProgramDao {
    @Query("SELECT * FROM training_programs ORDER BY createdAt DESC")
    fun getAllPrograms(): Flow<List<TrainingProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: TrainingProgramEntity): Long

    @Query("DELETE FROM training_programs WHERE id = :id")
    suspend fun deleteProgram(id: Long)

    @Query("SELECT * FROM training_programs")
    suspend fun getAllProgramsOnce(): List<TrainingProgramEntity>

    @Query("DELETE FROM training_programs")
    suspend fun deleteAll()
}
