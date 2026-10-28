package com.example.medicat.ui.components

import com.example.medicat.data.local.MedicationEntity

/** Dosage forms offered in the add form. */
val MedicationForms = listOf("Tablet", "Capsule", "Liquid", "Cream", "Drops")

/** Where things are usually kept. */
val StorageLocations = listOf("Bathroom", "Kitchen", "Bedroom", "First aid kit")

/** The word used when counting stock of [form], e.g. "16 tablets". */
fun stockUnit(form: String, count: Int): String = when (form.trim().lowercase()) {
    "tablet" -> if (count == 1) "tablet" else "tablets"
    "capsule" -> if (count == 1) "capsule" else "capsules"
    "liquid", "drops" -> "ml"
    "cream" -> "g"
    else -> "left"
}

/** Short subtitle such as "200 mg · 16 left", falling back to the category. */
fun MedicationEntity.doseLine(): String {
    val stock = when {
        quantity > 0 && remaining > 0 -> "$remaining left"
        quantity > 0 -> "none left"
        else -> null
    }
    return listOfNotNull(strength.takeIf { it.isNotBlank() }, stock)
        .ifEmpty { listOf(category.ifBlank { form.ifBlank { "Medication" } }) }
        .joinToString(" · ")
}
