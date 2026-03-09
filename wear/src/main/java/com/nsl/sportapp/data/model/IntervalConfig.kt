package com.nsl.sportapp.data.model

import kotlinx.serialization.Serializable

enum class IntervalMode {
    ACTIVITY_BASED,
    PACE_CONTROLLED
}

enum class ActivityType(val label: String) {
    RUN("วิ่ง"),
    WALK("เดิน"),
    REST("พัก")
}

@Serializable
data class IntervalActivity(
    val type: ActivityType,
    val distanceMeters: Int
)

@Serializable
data class IntervalConfig(
    val mode: IntervalMode = IntervalMode.ACTIVITY_BASED,
    val activities: List<IntervalActivity> = emptyList(),
    val repetitions: Int = 1,
    val minPaceSecsPerKm: Int = 300,
    val maxPaceSecsPerKm: Int = 600,
    val paceAlertEnabled: Boolean = true
) {
    fun totalCycleDistanceMeters(): Int = activities.sumOf { it.distanceMeters }

    fun activityLabel(index: Int): String {
        if (activities.isEmpty()) return ""
        return activities.getOrNull(index % activities.size)?.type?.label ?: ""
    }

    fun activityDistance(index: Int): Int {
        if (activities.isEmpty()) return 0
        return activities.getOrNull(index % activities.size)?.distanceMeters ?: 0
    }

    companion object {
        fun formatPace(secsPerKm: Int): String {
            val mins = secsPerKm / 60
            val secs = secsPerKm % 60
            return "$mins:${secs.toString().padStart(2, '0')}"
        }

        val PACE_PRESETS = listOf(
            Pair("Easy Run", 480 to 600),
            Pair("Tempo", 300 to 360),
            Pair("Zone 2", 420 to 480),
            Pair("Marathon", 360 to 420),
            Pair("5K Race", 240 to 300),
        )
    }
}
