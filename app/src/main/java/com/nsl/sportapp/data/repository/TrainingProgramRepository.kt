package com.nsl.sportapp.data.repository

import android.content.Context
import com.nsl.sportapp.data.db.AppDatabase
import com.nsl.sportapp.data.db.entity.TrainingProgramEntity
import com.nsl.sportapp.data.model.IntervalConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TrainingProgramRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).trainingProgramDao()
    private val json = Json { ignoreUnknownKeys = true }

    val allPrograms: Flow<List<TrainingProgramEntity>> = dao.getAllPrograms()

    suspend fun saveProgram(name: String, config: IntervalConfig): Long {
        val entity = TrainingProgramEntity(
            name = name,
            intervalConfigJson = json.encodeToString(config)
        )
        return dao.insertProgram(entity)
    }

    suspend fun deleteProgram(id: Long) = dao.deleteProgram(id)

    suspend fun getAllProgramsOnce(): List<TrainingProgramEntity> = dao.getAllProgramsOnce()

    suspend fun replaceAllPrograms(programs: List<TrainingProgramEntity>) {
        dao.deleteAll()
        programs.forEach { dao.insertProgram(it.copy(id = 0)) }
    }

    fun parseConfig(entity: TrainingProgramEntity): IntervalConfig? = runCatching {
        json.decodeFromString<IntervalConfig>(entity.intervalConfigJson)
    }.getOrNull()

    fun parseConfigJson(configJson: String): IntervalConfig? = runCatching {
        json.decodeFromString<IntervalConfig>(configJson)
    }.getOrNull()
}
