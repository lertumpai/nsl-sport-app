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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
        !state.paceAlertEnabled || state.currentPaceSecsPerKm <= 0 -> Color.White
        state.paceInRange -> Color(0xFF4CAF50)
        else -> Color(0xFFFF5252)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ── Top: Timer + GPS indicator ───────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                formatDuration(state.elapsedMillis),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (state.gpsActive) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                )
            }
        }

        // ── Middle: HR  |  Distance  |  Pace ────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Heart Rate
            WearStatBlock(
                value = if (state.heartRate > 0) "${state.heartRate}" else "--",
                label = "❤ bpm",
                valueColor = Color(0xFFFF5252),
                labelColor = Color(0xFFFF5252).copy(alpha = 0.7f)
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )

            // Distance
            WearStatBlock(
                value = formatDistance(state.distanceMeters),
                label = "ระยะทาง",
                valueColor = Color.White
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )

            // Pace
            WearStatBlock(
                value = formatPace(state.currentPaceSecsPerKm),
                label = "Pace/km",
                valueColor = paceColor,
                labelColor = paceColor.copy(alpha = 0.7f)
            )
        }

        // ── Pace alert range ─────────────────────────────────────────────
        if (state.paceAlertEnabled) {
            Box(
                modifier = Modifier
                    .background(
                        color = paceColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${formatPace(state.minPaceSecsPerKm.toFloat())} – ${formatPace(state.maxPaceSecsPerKm.toFloat())}",
                    fontSize = 10.sp,
                    color = paceColor,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Spacer(Modifier.height(4.dp))
        }

        // ── Interval info ────────────────────────────────────────────────
        if (state.intervalConfig != null && state.currentActivityLabel.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFFFEB3B).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    "${state.currentActivityLabel}  รอบ ${state.currentRepetition}/${state.intervalConfig!!.repetitions}",
                    fontSize = 10.sp,
                    color = Color(0xFFFFEB3B),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        } else if (state.currentActivityLabel == "เสร็จแล้ว!") {
            Text(
                "เสร็จแล้ว! 🎉",
                fontSize = 11.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }

        // ── Bottom: Pause / Stop buttons ─────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val action = if (state.isPaused) WearWorkoutService.ACTION_RESUME
                               else WearWorkoutService.ACTION_PAUSE
                    context.startService(
                        Intent(context, WearWorkoutService::class.java).apply { this.action = action }
                    )
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
                modifier = Modifier.size(40.dp)
            ) {
                Text(if (state.isPaused) "▶" else "⏸", fontSize = 14.sp)
            }

            Button(
                onClick = {
                    context.startService(Intent(context, WearWorkoutService::class.java).apply {
                        action = WearWorkoutService.ACTION_STOP
                    })
                    onWorkoutStopped()
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF5252)),
                modifier = Modifier.size(40.dp)
            ) {
                Text("■", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun WearStatBlock(
    value: String,
    label: String,
    valueColor: Color = Color.White,
    labelColor: Color = Color.White.copy(alpha = 0.6f)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.Center
        )
        Text(
            label,
            fontSize = 8.sp,
            color = labelColor,
            textAlign = TextAlign.Center
        )
    }
}

internal fun formatDuration(millis: Long): String {
    val secs = millis / 1000
    val h = secs / 3600; val m = (secs % 3600) / 60; val s = secs % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

internal fun formatDistance(meters: Float): String =
    if (meters >= 1000) String.format("%.2f km", meters / 1000f) else "${meters.toInt()} m"

internal fun formatPace(secsPerKm: Float): String {
    if (secsPerKm <= 0) return "--:--"
    val mins = secsPerKm.toInt() / 60
    val secs = secsPerKm.toInt() % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}
