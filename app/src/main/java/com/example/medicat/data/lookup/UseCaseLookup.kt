package com.example.medicat.data.lookup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** What a medication is commonly used for, and where that came from. */
data class UseInfo(val text: String, val source: String)

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

/** Response parsers, kept free of networking so they can be unit tested. */
object UseCaseParsers {
    private const val MAX_LENGTH = 240

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
        val text = tidyUses(uses).takeIf { it.length >= 12 } ?: return null
        return UseInfo(text, "openFDA")
    }

    fun wikipedia(json: JSONObject): UseInfo? {
        if (json.optString("type") != "standard") return null
        val extract = json.optString("extract").trim()
        if (extract.isBlank() || !medical.containsMatchIn(extract)) return null
        val sentences = extract.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val pick = sentences.firstOrNull { Regex("\\bused\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) } ?: sentences.first()
        return UseInfo(shorten(pick), "Wikipedia")
    }

    /** "Uses • temporarily relieves … due to: • headache • backache" -> "Temporarily relieves … due to: headache, backache". */
    internal fun tidyUses(raw: String): String {
        val body = raw.trim().replace(Regex("^(uses?|indications? and usage)\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
        val parts = body.split('•', '■', '●').map { it.trim().trimEnd(',', ';') }.filter { it.isNotEmpty() }
        val joined = buildString {
            parts.forEachIndexed { i, part ->
                if (i > 0) append(if (this.endsWith(":")) " " else ", ")
                append(part)
            }
        }
        return shorten(joined.replace(Regex("\\s+"), " ").replaceFirstChar(Char::uppercase))
    }

    private fun shorten(text: String): String {
        if (text.length <= MAX_LENGTH) return text.trim()
        val cut = text.take(MAX_LENGTH)
        val end = maxOf(cut.lastIndexOf(", "), cut.lastIndexOf(". "))
        return (if (end > MAX_LENGTH / 2) cut.take(end) else cut.substringBeforeLast(' ')).trim() + "…"
    }

    private fun JSONObject.firstString(key: String): String? =
        optJSONArray(key)?.optString(0)?.trim()?.takeIf(String::isNotEmpty)
}
