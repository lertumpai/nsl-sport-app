package com.nsl.sportapp.ui.interval

import androidx.lifecycle.ViewModel
import com.nsl.sportapp.data.model.ActivityType
import com.nsl.sportapp.data.model.IntervalActivity
import com.nsl.sportapp.data.model.IntervalConfig
import com.nsl.sportapp.data.model.IntervalMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class IntervalSetupViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(IntervalSetupUiState())
    val uiState: StateFlow<IntervalSetupUiState> = _uiState.asStateFlow()

    // ── Mode ──────────────────────────────────────────────────────────────

    fun setMode(mode: IntervalMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    // ── Activity-based ────────────────────────────────────────────────────

    fun addActivity(type: ActivityType, distanceMeters: Int) {
        if (distanceMeters <= 0) return
        _uiState.update {
            it.copy(activities = it.activities + IntervalActivity(type, distanceMeters))
        }
    }

    fun removeActivity(index: Int) {
        _uiState.update {
            val list = it.activities.toMutableList()
            if (index in list.indices) list.removeAt(index)
            it.copy(activities = list)
        }
    }

    fun moveActivityUp(index: Int) {
        _uiState.update {
            val list = it.activities.toMutableList()
            if (index > 0 && index < list.size) {
                val tmp = list[index]
                list[index] = list[index - 1]
                list[index - 1] = tmp
            }
            it.copy(activities = list)
        }
    }

    fun moveActivityDown(index: Int) {
        _uiState.update {
            val list = it.activities.toMutableList()
            if (index >= 0 && index < list.size - 1) {
                val tmp = list[index]
                list[index] = list[index + 1]
                list[index + 1] = tmp
            }
            it.copy(activities = list)
        }
    }

    fun setRepetitions(reps: Int) {
        _uiState.update { it.copy(repetitions = reps.coerceIn(1, 100)) }
    }

    // ── Pace ──────────────────────────────────────────────────────────────

    fun setMinPace(secsPerKm: Int) {
        _uiState.update { it.copy(minPaceSecsPerKm = secsPerKm.coerceIn(60, 1800)) }
    }

    fun setMaxPace(secsPerKm: Int) {
        _uiState.update { it.copy(maxPaceSecsPerKm = secsPerKm.coerceIn(60, 1800)) }
    }

    fun setPaceAlert(enabled: Boolean) {
        _uiState.update { it.copy(paceAlertEnabled = enabled) }
    }

    fun applyPreset(minSecs: Int, maxSecs: Int) {
        _uiState.update {
            it.copy(minPaceSecsPerKm = minSecs, maxPaceSecsPerKm = maxSecs)
        }
    }

    // ── Build Config ─────────────────────────────────────────────────────

    fun buildIntervalConfig(): IntervalConfig? {
        val state = _uiState.value
        return when (state.mode) {
            IntervalMode.ACTIVITY_BASED -> {
                if (state.activities.isEmpty()) return null
                IntervalConfig(
                    mode = IntervalMode.ACTIVITY_BASED,
                    activities = state.activities,
                    repetitions = state.repetitions,
                    minPaceSecsPerKm = state.minPaceSecsPerKm,
                    maxPaceSecsPerKm = state.maxPaceSecsPerKm,
                    paceAlertEnabled = state.paceAlertEnabled
                )
            }
            IntervalMode.PACE_CONTROLLED -> {
                IntervalConfig(
                    mode = IntervalMode.PACE_CONTROLLED,
                    activities = emptyList(),
                    repetitions = 1,
                    minPaceSecsPerKm = state.minPaceSecsPerKm,
                    maxPaceSecsPerKm = state.maxPaceSecsPerKm,
                    paceAlertEnabled = true
                )
            }
        }
    }
}

data class IntervalSetupUiState(
    val mode: IntervalMode = IntervalMode.ACTIVITY_BASED,
    // Activity-based
    val activities: List<IntervalActivity> = emptyList(),
    val repetitions: Int = 3,
    // Pace
    val minPaceSecsPerKm: Int = 360,   // 6:00
    val maxPaceSecsPerKm: Int = 480,   // 8:00
    val paceAlertEnabled: Boolean = true
)
