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
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nsl.sportapp.wear.service.WearWorkoutService

@Composable
fun MainWearScreen(onStartWorkout: () -> Unit, onHistory: () -> Unit = {}) {
    val state by WearWorkoutService.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                "NSL Sport",
                color = MaterialTheme.colors.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            HeartRateDisplay(state.heartRate)
            Spacer(Modifier.height(12.dp))
            // Two main buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onStartWorkout,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6B35)),
                    modifier = Modifier.size(width = 80.dp, height = 36.dp)
                ) {
                    Text("เริ่มวิ่ง", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onHistory,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
                    modifier = Modifier.size(width = 72.dp, height = 36.dp)
                ) {
                    Text("ประวัติ", fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "หรือเริ่มจากโทรศัพท์",
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HeartRateDisplay(heartRate: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "❤", fontSize = 20.sp, color = Color.Red)
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
