package com.nsl.sportapp.wear.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nsl.sportapp.wear.service.WearWorkoutService

@Composable
fun ActiveWearWorkoutScreen() {
    val heartRate by WearWorkoutService.heartRate.collectAsState()
    val distanceMeters by WearWorkoutService.distanceMeters.collectAsState()
    val paceSecsPerKm by WearWorkoutService.paceSecsPerKm.collectAsState()
    val currentPhase by WearWorkoutService.currentPhase.collectAsState()

    val phaseColor = when (currentPhase) {
        "เดิน" -> Color(0xFF2196F3)
        "พัก" -> Color.Gray
        else -> Color(0xFFFF6B35)
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
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Phase indicator
            Text(
                text = currentPhase,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = phaseColor
            )

            Spacer(Modifier.height(6.dp))

            // Heart rate (largest)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("❤ ", fontSize = 14.sp, color = Color.Red)
                Text(
                    text = if (heartRate > 0) "$heartRate" else "--",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
                Text(" bpm", fontSize = 11.sp, color = Color.Red)
            }

            Spacer(Modifier.height(6.dp))

            // Distance + Pace row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WearStatColumn(
                    value = formatDistance(distanceMeters),
                    label = "ระยะทาง"
                )
                WearStatColumn(
                    value = formatPace(paceSecsPerKm),
                    label = "Pace/km"
                )
            }
        }
    }
}

@Composable
private fun WearStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
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
