package com.nsl.sportapp.ui.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nsl.sportapp.data.db.entity.TrainingProgramEntity
import com.nsl.sportapp.data.model.IntervalConfig
import com.nsl.sportapp.data.repository.TrainingProgramRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrainingProgramsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = TrainingProgramRepository(app)

    val programs: StateFlow<List<TrainingProgramEntity>> = repository.allPrograms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveProgram(name: String, config: IntervalConfig) {
        viewModelScope.launch { repository.saveProgram(name, config) }
    }

    fun deleteProgram(id: Long) {
        viewModelScope.launch { repository.deleteProgram(id) }
    }

    fun parseConfig(entity: TrainingProgramEntity): IntervalConfig? =
        repository.parseConfig(entity)
}
