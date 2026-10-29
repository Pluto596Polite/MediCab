package com.example.medicat.data.transfer

import com.example.medicat.data.local.DoseEntity
import com.example.medicat.data.local.MedicationEntity
import com.example.medicat.domain.ExpiryDates
import com.example.medicat.ui.components.MedicationForms
import com.example.medicat.ui.pack.PackTemplate
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** File types the cabinet can be exported to. */
enum class ExportFormat(val label: String, val extension: String, val mimeType: String, val description: String) {
    EXCEL("Excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Opens in Excel, Google Sheets and Numbers"),
    CSV("CSV", "csv", "text/csv", "A plain table for any spreadsheet app"),
    JSON("JSON backup", "json", "application/json", "Everything, including dose history. Best for moving to a new phone"),
    PDF("PDF", "pdf", "application/pdf", "A printable list for your doctor or pharmacist")
}

/** Everything a file can hold. Medication ids are the file's own, linking doses to their medication. */
data class CabinetData(val medications: List<MedicationEntity>, val doses: List<DoseEntity> = emptyList())

/** What an imported file held, and how many of its rows couldn't be used (no name or expiry date). */
data class ImportedFile(val data: CabinetData, val skippedRows: Int)

/** A file that can't be imported; [message] is shown to the user. */
class ImportException(message: String) : Exception(message)

/**
 * Reads and writes the cabinet as Excel, CSV and JSON. The spreadsheet formats
 * share one set of columns; imports match column headers loosely ("Expiry",
 * "Expires", "EXP"…) so sheets made by hand or by other apps work too.
 * Everything here is plain Kotlin/JVM so it can be unit tested.
 */
object CabinetFiles {

    internal enum class Column(val header: String, vararg val aliases: String) {
        NAME("Name", "medication", "medicine", "product", "drug", "item", "medication name", "medicine name", "product name"),
        STRENGTH("Strength", "dose", "dosage"),
        FORM("Form", "type", "dosage form"),
        CATEGORY("Category", "group"),
        PACK_SIZE("Pack size", "quantity", "qty", "pack", "count"),
        REMAINING("Remaining", "left", "stock", "on hand", "in stock"),
        EXPIRY("Expiry date", "expiry", "expires", "exp", "expiration", "expiration date", "expiry_date", "best before", "use by"),
        LOCATION("Kept in", "location", "where", "storage"),
        BARCODE("Barcode", "gtin", "ean", "upc"),
        NOTES("Notes", "description", "comments", "comment"),
        REMINDERS("Reminders", "remind", "reminder"),
        ACTIVE("Active", "in use"),
        PACK("3D pack", "template", "pack template");

        fun matches(header: String): Boolean {
            val h = header.trim().lowercase().replace(Regex("[_\\s]+"), " ")
            return h == this.header.lowercase() || h in aliases
        }
    }

    // ---------- Spreadsheet rows ----------

    private fun cells(med: MedicationEntity): Map<Column, Any> = mapOf(
        Column.NAME to med.name,
        Column.STRENGTH to med.strength,
        Column.FORM to med.form,
        Column.CATEGORY to med.category,
        Column.PACK_SIZE to med.quantity,
        Column.REMAINING to med.remaining,
        Column.EXPIRY to ExpiryDates.toLocalDate(med.expiryDate),
        Column.LOCATION to med.location,
        Column.BARCODE to med.barcode.orEmpty(),
        Column.NOTES to med.description,
        Column.REMINDERS to yesNo(med.remind),
        Column.ACTIVE to yesNo(med.isActive),
        Column.PACK to med.pack
    )

    private fun yesNo(value: Boolean) = if (value) "Yes" else "No"

    /** Builds medications from a table whose header row is somewhere in its first few rows. */
    internal fun fromTable(rows: List<List<String>>): ImportedFile {
        val headerAt = rows.take(10).indexOfFirst { row -> row.any(Column.NAME::matches) && row.any(Column.EXPIRY::matches) }
        if (headerAt < 0) {
            throw ImportException("Couldn't find the column headings. The first row needs at least \"Name\" and \"Expiry date\".")
        }
        val header = rows[headerAt]
        val index = Column.entries.associateWith { column -> header.indexOfFirst(column::matches) }.filterValues { it >= 0 }
        var skipped = 0
        val meds = rows.drop(headerAt + 1).mapNotNull { row ->
            fun cell(column: Column) = index[column]?.let(row::getOrNull)?.trim().orEmpty()
            if (row.all { it.isBlank() }) return@mapNotNull null
            val name = cell(Column.NAME)
            val expiry = parseDate(cell(Column.EXPIRY))
            if (name.isBlank() || expiry == null) {
                skipped++
                return@mapNotNull null
            }
            val quantity = number(cell(Column.PACK_SIZE))
            val remaining = cell(Column.REMAINING).takeIf { it.isNotBlank() }?.let(::number) ?: quantity
            MedicationEntity(
                name = name,
                description = cell(Column.NOTES),
                category = cell(Column.CATEGORY),
                expiryDate = ExpiryDates.toMillis(expiry),
                barcode = cell(Column.BARCODE).ifBlank { null },
                lastUsed = 0,
                isActive = flag(cell(Column.ACTIVE)),
                form = form(cell(Column.FORM)),
                strength = cell(Column.STRENGTH),
                quantity = quantity,
                remaining = if (quantity > 0) remaining.coerceAtMost(quantity) else remaining,
                location = cell(Column.LOCATION),
                remind = flag(cell(Column.REMINDERS)),
                pack = template(cell(Column.PACK))
            )
        }
        return ImportedFile(CabinetData(meds), skipped)
    }

    private fun number(text: String): Int =
        text.replace(',', '.').toDoubleOrNull()?.toInt()?.coerceIn(0, 999) ?: 0

    /** Blank means yes, so sheets without the column keep reminders on. */
    private fun flag(text: String): Boolean = text.trim().lowercase() !in setOf("no", "n", "false", "0", "off")

    /** "tablets" -> "Tablet"; anything unrecognised is kept as typed. */
    private fun form(text: String): String =
        MedicationForms.firstOrNull { text.lowercase().startsWith(it.lowercase().take(4)) } ?: text

    private fun template(text: String): String =
        PackTemplate.entries.firstOrNull { it.name.equals(text.trim(), true) || it.label.equals(text.trim(), true) }?.name.orEmpty()

    private val datePatterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "d MMM yyyy", "d MMMM yyyy", "MMM d, yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
    private val monthPatterns = listOf("M/yyyy", "MM/yyyy", "yyyy-MM", "MMM yyyy", "MMMM yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    /**
     * Dates as people and spreadsheets write them: ISO, South African day-first,
     * "31 Mar 2027", Excel's serial day numbers, and month-only expiries like
     * "03/2027", which mean the end of that month.
     */
    internal fun parseDate(text: String): LocalDate? {
        val t = text.trim().substringBefore('T').replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
        t.toDoubleOrNull()?.let { serial ->
            return if (serial in 20_000.0..80_000.0) EXCEL_EPOCH.plusDays(serial.toLong()) else null
        }
        datePatterns.forEach { f -> runCatching { return LocalDate.parse(t, f) } }
        monthPatterns.forEach { f -> runCatching { return YearMonth.parse(t, f).atEndOfMonth() } }
        return null
    }

    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    // ---------- CSV ----------

    /** UTF-8 with a byte order mark, so Excel shows accented names correctly. */
    fun csv(data: CabinetData): ByteArray {
        val rows = listOf(Column.entries.map { it.header }) +
            data.medications.map { med -> cells(med).let { c -> Column.entries.map { c.getValue(it).toString() } } }
        val text = rows.joinToString("\r\n") { row -> row.joinToString(",", transform = ::quote) } + "\r\n"
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
    }

    private fun quote(value: String): String =
        if (value.any { it in ",;\"\r\n" }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    /** Parses CSV with quoted fields; the delimiter (comma, semicolon or tab) is taken from the header line. */
    internal fun parseCsv(text: String): List<List<String>> {
        val body = text.removePrefix("﻿")
        val firstLine = body.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(',', ';', '\t').maxBy { d -> firstLine.count { it == d } }
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < body.length) {
            val ch = body[i]
            when {
                quoted && ch == '"' && body.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                quoted -> field.append(ch)
                ch == delimiter -> { row += field.toString(); field.clear() }
                ch == '\n' || ch == '\r' -> {
                    if (ch == '\r' && body.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    rows += row; row = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }

    // ---------- JSON ----------

    private const val JSON_VERSION = 1

    fun json(data: CabinetData, now: Instant = Instant.now()): ByteArray {
        val meds = JSONArray()
        data.medications.forEach { m ->
            meds.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("strength", m.strength)
                    .put("form", m.form)
                    .put("category", m.category)
                    .put("quantity", m.quantity)
                    .put("remaining", m.remaining)
                    .put("expiry", ExpiryDates.toLocalDate(m.expiryDate).toString())
                    .put("location", m.location)
                    .put("barcode", m.barcode ?: JSONObject.NULL)
                    .put("notes", m.description)
                    .put("remind", m.remind)
                    .put("isActive", m.isActive)
                    .put("pack", m.pack)
                    .put("lastUsed", m.lastUsed)
            )
        }
        val doses = JSONArray()
        data.doses.forEach { d ->
            doses.put(
                JSONObject()
                    .put("medicationId", d.medicationId)
                    .put("name", d.name)
                    .put("strength", d.strength)
                    .put("takenAt", Instant.ofEpochMilli(d.takenAt).toString())
            )
        }
        return JSONObject()
            .put("app", "MediCab")
            .put("version", JSON_VERSION)
            .put("exportedAt", now.toString())
            .put("medications", meds)
            .put("doses", doses)
            .toString(2)
            .toByteArray(Charsets.UTF_8)
    }

    /** A MediCab backup, or any JSON list of objects with a name and an expiry date. */
    internal fun parseJson(text: String): ImportedFile {
        val trimmed = text.trim().removePrefix("﻿")
        val root = runCatching { if (trimmed.startsWith("[")) JSONObject().put("medications", JSONArray(trimmed)) else JSONObject(trimmed) }
            .getOrElse { throw ImportException("This JSON file couldn't be read.") }
        val meds = root.optJSONArray("medications") ?: throw ImportException("This JSON file has no \"medications\" list.")
        var skipped = 0
        val medications = (0 until meds.length()).mapNotNull { i ->
            val o = meds.optJSONObject(i) ?: return@mapNotNull null.also { skipped++ }
            val name = o.optString("name").trim()
            val expiry = when (val e = o.opt("expiry") ?: o.opt("expiryDate")) {
                is Number -> ExpiryDates.toLocalDate(e.toLong())
                is String -> parseDate(e)
                else -> null
            }
            if (name.isBlank() || expiry == null) return@mapNotNull null.also { skipped++ }
            val quantity = o.optInt("quantity").coerceIn(0, 999)
            MedicationEntity(
                id = o.optInt("id"),
                name = name,
                description = o.optString("notes", o.optString("description")),
                category = o.optString("category"),
                expiryDate = ExpiryDates.toMillis(expiry),
                barcode = o.optString("barcode").takeUnless { it.isBlank() || o.isNull("barcode") },
                lastUsed = o.optLong("lastUsed"),
                isActive = o.optBoolean("isActive", true),
                form = form(o.optString("form")),
                strength = o.optString("strength"),
                quantity = quantity,
                remaining = o.optInt("remaining", quantity).coerceIn(0, 999),
                location = o.optString("location"),
                remind = o.optBoolean("remind", true),
                pack = template(o.optString("pack"))
            )
        }
        val doseArray = root.optJSONArray("doses") ?: JSONArray()
        val doses = (0 until doseArray.length()).mapNotNull { i ->
            val o = doseArray.optJSONObject(i) ?: return@mapNotNull null
            val takenAt = when (val t = o.opt("takenAt")) {
                is Number -> t.toLong()
                is String -> runCatching { Instant.parse(t).toEpochMilli() }.getOrNull()
                else -> null
            } ?: return@mapNotNull null
            DoseEntity(medicationId = o.optInt("medicationId"), name = o.optString("name"), strength = o.optString("strength"), takenAt = takenAt)
        }
        return ImportedFile(CabinetData(medications, doses), skipped)
    }

    // ---------- Excel (.xlsx) ----------

    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val STYLE_HEADER = 1
    private const val STYLE_DATE = 2
    private const val STYLE_DATE_TIME = 3

    private sealed interface Cell
    private data class Text(val value: String, val style: Int = 0) : Cell
    private data class Num(val value: Number, val style: Int = 0) : Cell

    /** A workbook with the cabinet on the first sheet and the dose history on the second. */
    fun xlsx(data: CabinetData, zone: ZoneId = ZoneId.systemDefault()): ByteArray {
        val cabinet = listOf(Column.entries.map { Text(it.header, STYLE_HEADER) }) + data.medications.map { med ->
            cells(med).let { c ->
                Column.entries.map { column ->
                    when (val v = c.getValue(column)) {
                        is LocalDate -> Num(ChronoUnit.DAYS.between(EXCEL_EPOCH, v), STYLE_DATE)
                        is Int -> Num(v)
                        else -> Text(v.toString())
                    }
                }
            }
        }
        val history = listOf(listOf("Taken", "Medication", "Strength").map { Text(it, STYLE_HEADER) }) +
            data.doses.sortedByDescending { it.takenAt }.map { d ->
                val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(d.takenAt), zone)
                val serial = ChronoUnit.SECONDS.between(EXCEL_EPOCH.atStartOfDay(), at) / 86_400.0
                listOf(Num(serial, STYLE_DATE_TIME), Text(d.name), Text(d.strength))
            }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n$xml").toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                    """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                    """<Default Extension="xml" ContentType="application/xml"/>""" +
                    """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                    """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
                    """<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
                    """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                    """</Types>"""
            )
            put(
                "_rels/.rels",
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="$REL_NS/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
            )
            put(
                "xl/workbook.xml",
                """<workbook xmlns="$MAIN_NS" xmlns:r="$REL_NS"><sheets>""" +
                    """<sheet name="Cabinet" sheetId="1" r:id="rId1"/><sheet name="Dose history" sheetId="2" r:id="rId2"/>""" +
                    """</sheets></workbook>"""
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="$REL_NS/worksheet" Target="worksheets/sheet1.xml"/>""" +
                    """<Relationship Id="rId2" Type="$REL_NS/worksheet" Target="worksheets/sheet2.xml"/>""" +
                    """<Relationship Id="rId3" Type="$REL_NS/styles" Target="styles.xml"/></Relationships>"""
            )
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheetXml(cabinet, listOf(28, 16, 12, 16, 10, 11, 13, 14, 16, 40, 11, 9, 13)))
            put("xl/worksheets/sheet2.xml", sheetXml(history, listOf(18, 28, 16)))
        }
        return out.toByteArray()
    }

    private val STYLES =
        """<styleSheet xmlns="$MAIN_NS">""" +
            """<numFmts count="2"><numFmt numFmtId="164" formatCode="yyyy-mm-dd"/><numFmt numFmtId="165" formatCode="yyyy-mm-dd hh:mm"/></numFmts>""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="4">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    private fun sheetXml(rows: List<List<Cell>>, widths: List<Int>): String = buildString {
        append("""<worksheet xmlns="$MAIN_NS" xmlns:r="$REL_NS">""")
        // Keep the header row in view while scrolling.
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("<cols>")
        widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        append("</cols><sheetData>")
        rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, cell ->
                val ref = columnName(c) + (r + 1)
                when (cell) {
                    is Text -> if (cell.value.isNotEmpty() || cell.style != 0) {
                        append("""<c r="$ref" t="inlineStr"${style(cell.style)}><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>""")
                    }
                    is Num -> append("""<c r="$ref"${style(cell.style)}><v>${cell.value}</v></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun style(id: Int) = if (id == 0) "" else """ s="$id""""

    private fun columnName(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            name.insert(0, 'A' + (n - 1) % 26)
            n = (n - 1) / 26
        }
        return name.toString()
    }

    private fun columnIndex(ref: String): Int =
        ref.takeWhile(Char::isLetter).uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1

    private fun escape(text: String): String = buildString {
        text.forEach { ch ->
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch < ' ' && ch != '\n' && ch != '\t' -> Unit // not allowed in XML
                else -> append(ch)
            }
        }
    }

    /** The first sheet of a workbook as a table of text; numbers stay as Excel wrote them (dates are day serials). */
    internal fun parseXlsx(bytes: ByteArray): List<List<String>> {
        val parts = unzip(bytes)
        val workbook = parts["xl/workbook.xml"]?.let(::xml) ?: throw ImportException("This Excel file couldn't be read.")
        val firstSheet = workbook.getElementsByTagNameNS("*", "sheet").item(0) as? Element
            ?: throw ImportException("This Excel file has no sheets.")
        val relId = firstSheet.getAttributeNS(REL_NS, "id")
        val target = parts["xl/_rels/workbook.xml.rels"]?.let(::xml)?.let { rels ->
            val list = rels.getElementsByTagNameNS("*", "Relationship")
            (0 until list.length).map { list.item(it) as Element }.firstOrNull { it.getAttribute("Id") == relId }?.getAttribute("Target")
        } ?: "worksheets/sheet1.xml"
        val sheetPath = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
        val sheet = parts[sheetPath]?.let(::xml) ?: throw ImportException("This Excel file's first sheet is missing.")

        val shared = parts["xl/sharedStrings.xml"]?.let(::xml)?.let { doc ->
            val items = doc.getElementsByTagNameNS("*", "si")
            (0 until items.length).map { i -> textOf(items.item(i) as Element) }
        }.orEmpty()

        val rows = sheet.getElementsByTagNameNS("*", "row")
        return (0 until rows.length).map { r ->
            val cells = (rows.item(r) as Element).getElementsByTagNameNS("*", "c")
            val values = sortedMapOf<Int, String>()
            for (c in 0 until cells.length) {
                val cell = cells.item(c) as Element
                val col = cell.getAttribute("r").takeIf { it.isNotEmpty() }?.let(::columnIndex) ?: (values.keys.lastOrNull()?.plus(1) ?: 0)
                val raw = cell.getElementsByTagNameNS("*", "v").item(0)?.textContent.orEmpty()
                values[col] = when (cell.getAttribute("t")) {
                    "s" -> raw.toIntOrNull()?.let(shared::getOrNull).orEmpty()
                    "inlineStr" -> textOf(cell)
                    "b" -> if (raw == "1") "Yes" else "No"
                    else -> raw.removeSuffix(".0")
                }
            }
            val width = (values.keys.maxOrNull() ?: -1) + 1
            (0 until width).map { values[it].orEmpty() }
        }
    }

    /** The text runs of a shared or inline string, leaving out phonetic guides. */
    private fun textOf(element: Element): String {
        val runs = element.getElementsByTagNameNS("*", "t")
        return (0 until runs.length).map { runs.item(it) }
            .filter { it.parentNode?.localName != "rPh" }
            .joinToString("") { it.textContent }
    }

    private const val MAX_PART_BYTES = 20 * 1024 * 1024

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val parts = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.isDirectory) return@forEach
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_PART_BYTES) throw ImportException("This Excel file is too large to import.")
                }
                parts[entry.name.removePrefix("/")] = out.toByteArray()
            }
        }
        return parts
    }

    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        // Files come from outside the app: never fetch external entities.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement

    // ---------- Reading any file ----------

    /** Reads an import, choosing the format from the file name or, failing that, the content. */
    fun read(bytes: ByteArray, fileName: String?): ImportedFile {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val start = bytes.toString(Charsets.UTF_8).take(64).trimStart('﻿', ' ', '\n', '\r', '\t')
        return when {
            ext == "xls" -> throw ImportException("Old .xls files aren't supported. Save it as .xlsx or CSV first.")
            ext == "xlsx" || (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) ->
                fromTable(parseXlsx(bytes))
            ext == "json" || start.startsWith("{") || start.startsWith("[") -> parseJson(bytes.toString(Charsets.UTF_8))
            else -> fromTable(parseCsv(bytes.toString(Charsets.UTF_8)))
        }
    }
}
