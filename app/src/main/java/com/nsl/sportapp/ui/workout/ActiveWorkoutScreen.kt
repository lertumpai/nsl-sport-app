package com.nsl.sportapp.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsl.sportapp.data.model.ActivityType
import com.nsl.sportapp.data.model.IntervalConfig
import com.nsl.sportapp.data.model.IntervalMode

@Composable
fun ActiveWorkoutScreen(
    viewModel: WorkoutViewModel,
    onWorkoutFinished: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.workoutState.collectAsState()
    var showStopDialog by remember { mutableStateOf(false) }

    // Auto-start only once when screen first enters composition (free-run mode)
    LaunchedEffect(Unit) {
        if (!state.isRunning && !state.isPaused) {
            viewModel.startWorkout(context, null)
        }
    }

    val phaseColor by animateColorAsState(
        targetValue = when (state.currentActivityLabel()) {
            "เดิน" -> MaterialTheme.colorScheme.secondary
            "พัก" -> Color.Gray
            else -> MaterialTheme.colorScheme.primary
        },
        label = "phaseColor"
    )

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Elapsed time
                Text(
                    formatDuration(state.elapsedMillis),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(16.dp))

                // Primary stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BigStatCard(
                        value = formatDistance(state.distanceMeters),
                        label = "ระยะทาง",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    BigStatCard(
                        value = formatPace(state.currentPaceSecsPerKm),
                        label = "Pace /km",
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Heart rate + avg pace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SmallStatCard(
                        icon = { Icon(Icons.Default.Favorite, null, tint = Color.Red, modifier = Modifier.size(16.dp)) },
                        value = if (state.heartRate > 0) "${state.heartRate} bpm" else "-- bpm",
                        label = "อัตราหัวใจ"
                    )
                    SmallStatCard(
                        icon = null,
                        value = formatPace(state.avgPaceSecsPerKm),
                        label = "Avg Pace"
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Interval section
                if (state.isIntervalMode) {
                    val config = state.intervalConfig
                    if (config != null && config.mode == IntervalMode.ACTIVITY_BASED) {
                        IntervalStatusCard(state = state, phaseColor = phaseColor)
                    } else if (config != null && config.mode == IntervalMode.PACE_CONTROLLED) {
                        PaceControlCard(config = config, currentPace = state.currentPaceSecsPerKm)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Spacer(Modifier.weight(1f))

                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stop button
                    Button(
                        onClick = { showStopDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "หยุด", modifier = Modifier.size(32.dp))
                    }

                    // Pause/Resume button
                    Button(
                        onClick = {
                            if (state.isPaused) viewModel.resumeWorkout(context)
                            else viewModel.pauseWorkout(context)
                        },
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "พัก/เล่น",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("หยุดการวิ่ง?") },
            text = { Text("ระบบจะบันทึกสถิติและสิ้นสุดการออกกำลังกาย") },
            confirmButton = {
                Button(
                    onClick = {
                        showStopDialog = false
                        viewModel.stopWorkout(context)
                        onWorkoutFinished()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("หยุด") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStopDialog = false }) { Text("ยกเลิก") }
            }
        )
    }
}

@Composable
private fun IntervalStatusCard(
    state: com.nsl.sportapp.service.WorkoutTrackingService.WorkoutState,
    phaseColor: Color
) {
    val config = state.intervalConfig ?: return
    val actIndex = state.currentActivityIndex % config.activities.size.coerceAtLeast(1)
    val targetDist = config.activityDistance(actIndex).toFloat().coerceAtLeast(1f)
    val progress = (state.currentActivityDistanceMeters / targetDist).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = phaseColor.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.currentActivityLabel().ifEmpty { "วิ่ง" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = phaseColor
                )
                Text(
                    "รอบ ${state.currentRepetition}/${state.totalRepetitions}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = phaseColor,
                trackColor = phaseColor.copy(alpha = 0.2f)
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${state.currentActivityDistanceMeters.toInt()} m",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "เป้า ${config.activityDistance(actIndex)} m",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Pace range indicator
            if (config.paceAlertEnabled) {
                Spacer(Modifier.height(8.dp))
                val currentPace = state.currentPaceSecsPerKm.toInt()
                val inRange = currentPace in config.minPaceSecsPerKm..config.maxPaceSecsPerKm
                val rangeColor = if (inRange || currentPace == 0) Color(0xFF4CAF50) else Color.Red
                Text(
                    "เป้า Pace: ${IntervalConfig.formatPace(config.minPaceSecsPerKm)}–${IntervalConfig.formatPace(config.maxPaceSecsPerKm)}",
                    fontSize = 12.sp,
                    color = rangeColor
                )
            }
        }
    }
}

@Composable
private fun PaceControlCard(config: IntervalConfig, currentPace: Float) {
    val currentPaceInt = currentPace.toInt()
    val inRange = currentPaceInt == 0 || currentPaceInt in config.minPaceSecsPerKm..config.maxPaceSecsPerKm
    val cardColor = if (inRange) Color(0xFF4CAF50) else Color.Red

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "ควบคุม Pace",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "เป้าหมาย: ${IntervalConfig.formatPace(config.minPaceSecsPerKm)}–${IntervalConfig.formatPace(config.maxPaceSecsPerKm)} /km",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (inRange || currentPaceInt == 0) "✓ ในช่วง" else "⚠ ออกนอกช่วง",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardColor
                )
            }
        }
    }
}

@Composable
private fun BigStatCard(value: String, label: String, color: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.size(width = 160.dp, height = 100.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SmallStatCard(
    icon: (@Composable () -> Unit)?,
    value: String,
    label: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.size(width = 160.dp, height = 72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.invoke()
            Column {
                Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSecs = millis / 1000
    val hrs = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hrs > 0)
        "${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    else
        "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}

private fun formatDistance(meters: Float): String {
    return if (meters >= 1000) String.format("%.2f km", meters / 1000f)
    else "${meters.toInt()} m"
}

private fun formatPace(secsPerKm: Float): String {
    if (secsPerKm <= 0) return "--:--"
    val mins = secsPerKm.toInt() / 60
    val secs = secsPerKm.toInt() % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

private fun IntervalConfig.formatPace(secs: Int) = IntervalConfig.formatPace(secs)
