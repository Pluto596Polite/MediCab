package com.example.medicat.data.local

import androidx.room.AutoMigration
import androidx.room.DeleteColumn
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec

@Database(
    entities = [MedicationEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = MediCabDatabase.DropPhotoModels::class)
    ]
)
abstract class MediCabDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao

    /** v5: packs are always drawn from templates, so the photo-model columns go. */
    @DeleteColumn.Entries(
        DeleteColumn(tableName = "medications", columnName = "modelId"),
        DeleteColumn(tableName = "medications", columnName = "modelAspect"),
        DeleteColumn(tableName = "medications", columnName = "modelDepth")
    )
    class DropPhotoModels : AutoMigrationSpec
}
