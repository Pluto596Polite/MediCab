package com.example.medicat.domain

import java.time.LocalDate
import java.time.YearMonth

/** One line of text read off a pack. [height] is the line's on-screen height, a stand-in for font size. */
data class OcrLine(val text: String, val height: Float)

/** Whatever could be read off a medicine label; every field is optional. */
data class LabelFields(
    val name: String? = null,
    val strength: String? = null,
    val quantity: Int? = null,
    val form: String? = null,
    val category: String? = null,
    val expiry: LocalDate? = null,
    val batch: String? = null,
    /** Active ingredient, when it differs from the name. */
    val activeIngredient: String? = null
) {
    val isEmpty get() = this == LabelFields()
}

/**
 * Pulls medication details out of text recognised on a box, bottle or tube.
 * It is rule-based on purpose: label wording is formulaic ("EXP 03/2027",
 * "24 tablets", "200 mg"), so patterns plus a small vocabulary of common
 * ingredients and brands beat a heavier model, and everything stays offline.
 * The name prefers known products (the user's own cabinet first), falling back
 * to the largest prominent line of text.
 */
object LabelParser {

    private val months = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "SEPT" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
    )
    private const val MONTH_NAMES = "JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEPT|SEP|OCT|NOV|DEC"

    /** Ingredient (lower case) to category, used for both the category and as a name fallback. */
    private val ingredients = mapOf(
        "ibuprofen" to "Pain relief", "paracetamol" to "Pain relief", "acetaminophen" to "Pain relief",
        "aspirin" to "Pain relief",
        "diclofenac" to "Inflammation", "naproxen" to "Inflammation", "meloxicam" to "Inflammation",
        "celecoxib" to "Inflammation", "etoricoxib" to "Inflammation", "piroxicam" to "Inflammation",
        "indometacin" to "Inflammation", "ketoprofen" to "Inflammation", "prednisolone" to "Inflammation",
        "codeine" to "Pain relief", "mefenamic acid" to "Pain relief", "tramadol" to "Pain relief",
        "cetirizine" to "Allergy", "levocetirizine" to "Allergy", "loratadine" to "Allergy",
        "desloratadine" to "Allergy", "fexofenadine" to "Allergy", "chlorphenamine" to "Allergy",
        "chlorpheniramine" to "Allergy", "promethazine" to "Allergy", "dexchlorpheniramine" to "Allergy",
        "pseudoephedrine" to "Cold & flu", "phenylephrine" to "Cold & flu", "guaifenesin" to "Cold & flu",
        "dextromethorphan" to "Cold & flu", "oxymetazoline" to "Cold & flu", "xylometazoline" to "Cold & flu",
        "loperamide" to "Digestive", "simethicone" to "Digestive", "omeprazole" to "Digestive",
        "pantoprazole" to "Digestive", "ranitidine" to "Digestive", "hyoscine" to "Digestive",
        "sodium alginate" to "Digestive", "bisacodyl" to "Digestive", "lactulose" to "Digestive",
        "povidone-iodine" to "First aid", "povidone iodine" to "First aid", "chlorhexidine" to "First aid",
        "hydrocortisone" to "Skin care", "clotrimazole" to "Skin care", "calamine" to "Skin care",
        "amoxicillin" to "Prescription", "metformin" to "Prescription", "atorvastatin" to "Prescription",
        "amlodipine" to "Prescription", "losartan" to "Prescription", "prednisone" to "Prescription",
        "vitamin" to "Vitamins", "multivitamin" to "Vitamins", "zinc" to "Vitamins", "magnesium" to "Vitamins",
        "calcium" to "Vitamins", "omega-3" to "Vitamins", "folic acid" to "Vitamins", "iron" to "Vitamins"
    )

    /** Common over-the-counter brands (South African and international) and their category. */
    private val brands = mapOf(
        "Panado" to "Pain relief", "Myprodol" to "Pain relief", "Grand-Pa" to "Pain relief",
        "Disprin" to "Pain relief", "Nurofen" to "Pain relief", "Voltaren" to "Inflammation",
        "Cataflam" to "Inflammation", "Celebrex" to "Inflammation", "Arcoxia" to "Inflammation", "Mobic" to "Inflammation",
        "Mypaid" to "Pain relief", "Adco-Dol" to "Pain relief", "Stilpane" to "Pain relief",
        "Compral" to "Pain relief", "Advil" to "Pain relief", "Tylenol" to "Pain relief",
        "Aleve" to "Pain relief", "Calpol" to "Pain relief", "Panadol" to "Pain relief",
        "Allergex" to "Allergy", "Texa" to "Allergy", "Clarityne" to "Allergy", "Zyrtec" to "Allergy",
        "Telfast" to "Allergy", "Allecet" to "Allergy", "Claritin" to "Allergy", "Benadryl" to "Allergy",
        "Corenza C" to "Cold & flu", "Sinutab" to "Cold & flu", "Benylin" to "Cold & flu",
        "Med-Lemon" to "Cold & flu", "Strepsils" to "Cold & flu", "Linctagon" to "Cold & flu",
        "Sudafed" to "Cold & flu", "Lemsip" to "Cold & flu", "Vicks" to "Cold & flu", "Otrivin" to "Cold & flu",
        "Gaviscon" to "Digestive", "Rennie" to "Digestive", "Imodium" to "Digestive", "Buscopan" to "Digestive",
        "Eno" to "Digestive", "Citro-Soda" to "Digestive", "Nexium" to "Digestive",
        "Betadine" to "First aid", "Savlon" to "First aid", "Dettol" to "First aid", "Elastoplast" to "First aid",
        "Germolene" to "First aid", "Bactroban" to "First aid",
        "Berocca" to "Vitamins", "Centrum" to "Vitamins", "Redoxon" to "Vitamins", "Pharmaton" to "Vitamins",
        "Bennetts" to "Skin care", "Canesten" to "Skin care", "Bepanthen" to "Skin care"
    )

    private val formWords = listOf(
        "Tablet" to Regex("""\b(tablets?|tabs?|caplets?)\b""", RegexOption.IGNORE_CASE),
        "Capsule" to Regex("""\b(capsules?|caps|softgels?|gelcaps?)\b""", RegexOption.IGNORE_CASE),
        "Liquid" to Regex("""\b(syrup|suspension|solution|mixture|elixir|linctus|liquid|oral liquid)\b""", RegexOption.IGNORE_CASE),
        "Cream" to Regex("""\b(cream|ointment|gel|lotion|balm)\b""", RegexOption.IGNORE_CASE),
        "Drops" to Regex("""\b(drops|eye drops|ear drops|nasal spray|spray)\b""", RegexOption.IGNORE_CASE)
    )

    private val strengthPattern = Regex(
        """(\d+(?:[.,]\d+)?)\s?(mg/5\s?ml|mg/ml|mcg|µg|mg|iu|i\.u\.|%)(?![a-z])""",
        RegexOption.IGNORE_CASE
    )
    private val countPattern = Regex(
        """\b(\d{1,3})\s*(?:x\s*)?(film[- ]coated tablets?|effervescent tablets?|chewable tablets?|tablets?|tabs|caplets?|capsules?|caps|softgels?|sachets?|lozenges?|pastilles?|plasters?|strips?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val volumePattern = Regex("""\b(\d{2,4})\s?(ml|g)\b(?!\s*/)""", RegexOption.IGNORE_CASE)
    private val batchPattern = Regex(
        """\b(?:LOT|BATCH|B/N|BN|LOT\s?NO|BATCH\s?NO)\.?\s*[:#.]?\s*([A-Z0-9][A-Z0-9-]{2,14})\b""",
        RegexOption.IGNORE_CASE
    )
    private val expiryKeyword = Regex(
        """\b(EXP(?:IRY)?(?:\s?DATE)?|EXPIRES|USE\s?BY|BEST\s?BEFORE|BB|ED|VAL)\b\.?\s*[:.]?""",
        RegexOption.IGNORE_CASE
    )
    private val manufactureKeyword = Regex("""\b(MFG|MFD|MAN(?:UF)?|MANUFACTURED|PROD(?:UCED)?|DOM|PKD)\b""", RegexOption.IGNORE_CASE)

    /** Words that make a line unlikely to be the product name. */
    private val notNameWords = Regex(
        """\b(tablets?|capsules?|mg|ml|exp|expiry|lot|batch|each|contains?|store|keep|use|directions|dosage|warning|reg\.?\s?no|www|ltd|pty|inc|pharma\w*|laboratories|made in|ingredients?|children|adults?|dose|daily|only|read|leaflet|schedule|barcode)\b""",
        RegexOption.IGNORE_CASE
    )

    fun parse(lines: List<OcrLine>, knownNames: Collection<String> = emptyList(), today: LocalDate = LocalDate.now()): LabelFields {
        val cleaned = lines.map { it.copy(text = it.text.replace('\n', ' ').trim()) }.filter { it.text.isNotEmpty() }
        if (cleaned.isEmpty()) return LabelFields()
        val all = cleaned.joinToString("\n") { it.text }

        val ingredient = ingredients.keys
            .filter { Regex("""\b${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(all) }
            .maxByOrNull { it.length }
        val brand = brands.keys.firstOrNull { Regex("""\b${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(all) }
        val known = knownNames
            .filter { it.length >= 3 }
            .sortedByDescending { it.length }
            .firstOrNull { Regex("""\b${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(all) }

        val name = known ?: brand ?: prominentLine(cleaned) ?: ingredient?.let(::titleCase)

        val count = countPattern.find(all)
        val form = count?.groupValues?.get(2)?.let(::formOf)
            ?: formWords.firstOrNull { (_, pattern) -> pattern.containsMatchIn(all) }?.first
        val volume = volumePattern.findAll(all)
            .filterNot { m -> strengthPattern.findAll(all).any { it.range.first <= m.range.first && it.range.last >= m.range.last } }
            .firstOrNull()

        val quantity = count?.groupValues?.get(1)?.toIntOrNull()
            ?: volume?.groupValues?.get(1)?.toIntOrNull()?.takeIf { form == "Liquid" || form == "Cream" || form == "Drops" }

        val category = brand?.let(brands::get)
            ?: ingredient?.let(ingredients::get)
            ?: categoryFromWords(all)

        return LabelFields(
            name = name,
            strength = strengthPattern.find(all)?.let { formatStrength(it.groupValues[1], it.groupValues[2]) },
            quantity = quantity?.takeIf { it in 1..999 },
            form = form,
            category = category,
            expiry = findExpiry(cleaned, today),
            batch = batchPattern.find(all)?.groupValues?.get(1)?.uppercase()?.takeIf { it.any(Char::isDigit) },
            activeIngredient = ingredient?.let(::titleCase)?.takeUnless { name.equals(it, ignoreCase = true) }
        )
    }

    /** The biggest line of text that looks like a name rather than small print. */
    private fun prominentLine(lines: List<OcrLine>): String? = lines
        .filter { line ->
            val text = line.text
            val letters = text.count(Char::isLetter)
            text.length in 3..32 &&
                letters >= 3 &&
                letters.toFloat() / text.length > 0.6f &&
                !notNameWords.containsMatchIn(text) &&
                !expiryKeyword.containsMatchIn(text)
        }
        .maxByOrNull { it.height }
        ?.text
        ?.let(::tidyName)

    private fun tidyName(raw: String): String {
        val trimmed = raw.trim().trim('®', '™', '©', '*', '.', ',', '-', ' ')
        // All-caps box text reads better in title case, but keep short brand acronyms.
        return if (trimmed.length > 4 && trimmed == trimmed.uppercase()) titleCase(trimmed) else trimmed
    }

    private fun titleCase(text: String) = text.lowercase().split(' ').joinToString(" ") { word ->
        word.split('-').joinToString("-") { it.replaceFirstChar(Char::uppercase) }
    }

    /** Dosage form for a counted unit; plasters, sachets and the like have none. */
    private fun formOf(word: String): String? = when {
        word.contains("caplet", ignoreCase = true) || word.contains("tab", ignoreCase = true) -> "Tablet"
        word.contains("cap", ignoreCase = true) || word.contains("softgel", ignoreCase = true) -> "Capsule"
        else -> null
    }

    private fun formatStrength(amount: String, unit: String): String {
        val u = when (unit.lowercase().replace(" ", "")) {
            "mcg", "µg" -> "mcg"
            "iu", "i.u." -> "IU"
            "mg/5ml" -> "mg/5 ml"
            "mg/ml" -> "mg/ml"
            "%" -> "%"
            else -> "mg"
        }
        val n = amount.replace(',', '.')
        return if (u == "%") "$n%" else "$n $u"
    }

    private fun categoryFromWords(text: String): String? {
        val t = text.lowercase()
        return when {
            listOf("anti-inflammatory", "inflammation", "inflammatory", "swelling", "arthritis").any(t::contains) -> "Inflammation"
            listOf("pain", "fever", "headache", "analgesic").any(t::contains) -> "Pain relief"
            listOf("hay fever", "allerg", "antihistamine").any(t::contains) -> "Allergy"
            listOf("cough", "cold", "flu", "sinus", "congestion", "throat").any(t::contains) -> "Cold & flu"
            listOf("antacid", "heartburn", "indigestion", "diarrh", "constipation").any(t::contains) -> "Digestive"
            listOf("antiseptic", "wound", "plaster", "bandage", "burn").any(t::contains) -> "First aid"
            listOf("eczema", "rash", "itch", "skin").any(t::contains) -> "Skin care"
            listOf("supplement", "vitamin").any(t::contains) -> "Vitamins"
            else -> null
        }
    }

    /**
     * Finds the expiry date. A date right after an expiry keyword wins; otherwise
     * any plausible future date not marked as a manufacture date. Month-only
     * dates mean the end of that month, as on medicine packs.
     */
    fun findExpiry(lines: List<OcrLine>, today: LocalDate = LocalDate.now()): LocalDate? {
        val text = lines.joinToString("\n") { it.text }
        expiryKeyword.findAll(text).forEach { keyword ->
            val after = text.substring(keyword.range.last + 1).take(24)
            firstDate(after, today)?.let { return it }
        }
        // No keyword: the latest plausible date that isn't labelled as manufacture.
        return lines
            .filterNot { manufactureKeyword.containsMatchIn(it.text) }
            .flatMap { allDates(it.text, today) }
            .filter { it.isAfter(today.minusYears(2)) }
            .maxOrNull()
    }

    private fun firstDate(text: String, today: LocalDate): LocalDate? = allDates(text, today).firstOrNull()

    private fun allDates(text: String, today: LocalDate): List<LocalDate> {
        val upper = text.uppercase()
        val found = mutableListOf<Pair<Int, LocalDate>>()
        fun add(index: Int, date: LocalDate?) {
            if (date != null && date.year in (today.year - 3)..(today.year + 12)) found += index to date
        }
        // 31/03/2027, 31.03.27
        Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4}|\d{2})\b""").findAll(upper).forEach {
            val (d, m, y) = it.destructured
            add(it.range.first, safeDate(year(y), m.toInt(), d.toInt()))
        }
        // 2027-03-31
        Regex("""\b(\d{4})[/.\-](\d{1,2})[/.\-](\d{1,2})\b""").findAll(upper).forEach {
            val (y, m, d) = it.destructured
            add(it.range.first, safeDate(y.toInt(), m.toInt(), d.toInt()))
        }
        // 03/2027, 03-27, 03.2027 (not part of a full date)
        Regex("""(?<![\d/.\-])(\d{1,2})\s?[/.\-]\s?(\d{4}|\d{2})(?![\d/.\-])""").findAll(upper).forEach {
            val (m, y) = it.destructured
            add(it.range.first, endOfMonth(year(y), m.toInt()))
        }
        // 2027/03, 2027-03
        Regex("""(?<![\d/.\-])(\d{4})[/.\-](\d{1,2})(?![\d/.\-])""").findAll(upper).forEach {
            val (y, m) = it.destructured
            add(it.range.first, endOfMonth(y.toInt(), m.toInt()))
        }
        // MAR 2027, MAR-27, 31 MAR 2027
        Regex("""\b(?:(\d{1,2})\s?)?($MONTH_NAMES)[A-Z]*[\s.\-/]*(\d{4}|\d{2})\b""").findAll(upper).forEach {
            val (d, mon, y) = it.destructured
            val m = months[mon] ?: return@forEach
            add(it.range.first, if (d.isNotEmpty()) safeDate(year(y), m, d.toInt()) else endOfMonth(year(y), m))
        }
        // 2027 MAR
        Regex("""\b(\d{4})\s?($MONTH_NAMES)\b""").findAll(upper).forEach {
            val (y, mon) = it.destructured
            add(it.range.first, endOfMonth(y.toInt(), months.getValue(mon)))
        }
        return found.sortedBy { it.first }.map { it.second }.distinct()
    }

    private fun year(y: String) = if (y.length == 2) 2000 + y.toInt() else y.toInt()

    private fun endOfMonth(year: Int, month: Int): LocalDate? =
        if (month in 1..12) YearMonth.of(year, month).atEndOfMonth() else null

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        if (month in 1..12 && day in 1..YearMonth.of(year, month).lengthOfMonth()) LocalDate.of(year, month, day) else null
}

/**
 * Combines readings from many camera frames. OCR on a single frame is noisy and
 * only sees one side of the box, so each field keeps a tally and the most
 * frequent reading wins; turning the pack around keeps adding to it.
 */
class LabelAccumulator {
    private val tallies = mutableMapOf<String, MutableMap<Any, Int>>()

    fun add(fields: LabelFields) {
        fun vote(key: String, value: Any?) {
            if (value == null) return
            val tally = tallies.getOrPut(key) { mutableMapOf() }
            tally[value] = (tally[value] ?: 0) + 1
        }
        vote("name", fields.name)
        vote("strength", fields.strength)
        vote("quantity", fields.quantity)
        vote("form", fields.form)
        vote("category", fields.category)
        vote("expiry", fields.expiry)
        vote("batch", fields.batch)
        vote("active", fields.activeIngredient)
    }

    private inline fun <reified T> best(key: String): T? =
        tallies[key]?.maxByOrNull { it.value }?.key as? T

    fun current() = LabelFields(
        name = best("name"),
        strength = best("strength"),
        quantity = best("quantity"),
        form = best("form"),
        category = best("category"),
        expiry = best("expiry"),
        batch = best("batch"),
        activeIngredient = best("active")
    )

    fun clear() = tallies.clear()
}
