package com.nsl.sportapp.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.nsl.sportapp.data.model.ActivityType
import com.nsl.sportapp.data.model.IntervalActivity
import com.nsl.sportapp.data.model.IntervalConfig
import com.nsl.sportapp.data.model.IntervalMode

data class WearIntervalState(
    val mode: IntervalMode = IntervalMode.ACTIVITY_BASED,
    val activities: List<IntervalActivity> = emptyList(),
    val repetitions: Int = 3,
    val minPaceSecsPerKm: Int = 360,
    val maxPaceSecsPerKm: Int = 480,
    val paceAlertEnabled: Boolean = true
)

@Composable
fun WearIntervalSetupScreen(
    onStart: (IntervalConfig) -> Unit,
    onSaveProgram: ((String, IntervalConfig) -> Unit)? = null,
    onBack: () -> Unit
) {
    var state by remember { mutableStateOf(WearIntervalState()) }
    var showSaveInput by remember { mutableStateOf(false) }

    // Custom distance state — adjustable in 100 m increments
    var customRunDist by remember { mutableStateOf(300) }
    var customWalkDist by remember { mutableStateOf(100) }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    "ตั้งค่า Interval", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
            }

            // Mode toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ModeChip(
                        label = "Activity",
                        selected = state.mode == IntervalMode.ACTIVITY_BASED,
                        onClick = { state = state.copy(mode = IntervalMode.ACTIVITY_BASED) }
                    )
                    ModeChip(
                        label = "Pace",
                        selected = state.mode == IntervalMode.PACE_CONTROLLED,
                        onClick = { state = state.copy(mode = IntervalMode.PACE_CONTROLLED) }
                    )
                }
            }

            // Activity list + custom add (ACTIVITY_BASED only)
            if (state.mode == IntervalMode.ACTIVITY_BASED) {

                // Current activities — tap to remove
                state.activities.forEachIndexed { idx, act ->
                    item(key = "act_$idx") {
                        Chip(
                            onClick = {
                                state = state.copy(
                                    activities = state.activities.toMutableList().also { it.removeAt(idx) }
                                )
                            },
                            label = { Text("${act.type.label} ${act.distanceMeters}m  ✕", fontSize = 12.sp) },
                            colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── Custom Run distance ──────────────────────────────────────
                item {
                    Text(
                        "+ เพิ่ม Activity (แตะ ± เพื่อตั้งระยะ):",
                        fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Distance picker for Run
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallCounterButton("−") {
                                customRunDist = (customRunDist - 100).coerceAtLeast(100)
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${customRunDist}m",
                                fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                modifier = Modifier.width(46.dp),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.width(4.dp))
                            SmallCounterButton("+") {
                                customRunDist += 100
                            }
                        }
                        // Add run button
                        Button(
                            onClick = {
                                state = state.copy(
                                    activities = state.activities + IntervalActivity(ActivityType.RUN, customRunDist)
                                )
                            },
                            modifier = Modifier.size(width = 60.dp, height = 28.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6B35))
                        ) { Text("+ วิ่ง", fontSize = 10.sp, textAlign = TextAlign.Center) }
                    }
                }

                // ── Custom Walk distance ─────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Distance picker for Walk
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallCounterButton("−") {
                                customWalkDist = (customWalkDist - 100).coerceAtLeast(100)
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${customWalkDist}m",
                                fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                modifier = Modifier.width(46.dp),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.width(4.dp))
                            SmallCounterButton("+") {
                                customWalkDist += 100
                            }
                        }
                        // Add walk button
                        Button(
                            onClick = {
                                state = state.copy(
                                    activities = state.activities + IntervalActivity(ActivityType.WALK, customWalkDist)
                                )
                            },
                            modifier = Modifier.size(width = 60.dp, height = 28.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3))
                        ) { Text("+ เดิน", fontSize = 10.sp, textAlign = TextAlign.Center) }
                    }
                }

                // Repetitions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("รอบ:", fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallCounterButton("−") {
                                state = state.copy(repetitions = (state.repetitions - 1).coerceAtLeast(1))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("${state.repetitions}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            SmallCounterButton("+") {
                                state = state.copy(repetitions = (state.repetitions + 1).coerceAtMost(50))
                            }
                        }
                    }
                }
            }

            // Pace alert settings
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("แจ้งเตือน Pace", fontSize = 12.sp)
                        Switch(checked = state.paceAlertEnabled, onCheckedChange = {
                            state = state.copy(paceAlertEnabled = it)
                        })
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("เร็ว:", fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallCounterButton("−") {
                            state = state.copy(minPaceSecsPerKm = (state.minPaceSecsPerKm - 30).coerceAtLeast(120))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(IntervalConfig.formatPace(state.minPaceSecsPerKm), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        SmallCounterButton("+") {
                            state = state.copy(minPaceSecsPerKm = (state.minPaceSecsPerKm + 30).coerceAtMost(state.maxPaceSecsPerKm - 30))
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ช้า:", fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallCounterButton("−") {
                            state = state.copy(maxPaceSecsPerKm = (state.maxPaceSecsPerKm - 30).coerceAtLeast(state.minPaceSecsPerKm + 30))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(IntervalConfig.formatPace(state.maxPaceSecsPerKm), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        SmallCounterButton("+") {
                            state = state.copy(maxPaceSecsPerKm = (state.maxPaceSecsPerKm + 30).coerceAtMost(900))
                        }
                    }
                }
            }

            // Save as program
            if (onSaveProgram != null && !showSaveInput) {
                item {
                    Chip(
                        onClick = { showSaveInput = true },
                        label = { Text("บันทึกโปรแกรม", fontSize = 12.sp) },
                        colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.mode == IntervalMode.PACE_CONTROLLED || state.activities.isNotEmpty()
                    )
                }
            }

            // Save program name input
            if (showSaveInput) {
                item {
                    var name by remember { mutableStateOf("โปรแกรมใหม่") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ชื่อ: $name", fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = {
                                    val config = buildConfig(state)
                                    if (config != null) onSaveProgram?.invoke(name, config)
                                    showSaveInput = false
                                },
                                modifier = Modifier.size(48.dp, 28.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
                            ) { Text("บันทึก", fontSize = 10.sp) }
                            Button(
                                onClick = { showSaveInput = false },
                                modifier = Modifier.size(48.dp, 28.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray)
                            ) { Text("ยกเลิก", fontSize = 10.sp) }
                        }
                    }
                }
            }

            // Start button
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { buildConfig(state)?.let { onStart(it) } },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                    enabled = state.mode == IntervalMode.PACE_CONTROLLED || state.activities.isNotEmpty()
                ) {
                    Text("เริ่มวิ่ง!", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
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

private fun buildConfig(state: WearIntervalState): IntervalConfig? {
    return when (state.mode) {
        IntervalMode.ACTIVITY_BASED -> {
            if (state.activities.isEmpty()) return null
            IntervalConfig(
                mode = IntervalMode.ACTIVITY_BASED,
                activities = state.activities,
                repetitions = state.repetitions,
                minPaceSecsPerKm = state.minPaceSecsPerKm,
                maxPaceSecsPerKm = state.maxPaceSecsPerKm,
                paceAlertEnabled = state.paceAlertEnabled
            )
        }
        IntervalMode.PACE_CONTROLLED -> IntervalConfig(
            mode = IntervalMode.PACE_CONTROLLED,
            activities = emptyList(),
            repetitions = 1,
            minPaceSecsPerKm = state.minPaceSecsPerKm,
            maxPaceSecsPerKm = state.maxPaceSecsPerKm,
            paceAlertEnabled = true
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        colors = if (selected)
            ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.primary)
        else
            ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
        modifier = Modifier.width(72.dp)
    )
}

@Composable
private fun SmallCounterButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface)
    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}
