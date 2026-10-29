package com.example.medicat.data.transfer

import com.example.medicat.data.local.DoseEntity
import com.example.medicat.data.local.MedicationEntity
import com.example.medicat.domain.ExpiryDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CabinetFilesTest {

    private val panado = MedicationEntity(
        id = 7,
        name = "Panado Tablets",
        description = "Contains Paracetamol\nCommon uses (openFDA):\n• Reduces fever, \"fast\"",
        category = "Pain relief",
        expiryDate = ExpiryDates.toMillis(LocalDate.of(2027, 3, 31)),
        barcode = "06001234567890",
        lastUsed = 1_700_000_000_000,
        form = "Tablet",
        strength = "500 mg",
        quantity = 24,
        remaining = 16,
        location = "Bathroom",
        remind = false,
        pack = "CARTON"
    )
    private val cream = MedicationEntity(
        id = 9, name = "Bennetts, Aqueous; cream", description = "", category = "Skin care",
        expiryDate = ExpiryDates.toMillis(LocalDate.of(2026, 1, 15)), barcode = null, lastUsed = 0,
        isActive = false, form = "Cream"
    )
    private val dose = DoseEntity(medicationId = 7, name = "Panado Tablets", strength = "500 mg", takenAt = 1_760_000_000_000)
    private val data = CabinetData(listOf(panado, cream), listOf(dose))

    /** What survives a spreadsheet: everything but the ids and last-used time. */
    private fun MedicationEntity.sheetFields() = copy(id = 0, lastUsed = 0)

    @Test
    fun `csv round trip keeps every column, including quotes, commas and line breaks`() {
        val bytes = CabinetFiles.csv(data)
        assertEquals(0xEF.toByte(), bytes[0]) // byte order mark for Excel
        val back = CabinetFiles.read(bytes, "cabinet.csv")
        assertEquals(data.medications.map { it.sheetFields() }, back.data.medications)
        assertEquals(0, back.skippedRows)
    }

    @Test
    fun `excel round trip keeps every column and stores expiry as a real date`() {
        val bytes = CabinetFiles.xlsx(data, ZoneOffset.UTC)
        val rows = CabinetFiles.parseXlsx(bytes)
        assertEquals("Expiry date", rows[0][6])
        assertEquals("46477", rows[1][6]) // 31 Mar 2027 as an Excel day number
        val back = CabinetFiles.read(bytes, null)
        assertEquals(data.medications.map { it.sheetFields() }, back.data.medications)
    }

    @Test
    fun `json backup keeps ids, last used and dose history`() {
        val bytes = CabinetFiles.json(data, Instant.parse("2026-10-29T10:00:00Z"))
        val back = CabinetFiles.read(bytes, "backup.json")
        assertEquals(data.medications, back.data.medications)
        assertEquals(listOf(dose), back.data.doses)
    }

    @Test
    fun `json list from elsewhere needs only a name and an expiry`() {
        val back = CabinetFiles.read(
            """[{"name":"Allergex","expiry":"2027-06-30","quantity":30},{"name":"No date"},{"expiry":"2027-01-01"}]""".toByteArray(),
            "meds.json"
        )
        assertEquals(listOf("Allergex"), back.data.medications.map { it.name })
        assertEquals(30, back.data.medications.single().remaining)
        assertEquals(2, back.skippedRows)
    }

    @Test
    fun `hand made sheet with other headings, semicolons and a title row`() {
        val csv = """
            My medicine list;;;
            Medication;Qty;Expires;Location
            Myprodol;30;31/12/2027;Kitchen
            Corenza C;10;06/2027;
            ;;;
            Mystery pills;5;soon;
        """.trimIndent()
        val back = CabinetFiles.read(csv.toByteArray(), "list.csv")
        val meds = back.data.medications
        assertEquals(listOf("Myprodol", "Corenza C"), meds.map { it.name })
        assertEquals(LocalDate.of(2027, 12, 31), ExpiryDates.toLocalDate(meds[0].expiryDate))
        assertEquals(LocalDate.of(2027, 6, 30), ExpiryDates.toLocalDate(meds[1].expiryDate)) // month-only = end of month
        assertEquals(30, meds[0].quantity)
        assertEquals("Kitchen", meds[0].location)
        assertTrue(meds.all { it.remind && it.isActive })
        assertEquals(1, back.skippedRows)
    }

    @Test
    fun `excel file made elsewhere with shared strings`() {
        val bytes = workbook(
            sharedStrings = listOf("Name", "Expiry", "Panado", "Pack size"),
            sheet = """<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="D1" t="s"><v>3</v></c></row>""" +
                """<row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" s="1"><v>46477</v></c><c r="D2"><v>24</v></c></row>"""
        )
        val med = CabinetFiles.read(bytes, "from-excel.xlsx").data.medications.single()
        assertEquals("Panado", med.name)
        assertEquals(LocalDate.of(2027, 3, 31), ExpiryDates.toLocalDate(med.expiryDate))
        assertEquals(24, med.quantity)
    }

    @Test
    fun `dates in the formats people use`() {
        val expected = LocalDate.of(2027, 3, 31)
        listOf("2027-03-31", "2027/03/31", "31/03/2027", "31-3-2027", "31.03.2027", "31 Mar 2027", "31 March 2027", "46477", "2027-03-31T00:00:00Z", "03/2027", "Mar 2027")
            .forEach { assertEquals(it, expected, CabinetFiles.parseDate(it)) }
        assertNull(CabinetFiles.parseDate("soon"))
        assertNull(CabinetFiles.parseDate("12"))
    }

    @Test
    fun `files that can't be imported say why`() {
        val noHeader = assertThrows(ImportException::class.java) { CabinetFiles.read("a,b\n1,2".toByteArray(), "x.csv") }
        assertTrue(noHeader.message!!.contains("Expiry date"))
        assertThrows(ImportException::class.java) { CabinetFiles.read(byteArrayOf(1, 2, 3), "old.xls") }
        assertThrows(ImportException::class.java) { CabinetFiles.read("{not json".toByteArray(), "x.json") }
    }

    @Test
    fun `import summary`() {
        assertEquals(
            "Added 12 medications · 2 already in your cabinet · 1 row skipped (no name or expiry date)",
            ImportResult(added = 12, alreadyThere = 2, skippedRows = 1, doses = 0).summary()
        )
        assertEquals("Nothing new to add · 3 already in your cabinet", ImportResult(0, 3, 0, 0).summary())
        assertEquals("Added 1 medication · 4 doses of history", ImportResult(1, 0, 0, 4).summary())
    }

    /** A minimal workbook laid out the way Excel saves one, with shared strings. */
    private fun workbook(sharedStrings: List<String>, sheet: String): ByteArray {
        val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(xml.toByteArray()); zip.closeEntry()
            }
            put("xl/workbook.xml", """<workbook xmlns="$main" xmlns:r="$rel"><sheets><sheet name="Meds" sheetId="1" r:id="rId5"/></sheets></workbook>""")
            put(
                "xl/_rels/workbook.xml.rels",
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId5" Type="$rel/worksheet" Target="/xl/worksheets/meds.xml"/></Relationships>"""
            )
            put("xl/sharedStrings.xml", """<sst xmlns="$main">${sharedStrings.joinToString("") { "<si><t>$it</t></si>" }}</sst>""")
            put("xl/worksheets/meds.xml", """<worksheet xmlns="$main"><sheetData>$sheet</sheetData></worksheet>""")
        }
        return out.toByteArray()
    }
}
