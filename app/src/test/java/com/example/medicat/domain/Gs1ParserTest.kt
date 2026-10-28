package com.example.medicat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class Gs1ParserTest {

    @Test
    fun `EAN-13 is normalised to GTIN-14`() {
        val result = Gs1Parser.parse("6001234567890")
        assertEquals("06001234567890", result.key)
        assertNull(result.expiry)
    }

    @Test
    fun `raw DataMatrix with GS separator yields gtin, expiry and batch`() {
        val raw = "0106001234567890" + "17270331" + "10AB123\u001D" + "21SERIAL9"
        val result = Gs1Parser.parse(raw)
        assertEquals("06001234567890", result.key)
        assertEquals(LocalDate.of(2027, 3, 31), result.expiry)
        assertEquals("AB123", result.batch)
    }

    @Test
    fun `symbology identifier prefix is ignored`() {
        val result = Gs1Parser.parse("]d20106001234567890172612")
        assertEquals("06001234567890", result.key)
    }

    @Test
    fun `day 00 means last day of month`() {
        val result = Gs1Parser.parse("010600123456789017280200")
        assertEquals(LocalDate.of(2028, 2, 29), result.expiry)
    }

    @Test
    fun `bracketed human readable form is parsed`() {
        val result = Gs1Parser.parse("(01)06001234567890(17)261130(10)LOT42")
        assertEquals("06001234567890", result.key)
        assertEquals(LocalDate.of(2026, 11, 30), result.expiry)
        assertEquals("LOT42", result.batch)
    }

    @Test
    fun `scanning EAN-13 and DataMatrix of same pack gives same key`() {
        val fromBox = Gs1Parser.parse("6001234567890").key
        val fromMatrix = Gs1Parser.parse("010600123456789017270331").key
        assertEquals(fromBox, fromMatrix)
    }

    @Test
    fun `non GS1 QR content is kept as is`() {
        val result = Gs1Parser.parse("https://example.com/med/42")
        assertEquals("https://example.com/med/42", result.key)
        assertNull(result.expiry)
    }

    @Test
    fun `invalid expiry month is ignored`() {
        assertNull(Gs1Parser.parse("010600123456789017271301").expiry)
    }
}
