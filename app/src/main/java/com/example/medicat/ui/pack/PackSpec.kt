package com.example.medicat.ui.pack

import androidx.compose.ui.graphics.Color
import com.example.medicat.data.local.MedicationEntity
import com.example.medicat.domain.ExpiryDates
import com.example.medicat.domain.ExpiryStatus
import com.example.medicat.ui.components.MedArtKind
import com.example.medicat.ui.components.categoryColor
import com.example.medicat.ui.components.medArtFor
import com.example.medicat.ui.inventory.MedicationItem

/** How a template is built: a carton-like box or a round container. */
enum class PackFamily { BOX, CYLINDER }

/**
 * The predefined 3D objects a medication can be, printed with its details.
 * Proportions: [aspect] = width/height of the front (the body, for round
 * packs), [depth] = depth/height (boxes only).
 */
enum class PackTemplate(
    val label: String,
    val family: PackFamily,
    val aspect: Float,
    val depth: Float = 0f,
    /** Round packs: share of the total height taken by the body; the rest is lid/neck. */
    val bodyShare: Float = 1f,
    /** Round packs: lid radius relative to the body. */
    val lidRadius: Float = 1f,
    /** Syrup bottles: a narrow neck between body and cap. */
    val neck: Boolean = false
) {
    CARTON("Pill box", PackFamily.BOX, aspect = 0.72f, depth = 0.32f),
    BLISTER("Blister pack", PackFamily.BOX, aspect = 1.55f, depth = 0.14f),
    CREAM_TUBE("Cream tube", PackFamily.BOX, aspect = 0.36f, depth = 0.16f),
    PILL_BOTTLE("Pill bottle", PackFamily.CYLINDER, aspect = 0.72f, bodyShare = 0.78f, lidRadius = 0.94f),
    FIZZ_TUBE("Fizz tube", PackFamily.CYLINDER, aspect = 0.34f, bodyShare = 0.88f, lidRadius = 1.08f),
    SYRUP("Syrup bottle", PackFamily.CYLINDER, aspect = 0.62f, bodyShare = 0.74f, lidRadius = 0.46f, neck = true),
    JAR("Jar", PackFamily.CYLINDER, aspect = 1.15f, bodyShare = 0.74f, lidRadius = 1.05f);

    /** The matching flat illustration, so cards and 3D models agree. */
    val art: MedArtKind
        get() = when (this) {
            CARTON -> MedArtKind.BOX
            BLISTER -> MedArtKind.BLISTER
            CREAM_TUBE -> MedArtKind.TUBE
            PILL_BOTTLE -> MedArtKind.BOTTLE
            FIZZ_TUBE -> MedArtKind.FIZZ
            SYRUP -> MedArtKind.SYRUP
            JAR -> MedArtKind.JAR
        }

    companion object {
        /** The stored choice if there is one, otherwise a guess from form and category. */
        fun of(pack: String, form: String, category: String): PackTemplate =
            entries.firstOrNull { it.name.equals(pack, ignoreCase = true) } ?: guess(form, category)

        fun guess(form: String, category: String): PackTemplate = when (medArtFor(form, category)) {
            MedArtKind.BOTTLE, MedArtKind.CAPSULE -> PILL_BOTTLE
            MedArtKind.SYRUP -> SYRUP
            MedArtKind.JAR -> JAR
            MedArtKind.TUBE -> CREAM_TUBE
            MedArtKind.BLISTER -> BLISTER
            MedArtKind.FIZZ -> FIZZ_TUBE
            MedArtKind.BOX -> CARTON
        }
    }
}

/** Everything a 3D pack shows: its template and the details printed on it. */
data class PackSpec(
    val template: PackTemplate,
    val name: String,
    /** e.g. "200 mg · Tablet". */
    val detail: String,
    val category: String,
    val color: Color,
    /** e.g. "EXP 31 MAR 2027"; null when unknown. */
    val expiry: String?,
    val status: ExpiryStatus?,
    /** Short status line such as "In 12 days". */
    val statusText: String?,
    val barcode: String?
)

fun packSpec(
    pack: String,
    name: String,
    strength: String,
    form: String,
    category: String,
    expiryMillis: Long?,
    reminderDays: Int = 30,
    barcode: String? = null
): PackSpec {
    val days = expiryMillis?.let(ExpiryDates::daysUntil)
    return PackSpec(
        template = PackTemplate.of(pack, form, category),
        name = name.ifBlank { "Medication" },
        detail = listOf(strength, form).filter { it.isNotBlank() }.joinToString(" · "),
        category = category.ifBlank { "Medication" },
        color = categoryColor(category),
        expiry = expiryMillis?.let { "EXP " + ExpiryDates.format(it).uppercase() },
        status = expiryMillis?.let { ExpiryDates.statusOf(it, reminderDays) },
        statusText = days?.let { if (it < 0) "Expired" else ExpiryDates.relative(it).replaceFirstChar(Char::uppercase) },
        barcode = barcode
    )
}

fun MedicationEntity.packSpec(reminderDays: Int = 30) = packSpec(
    pack, name, strength, form, category, expiryDate, reminderDays, barcode
)

fun MedicationItem.packSpec(): PackSpec {
    val days = daysUntilExpiry
    return medication.packSpec().copy(
        status = status,
        statusText = if (days < 0) "Expired" else ExpiryDates.relative(days).replaceFirstChar(Char::uppercase)
    )
}
