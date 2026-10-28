package com.example.medicat.data

import com.example.medicat.data.local.MedicationDao
import com.example.medicat.data.local.MedicationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepository @Inject constructor(
    private val dao: MedicationDao
) {
    fun observeAll(): Flow<List<MedicationEntity>> = dao.getAllMedications()

    suspend fun getById(id: Int): MedicationEntity? = dao.getMedicationById(id)

    fun observeById(id: Int): Flow<MedicationEntity?> = dao.observeMedicationById(id)

    suspend fun findByBarcode(barcode: String): MedicationEntity? = dao.findByBarcode(barcode)

    suspend fun getActiveExpiringBefore(before: Long): List<MedicationEntity> =
        dao.getActiveExpiringBefore(before)

    /** Inserts or updates, stamping [MedicationEntity.lastUsed] with the current time. */
    suspend fun save(medication: MedicationEntity) {
        val stamped = medication.copy(lastUsed = System.currentTimeMillis())
        if (stamped.id == 0) dao.insert(stamped) else dao.update(stamped)
    }

    suspend fun setActive(medication: MedicationEntity, active: Boolean) =
        save(medication.copy(isActive = active))

    suspend fun delete(medication: MedicationEntity) = dao.delete(medication)
}
