package com.example.medicat.data.lookup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** What a medication is commonly used for, as short points, and where that came from. */
data class UseInfo(val points: List<UsePoint>, val source: String)

/** One use, e.g. "Relieves minor aches and pains", with the symptoms the label lists under it. */
data class UsePoint(val text: String, val details: List<String> = emptyList())

/**
 * Finds common uses for a medication online, to pre-fill its notes. openFDA's
 * drug labels come first because their "Uses" sections are written for
 * consumers; Wikipedia's summary is the fallback, which also covers brands
 * sold outside the US via their active ingredient. Only the search terms
 * (a name or ingredient) are sent.
 */
@Singleton
class UseCaseLookup @Inject constructor() {

    /** [terms] in order of preference, e.g. the active ingredient, then the product name. */
    suspend fun lookup(terms: List<String>): UseInfo? = withContext(Dispatchers.IO) {
        val clean = terms.map { it.trim() }.filter { it.length >= 3 }.distinctBy { it.lowercase() }
        clean.firstNotNullOfOrNull { term -> getJson(openFdaUrl(UseCaseParsers.usName(term)))?.let(UseCaseParsers::openFda) }
            ?: clean.firstNotNullOfOrNull { term -> getJson(wikipediaUrl(term))?.let(UseCaseParsers::wikipedia) }
    }

    private fun openFdaUrl(term: String): String {
        val q = term.replace("\"", "")
        val query = URLEncoder.encode("(openfda.generic_name:\"$q\" OR openfda.brand_name:\"$q\")", "UTF-8")
        return "https://api.fda.gov/drug/label.json?search=$query&limit=5"
    }

    private fun wikipediaUrl(term: String): String =
        "https://en.wikipedia.org/api/rest_v1/page/summary/" +
            URLEncoder.encode(term.trim().replace(' ', '_'), "UTF-8").replace("+", "%20")
}

/** How common uses sit in a medication's notes: a heading line, then one "• " line per point. */
object UsesNote {
    const val LABEL = "Common uses"

    private const val DETAIL = "   – "

    fun format(info: UseInfo): String = buildList {
        add("$LABEL (${info.source}):")
        info.points.forEach { point ->
            add("• ${point.text}")
            point.details.forEach { add("$DETAIL$it") }
        }
    }.joinToString("\n")

    /** [notes] without an earlier uses block, whether bulleted or the older single line. */
    fun remove(notes: String): String {
        var inBlock = false
        return notes.lines().filter { line ->
            when {
                line.startsWith(LABEL) -> { inBlock = true; false }
                inBlock && (line.startsWith("• ") || line.startsWith(DETAIL)) -> false
                else -> { inBlock = false; true }
            }
        }.joinToString("\n").trim()
    }
}

/** Response parsers, kept free of networking so they can be unit tested. */
object UseCaseParsers {
    private const val MAX_LENGTH = 240
    private const val MAX_POINTS = 6
    private const val MAX_POINT_LENGTH = 90

    /** A label phrase that starts a new use ("temporarily reduces fever") rather than listing a symptom. */
    private val action = Regex(
        """^(temporarily|helps?|relieves?|reduces?|prevents?|treats?|controls?|soothes?|protects?|kills?|stops?|cleans?|moisturi[sz]es?|aids?|for|used)\b""",
        RegexOption.IGNORE_CASE
    )

    /** openFDA labels use US names for some ingredients. */
    fun usName(term: String): String = when (term.trim().lowercase()) {
        "paracetamol" -> "acetaminophen"
        "chlorphenamine" -> "chlorpheniramine"
        "salbutamol" -> "albuterol"
        "adrenaline" -> "epinephrine"
        else -> term
    }

    /** Words that show a Wikipedia summary is about a medicine, not e.g. a dish of the same name. */
    private val medical = Regex(
        """\b(medication|medicine|drug|used to treat|treatment of|analgesic|antihistamine|antibiotic|antiseptic|anti-inflammatory|NSAID|vitamin|supplement|decongestant|antacid|pharmaceutical)\b""",
        RegexOption.IGNORE_CASE
    )

    fun openFda(json: JSONObject): UseInfo? {
        val results = json.optJSONArray("results") ?: return null
        val labels = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
        // Prefer a label whose "Uses" text is bulleted: it splits into a clean list.
        val uses = labels.mapNotNull { it.firstString("indications_and_usage") }
            .sortedByDescending { '•' in it }
            .firstOrNull()
            ?: return null
        val points = summariseUses(uses).takeIf { list -> list.sumOf { it.text.length } >= 12 } ?: return null
        return UseInfo(points, "openFDA")
    }

    fun wikipedia(json: JSONObject): UseInfo? {
        if (json.optString("type") != "standard") return null
        val extract = json.optString("extract").trim()
        if (extract.isBlank() || !medical.containsMatchIn(extract)) return null
        val sentences = extract.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val at = sentences.indexOfFirst { Regex("\\bused\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        val pick = sentences.getOrElse(at) { sentences.first() }
        // "This includes migraines, …" right after the "used to" sentence adds to the list.
        val more = sentences.getOrNull(at + 1)?.takeIf { at >= 0 && Regex("^(this|these|it) includes?\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        val points = usesIn(pick)?.let { it + more?.let(::includedIn).orEmpty() }
            ?: listOf(shorten(pick))
        return UseInfo(points.distinctBy(String::lowercase).take(MAX_POINTS).map(::UsePoint), "Wikipedia")
    }

    /** "… is used to relieve pain, fever, and inflammation." -> ["Pain", "Fever", "Inflammation"]. */
    private fun usesIn(sentence: String): List<String>? {
        val m = Regex(
            """\bused (?:to treat|to relieve|to prevent|to manage|to reduce|in the treatment of|for the treatment of|for|to)\s+(.+?)\.?$""",
            RegexOption.IGNORE_CASE
        ).find(sentence.replace(Regex("\\s*\\([^)]*\\)"), "")) ?: return null
        return listItems(m.groupValues[1]).takeIf { it.isNotEmpty() }
    }

    /** "This includes painful menstrual periods, migraines, and rheumatoid arthritis." -> its items. */
    private fun includedIn(sentence: String): List<String> =
        listItems(sentence.replace(Regex("^\\w+ includes?\\s+", RegexOption.IGNORE_CASE), "").trimEnd('.'))

    private fun listItems(text: String): List<String> = text
        .split(Regex(",\\s*(?:and |or )?|\\s+(?:and|or)\\s+"))
        .map { it.trim().trimEnd('.') }
        .filter { it.length >= 3 }
        .map { capitalise(shortPoint(it)) }
        .take(MAX_POINTS)

    /**
     * Turns a label's "Uses" section into short points: each use is a point, and the
     * symptoms listed after a colon become its details. "Uses • temporarily relieves
     * minor aches and pains due to: • headache • toothache • temporarily reduces fever"
     * -> "Relieves minor aches and pains" (headache, toothache) and "Reduces fever".
     */
    internal fun summariseUses(raw: String): List<UsePoint> {
        val body = raw.trim().replace(Regex("^(uses?|indications? and usage)\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
        val parts = body.split('•', '■', '●')
            .flatMap { it.split(Regex("(?<=[.;])\\s+|\\s+(?=temporarily\\s)", RegexOption.IGNORE_CASE)) }
            .map { it.replace(Regex("\\s+"), " ").trim().trim(',', ';', '.').trim() }
            .filter { it.isNotEmpty() }
            .map { if (it == it.uppercase()) it.lowercase() else it }

        class Point(val head: String, val items: MutableList<String> = mutableListOf(), val open: Boolean = false)
        val points = mutableListOf<Point>()
        parts.forEach { part ->
            val last = points.lastOrNull()
            when {
                ':' in part -> {
                    val (head, tail) = part.split(':', limit = 2)
                    // Unbulleted symptoms can't be split reliably ("itchy, watery eyes"), so they stay one detail.
                    points += Point(tidyHead(head), listOfNotNull(tail.trim().ifEmpty { null }).toMutableList(), open = true)
                }
                last != null && last.open && !action.containsMatchIn(part) -> last.items += part
                else -> points += Point(tidyHead(part))
            }
        }
        return points
            .filter { it.head.isNotBlank() || it.items.isNotEmpty() }
            .map { point ->
                val details = point.items.map { capitalise(shortPoint(it.replace(Regex("^the ", RegexOption.IGNORE_CASE), ""))) }
                if (point.head.isBlank()) UsePoint(details.first(), details.drop(1).take(MAX_POINTS))
                else UsePoint(shortPoint(point.head), details.distinct().take(MAX_POINTS))
            }
            .distinctBy { it.text }
            .take(MAX_POINTS)
    }

    /** "temporarily relieves minor aches and pains due to" -> "Relieves minor aches and pains". */
    private fun tidyHead(text: String): String = capitalise(
        text.trim()
            .replace(Regex("^temporarily\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bthese symptoms (due to|of)\\s+", RegexOption.IGNORE_CASE), "symptoms of ")
            .replace(Regex("^for the temporary relief of\\s+", RegexOption.IGNORE_CASE), "relieves ")
            .replace(Regex("\\s+(due to|associated with|caused by|from|of)$", RegexOption.IGNORE_CASE), "")
            .trim()
    )

    private fun capitalise(text: String) = text.replaceFirstChar(Char::uppercase)

    private fun shortPoint(text: String): String =
        if (text.length <= MAX_POINT_LENGTH) text else text.take(MAX_POINT_LENGTH).substringBeforeLast(' ').trimEnd(',', ';') + "…"


    private fun shorten(text: String): String {
        if (text.length <= MAX_LENGTH) return text.trim()
        val cut = text.take(MAX_LENGTH)
        val end = maxOf(cut.lastIndexOf(", "), cut.lastIndexOf(". "))
        return (if (end > MAX_LENGTH / 2) cut.take(end) else cut.substringBeforeLast(' ')).trim() + "…"
    }

    private fun JSONObject.firstString(key: String): String? =
        optJSONArray(key)?.optString(0)?.trim()?.takeIf(String::isNotEmpty)
}
