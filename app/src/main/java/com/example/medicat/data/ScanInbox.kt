package com.example.medicat.data

import com.example.medicat.domain.LabelFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the label scanner read off one pack. [template] is a PackTemplate name, if the user picked one. */
data class LabelScan(val fields: LabelFields, val barcode: String?, val template: String?)

/** Hands a finished label scan from the scanner to the add/edit form. */
@Singleton
class ScanInbox @Inject constructor() {
    private val _latest = MutableStateFlow<LabelScan?>(null)
    val latest: StateFlow<LabelScan?> = _latest.asStateFlow()

    fun post(scan: LabelScan) {
        _latest.value = scan
    }

    /** Returns the waiting scan, if any, and clears it so it's applied once. */
    fun take(): LabelScan? = _latest.getAndUpdate { null }
}
