package com.nsl.sportapp.wear.ui

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.repository.WearWorkoutRepository

@Composable
fun WearHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { WearWorkoutRepository(context) }
    val workouts by repository.allWorkouts.collectAsState(initial = emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray),
                    modifier = Modifier.height(28.dp).padding(end = 4.dp)
                ) { Text("←", fontSize = 12.sp) }
                Text(
                    "ประวัติการวิ่ง",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground
                )
            }

            if (workouts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ยังไม่มีประวัติ", fontSize = 12.sp,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(workouts) { workout ->
                        WearWorkoutCard(workout)
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun WearWorkoutCard(workout: WearWorkoutEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(workout.formattedDate(), fontSize = 10.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(workout.formattedDistance(), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface)
            Text(workout.formattedDuration(), fontSize = 13.sp,
                color = MaterialTheme.colors.onSurface)
        }
        if (workout.avgHeartRate > 0) {
            Text("❤ ${workout.avgHeartRate} bpm", fontSize = 10.sp, color = Color.Red)
        }
        if (!workout.synced) {
            Text("⏳ รอซิงค์", fontSize = 9.sp, color = Color(0xFFFF9800))
        }
    }
}
