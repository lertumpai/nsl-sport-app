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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutSegmentEntity
import com.nsl.sportapp.wear.data.repository.WearWorkoutRepository

@Composable
fun WearWorkoutDetailScreen(workout: WearWorkoutEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    var segments by remember { mutableStateOf<List<WearWorkoutSegmentEntity>>(emptyList()) }

    // Load GPS segments to compute per-km splits
    LaunchedEffect(workout.id) {
        segments = WearWorkoutRepository(context).getSegments(workout.id)
    }

    // Compute per-km splits from stored GPS segments
    val kmSplits = remember(segments) { computeKmSplits(segments, workout.durationMillis) }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────
            item {
                Text(
                    workout.formattedDate(),
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            // ── Primary stats: Distance + Duration ──────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailStatBlock(
                        value = workout.formattedDistance(),
                        label = "ระยะทาง",
                        valueColor = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    )
                    DetailStatBlock(
                        value = workout.formattedDuration(),
                        label = "เวลา",
                        valueColor = Color.White
                    )
                }
            }

            // ── Pace stats ───────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailStatBlock(
                        value = formatPace(workout.avgPaceSecsPerKm),
                        label = "Pace เฉลี่ย",
                        valueColor = Color(0xFF4CAF50)
                    )
                    if (workout.avgHeartRate > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )
                        DetailStatBlock(
                            value = "${workout.avgHeartRate}",
                            label = "❤ เฉลี่ย bpm",
                            valueColor = Color(0xFFFF5252)
                        )
                    }
                    if (workout.maxHeartRate > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )
                        DetailStatBlock(
                            value = "${workout.maxHeartRate}",
                            label = "❤ สูงสุด bpm",
                            valueColor = Color(0xFFFF8A80)
                        )
                    }
                }
            }

            // ── Per-km splits ────────────────────────────────────────────
            if (kmSplits.isNotEmpty()) {
                item {
                    Text(
                        "แบ่งตาม km",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                    )
                }

                kmSplits.forEachIndexed { _, split ->
                    item(key = "split_${split.km}") {
                        KmSplitRow(split)
                    }
                }

                // Average of all km splits
                val avgSplitPace = kmSplits.map { it.pace }.average().toFloat()
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("เฉลี่ย", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                        Text(
                            "${formatPace(avgSplitPace)}/km",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // ── Sync status ──────────────────────────────────────────────
            item {
                Text(
                    if (workout.synced) "✓ ซิงค์แล้ว" else "⏳ ยังไม่ได้ซิงค์",
                    fontSize = 9.sp,
                    color = if (workout.synced) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
            }

            // ── Back button ──────────────────────────────────────────────
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

// ─── Per-km split row ─────────────────────────────────────────────────────────

private data class KmSplitData(val km: Int, val pace: Float, val timeMillis: Long)

/** Derive per-km splits from stored GPS segments (same algorithm as the live service). */
private fun computeKmSplits(
    segments: List<WearWorkoutSegmentEntity>,
    totalDurationMillis: Long
): List<KmSplitData> {
    if (segments.isEmpty()) return emptyList()
    val splits = mutableListOf<KmSplitData>()

    // Reconstruct elapsed time per segment using timestamps
    val startTs = segments.first().timestamp
    var lastSplitKm = 0
    var lastSplitTs = startTs
    var lastSplitDist = 0f

    for (seg in segments) {
        val totalDist = seg.distanceFromStartMeters
        val completedKm = (totalDist / 1000).toInt()
        if (completedKm > lastSplitKm) {
            val splitMs = seg.timestamp - lastSplitTs
            val splitDist = totalDist - lastSplitDist
            val splitPace = if (splitDist > 0 && splitMs > 0)
                (splitMs / 1000f) / (splitDist / 1000f) else 0f
            splits.add(KmSplitData(km = completedKm, pace = splitPace, timeMillis = seg.timestamp - startTs))
            lastSplitKm = completedKm
            lastSplitTs = seg.timestamp
            lastSplitDist = totalDist
        }
    }
    return splits
}

@Composable
private fun KmSplitRow(split: KmSplitData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${split.km} km",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.width(36.dp)
        )
        // Pace bar (visual) — scaled 3:00–12:00/km range
        val paceNorm = ((split.pace - 180) / (720 - 180)).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .padding(horizontal = 6.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(paceNorm)
                    .height(4.dp)
                    .background(Color(0xFF4CAF50), RoundedCornerShape(2.dp))
            )
        }
        Text(
            "${formatPace(split.pace)}/km",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp)
        )
    }
}

@Composable
private fun DetailStatBlock(value: String, label: String, valueColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontSize = 8.sp, color = Color.White.copy(alpha = 0.55f))
    }
}
