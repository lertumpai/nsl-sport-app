package com.nsl.sportapp.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.repository.WearWorkoutRepository
import com.nsl.sportapp.wear.datalayer.WearSyncHelper
import kotlinx.coroutines.launch

@Composable
fun WearHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { WearWorkoutRepository(context) }
    val workouts by repository.allWorkouts.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var lastSyncCount by remember { mutableStateOf(-1) }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    "ประวัติการวิ่ง",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center
                )
            }

            if (workouts.isEmpty()) {
                item {
                    Text(
                        "ยังไม่มีประวัติ",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                workouts.forEach { workout ->
                    item(key = workout.id) {
                        WearWorkoutCard(workout)
                    }
                }
            }

            // Sync button — syncs workouts to phone + requests programs
            item {
                Button(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            lastSyncCount = WearSyncHelper.syncWorkoutsToPhone(context)
                            WearSyncHelper.requestProgramsFromPhone(context)
                            isSyncing = false
                        }
                    },
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text(
                        when {
                            isSyncing -> "กำลังซิงค์..."
                            lastSyncCount == 0 -> "ซิงค์แล้ว ✓"
                            lastSyncCount > 0 -> "ซิงค์แล้ว $lastSyncCount รายการ ✓"
                            else -> "ซิงค์ข้อมูล"
                        },
                        fontSize = 11.sp
                    )
                }
            }

            // Back button at bottom — important for round watch faces
            item {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("กลับ", fontSize = 12.sp)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun WearWorkoutCard(workout: WearWorkoutEntity) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                workout.formattedDate(),
                fontSize = 9.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
            Text(
                if (workout.synced) "✓" else "⏳",
                fontSize = 9.sp,
                color = if (workout.synced) Color(0xFF4CAF50) else Color(0xFFFF9800)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                workout.formattedDistance(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface
            )
            Text(
                workout.formattedDuration(),
                fontSize = 13.sp,
                color = MaterialTheme.colors.onSurface
            )
        }
        if (workout.avgHeartRate > 0) {
            Text("❤ ${workout.avgHeartRate} bpm", fontSize = 10.sp, color = Color.Red)
        }
    }
}
