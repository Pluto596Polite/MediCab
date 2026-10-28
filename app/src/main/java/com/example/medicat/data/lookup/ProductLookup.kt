package com.example.medicat.data.lookup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Product details found online for a barcode. */
data class ProductInfo(
    val name: String,
    val category: String,
    val description: String,
    val source: String
)

/**
 * Looks a GTIN up in free public product databases. All sources are queried in
 * parallel; the first hit in priority order wins (medicine-specific sources first).
 */
@Singleton
class ProductLookup @Inject constructor() {

    suspend fun lookup(gtin14: String): ProductInfo? = withContext(Dispatchers.IO) {
        val ean13 = if (gtin14.length == 14 && gtin14.startsWith("0")) gtin14.substring(1) else gtin14
        val upc12 = if (ean13.length == 13 && ean13.startsWith("0")) ean13.substring(1) else null

        coroutineScope {
            listOf(
                async { getJson(openFactsUrl("world.openproductsfacts.org", ean13))?.let { ProductParsers.openFacts(it, "Open Products Facts") } },
                async { upc12?.let { getJson(openFdaUrl(ean13)) }?.let(ProductParsers::openFda) },
                async { getJson(openFactsUrl("world.openbeautyfacts.org", ean13))?.let { ProductParsers.openFacts(it, "Open Beauty Facts") } },
                async { getJson(openFactsUrl("world.openfoodfacts.org", ean13))?.let { ProductParsers.openFacts(it, "Open Food Facts") } },
                async { getJson("https://api.upcitemdb.com/prod/trial/lookup?upc=${upc12 ?: ean13}")?.let(ProductParsers::upcItemDb) }
            ).firstNotNullOfOrNull { it.await() }
        }
    }

    private fun openFactsUrl(host: String, code: String) =
        "https://$host/api/v2/product/$code.json?fields=product_name,product_name_en,generic_name,brands,quantity,categories_tags"

    private fun openFdaUrl(upc13: String): String {
        val query = URLEncoder.encode("openfda.upc:\"$upc13\"", "UTF-8")
        return "https://api.fda.gov/drug/label.json?search=$query&limit=1"
    }

}

private const val TIMEOUT_MS = 7_000
private const val USER_AGENT = "MediCab/1.0 (Android medicine cabinet app)"

/** GET [url] and parse the body as JSON; null on any network or parse failure. */
internal fun getJson(url: String): JSONObject? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", "application/json")
    }
    return try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) null
        else JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

/** Response parsers, kept free of networking so they can be unit tested. */
object ProductParsers {

    fun openFacts(json: JSONObject, source: String): ProductInfo? {
        if (json.optInt("status") != 1) return null
        val product = json.optJSONObject("product") ?: return null
        val title = product.optString("product_name").ifBlank { product.optString("product_name_en") }
        if (title.isBlank()) return null
        val (name, rest) = splitTitle(title)
        val brand = product.optString("brands").substringBefore(',').trim()
        val generic = product.optString("generic_name").trim()
        val tags = product.optJSONArray("categories_tags")
            ?.let { arr -> (0 until arr.length()).joinToString(" ") { arr.optString(it).substringAfter(':') } }
            .orEmpty()
        return ProductInfo(
            name = name,
            category = CategoryGuesser.guess("$title $generic", tags),
            description = listOf(brand.takeUnless { name.contains(it, ignoreCase = true) }, generic, rest, product.optString("quantity"))
                .joinDescription(),
            source = source
        )
    }

    fun openFda(json: JSONObject): ProductInfo? {
        val result = json.optJSONArray("results")?.optJSONObject(0) ?: return null
        val openfda = result.optJSONObject("openfda") ?: return null
        val brand = openfda.firstString("brand_name") ?: return null
        val generic = openfda.firstString("generic_name")
        val purpose = result.firstString("purpose")
            ?.replace(Regex("^\\s*purposes?\\s*", RegexOption.IGNORE_CASE), "")
        return ProductInfo(
            name = brand.titleCase(),
            category = CategoryGuesser.guess("$brand ${generic.orEmpty()} ${purpose.orEmpty()}", ""),
            description = listOf(generic?.titleCase(), purpose).joinDescription(),
            source = "openFDA"
        )
    }

    fun upcItemDb(json: JSONObject): ProductInfo? {
        val item = json.optJSONArray("items")?.optJSONObject(0) ?: return null
        val title = item.optString("title")
        if (title.isBlank()) return null
        val (name, rest) = splitTitle(title)
        val brand = item.optString("brand").trim()
        return ProductInfo(
            name = name,
            category = CategoryGuesser.guess(title, item.optString("category")),
            description = listOf(brand.takeUnless { name.contains(it, ignoreCase = true) }, rest).joinDescription(),
            source = "UPCitemdb"
        )
    }

    /** "Advil Tablets - Ibuprofen 200mg - 24ct" -> "Advil Tablets" and "Ibuprofen 200mg · 24ct". */
    internal fun splitTitle(title: String): Pair<String, String> {
        val parts = title.split(Regex("\\s+-{1,2}\\s+|\\s*\\|\\s*")).map(String::trim).filter(String::isNotEmpty)
        val name = parts.firstOrNull().orEmpty().let { if (it.length > 48) it.take(48).substringBeforeLast(' ') else it }
        return name to parts.drop(1).joinToString(" · ")
    }

    private fun List<String?>.joinDescription(): String =
        mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString(" · ").take(160)

    private fun JSONObject.firstString(key: String): String? =
        optJSONArray(key)?.optString(0)?.trim()?.takeIf(String::isNotEmpty)

    private fun String.titleCase(): String =
        lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }
}

/** Maps product text onto the app's category names. */
object CategoryGuesser {
    private val rules = listOf(
        "Allergy" to listOf("allerg", "antihist", "hay fever", "loratadine", "cetirizine", "fexofenadine", "chlorphenamine"),
        "Cold & flu" to listOf("cold & flu", "flu", "cough", "sinus", "decongest", "sore throat", "congestion",
            "phenylephrine", "pseudoephedrine", "guaifenesin", "dextromethorphan"),
        "Digestive" to listOf("antacid", "heartburn", "indigestion", "digest", "laxative", "diarrh", "constipation",
            "nausea", "reflux", "loperamide", "omeprazole"),
        "Pain relief" to listOf("pain", "analges", "paracetamol", "acetaminophen", "ibuprofen", "aspirin", "naproxen",
            "fever", "headache", "nsaid"),
        "Vitamins" to listOf("vitamin", "supplement", "multivit", "mineral", "zinc", "magnesium", "omega", "probiotic"),
        "First aid" to listOf("plaster", "bandage", "antiseptic", "wound", "first aid", "gauze", "dressing"),
        "Skin care" to listOf("ointment", "lotion", "skin", "derm", "sunscreen", "balm", "cream")
    )

    /** Tries the product's own words first, then the database's category tags. */
    fun guess(primary: String, secondary: String): String =
        match(primary) ?: match(secondary) ?: ""

    private fun match(text: String): String? {
        val lower = text.lowercase()
        return rules.firstOrNull { (_, words) -> words.any { it in lower } }?.first
    }
}
