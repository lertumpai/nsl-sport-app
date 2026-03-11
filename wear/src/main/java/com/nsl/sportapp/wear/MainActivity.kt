package com.nsl.sportapp.wear

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.wear.compose.material.MaterialTheme
import com.nsl.sportapp.data.model.IntervalConfig
import com.nsl.sportapp.wear.data.db.entity.WearWorkoutEntity
import com.nsl.sportapp.wear.data.repository.WearWorkoutRepository
import com.nsl.sportapp.wear.service.WearWorkoutService
import com.nsl.sportapp.wear.ui.ActiveWearWorkoutScreen
import com.nsl.sportapp.wear.ui.MainWearScreen
import com.nsl.sportapp.wear.ui.WearHistoryScreen
import com.nsl.sportapp.wear.ui.WearIntervalSetupScreen
import com.nsl.sportapp.wear.ui.WearPaceSetupScreen
import com.nsl.sportapp.wear.ui.WearProgramListScreen
import com.nsl.sportapp.wear.ui.WearWorkoutDetailScreen
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handle */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                WearApp()
            }
        }

        // Request permissions after UI is shown to avoid blocking first render
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

private enum class WearScreen { MAIN, PACE_SETUP, ACTIVE, HISTORY, HISTORY_DETAIL, PROGRAMS, INTERVAL_SETUP }

@Composable
fun WearApp() {
    val state by WearWorkoutService.state.collectAsState()
    var screen by remember { mutableStateOf(WearScreen.MAIN) }
    var selectedWorkout by remember { mutableStateOf<WearWorkoutEntity?>(null) }
    val context = LocalContext.current
    val repository = remember { WearWorkoutRepository(context) }
    val scope = rememberCoroutineScope()

    // Always show active screen while workout is running/paused
    if (state.isRunning || state.isPaused) {
        ActiveWearWorkoutScreen(onWorkoutStopped = { screen = WearScreen.MAIN })
        return
    }

    when (screen) {
        WearScreen.MAIN -> MainWearScreen(
            onStartWorkout = { screen = WearScreen.PACE_SETUP },
            onHistory = { screen = WearScreen.HISTORY },
            onPrograms = { screen = WearScreen.PROGRAMS }
        )

        WearScreen.PACE_SETUP -> WearPaceSetupScreen(
            onStarted = { screen = WearScreen.ACTIVE },
            onBack = { screen = WearScreen.MAIN }
        )

        WearScreen.ACTIVE -> ActiveWearWorkoutScreen(onWorkoutStopped = { screen = WearScreen.MAIN })

        WearScreen.HISTORY -> WearHistoryScreen(
            onBack = { screen = WearScreen.MAIN },
            onWorkoutClick = { workout ->
                selectedWorkout = workout
                screen = WearScreen.HISTORY_DETAIL
            }
        )

        WearScreen.HISTORY_DETAIL -> {
            val workout = selectedWorkout
            if (workout != null) {
                WearWorkoutDetailScreen(workout = workout, onBack = { screen = WearScreen.HISTORY })
            } else {
                screen = WearScreen.HISTORY
            }
        }

        WearScreen.PROGRAMS -> WearProgramListScreen(
            repository = repository,
            onStartProgram = { config -> startProgramWorkout(context, config) ; screen = WearScreen.ACTIVE },
            onCreateNew = { screen = WearScreen.INTERVAL_SETUP },
            onBack = { screen = WearScreen.MAIN }
        )

        WearScreen.INTERVAL_SETUP -> WearIntervalSetupScreen(
            onStart = { config -> startProgramWorkout(context, config) ; screen = WearScreen.ACTIVE },
            onSaveProgram = { name, config ->
                val json = Json.encodeToString(config)
                scope.launch {
                    repository.saveProgram(name, json)
                }
            },
            onBack = { screen = WearScreen.PROGRAMS }
        )
    }
}

private fun startProgramWorkout(context: android.content.Context, config: IntervalConfig) {
    val intent = Intent(context, WearWorkoutService::class.java).apply {
        action = WearWorkoutService.ACTION_START
        putExtra(WearWorkoutService.EXTRA_MIN_PACE, config.minPaceSecsPerKm)
        putExtra(WearWorkoutService.EXTRA_MAX_PACE, config.maxPaceSecsPerKm)
        putExtra(WearWorkoutService.EXTRA_INTERVAL_CONFIG, Json.encodeToString(config))
    }
    context.startForegroundService(intent)
}
