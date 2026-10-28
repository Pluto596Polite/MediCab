package com.example.medicat.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicat.data.MedicationRepository
import com.example.medicat.data.Settings
import com.example.medicat.data.SettingsRepository
import com.example.medicat.data.local.MedicationEntity
import com.example.medicat.domain.ExpiryDates
import com.example.medicat.domain.ExpiryStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DetailUiState(
    val medication: MedicationEntity? = null,
    val status: ExpiryStatus = ExpiryStatus.SAFE,
    val daysUntilExpiry: Long = 0,
    val reminderDays: Int = Settings.DEFAULT_REMINDER_DAYS,
    val isLoading: Boolean = true
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MedicationRepository,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id: Int = savedStateHandle.get<Int>("id") ?: -1

    private val _gone = MutableStateFlow(false)
    /** True once the item was deleted, so the screen can close. */
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    val uiState: StateFlow<DetailUiState> = combine(
        repository.observeById(id),
        settingsRepository.settings
    ) { med, settings ->
        val today = LocalDate.now()
        DetailUiState(
            medication = med,
            status = med?.let { ExpiryDates.statusOf(it.expiryDate, settings.reminderDays, today) } ?: ExpiryStatus.SAFE,
            daysUntilExpiry = med?.let { ExpiryDates.daysUntil(it.expiryDate, today) } ?: 0,
            reminderDays = settings.reminderDays,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private fun update(transform: (MedicationEntity) -> MedicationEntity) {
        val med = uiState.value.medication ?: return
        viewModelScope.launch { repository.save(transform(med)) }
    }

    /** Takes one dose out of the pack. */
    fun logDose() = update { it.copy(remaining = (it.remaining - 1).coerceAtLeast(0)) }

    fun addOne() = update {
        val cap = if (it.quantity > 0) it.quantity else Int.MAX_VALUE
        it.copy(remaining = (it.remaining + 1).coerceAtMost(cap))
    }

    fun setRemind(remind: Boolean) = update { it.copy(remind = remind) }

    fun setActive(active: Boolean) = update { it.copy(isActive = active) }

    fun delete() {
        val med = uiState.value.medication ?: return
        viewModelScope.launch {
            repository.delete(med)
            _gone.value = true
        }
    }
}
