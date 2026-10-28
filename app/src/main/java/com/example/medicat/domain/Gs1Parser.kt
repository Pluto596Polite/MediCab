package com.example.medicat.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Data extracted from a scanned code. [key] is what gets stored as the barcode:
 * a GTIN-14 for retail/GS1 codes (so an EAN-13 on the box and the DataMatrix on
 * the same pack match), otherwise the raw value.
 */
data class ScannedCode(
    val key: String,
    val expiry: LocalDate? = null,
    val batch: String? = null
)

/**
 * Parses medicine pack barcodes. Pharmaceutical packs usually carry a GS1
 * DataMatrix encoding GTIN (01), expiry (17) and batch (10); retail boxes carry
 * an EAN-13/UPC-A. Both forms are handled, as is the human-readable
 * "(01)...(17)..." notation.
 */
object Gs1Parser {
    private const val GS = '\u001D'

    /** Fixed data lengths for the application identifiers we may encounter. */
    private val fixedLengths = mapOf(
        "00" to 18, "01" to 14, "02" to 14,
        "11" to 6, "12" to 6, "13" to 6, "15" to 6, "16" to 6, "17" to 6,
        "20" to 2
    )

    /** Variable-length AIs (terminated by GS or end of data). */
    private val variableAis = setOf("10", "21", "22", "240", "241", "710", "711", "712", "713", "714")

    fun parse(raw: String): ScannedCode {
        val value = raw.trim().removePrefix("]d2").removePrefix("]C1").removePrefix("]Q3").trimStart(GS)

        if (value.all(Char::isDigit) && value.length in setOf(8, 12, 13, 14)) {
            return ScannedCode(key = value.padStart(14, '0'))
        }

        val fields = if (value.startsWith("(")) parseBracketed(value) else parseRaw(value)
        val gtin = fields["01"] ?: return ScannedCode(key = raw.trim())

        return ScannedCode(
            key = gtin,
            expiry = fields["17"]?.let(::parseDate),
            batch = fields["10"]
        )
    }

    private fun parseBracketed(value: String): Map<String, String> =
        Regex("""\((\d{2,4})\)([^(]*)""").findAll(value)
            .associate { it.groupValues[1] to it.groupValues[2].trim() }

    private fun parseRaw(value: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var i = 0
        while (i < value.length) {
            if (value[i] == GS) { i++; continue }
            val two = value.substring(i, minOf(i + 2, value.length))
            val three = value.substring(i, minOf(i + 3, value.length))
            val ai = when {
                two in fixedLengths || two in variableAis -> two
                three in variableAis -> three
                else -> return result
            }
            i += ai.length
            val fixed = fixedLengths[ai]
            val end = if (fixed != null) {
                i + fixed
            } else {
                value.indexOf(GS, i).takeIf { it >= 0 } ?: value.length
            }
            if (end > value.length) return result
            result[ai] = value.substring(i, end)
            i = end
        }
        return result
    }

    /** GS1 dates are YYMMDD; a day of "00" means the last day of the month. */
    private fun parseDate(yymmdd: String): LocalDate? {
        if (yymmdd.length != 6 || !yymmdd.all(Char::isDigit)) return null
        val year = 2000 + yymmdd.substring(0, 2).toInt()
        val month = yymmdd.substring(2, 4).toInt()
        val day = yymmdd.substring(4, 6).toInt()
        if (month !in 1..12) return null
        val ym = YearMonth.of(year, month)
        return when {
            day == 0 -> ym.atEndOfMonth()
            day <= ym.lengthOfMonth() -> ym.atDay(day)
            else -> null
        }
    }
}
