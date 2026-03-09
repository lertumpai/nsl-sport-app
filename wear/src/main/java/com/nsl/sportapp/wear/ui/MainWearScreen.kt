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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.wearable.Wearable
import com.nsl.sportapp.wear.service.WearWorkoutService
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

@Composable
fun MainWearScreen(
    onStartWorkout: () -> Unit,
    onHistory: () -> Unit = {},
    onPrograms: () -> Unit = {}
) {
    val state by WearWorkoutService.state.collectAsState()
    val context = LocalContext.current

    // Independent connection check — polls every 5 s so it stays accurate
    // even when WearWorkoutService is not running (e.g. after workout ends).
    var phoneConnected by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            phoneConnected = try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                nodes.isNotEmpty()
            } catch (e: Exception) {
                false
            }
            delay(5_000L)
        }
    }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    "NSL Sport",
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Bluetooth connection status (independent of service state)
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (phoneConnected) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        if (phoneConnected) "เชื่อมต่อแล้ว" else "ไม่ได้เชื่อมต่อ",
                        fontSize = 10.sp,
                        color = if (phoneConnected) Color(0xFF4CAF50) else Color.Gray
                    )
                }
            }

            item {
                HeartRateDisplay(state.heartRate)
            }

            // Start workout button
            item {
                Button(
                    onClick = onStartWorkout,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6B35)),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text("เริ่มวิ่ง", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Programs button
            item {
                Button(
                    onClick = onPrograms,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF9C27B0)),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Text("โปรแกรม", fontSize = 12.sp)
                }
            }

            // History button
            item {
                Button(
                    onClick = onHistory,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Text("ประวัติ", fontSize = 12.sp)
                }
            }
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
