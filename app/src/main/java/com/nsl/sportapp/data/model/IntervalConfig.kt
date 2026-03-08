package com.nsl.sportapp.data.model

import kotlinx.serialization.Serializable

enum class IntervalMode {
    ACTIVITY_BASED,   // วิ่ง 500m เดิน 100m วนซ้ำ
    PACE_CONTROLLED   // ควบคุม pace ให้อยู่ในช่วงที่กำหนด
}

enum class ActivityType(val label: String) {
    RUN("วิ่ง"),
    WALK("เดิน"),
    REST("พัก")
}

@Serializable
data class IntervalActivity(
    val type: ActivityType,
    val distanceMeters: Int  // ระยะทางของ activity นี้ (เมตร)
)

@Serializable
data class IntervalConfig(
    val mode: IntervalMode = IntervalMode.ACTIVITY_BASED,

    // สำหรับ ACTIVITY_BASED: รายการ activities ที่จะวนซ้ำ
    val activities: List<IntervalActivity> = emptyList(),
    val repetitions: Int = 1,          // จำนวนรอบที่วน

    // สำหรับทุก mode: ช่วง pace ที่ยอมรับได้ (วินาที/กม.)
    // ถ้าอยู่นอกช่วงนี้จะสั่นเตือน
    val minPaceSecsPerKm: Int = 300,   // 5:00 min/km
    val maxPaceSecsPerKm: Int = 600,   // 10:00 min/km
    val paceAlertEnabled: Boolean = true
) {
    /** ระยะทางรวมของ 1 รอบ (เมตร) */
    fun totalCycleDistanceMeters(): Int = activities.sumOf { it.distanceMeters }

    /** ชื่อ activity ณ index ที่กำหนด */
    fun activityLabel(index: Int): String =
        activities.getOrNull(index % activities.size)?.type?.label ?: ""

    /** ระยะทางของ activity ณ index ที่กำหนด */
    fun activityDistance(index: Int): Int =
        activities.getOrNull(index % activities.size)?.distanceMeters ?: 0

    companion object {
        /** แปลง seconds/km เป็น string "m:ss" */
        fun formatPace(secsPerKm: Int): String {
            val mins = secsPerKm / 60
            val secs = secsPerKm % 60
            return "$mins:${secs.toString().padStart(2, '0')}"
        }

        /** Pace presets สำหรับ PACE_CONTROLLED mode */
        val PACE_PRESETS = listOf(
            Pair("Easy Run", 480 to 600),       // 8:00 - 10:00
            Pair("Tempo", 300 to 360),          // 5:00 - 6:00
            Pair("Zone 2", 420 to 480),         // 7:00 - 8:00
            Pair("Marathon", 360 to 420),       // 6:00 - 7:00
            Pair("5K Race", 240 to 300),        // 4:00 - 5:00
        )
    }
}
