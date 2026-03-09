package com.nsl.sportapp.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsl.sportapp.data.db.entity.WorkoutEntity
import com.nsl.sportapp.data.db.entity.WorkoutSegmentEntity
import com.nsl.sportapp.data.model.IntervalConfig
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    viewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    var workout by remember { mutableStateOf<WorkoutEntity?>(null) }
    var segments by remember { mutableStateOf<List<WorkoutSegmentEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(workoutId) {
        val (w, s) = viewModel.getWorkoutWithSegments(workoutId)
        workout = w
        segments = s
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.formattedDate() ?: "รายละเอียด") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "กลับ")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val w = workout ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ไม่พบข้อมูล")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main stats
            StatGroupCard(title = "สรุปการวิ่ง") {
                StatRow("ระยะทาง", w.formattedDistance())
                StatRow("เวลา", w.formattedDuration())
                StatRow("Avg Pace", w.formattedAvgPace())
                StatRow("Max Pace", formatPace(w.maxPaceSecsPerKm))
            }

            // Heart rate
            if (w.avgHeartRate > 0) {
                StatGroupCard(title = "อัตราการเต้นหัวใจ") {
                    StatRow("เฉลี่ย", "${w.avgHeartRate} bpm", color = Color.Red)
                    StatRow("สูงสุด", "${w.maxHeartRate} bpm", color = Color.Red)
                }
            }

            // Interval config
            val intervalConfig = w.intervalConfigJson?.let { json ->
                runCatching { Json.decodeFromString<IntervalConfig>(json) }.getOrNull()
            }
            intervalConfig?.let { config ->
                StatGroupCard(title = "การตั้งค่า Interval") {
                    StatRow("โหมด", if (config.mode.name == "ACTIVITY_BASED") "Activity List" else "ควบคุม Pace")
                    if (config.activities.isNotEmpty()) {
                        config.activities.forEachIndexed { i, act ->
                            StatRow("  ${i + 1}. ${act.type.label}", "${act.distanceMeters} เมตร")
                        }
                        StatRow("จำนวนรอบ", "${config.repetitions} รอบ")
                    }
                    if (config.paceAlertEnabled) {
                        StatRow(
                            "ช่วง Pace",
                            "${IntervalConfig.formatPace(config.minPaceSecsPerKm)} – ${IntervalConfig.formatPace(config.maxPaceSecsPerKm)}"
                        )
                    }
                }
            }

            // GPS summary
            if (segments.isNotEmpty()) {
                StatGroupCard(title = "GPS (${segments.size} จุด)") {
                    val hrList = segments.map { it.heartRate }.filter { it > 0 }
                    if (hrList.isNotEmpty()) {
                        StatRow("HR สูงสุด", "${hrList.max()} bpm")
                    }
                    val paceList = segments.map { it.paceSecsPerKm }.filter { it > 0 }
                    if (paceList.isNotEmpty()) {
                        StatRow("Pace เร็วสุด", formatPace(paceList.min()))
                        StatRow("Pace ช้าสุด", formatPace(paceList.max()))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatGroupCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
}

private fun formatPace(secsPerKm: Float): String {
    if (secsPerKm <= 0) return "--:--"
    val mins = secsPerKm.toInt() / 60
    val secs = secsPerKm.toInt() % 60
    return "$mins:${secs.toString().padStart(2, '0')} /km"
}
