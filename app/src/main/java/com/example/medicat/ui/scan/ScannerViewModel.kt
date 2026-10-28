package com.example.medicat.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicat.data.LabelScan
import com.example.medicat.data.MedicationRepository
import com.example.medicat.data.ScanInbox
import com.example.medicat.domain.LabelAccumulator
import com.example.medicat.domain.LabelFields
import com.example.medicat.domain.LabelParser
import com.example.medicat.domain.OcrLine
import com.example.medicat.ui.pack.PackTemplate
import com.google.mlkit.vision.text.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LabelScanState(
    val fields: LabelFields = LabelFields(),
    val barcode: String? = null,
    /** The 3D template the user said the pack is; null until they choose. */
    val template: PackTemplate? = null
) {
    val hasAnything get() = !fields.isEmpty || barcode != null
}

/**
 * Reads the pack's label from live camera frames. The values it finds, plus
 * the pack type the user picks, fill in the medication and its 3D template.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val inbox: ScanInbox
) : ViewModel() {

    private val accumulator = LabelAccumulator()
    private var knownNames: List<String> = emptyList()

    private val _state = MutableStateFlow(LabelScanState())
    val state: StateFlow<LabelScanState> = _state.asStateFlow()

    init {
        // Packs already in the cabinet are the best guess for the name.
        viewModelScope.launch { knownNames = repository.observeAll().first().map { it.name } }
    }

    /** Text the live camera analyzer read from one frame. */
    fun onFrameText(lines: List<OcrLine>) {
        if (lines.isEmpty()) return
        val fields = LabelParser.parse(lines, knownNames)
        if (fields.isEmpty) return
        accumulator.add(fields)
        _state.update { it.copy(fields = accumulator.current()) }
    }

    fun onBarcode(raw: String) {
        if (_state.value.barcode != raw) _state.update { it.copy(barcode = raw) }
    }

    fun chooseTemplate(template: PackTemplate?) = _state.update { it.copy(template = template) }

    /** Starts over: forgets everything read so far. */
    fun reset() {
        accumulator.clear()
        _state.update { LabelScanState(template = it.template) }
    }

    /** Sends what was read to the add form. */
    fun finish() {
        val s = _state.value
        inbox.post(LabelScan(fields = s.fields, barcode = s.barcode, template = s.template?.name))
    }
}

fun Text.toOcrLines(): List<OcrLine> = textBlocks.flatMap { block ->
    block.lines.map { line -> OcrLine(line.text, line.boundingBox?.height()?.toFloat() ?: 0f) }
}
