package com.example.medicat.data.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.example.medicat.data.local.DoseDao
import com.example.medicat.data.local.MediCabDatabase
import com.example.medicat.data.local.MedicationDao
import com.example.medicat.data.local.MedicationEntity
import com.example.medicat.domain.ExpiryDates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** How an import went, for the message shown afterwards. */
data class ImportResult(val added: Int, val alreadyThere: Int, val skippedRows: Int, val doses: Int) {
    /** e.g. "Added 12 medications · 2 already in your cabinet · 1 row skipped (no name or expiry date)". */
    fun summary(): String = listOfNotNull(
        if (added == 0) "Nothing new to add" else "Added $added ${plural(added, "medication")}",
        alreadyThere.takeIf { it > 0 }?.let { "$it already in your cabinet" },
        doses.takeIf { it > 0 }?.let { "$it ${plural(it, "dose")} of history" },
        skippedRows.takeIf { it > 0 }?.let { "$it ${plural(it, "row")} skipped (no name or expiry date)" }
    ).joinToString(" · ")

    private fun plural(n: Int, word: String) = if (n == 1) word else "${word}s"
}

/**
 * Exports the cabinet to a file the user picked and imports files back into
 * it. Imports only add: a medication already in the cabinet (same name,
 * strength and expiry date) is left as it is, so importing a backup twice is
 * harmless.
 */
@Singleton
class CabinetTransfer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MediCabDatabase,
    private val medicationDao: MedicationDao,
    private val doseDao: DoseDao
) {

    /** Writes the whole cabinet to [uri]; returns how many medications were written. */
    suspend fun export(format: ExportFormat, uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = CabinetData(medicationDao.getAll(), doseDao.getAll())
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file")
        out.use { stream ->
            when (format) {
                ExportFormat.EXCEL -> stream.write(CabinetFiles.xlsx(data))
                ExportFormat.CSV -> stream.write(CabinetFiles.csv(data))
                ExportFormat.JSON -> stream.write(CabinetFiles.json(data))
                ExportFormat.PDF -> CabinetPdf.write(data, stream)
            }
        }
        data.medications.size
    }

    /** Reads [uri] and adds what's new to the cabinet. Throws [ImportException] for unreadable files. */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val bytes = resolver.openInputStream(uri)?.use(::readLimited) ?: throw ImportException("Couldn't open that file.")
        val file = CabinetFiles.read(bytes, name)
        if (file.data.medications.isEmpty()) {
            throw ImportException(
                if (file.skippedRows > 0) "None of the ${file.skippedRows} rows had both a name and an expiry date."
                else "That file has no medications in it."
            )
        }
        database.withTransaction { merge(file) }
    }

    private suspend fun merge(file: ImportedFile): ImportResult {
        val existing = medicationDao.getAll().associateByTo(mutableMapOf(), ::sameMedication)
        // The file's medication ids, mapped to ids in this cabinet, so doses stay linked.
        val ids = mutableMapOf<Int, Int>()
        var added = 0
        var alreadyThere = 0
        file.data.medications.forEach { med ->
            val key = sameMedication(med)
            val match = existing[key]
            if (match != null) {
                alreadyThere++
                ids[med.id] = match.id
            } else {
                val stored = med.copy(id = 0, lastUsed = med.lastUsed.takeIf { it > 0 } ?: System.currentTimeMillis())
                val newId = medicationDao.insert(stored).toInt()
                existing[key] = stored.copy(id = newId)
                ids[med.id] = newId
                added++
            }
        }
        val loggedDoses = doseDao.getAll().mapTo(HashSet()) { it.name to it.takenAt }
        var doses = 0
        file.data.doses.forEach { dose ->
            if (loggedDoses.add(dose.name to dose.takenAt)) {
                doseDao.insert(dose.copy(id = 0, medicationId = ids[dose.medicationId] ?: 0))
                doses++
            }
        }
        return ImportResult(added, alreadyThere, file.skippedRows, doses)
    }

    private fun sameMedication(med: MedicationEntity): Triple<String, String, LocalDate> =
        Triple(med.name.trim().lowercase(), med.strength.trim().lowercase(), ExpiryDates.toLocalDate(med.expiryDate))

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_FILE_BYTES) throw ImportException("That file is too large to import (over 20 MB).")
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
    }
}
