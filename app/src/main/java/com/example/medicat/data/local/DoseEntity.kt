package com.example.medicat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One dose the user logged. The medication's name and strength are copied in
 * so the history still reads correctly after the medication is deleted.
 */
@Entity(tableName = "doses", indices = [Index("takenAt"), Index("medicationId")])
data class DoseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Int,
    val name: String,
    val strength: String,
    /** Epoch millis. */
    val takenAt: Long
)
