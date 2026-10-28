package com.example.medicat.data

import com.example.medicat.data.local.DoseDao
import com.example.medicat.data.local.DoseEntity
import com.example.medicat.data.local.MedicationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoseRepository @Inject constructor(private val dao: DoseDao) {

    /** Records a dose of [medication] now; returns its id so it can be undone. */
    suspend fun log(medication: MedicationEntity, at: Long = System.currentTimeMillis()): Long =
        dao.insert(DoseEntity(medicationId = medication.id, name = medication.name, strength = medication.strength, takenAt = at))

    suspend fun delete(id: Long) = dao.delete(id)

    fun recent(limit: Int = 100): Flow<List<DoseEntity>> = dao.observeRecent(limit)
}
