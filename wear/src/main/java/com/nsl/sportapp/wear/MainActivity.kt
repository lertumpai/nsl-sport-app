package com.nsl.sportapp.wear

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material.MaterialTheme
import com.nsl.sportapp.wear.service.WearWorkoutService
import com.nsl.sportapp.wear.ui.ActiveWearWorkoutScreen
import com.nsl.sportapp.wear.ui.MainWearScreen
import com.nsl.sportapp.wear.ui.WearHistoryScreen
import com.nsl.sportapp.wear.ui.WearPaceSetupScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handle */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            MaterialTheme {
                WearApp()
            }
        }
    }
}

private enum class WearScreen { MAIN, PACE_SETUP, ACTIVE, HISTORY }

@Composable
fun WearApp() {
    val state by WearWorkoutService.state.collectAsState()
    var screen by remember { mutableStateOf(WearScreen.MAIN) }

    // Always show active screen while workout is running/paused
    if (state.isRunning || state.isPaused) {
        ActiveWearWorkoutScreen(onWorkoutStopped = { screen = WearScreen.MAIN })
        return
    }

    when (screen) {
        WearScreen.MAIN -> MainWearScreen(
            onStartWorkout = { screen = WearScreen.PACE_SETUP },
            onHistory = { screen = WearScreen.HISTORY }
        )
        WearScreen.PACE_SETUP -> WearPaceSetupScreen(
            onStarted = { screen = WearScreen.ACTIVE },
            onBack = { screen = WearScreen.MAIN }
        )
        WearScreen.ACTIVE -> ActiveWearWorkoutScreen(onWorkoutStopped = { screen = WearScreen.MAIN })
        WearScreen.HISTORY -> WearHistoryScreen(onBack = { screen = WearScreen.MAIN })
    }
}
