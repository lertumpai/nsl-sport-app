package com.nsl.sportapp.wear.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nsl.sportapp.wear.data.db.entity.WearTrainingProgramEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutSegmentEntity

@Database(
    entities = [WearWorkoutEntity::class, WearWorkoutSegmentEntity::class, WearTrainingProgramEntity::class],
    version = 2,
    exportSchema = false
)
abstract class WearWorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WearWorkoutDao
    abstract fun trainingProgramDao(): WearTrainingProgramDao

    companion object {
        @Volatile private var INSTANCE: WearWorkoutDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS wear_training_programs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        intervalConfigJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        syncedFromPhone INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }

        fun getInstance(context: Context): WearWorkoutDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WearWorkoutDatabase::class.java,
                    "wear_workout_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
