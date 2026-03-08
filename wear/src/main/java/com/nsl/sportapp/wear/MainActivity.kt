package com.nsl.sportapp.wear

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.wear.compose.material.MaterialTheme
import com.nsl.sportapp.wear.service.WearWorkoutService
import com.nsl.sportapp.wear.ui.ActiveWearWorkoutScreen
import com.nsl.sportapp.wear.ui.MainWearScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handle */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        )

        setContent {
            MaterialTheme {
                WearApp()
            }
        }
    }
}

@Composable
fun WearApp() {
    val isActive by WearWorkoutService.isActive.collectAsState()

    // Simple navigation: show ActiveWorkout screen when workout is active
    if (isActive) {
        ActiveWearWorkoutScreen()
    } else {
        MainWearScreen(
            onStartWorkout = { /* controlled from phone */ },
            onViewActiveWorkout = { /* already isActive */ }
        )
    }
}
