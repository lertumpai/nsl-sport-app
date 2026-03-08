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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nsl.sportapp.wear.service.WearWorkoutService

@Composable
fun MainWearScreen(
    onStartWorkout: () -> Unit,
    onViewActiveWorkout: () -> Unit
) {
    val isActive by WearWorkoutService.isActive.collectAsState()
    val heartRate by WearWorkoutService.heartRate.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            if (isActive) {
                // Show HR and link to active workout
                Text(
                    "กำลังวิ่ง",
                    color = Color(0xFFFF6B35),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                HeartRateDisplay(heartRate)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onViewActiveWorkout,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3))
                ) {
                    Text("ดูสถิติ", fontSize = 12.sp)
                }
            } else {
                Text(
                    "NSL Sport",
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "รอสัญญาณจากโทรศัพท์",
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                HeartRateDisplay(heartRate)
            }
        }
    }
}

@Composable
fun HeartRateDisplay(heartRate: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "❤",
            fontSize = 20.sp,
            color = Color.Red
        )
        Text(
            text = if (heartRate > 0) "$heartRate" else "--",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Red
        )
        Text(
            text = "bpm",
            fontSize = 11.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
        )
    }
}
