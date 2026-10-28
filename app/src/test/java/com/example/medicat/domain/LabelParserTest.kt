package com.example.medicat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LabelParserTest {

    private val today = LocalDate.of(2026, 10, 28)

    private fun lines(vararg pairs: Pair<String, Float>) = pairs.map { OcrLine(it.first, it.second) }

    @Test
    fun `typical tablet box`() {
        val result = LabelParser.parse(
            lines(
                "MYPRODOL" to 80f,
                "Ibuprofen 200 mg, Paracetamol 250 mg" to 24f,
                "24 CAPSULES" to 30f,
                "Reg. No. 33/2.8/0123" to 12f,
                "LOT AB1234   EXP 03/2027" to 14f
            ),
            today = today
        )
        assertEquals("Myprodol", result.name)
        assertEquals("200 mg", result.strength)
        assertEquals(24, result.quantity)
        assertEquals("Capsule", result.form)
        assertEquals("Pain relief", result.category)
        assertEquals(LocalDate.of(2027, 3, 31), result.expiry)
        assertEquals("AB1234", result.batch)
    }

    @Test
    fun `unknown brand falls back to the biggest line`() {
        val result = LabelParser.parse(
            lines(
                "Keep out of reach of children" to 14f,
                "ZETRIN" to 70f,
                "Cetirizine dihydrochloride 10 mg" to 22f,
                "30 film-coated tablets" to 20f
            ),
            today = today
        )
        assertEquals("Zetrin", result.name)
        assertEquals("10 mg", result.strength)
        assertEquals(30, result.quantity)
        assertEquals("Tablet", result.form)
        assertEquals("Allergy", result.category)
        assertEquals("Cetirizine", result.activeIngredient)
    }

    @Test
    fun `cabinet names win over everything`() {
        val result = LabelParser.parse(
            lines("CORENZA C" to 60f, "Cold and flu relief" to 30f),
            knownNames = listOf("Corenza C"),
            today = today
        )
        assertEquals("Corenza C", result.name)
        assertEquals("Cold & flu", result.category)
    }

    @Test
    fun `syrup bottle gives volume as pack size`() {
        val result = LabelParser.parse(
            lines(
                "Benylin" to 60f,
                "Chesty Cough syrup" to 30f,
                "Guaifenesin 100 mg/5 ml" to 18f,
                "100 ml" to 20f,
                "Use by: MAR 2027" to 12f
            ),
            today = today
        )
        assertEquals("Benylin", result.name)
        assertEquals("Liquid", result.form)
        assertEquals(100, result.quantity)
        assertEquals("100 mg/5 ml", result.strength)
        assertEquals(LocalDate.of(2027, 3, 31), result.expiry)
    }

    @Test
    fun `manufacture date is not taken as expiry`() {
        val result = LabelParser.findExpiry(
            lines("MFG 01/2025" to 12f, "EXP 01/2028" to 12f),
            today
        )
        assertEquals(LocalDate.of(2028, 1, 31), result)
    }

    @Test
    fun `date without keyword ignores manufacture lines`() {
        val result = LabelParser.findExpiry(
            lines("MFD 05.2025" to 12f, "05.2028" to 12f),
            today
        )
        assertEquals(LocalDate.of(2028, 5, 31), result)
    }

    @Test
    fun `full dates and month names are understood`() {
        assertEquals(LocalDate.of(2027, 11, 30), LabelParser.findExpiry(lines("EXP 30/11/2027" to 10f), today))
        assertEquals(LocalDate.of(2027, 6, 30), LabelParser.findExpiry(lines("EXP: JUN-27" to 10f), today))
        assertEquals(LocalDate.of(2028, 2, 29), LabelParser.findExpiry(lines("Expiry date 2028-02" to 10f), today))
    }

    @Test
    fun `anti-inflammatories get their own category`() {
        val gel = LabelParser.parse(lines("Voltaren" to 60f, "Emulgel" to 30f, "Diclofenac 1%" to 18f), today = today)
        assertEquals("Inflammation", gel.category)
        val unknown = LabelParser.parse(lines("FLEXIDOL" to 60f, "Anti-inflammatory gel" to 20f), today = today)
        assertEquals("Inflammation", unknown.category)
    }

    @Test
    fun `plasters have no dosage form`() {
        val result = LabelParser.parse(lines("Elastoplast" to 50f, "20 plasters" to 20f), today = today)
        assertEquals("Elastoplast", result.name)
        assertEquals(20, result.quantity)
        assertNull(result.form)
        assertEquals("First aid", result.category)
    }

    @Test
    fun `accumulator keeps the most common reading`() {
        val acc = LabelAccumulator()
        acc.add(LabelFields(name = "Panado", strength = "500 mg"))
        acc.add(LabelFields(name = "Panad0"))
        acc.add(LabelFields(name = "Panado", expiry = LocalDate.of(2027, 1, 31)))
        val current = acc.current()
        assertEquals("Panado", current.name)
        assertEquals("500 mg", current.strength)
        assertEquals(LocalDate.of(2027, 1, 31), current.expiry)
    }
}
