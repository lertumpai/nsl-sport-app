package com.nsl.sportapp.wear.data.repository

import android.content.Context
import com.nsl.sportapp.wear.data.db.WearWorkoutDatabase
import com.nsl.sportapp.wear.data.db.entity.WearTrainingProgramEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutSegmentEntity
import kotlinx.coroutines.flow.Flow

class WearWorkoutRepository(context: Context) {

    private val db = WearWorkoutDatabase.getInstance(context)
    private val dao = db.workoutDao()
    private val programDao = db.trainingProgramDao()

    val allWorkouts: Flow<List<WearWorkoutEntity>> = dao.getAllWorkouts()
    val allPrograms: Flow<List<WearTrainingProgramEntity>> = programDao.getAllPrograms()

    suspend fun saveWorkout(workout: WearWorkoutEntity, segments: List<WearWorkoutSegmentEntity>): Long =
        dao.saveWorkout(workout, segments)

    suspend fun getUnsyncedWorkouts(): List<WearWorkoutEntity> = dao.getUnsyncedWorkouts()

    suspend fun getSegments(workoutId: Long): List<WearWorkoutSegmentEntity> = dao.getSegments(workoutId)

    suspend fun markSynced(workoutId: Long) = dao.markSynced(workoutId)

    // Training programs
    suspend fun saveProgram(name: String, configJson: String): Long =
        programDao.insertProgram(WearTrainingProgramEntity(name = name, intervalConfigJson = configJson))

    suspend fun deleteProgram(id: Long) = programDao.deleteProgram(id)

    suspend fun getAllProgramsOnce(): List<WearTrainingProgramEntity> = programDao.getAllProgramsOnce()

    suspend fun replaceAllPrograms(programs: List<WearTrainingProgramEntity>) {
        programDao.deleteAll()
        programs.forEach { programDao.insertProgram(it.copy(id = 0)) }
    }
}
