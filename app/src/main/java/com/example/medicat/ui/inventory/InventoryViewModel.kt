package com.example.medicat.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicat.data.MedicationRepository
import com.example.medicat.data.SettingsRepository
import com.example.medicat.data.local.MedicationEntity
import com.example.medicat.domain.ExpiryDates
import com.example.medicat.domain.ExpiryStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class InventoryFilter(val label: String) {
    ALL("All"),
    EXPIRING_SOON("Expiring"),
    EXPIRED("Expired"),
    USED_UP("Used up")
}

data class MedicationItem(
    val medication: MedicationEntity,
    val status: ExpiryStatus,
    val daysUntilExpiry: Long
) {
    val categoryLabel: String get() = medication.category.trim().ifBlank { "Other" }
}

data class InventoryUiState(
    val items: List<MedicationItem> = emptyList(),
    val safeCount: Int = 0,
    val expiringCount: Int = 0,
    val expiredCount: Int = 0,
    val filter: InventoryFilter = InventoryFilter.ALL,
    /** Category chip, applied on top of [filter]; null shows every category. */
    val category: String? = null,
    /** Categories of the medication in the cabinet, for the filter chips. */
    val categories: List<String> = emptyList(),
    /** Active, in-date items, soonest first; used by the alerts screen. */
    val active: List<MedicationItem> = emptyList(),
    val query: String = "",
    val reminderDays: Int = 30,
    /** For the greeting; blank when the user hasn't set it. */
    val profileName: String = "",
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: MedicationRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val filter = MutableStateFlow(InventoryFilter.ALL)
    private val category = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<InventoryUiState> = combine(
        repository.observeAll(),
        settingsRepository.settings,
        filter,
        category,
        query
    ) { medications, settings, filter, category, query ->
        val today = LocalDate.now()
        val (active, inactive) = medications.partition { it.isActive }
        fun toItem(med: MedicationEntity) = MedicationItem(
            medication = med,
            status = ExpiryDates.statusOf(med.expiryDate, settings.reminderDays, today),
            daysUntilExpiry = ExpiryDates.daysUntil(med.expiryDate, today)
        )
        val activeItems = active.map(::toItem)

        val visible = when (filter) {
            InventoryFilter.ALL -> activeItems
            InventoryFilter.EXPIRING_SOON -> activeItems.filter { it.status == ExpiryStatus.EXPIRING_SOON }
            InventoryFilter.EXPIRED -> activeItems.filter { it.status == ExpiryStatus.EXPIRED }
            InventoryFilter.USED_UP -> inactive.map(::toItem)
        }.filter { it.matches(query) && (category == null || it.categoryLabel == category) }

        InventoryUiState(
            items = visible,
            safeCount = activeItems.count { it.status == ExpiryStatus.SAFE },
            expiringCount = activeItems.count { it.status == ExpiryStatus.EXPIRING_SOON },
            expiredCount = activeItems.count { it.status == ExpiryStatus.EXPIRED },
            filter = filter,
            category = category,
            categories = activeItems.map { it.categoryLabel }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER),
            active = activeItems,
            query = query,
            reminderDays = settings.reminderDays,
            profileName = settings.profileName,
            isLoading = false,
            isEmpty = medications.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    fun setFilter(value: InventoryFilter) {
        filter.value = value
        category.value = null
    }

    /** Shows one category of the cabinet; picking it again clears it. */
    fun setCategory(value: String?) {
        filter.value = InventoryFilter.ALL
        category.value = value?.takeUnless { it == category.value }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setActive(medication: MedicationEntity, active: Boolean) {
        viewModelScope.launch { repository.setActive(medication, active) }
    }

    private fun MedicationItem.matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return medication.name.contains(q, ignoreCase = true) ||
            medication.category.contains(q, ignoreCase = true) ||
            medication.description.contains(q, ignoreCase = true)
    }
}
