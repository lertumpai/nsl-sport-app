package com.nsl.sportapp.wear.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "wear_workouts")
data class WearWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationMillis: Long,
    val totalDistanceMeters: Float,
    val avgPaceSecsPerKm: Float,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val synced: Boolean = false  // true after successfully sent to phone
) {
    fun formattedDate(): String =
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(startTime))

    fun formattedDuration(): String {
        val secs = durationMillis / 1000
        val h = secs / 3600; val m = (secs % 3600) / 60; val s = secs % 60
        return if (h > 0) "$h:${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}"
        else "${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}"
    }

    fun formattedDistance(): String =
        if (totalDistanceMeters >= 1000) String.format("%.2f km", totalDistanceMeters / 1000f)
        else "${totalDistanceMeters.toInt()} m"
}
