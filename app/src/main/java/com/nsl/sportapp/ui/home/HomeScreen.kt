package com.nsl.sportapp.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.google.android.gms.wearable.Wearable
import com.nsl.sportapp.data.repository.WorkoutRepository
import com.nsl.sportapp.datalayer.SyncHelper
import com.nsl.sportapp.ui.history.HistoryViewModel
import com.nsl.sportapp.ui.workout.WorkoutViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    historyViewModel: HistoryViewModel,
    workoutViewModel: WorkoutViewModel,
    onStartFreeRun: () -> Unit,
    onOpenIntervalSetup: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPrograms: () -> Unit = {}
) {
    val workouts by historyViewModel.workouts.collectAsState()
    val workoutState by workoutViewModel.workoutState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var watchConnected by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    // Check connection status once when screen opens
    LaunchedEffect(Unit) {
        watchConnected = try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("NSL Sport", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "ประวัติ")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // Watch connection status + manual sync button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (watchConnected) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                CircleShape
                            )
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (watchConnected) "นาฬิกาเชื่อมต่อแล้ว" else "ไม่ได้เชื่อมต่อนาฬิกา",
                        fontSize = 13.sp,
                        color = if (watchConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            watchConnected = try {
                                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                                nodes.isNotEmpty()
                            } catch (e: Exception) { false }
                            SyncHelper.syncProgramsToWatch(context)
                            isSyncing = false
                        }
                    },
                    enabled = !isSyncing
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "ซิงค์โปรแกรมไปนาฬิกา",
                        tint = if (watchConnected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Active workout indicator
            if (workoutState.isRunning) {
                ActiveWorkoutBanner(onContinue = onStartFreeRun)
                Spacer(Modifier.height(24.dp))
            }

            // Start buttons
            Button(
                onClick = onStartFreeRun,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.DirectionsRun,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    "  วิ่งอิสระ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onOpenIntervalSetup,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null)
                Text("  ตั้งค่า Interval", fontSize = 18.sp)
            }

            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onOpenPrograms,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Text("  โปรแกรมของฉัน", fontSize = 18.sp)
            }

            Spacer(Modifier.height(32.dp))

            // Stats summary
            if (workouts.isNotEmpty()) {
                Text(
                    "สถิติของคุณ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                StatsSummaryCard(workouts.size, workouts.sumOf { it.totalDistanceMeters.toLong() })
                Spacer(Modifier.height(16.dp))

                // Recent workout
                Text(
                    "ออกกำลังกายล่าสุด",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                workouts.take(3).forEach { workout ->
                    RecentWorkoutCard(workout)
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                EmptyStateCard()
            }
        }
    }
}

@Composable
private fun ActiveWorkoutBanner(onContinue: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("มีการวิ่งที่กำลังดำเนินอยู่", fontWeight = FontWeight.Bold)
            Button(onClick = onContinue) { Text("ดูสถิติ") }
        }
    }
}

@Composable
private fun StatsSummaryCard(totalWorkouts: Int, totalDistanceMeters: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(value = "$totalWorkouts", label = "ครั้ง")
            StatItem(
                value = String.format("%.1f", totalDistanceMeters / 1000f),
                label = "กม. รวม"
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecentWorkoutCard(workout: com.nsl.sportapp.data.db.entity.WorkoutEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(workout.formattedDate(), fontWeight = FontWeight.Medium)
                Text(
                    "${workout.formattedDistance()}  •  ${workout.formattedDuration()}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                workout.formattedAvgPace(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.DirectionsRun,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "ยังไม่มีข้อมูลการวิ่ง\nกดเริ่มวิ่งเพื่อบันทึกสถิติ",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
