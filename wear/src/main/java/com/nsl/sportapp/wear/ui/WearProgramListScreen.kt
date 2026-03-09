package com.nsl.sportapp.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.nsl.sportapp.data.model.IntervalConfig
import com.nsl.sportapp.data.model.IntervalMode
import com.nsl.sportapp.wear.data.db.entity.WearTrainingProgramEntity
import com.nsl.sportapp.wear.data.repository.WearWorkoutRepository
import kotlinx.serialization.json.Json

@Composable
fun WearProgramListScreen(
    repository: WearWorkoutRepository,
    onStartProgram: (IntervalConfig) -> Unit,
    onCreateNew: () -> Unit,
    onBack: () -> Unit
) {
    val programs by repository.allPrograms.collectAsState(initial = emptyList())
    val json = Json { ignoreUnknownKeys = true }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text("โปรแกรม", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(2.dp))
            }

            if (programs.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            "ยังไม่มีโปรแกรม\nสร้างหรือซิงค์จากโทรศัพท์",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                programs.forEach { program ->
                    item(key = program.id) {
                        val config = runCatching { json.decodeFromString<IntervalConfig>(program.intervalConfigJson) }.getOrNull()
                        ProgramChip(
                            program = program,
                            config = config,
                            onStart = { config?.let { onStartProgram(it) } }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                ) { Text("+ สร้างใหม่", fontSize = 12.sp) }
            }

            item {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray)
                ) { Text("กลับ", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun ProgramChip(
    program: WearTrainingProgramEntity,
    config: IntervalConfig?,
    onStart: () -> Unit
) {
    val subtitle = when (config?.mode) {
        IntervalMode.ACTIVITY_BASED -> "× ${config.repetitions} รอบ · ${config.activities.size} activities"
        IntervalMode.PACE_CONTROLLED -> "Pace ${IntervalConfig.formatPace(config.minPaceSecsPerKm)}–${IntervalConfig.formatPace(config.maxPaceSecsPerKm)}"
        null -> "ข้อมูลไม่สมบูรณ์"
    }
    Chip(
        onClick = onStart,
        label = {
            Text(program.name, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = { Text(subtitle, fontSize = 11.sp, color = Color.Gray) },
        colors = ChipDefaults.chipColors(
            backgroundColor = if (config != null) MaterialTheme.colors.surface else Color.DarkGray
        ),
        modifier = Modifier.fillMaxWidth(),
        enabled = config != null
    )
}
