package com.nsl.sportapp.wear.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutSegmentEntity

@Database(
    entities = [WearWorkoutEntity::class, WearWorkoutSegmentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WearWorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WearWorkoutDao

    companion object {
        @Volatile private var INSTANCE: WearWorkoutDatabase? = null

        fun getInstance(context: Context): WearWorkoutDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WearWorkoutDatabase::class.java,
                    "wear_workout_db"
                ).build().also { INSTANCE = it }
            }
    }
}
