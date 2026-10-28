package com.example.medicat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseDao {

    @Insert
    suspend fun insert(dose: DoseEntity): Long

    @Query("DELETE FROM doses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM doses ORDER BY takenAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DoseEntity>>
}
