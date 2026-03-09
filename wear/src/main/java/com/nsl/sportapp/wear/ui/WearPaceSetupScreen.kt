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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.nsl.sportapp.wear.service.WearWorkoutService
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Simple pace alert setup screen on the watch.
 * User sets min/max pace (secs per km) or disables pace alert.
 */
@Composable
fun WearPaceSetupScreen(
    onStarted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var paceAlertEnabled by remember { mutableStateOf(false) }
    // Defaults: 5:00/km min, 6:30/km max
    var minPaceMinutes by remember { mutableIntStateOf(5) }
    var minPaceSeconds by remember { mutableIntStateOf(0) }
    var maxPaceMinutes by remember { mutableIntStateOf(6) }
    var maxPaceSeconds by remember { mutableIntStateOf(30) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("ตั้งค่า Pace", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onBackground)

            Spacer(Modifier.height(6.dp))

            // Toggle pace alert
            Button(
                onClick = { paceAlertEnabled = !paceAlertEnabled },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (paceAlertEnabled) Color(0xFF4CAF50) else Color.Gray
                ),
                modifier = Modifier.fillMaxWidth(0.8f).height(32.dp)
            ) {
                Text(if (paceAlertEnabled) "แจ้งเตือน: เปิด" else "แจ้งเตือน: ปิด", fontSize = 11.sp)
            }

            if (paceAlertEnabled) {
                Spacer(Modifier.height(6.dp))
                Text("Pace เร็วสุด (min:sec/km)", fontSize = 9.sp,
                    color = MaterialTheme.colors.onBackground.copy(0.7f), textAlign = TextAlign.Center)
                PaceAdjustRow(
                    minutes = minPaceMinutes, seconds = minPaceSeconds,
                    onMinChange = { minPaceMinutes = it.coerceIn(3, 10) },
                    onSecChange = { minPaceSeconds = it }
                )

                Spacer(Modifier.height(4.dp))
                Text("Pace ช้าสุด (min:sec/km)", fontSize = 9.sp,
                    color = MaterialTheme.colors.onBackground.copy(0.7f), textAlign = TextAlign.Center)
                PaceAdjustRow(
                    minutes = maxPaceMinutes, seconds = maxPaceSeconds,
                    onMinChange = { maxPaceMinutes = it.coerceIn(3, 15) },
                    onSecChange = { maxPaceSeconds = it }
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray),
                    modifier = Modifier.size(width = 44.dp, height = 32.dp)
                ) { Text("←", fontSize = 12.sp) }

                Button(
                    onClick = {
                        val minPace = if (paceAlertEnabled) minPaceMinutes * 60 + minPaceSeconds else 0
                        val maxPace = if (paceAlertEnabled) maxPaceMinutes * 60 + maxPaceSeconds else 0
                        context.startForegroundService(
                            Intent(context, WearWorkoutService::class.java).apply {
                                action = WearWorkoutService.ACTION_START
                                putExtra(WearWorkoutService.EXTRA_MIN_PACE, minPace)
                                putExtra(WearWorkoutService.EXTRA_MAX_PACE, maxPace)
                            }
                        )
                        onStarted()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6B35)),
                    modifier = Modifier.size(width = 72.dp, height = 32.dp)
                ) { Text("เริ่ม!", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PaceAdjustRow(
    minutes: Int, seconds: Int,
    onMinChange: (Int) -> Unit, onSecChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Minutes
        Button(onClick = { onMinChange(minutes - 1) },
            modifier = Modifier.size(24.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF424242))) {
            Text("-", fontSize = 10.sp)
        }
        Text(
            "${minutes.toString().padStart(2,'0')}:${seconds.toString().padStart(2,'0')}",
            fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Button(onClick = { onMinChange(minutes + 1) },
            modifier = Modifier.size(24.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF424242))) {
            Text("+", fontSize = 10.sp)
        }
        Spacer(Modifier.size(4.dp))
        // Seconds +30
        Button(onClick = { onSecChange(if (seconds == 0) 30 else 0) },
            modifier = Modifier.size(width = 32.dp, height = 24.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF616161))) {
            Text("+30s", fontSize = 8.sp)
        }
    }
}
