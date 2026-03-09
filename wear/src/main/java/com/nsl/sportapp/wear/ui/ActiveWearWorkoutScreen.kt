package com.nsl.sportapp.wear.ui

import android.content.Intent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nsl.sportapp.wear.service.WearWorkoutService

@Composable
fun ActiveWearWorkoutScreen(onWorkoutStopped: () -> Unit) {
    val context = LocalContext.current
    val state by WearWorkoutService.state.collectAsState()

    val paceColor = when {
        !state.paceAlertEnabled || state.currentPaceSecsPerKm <= 0 -> MaterialTheme.colors.onBackground
        state.paceInRange -> Color(0xFF4CAF50)
        else -> Color.Red
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Elapsed time + GPS dot
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(state.elapsedMillis),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground
                )
                if (state.gpsActive) {
                    Spacer(Modifier.size(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Heart rate
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("❤ ", fontSize = 12.sp, color = Color.Red)
                Text(
                    text = if (state.heartRate > 0) "${state.heartRate}" else "--",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
                Text(" bpm", fontSize = 10.sp, color = Color.Red)
            }

            Spacer(Modifier.height(4.dp))

            // Distance + Pace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WearStatColumn(formatDistance(state.distanceMeters), "ระยะทาง", MaterialTheme.colors.onBackground)
                WearStatColumn(formatPace(state.currentPaceSecsPerKm), "Pace/km", paceColor)
            }

            // Pace alert range hint
            if (state.paceAlertEnabled) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatPace(state.minPaceSecsPerKm.toFloat())}–${formatPace(state.maxPaceSecsPerKm.toFloat())}",
                    fontSize = 9.sp,
                    color = paceColor.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Stop / Pause row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Pause/Resume
                Button(
                    onClick = {
                        val action = if (state.isPaused) WearWorkoutService.ACTION_RESUME
                                    else WearWorkoutService.ACTION_PAUSE
                        context.startService(Intent(context, WearWorkoutService::class.java).apply { this.action = action })
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(if (state.isPaused) "▶" else "⏸", fontSize = 12.sp)
                }

                // Stop
                Button(
                    onClick = {
                        context.startService(Intent(context, WearWorkoutService::class.java).apply {
                            action = WearWorkoutService.ACTION_STOP
                        })
                        onWorkoutStopped()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red),
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("■", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WearStatColumn(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f), textAlign = TextAlign.Center)
    }
}

internal fun formatDuration(millis: Long): String {
    val secs = millis / 1000
    val h = secs / 3600; val m = (secs % 3600) / 60; val s = secs % 60
    return if (h > 0) "$h:${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}"
    else "${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}"
}

internal fun formatDistance(meters: Float): String =
    if (meters >= 1000) String.format("%.2f km", meters / 1000f) else "${meters.toInt()} m"

internal fun formatPace(secsPerKm: Float): String {
    if (secsPerKm <= 0) return "--:--"
    val mins = secsPerKm.toInt() / 60
    val secs = secsPerKm.toInt() % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}
