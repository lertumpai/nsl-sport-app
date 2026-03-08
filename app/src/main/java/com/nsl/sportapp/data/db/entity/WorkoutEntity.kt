package com.nsl.sportapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationMillis: Long,
    val totalDistanceMeters: Float,
    val avgPaceSecsPerKm: Float,
    val maxPaceSecsPerKm: Float,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val intervalConfigJson: String? = null   // JSON ของ IntervalConfig (null = ไม่ใช้ interval)
) {
    fun formattedDate(): String {
        val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("th", "TH"))
        return sdf.format(Date(startTime))
    }

    fun formattedDuration(): String {
        val totalSecs = durationMillis / 1000
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hrs > 0)
            "${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
        else
            "${mins}:${secs.toString().padStart(2, '0')}"
    }

    fun formattedDistance(): String {
        return if (totalDistanceMeters >= 1000)
            String.format("%.2f km", totalDistanceMeters / 1000f)
        else
            "${totalDistanceMeters.toInt()} m"
    }

    fun formattedAvgPace(): String {
        if (avgPaceSecsPerKm <= 0) return "--:--"
        val mins = avgPaceSecsPerKm.toInt() / 60
        val secs = avgPaceSecsPerKm.toInt() % 60
        return "$mins:${secs.toString().padStart(2, '0')} /km"
    }
}
