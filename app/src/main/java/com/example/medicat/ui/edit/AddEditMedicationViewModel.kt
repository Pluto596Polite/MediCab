package com.example.medicat.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicat.data.MedicationRepository
import com.example.medicat.data.Settings
import com.example.medicat.data.LabelScan
import com.example.medicat.data.ScanInbox
import com.example.medicat.data.SettingsRepository
import com.example.medicat.data.local.MedicationEntity
import com.example.medicat.data.lookup.ProductInfo
import com.example.medicat.data.lookup.ProductLookup
import com.example.medicat.data.lookup.UseCaseLookup
import com.example.medicat.domain.LabelParser
import com.example.medicat.domain.ExpiryDates
import com.example.medicat.domain.Gs1Parser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val expiryDate: Long? = null,
    val barcode: String = "",
    val form: String = "",
    val strength: String = "",
    /** Pack size; 0 means stock isn't tracked. */
    val quantity: Int = 0,
    val location: String = "",
    /** Chosen 3D template name; blank = guess from form and category. */
    val pack: String = "",
    val reminderDays: Int = Settings.DEFAULT_REMINDER_DAYS,
    val showErrors: Boolean = false,
    /** Bumped on every failed save so the UI can replay its error animation. */
    val errorTick: Int = 0,
    val message: String? = null,
    val isDone: Boolean = false,
    /** True while the scanned barcode is being looked up online. */
    val lookingUp: Boolean = false,
    /** True while common uses are being looked up online. */
    val lookingUpUses: Boolean = false,
    /** Where the auto-filled details came from, e.g. "openFDA" or "your cabinet". */
    val filledFrom: String? = null,
    /** Bumped when a scan auto-fills fields, so they can flash. */
    val autoFillTick: Int = 0,
    /** Bumped when the UI should open the expiry picker by itself. */
    val expiryPromptTick: Int = 0
) {
    val nameError get() = showErrors && name.isBlank()
    val expiryError get() = showErrors && expiryDate == null
}

private const val USES_LABEL = "Common uses"

@HiltViewModel
class AddEditMedicationViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val settingsRepository: SettingsRepository,
    private val productLookup: ProductLookup,
    private val scanInbox: ScanInbox,
    private val useCaseLookup: UseCaseLookup,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val medicationId: Int = savedStateHandle.get<Int>("id") ?: -1
    private var original: MedicationEntity? = null

    private val _uiState = MutableStateFlow(EditUiState(isEditing = medicationId > 0, isLoading = medicationId > 0))
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(reminderDays = settings.reminderDays) }
            }
        }
        if (medicationId > 0) {
            viewModelScope.launch {
                val med = repository.getById(medicationId)
                original = med
                _uiState.update {
                    if (med == null) it.copy(isLoading = false, isDone = true)
                    else it.copy(
                        isLoading = false,
                        name = med.name,
                        category = med.category,
                        description = med.description,
                        expiryDate = med.expiryDate,
                        barcode = med.barcode.orEmpty(),
                        form = med.form,
                        strength = med.strength,
                        quantity = med.quantity,
                        location = med.location,
                        pack = med.pack
                    )
                }
            }
        }
        // A finished label scan, whether this form opened the scanner or the scanner opened this form.
        viewModelScope.launch {
            scanInbox.latest.filterNotNull().collect { scanInbox.take()?.let(::onLabelScanned) }
        }
        // Opened straight from the scanner: handle the code as if it had just been scanned here.
        savedStateHandle.get<String>("code")?.takeIf { it.isNotBlank() && medicationId <= 0 }?.let { code ->
            savedStateHandle["code"] = null
            onBarcodeScanned(code)
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }
    fun onCategoryChange(value: String) = _uiState.update { it.copy(category = value) }
    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }
    fun onExpiryChange(value: Long?) = _uiState.update { it.copy(expiryDate = value) }
    fun onBarcodeChange(value: String) = _uiState.update { it.copy(barcode = value) }
    fun onStrengthChange(value: String) = _uiState.update { it.copy(strength = value) }
    fun onQuantityChange(value: Int) = _uiState.update { it.copy(quantity = value.coerceIn(0, 999)) }

    /** Tapping the selected chip again clears it. */
    fun onFormChange(value: String) = _uiState.update { it.copy(form = if (it.form == value) "" else value) }
    /** Picking the current template again goes back to the automatic guess. */
    fun onPackChange(value: String) = _uiState.update { it.copy(pack = if (it.pack == value) "" else value) }
    fun onLocationChange(value: String) = _uiState.update { it.copy(location = if (it.location == value) "" else value) }
    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /**
     * Handles a raw scan. Details come from, in order: an earlier entry with the
     * same barcode, then public product databases. The expiry date comes from a
     * GS1 DataMatrix when present; otherwise the expiry picker opens straight
     * away so the user picks the date while the online lookup runs.
     */
    fun onBarcodeScanned(raw: String, quiet: Boolean = false) {
        val scanned = Gs1Parser.parse(raw)
        viewModelScope.launch {
            val previous = repository.findByBarcode(scanned.key)
            val batch = scanned.batch?.let { "Batch $it" }

            _uiState.update { state ->
                state.copy(
                    barcode = scanned.key,
                    expiryDate = scanned.expiry?.let(ExpiryDates::toMillis) ?: state.expiryDate,
                    name = state.name.ifBlank { previous?.name.orEmpty() },
                    category = state.category.ifBlank { previous?.category.orEmpty() },
                    form = state.form.ifBlank { previous?.form.orEmpty() },
                    strength = state.strength.ifBlank { previous?.strength.orEmpty() },
                    location = state.location.ifBlank { previous?.location.orEmpty() },
                    quantity = if (state.quantity == 0) previous?.quantity ?: 0 else state.quantity,
                    description = state.description.ifBlank { listOfNotNull(previous?.description, batch).joinNotes() },
                    filledFrom = if (previous != null) "your cabinet" else state.filledFrom,
                    autoFillTick = if (previous != null) state.autoFillTick + 1 else state.autoFillTick,
                    expiryPromptTick = if (scanned.expiry == null && state.expiryDate == null) state.expiryPromptTick + 1 else state.expiryPromptTick
                )
            }

            val canLookUp = previous == null &&
                scanned.key.length == 14 && scanned.key.all(Char::isDigit) &&
                settingsRepository.current().onlineLookup
            if (!canLookUp) {
                _uiState.update { it.copy(message = if (quiet) it.message else scanMessage(previous?.name, null, scanned.expiry != null)) }
                return@launch
            }

            _uiState.update { it.copy(lookingUp = true) }
            val info = productLookup.lookup(scanned.key)
            if (info != null) findUses(listOfNotNull(LabelParser.ingredientIn(info.description), info.name), automatic = true)
            _uiState.update { state ->
                if (info == null) {
                    state.copy(lookingUp = false, message = if (quiet) state.message else scanMessage(null, null, scanned.expiry != null))
                } else {
                    state.copy(
                        lookingUp = false,
                        name = state.name.ifBlank { info.name },
                        category = state.category.ifBlank { info.category },
                        description = state.description
                            .takeUnless { it.isBlank() || it == batch }
                            ?: listOfNotNull(info.description, batch).joinNotes(),
                        filledFrom = info.source,
                        autoFillTick = state.autoFillTick + 1,
                        message = if (quiet) state.message else scanMessage(null, info, scanned.expiry != null)
                    )
                }
            }
        }
    }

    /**
     * Fills the form from a label scan: what was read off the pack fills empty
     * fields, the pack type picks the 3D template, and a barcode seen on the way gets
     * the usual cabinet/online lookup for anything still missing.
     */
    fun onLabelScanned(scan: LabelScan) {
        val f = scan.fields
        val notes = listOfNotNull(f.activeIngredient?.let { "Contains $it" }, f.batch?.let { "Batch $it" })
        _uiState.update { state ->
            state.copy(
                name = state.name.ifBlank { f.name.orEmpty() },
                strength = state.strength.ifBlank { f.strength.orEmpty() },
                quantity = if (state.quantity == 0) f.quantity ?: 0 else state.quantity,
                form = state.form.ifBlank { f.form.orEmpty() },
                category = state.category.ifBlank { f.category.orEmpty() },
                expiryDate = state.expiryDate ?: f.expiry?.let(ExpiryDates::toMillis),
                description = state.description.ifBlank { notes.joinNotes() },
                pack = scan.template ?: state.pack,
                filledFrom = "the label",
                autoFillTick = state.autoFillTick + 1
            )
        }
        val found = listOfNotNull(
            f.name?.let { "name" }, f.strength?.let { "strength" }, f.quantity?.let { "pack size" }, f.expiry?.let { "expiry" }
        )
        val read = if (found.isEmpty()) "Couldn't read much off the label" else "Read the ${found.joinToString(", ")} off the label"
        _uiState.update { it.copy(message = read) }

        findUses(listOfNotNull(f.activeIngredient, f.name), automatic = true)
        when {
            scan.barcode != null -> onBarcodeScanned(scan.barcode, quiet = true)
            _uiState.value.expiryDate == null -> _uiState.update { it.copy(expiryPromptTick = it.expiryPromptTick + 1) }
        }
    }

    /** The Notes field's button: look up uses for whatever the form says the medication is. */
    fun findUsesNow() {
        val s = _uiState.value
        findUses(listOfNotNull(LabelParser.ingredientIn("${s.name} ${s.description}"), s.name), automatic = false)
    }

    /**
     * Looks up what the medication is commonly used for and adds it to the notes.
     * Automatic lookups (after a scan) respect the online-lookup setting and
     * never replace uses that are already there; the button always runs.
     */
    private fun findUses(terms: List<String>, automatic: Boolean) {
        if (terms.none { it.isNotBlank() } || _uiState.value.lookingUpUses) return
        viewModelScope.launch {
            if (automatic && (!settingsRepository.current().onlineLookup || USES_LABEL in _uiState.value.description)) return@launch
            _uiState.update { it.copy(lookingUpUses = true) }
            val info = useCaseLookup.lookup(terms)
            _uiState.update { state ->
                if (info == null) {
                    state.copy(lookingUpUses = false, message = if (automatic) state.message else "Couldn't find common uses online")
                } else {
                    val line = "$USES_LABEL (${info.source}): ${info.text}"
                    val kept = state.description.lines().filterNot { it.startsWith(USES_LABEL) }.joinToString("\n").trim()
                    state.copy(
                        lookingUpUses = false,
                        description = if (kept.isBlank()) line else "$kept\n$line",
                        autoFillTick = state.autoFillTick + 1,
                        message = if (automatic) state.message else "Added common uses from ${info.source}"
                    )
                }
            }
        }
    }

    private fun scanMessage(previousName: String?, online: ProductInfo?, expiryFromPack: Boolean): String {
        val found = when {
            previousName != null -> "Filled in from your cabinet"
            online != null -> "Found \"${online.name}\" on ${online.source}"
            else -> "Not found online. Fill it in once and MediCab will remember it"
        }
        return if (expiryFromPack) "$found · expiry read from pack" else found
    }

    private fun List<String>.joinNotes() = filter(String::isNotBlank).joinToString(" · ")

    fun save() {
        val state = _uiState.value
        val expiry = state.expiryDate
        if (state.name.isBlank() || expiry == null) {
            _uiState.update { it.copy(showErrors = true, errorTick = it.errorTick + 1) }
            return
        }
        val entity = (original ?: MedicationEntity(
            name = "",
            description = "",
            category = "",
            expiryDate = expiry,
            barcode = null,
            lastUsed = 0
        )).copy(
            name = state.name.trim(),
            category = state.category.trim(),
            description = state.description.trim(),
            expiryDate = expiry,
            barcode = state.barcode.trim().ifBlank { null },
            form = state.form,
            strength = state.strength.trim(),
            quantity = state.quantity,
            remaining = remainingAfterEdit(original, state.quantity),
            location = state.location,
            pack = state.pack
        )
        viewModelScope.launch {
            repository.save(entity)
            _uiState.update { it.copy(isDone = true) }
        }
    }

    /** A new pack starts full; an edited one keeps its count, capped to the new pack size. */
    private fun remainingAfterEdit(before: MedicationEntity?, quantity: Int): Int = when {
        before == null || before.quantity == 0 -> quantity
        quantity == 0 -> before.remaining
        before.remaining == before.quantity -> quantity
        else -> before.remaining.coerceAtMost(quantity)
    }
}
