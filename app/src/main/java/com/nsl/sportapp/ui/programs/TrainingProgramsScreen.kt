package com.nsl.sportapp.ui.programs

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.nsl.sportapp.data.db.entity.TrainingProgramEntity
import com.nsl.sportapp.data.model.IntervalConfig
import com.nsl.sportapp.data.model.IntervalMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingProgramsScreen(
    viewModel: TrainingProgramsViewModel,
    onStartProgram: (IntervalConfig) -> Unit,
    onCreateNew: () -> Unit,
    onBack: () -> Unit
) {
    val programs by viewModel.programs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("โปรแกรมของฉัน") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "กลับ")
                    }
                },
                actions = {
                    IconButton(onClick = onCreateNew) {
                        Icon(Icons.Default.Add, contentDescription = "สร้างใหม่")
                    }
                }
            )
        }
    ) { padding ->
        if (programs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "ยังไม่มีโปรแกรม\nกด + เพื่อสร้างโปรแกรมแรก",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onCreateNew) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("  สร้างโปรแกรมใหม่")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(programs, key = { it.id }) { program ->
                    ProgramCard(
                        program = program,
                        config = viewModel.parseConfig(program),
                        onStart = { config -> onStartProgram(config) },
                        onDelete = { viewModel.deleteProgram(program.id) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ProgramCard(
    program: TrainingProgramEntity,
    config: IntervalConfig?,
    onStart: (IntervalConfig) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(program.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "ลบ", tint = Color.Red, modifier = Modifier.size(20.dp))
                }
            }
            if (config != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    buildProgramSummary(config),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onStart(config) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = null)
                    Text("  เริ่มวิ่งทันที", fontWeight = FontWeight.Bold)
                }
            } else {
                Text("ข้อมูลโปรแกรมไม่สมบูรณ์", color = Color.Red, fontSize = 13.sp)
            }
        }
    }
}

private fun buildProgramSummary(config: IntervalConfig): String {
    return when (config.mode) {
        IntervalMode.ACTIVITY_BASED -> {
            val acts = config.activities.joinToString(" → ") { "${it.type.label} ${it.distanceMeters}m" }
            "× ${config.repetitions} รอบ: $acts\nPace ${IntervalConfig.formatPace(config.minPaceSecsPerKm)}–${IntervalConfig.formatPace(config.maxPaceSecsPerKm)} min/km"
        }
        IntervalMode.PACE_CONTROLLED -> {
            "ควบคุม Pace ${IntervalConfig.formatPace(config.minPaceSecsPerKm)}–${IntervalConfig.formatPace(config.maxPaceSecsPerKm)} min/km"
        }
    }
}
