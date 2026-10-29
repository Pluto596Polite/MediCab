package com.example.medicat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: MedicationEntity): Long

    @Update
    suspend fun update(medication: MedicationEntity)

    @Delete
    suspend fun delete(medication: MedicationEntity)

    @Query("SELECT * FROM medications ORDER BY expiryDate ASC")
    fun getAllMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications ORDER BY expiryDate ASC")
    suspend fun getAll(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: Int): MedicationEntity?

    @Query("SELECT * FROM medications WHERE id = :id")
    fun observeMedicationById(id: Int): Flow<MedicationEntity?>

    /** Most recent entry with this barcode, used to auto-fill details after a scan. */
    @Query("SELECT * FROM medications WHERE barcode = :barcode ORDER BY lastUsed DESC LIMIT 1")
    suspend fun findByBarcode(barcode: String): MedicationEntity?

    @Query("SELECT * FROM medications WHERE isActive = 1 AND remind = 1 AND expiryDate <= :before ORDER BY expiryDate ASC")
    suspend fun getActiveExpiringBefore(before: Long): List<MedicationEntity>

    @Query("DELETE FROM medications")
    suspend fun deleteAll()
}
