package com.example.medicat.data.lookup

import android.content.Context
import com.example.medicat.domain.LabelFields
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One pack size of a medicine registered in South Africa. Blank/0 fields are unknown. */
data class SaMedicine(
    val name: String,
    /** Active ingredients joined with " + ". */
    val ingredients: String,
    val strength: String,
    /** One of the app's forms (Tablet, Capsule, Liquid, Cream, Drops) or blank. */
    val form: String,
    val packSize: Int,
    /** "S0" to "S6". */
    val schedule: String,
    /** One of the app's categories, or blank. */
    val category: String,
    val discontinued: Boolean,
    /** The PackTemplate name its packaging looks like (e.g. CARTON, SYRUP), or blank to guess. */
    val template: String = ""
) {
    internal val key = normalise(name)
    internal val haystack = " $key ${normalise(strength)} ${normalise(ingredients)}"
    internal val brand = brandOf(name)
}

/** Where the bundled list comes from, shown when it fills the form. */
const val SA_MEDICINES_SOURCE = "the SA medicine list"

/**
 * The bundled list of South African registered medicines (built from the
 * Department of Health's medicine price database by tools/build_sa_medicines.py).
 * Loaded from assets once, on first use, and searched in memory: it is small
 * enough that a scan per keystroke is instant, and it works offline.
 */
@Singleton
class SaMedicines @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    @Volatile private var loaded: MedicineCatalogue? = null

    suspend fun catalogue(): MedicineCatalogue = loaded ?: mutex.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            context.assets.open(ASSET).bufferedReader().useLines(MedicineCatalogue::parse)
        }.also { loaded = it }
    }

    private companion object {
        const val ASSET = "sa_medicines.tsv"
    }
}

/** Searching and matching over a list of medicines; free of Android so it can be unit tested. */
class MedicineCatalogue(val medicines: List<SaMedicine>) {

    /** Brand word (e.g. "panado") to its medicines, for spotting a product in label text. */
    private val byBrand: Map<String, List<SaMedicine>> = run {
        val ingredientWords = medicines.flatMapTo(HashSet()) { med -> words(med.ingredients) }
        medicines
            .filter { it.brand.length >= 4 && it.brand !in ingredientWords }
            .groupBy { it.brand }
    }

    /**
     * Medicines whose name starts with [query], then those where every typed word
     * starts a word of the name, strength or ingredients ("pan 500", "ibuprofen").
     * Current products come before discontinued ones, shorter names first.
     */
    fun search(query: String, limit: Int = 6): List<SaMedicine> {
        val q = normalise(query)
        if (q.length < 2) return emptyList()
        val typed = q.split(' ')
        return medicines
            .mapNotNull { med ->
                val rank = when {
                    med.key.startsWith(q) -> 0
                    typed.all { " $it" in med.haystack } -> if (" ${typed.first()}" in " ${med.key}") 1 else 2
                    else -> return@mapNotNull null
                }
                rank to med
            }
            .sortedWith(
                compareBy<Pair<Int, SaMedicine>> { it.first }
                    .thenBy { it.second.discontinued }
                    .thenBy { it.second.name.length }
                    .thenBy { it.second.name.lowercase() }
                    .thenBy { it.second.packSize }
            )
            .map { it.second }
            .distinctBy { listOf(it.key, it.strength, it.form, it.packSize) }
            .take(limit)
    }

    /**
     * The listed product name that [text] (everything read off a pack) mentions,
     * e.g. "Panado Tablets" for a box saying "PANADO … 24 TABLETS". All of the
     * name's words must appear; the most specific (wordiest) name wins.
     */
    fun productIn(text: String): String? {
        val seen = words(text)
        return seen.asSequence()
            .mapNotNull(byBrand::get)
            .flatten()
            .distinctBy { it.key }
            .filter { med -> nameWords(med).all { it in seen } }
            .maxWithOrNull(compareBy<SaMedicine> { nameWords(it).size }.thenBy { !it.discontinued })
            ?.name
    }

    /**
     * The listed pack that best fits what a label scan read: same product, then
     * the closest strength, pack size and form. Null when the product isn't listed.
     */
    fun bestMatch(fields: LabelFields): SaMedicine? {
        val name = fields.name?.takeIf { it.isNotBlank() } ?: return null
        val key = normalise(name)
        // A bare brand ("Panado") matches its fuller names ("Panado Tablets"); any other name must match exactly.
        val candidates = medicines.filter { it.key == key }
            .ifEmpty { byBrand[brandOf(name)].orEmpty().filter { it.key.startsWith("$key ") } }
        val strength = fields.strength?.let(::compact)
        return candidates.maxByOrNull { med ->
            (if (med.key == key) 4 else 0) +
                (if (strength != null && compact(med.strength).startsWith(strength)) 3 else 0) +
                (if (fields.quantity != null && med.packSize == fields.quantity) 2 else 0) +
                (if (fields.form != null && med.form == fields.form) 1 else 0) +
                (if (med.discontinued) 0 else 1)
        }
    }

    private fun nameWords(med: SaMedicine) = words(med.name).filter { word -> word.any(Char::isLetter) && !UNIT.matches(word) }

    companion object {
        /** Reads the TSV written by tools/build_sa_medicines.py; '#' lines are comments. */
        fun parse(lines: Sequence<String>): MedicineCatalogue = MedicineCatalogue(
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val f = line.split('\t')
                    if (f.size < 8) return@mapNotNull null
                    SaMedicine(
                        name = f[0], ingredients = f[1], strength = f[2], form = f[3],
                        packSize = f[4].toIntOrNull() ?: 0, schedule = f[5], category = f[6],
                        discontinued = f[7] == "1", template = f.getOrElse(8) { "" }
                    )
                }
                .toList()
        )

        private val UNIT = Regex("""\d+(?:[.,]\d+)?(?:mg|mcg|g|ml|µg|%)""")
    }
}

/** Lower case, trademark signs dropped, hyphens and runs of spaces as single spaces. */
internal fun normalise(text: String): String =
    text.lowercase().replace(Regex("[®™*]"), "").replace('-', ' ').replace(Regex("\\s+"), " ").trim()

/** The first word of a product name, which for branded medicines is the brand ("med-lemon"). */
private fun brandOf(name: String): String = words(name).firstOrNull().orEmpty()

/** Lower-case words, keeping hyphenated brands such as "adco-dol" together. */
private fun words(text: String): Set<String> =
    text.lowercase().split(Regex("[^\\p{L}\\p{N}.%-]+"))
        .map { it.trim('-', '.') }
        .filterTo(LinkedHashSet()) { it.isNotEmpty() }

/** "200 mg + 10 mg" -> "200mg+10mg", so differently spaced strengths compare equal. */
private fun compact(strength: String) = strength.lowercase().replace(" ", "")
