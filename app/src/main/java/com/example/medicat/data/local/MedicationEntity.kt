package com.example.medicat.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a medication entry in the local database.
 */
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String,
    val category: String,
    val expiryDate: Long,
    val barcode: String?,
    val lastUsed: Long,
    val isActive: Boolean = true,
    /** Tablet, Capsule, Liquid, Cream or Drops; blank when unknown. */
    @ColumnInfo(defaultValue = "")
    val form: String = "",
    /** Free text such as "200 mg". */
    @ColumnInfo(defaultValue = "")
    val strength: String = "",
    /** Pack size; 0 means stock isn't tracked. */
    @ColumnInfo(defaultValue = "0")
    val quantity: Int = 0,
    /** How many are left in the pack. */
    @ColumnInfo(defaultValue = "0")
    val remaining: Int = 0,
    /** Where it's kept, e.g. "Bathroom". */
    @ColumnInfo(defaultValue = "")
    val location: String = "",
    /** Whether this item is included in expiry reminders. */
    @ColumnInfo(defaultValue = "1")
    val remind: Boolean = true,
    /** 3D template (PackTemplate name), blank = guess from form and category. */
    @ColumnInfo(defaultValue = "")
    val pack: String = ""
)
