package com.nsl.sportapp.ui.interval

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsl.sportapp.data.model.ActivityType
import com.nsl.sportapp.data.model.IntervalConfig
import com.nsl.sportapp.data.model.IntervalMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalSetupScreen(
    viewModel: IntervalSetupViewModel,
    onStartWorkout: (IntervalConfig?) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ตั้งค่า Interval") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "กลับ")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Mode tabs
            item {
                TabRow(selectedTabIndex = if (state.mode == IntervalMode.ACTIVITY_BASED) 0 else 1) {
                    Tab(
                        selected = state.mode == IntervalMode.ACTIVITY_BASED,
                        onClick = { viewModel.setMode(IntervalMode.ACTIVITY_BASED) },
                        text = { Text("Activity List") }
                    )
                    Tab(
                        selected = state.mode == IntervalMode.PACE_CONTROLLED,
                        onClick = { viewModel.setMode(IntervalMode.PACE_CONTROLLED) },
                        text = { Text("ควบคุม Pace") }
                    )
                }
            }

            // Activity-based section
            if (state.mode == IntervalMode.ACTIVITY_BASED) {
                item {
                    ActivityBasedSection(
                        state = state,
                        onAddActivity = viewModel::addActivity,
                        onRemove = viewModel::removeActivity,
                        onMoveUp = viewModel::moveActivityUp,
                        onMoveDown = viewModel::moveActivityDown,
                        onSetRepetitions = viewModel::setRepetitions
                    )
                }
            }

            // Pace section (shown for both modes)
            item {
                PaceSection(
                    state = state,
                    onSetMinPace = viewModel::setMinPace,
                    onSetMaxPace = viewModel::setMaxPace,
                    onSetPaceAlert = viewModel::setPaceAlert,
                    onApplyPreset = viewModel::applyPreset
                )
            }

            // Start button
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onStartWorkout(viewModel.buildIntervalConfig()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = state.mode == IntervalMode.PACE_CONTROLLED || state.activities.isNotEmpty()
                ) {
                    Text("เริ่มวิ่ง", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ActivityBasedSection(
    state: IntervalSetupUiState,
    onAddActivity: (ActivityType, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onSetRepetitions: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("รายการ Activities", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            // Activity list
            state.activities.forEachIndexed { index, activity ->
                ActivityRow(
                    index = index,
                    activity = activity,
                    isFirst = index == 0,
                    isLast = index == state.activities.lastIndex,
                    onRemove = { onRemove(index) },
                    onMoveUp = { onMoveUp(index) },
                    onMoveDown = { onMoveDown(index) }
                )
                if (index < state.activities.lastIndex) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            if (state.activities.isEmpty()) {
                Text(
                    "ยังไม่มี Activity — กด + เพื่อเพิ่ม",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Add activity form
            AddActivityForm(onAdd = onAddActivity)

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            // Repetitions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("จำนวนรอบ", fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onSetRepetitions(state.repetitions - 1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "${state.repetitions} รอบ",
                        modifier = Modifier.width(60.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(
                        onClick = { onSetRepetitions(state.repetitions + 1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    index: Int,
    activity: com.nsl.sportapp.data.model.IntervalActivity,
    isFirst: Boolean,
    isLast: Boolean,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val color = when (activity.type) {
        ActivityType.RUN -> MaterialTheme.colorScheme.primary
        ActivityType.WALK -> MaterialTheme.colorScheme.secondary
        ActivityType.REST -> Color.Gray
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${activity.type.label}", fontWeight = FontWeight.Medium)
            Text("${activity.distanceMeters} เมตร", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!isFirst) {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "ขึ้น", modifier = Modifier.size(20.dp))
            }
        }
        if (!isLast) {
            IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "ลง", modifier = Modifier.size(20.dp))
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "ลบ", modifier = Modifier.size(18.dp), tint = Color.Red)
        }
    }
}

@Composable
private fun AddActivityForm(onAdd: (ActivityType, Int) -> Unit) {
    var selectedType by remember { mutableStateOf(ActivityType.RUN) }
    var distanceText by remember { mutableStateOf("") }

    Column {
        Text("เพิ่ม Activity", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = distanceText,
                onValueChange = { distanceText = it.filter { c -> c.isDigit() } },
                label = { Text("ระยะทาง (เมตร)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    val dist = distanceText.toIntOrNull() ?: 0
                    if (dist > 0) {
                        onAdd(selectedType, dist)
                        distanceText = ""
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "เพิ่ม")
                Text("เพิ่ม")
            }
        }
    }
}

@Composable
private fun PaceSection(
    state: IntervalSetupUiState,
    onSetMinPace: (Int) -> Unit,
    onSetMaxPace: (Int) -> Unit,
    onSetPaceAlert: (Boolean) -> Unit,
    onApplyPreset: (Int, Int) -> Unit
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
                Text("ช่วง Pace", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("แจ้งเตือน", fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = state.paceAlertEnabled,
                        onCheckedChange = onSetPaceAlert
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Presets
            Text("Presets:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IntervalConfig.PACE_PRESETS.take(3).forEach { (name, range) ->
                    FilterChip(
                        selected = state.minPaceSecsPerKm == range.first && state.maxPaceSecsPerKm == range.second,
                        onClick = { onApplyPreset(range.first, range.second) },
                        label = { Text(name, fontSize = 12.sp) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IntervalConfig.PACE_PRESETS.drop(3).forEach { (name, range) ->
                    FilterChip(
                        selected = state.minPaceSecsPerKm == range.first && state.maxPaceSecsPerKm == range.second,
                        onClick = { onApplyPreset(range.first, range.second) },
                        label = { Text(name, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Min pace (faster = lower seconds)
            PaceSlider(
                label = "Pace เร็วสุด (เร็วกว่านี้สั่น)",
                secsPerKm = state.minPaceSecsPerKm,
                onValueChange = onSetMinPace
            )
            Spacer(Modifier.height(12.dp))
            PaceSlider(
                label = "Pace ช้าสุด (ช้ากว่านี้สั่น)",
                secsPerKm = state.maxPaceSecsPerKm,
                onValueChange = onSetMaxPace
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "ช่วง Pace: ${IntervalConfig.formatPace(state.minPaceSecsPerKm)} — ${IntervalConfig.formatPace(state.maxPaceSecsPerKm)} min/km",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun PaceSlider(
    label: String,
    secsPerKm: Int,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                IntervalConfig.formatPace(secsPerKm),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = secsPerKm.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 180f..900f,   // 3:00 to 15:00
            steps = 0,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("3:00", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("15:00", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
