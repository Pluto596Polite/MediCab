package com.example.medicat.data.lookup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are trimmed copies of real responses. */
class UseCaseParsersTest {

    @Test
    fun `openFDA bulleted uses become a readable list`() {
        val json = JSONObject(
            """{"results":[
              {"indications_and_usage":["Uses temporarily relieves minor aches and pains due to: headache toothache backache"]},
              {"indications_and_usage":["Uses • temporarily relieves minor aches and pains due to: • headache • muscular aches • toothache • temporarily reduces fever"]}
            ]}"""
        )
        val info = UseCaseParsers.openFda(json)!!
        assertEquals("openFDA", info.source)
        assertEquals(
            "Temporarily relieves minor aches and pains due to: headache, muscular aches, toothache, temporarily reduces fever",
            info.text
        )
    }

    @Test
    fun `wikipedia summary gives the sentence about use`() {
        val json = JSONObject(
            """{"type":"standard","extract":"Ibuprofen is a nonsteroidal anti-inflammatory drug (NSAID) that is used to relieve pain, fever, and inflammation. This includes painful menstrual periods, migraines, and rheumatoid arthritis."}"""
        )
        val info = UseCaseParsers.wikipedia(json)!!
        assertEquals("Wikipedia", info.source)
        assertTrue(info.text.startsWith("Ibuprofen is a nonsteroidal anti-inflammatory drug"))
    }

    @Test
    fun `wikipedia page about something else is ignored`() {
        // "Panado" redirects to a dish on Wikipedia.
        val json = JSONObject("""{"type":"standard","extract":"Breaded cutlet or braised cutlet is a dish made from coating a cutlet of meat with breading."}""")
        assertNull(UseCaseParsers.wikipedia(json))
        assertNull(UseCaseParsers.wikipedia(JSONObject("""{"type":"disambiguation","extract":"Aspirin may refer to a drug."}""")))
    }

    @Test
    fun `local ingredient names map to US ones for openFDA`() {
        assertEquals("acetaminophen", UseCaseParsers.usName("Paracetamol"))
        assertEquals("ibuprofen", UseCaseParsers.usName("ibuprofen"))
    }
}
