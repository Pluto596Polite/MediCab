package com.example.medicat.data.lookup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fixtures are trimmed copies of real responses. */
class UseCaseParsersTest {

    @Test
    fun `openFDA bulleted uses become points with their symptoms`() {
        val json = JSONObject(
            """{"results":[
              {"indications_and_usage":["Uses temporarily relieves minor aches and pains due to: headache toothache backache"]},
              {"indications_and_usage":["Uses • temporarily relieves minor aches and pains due to: • headache • muscular aches • the common cold • toothache • temporarily reduces fever"]}
            ]}"""
        )
        val info = UseCaseParsers.openFda(json)!!
        assertEquals("openFDA", info.source)
        assertEquals(
            listOf(
                UsePoint("Relieves minor aches and pains", listOf("Headache", "Muscular aches", "Common cold", "Toothache")),
                UsePoint("Reduces fever")
            ),
            info.points
        )
    }

    @Test
    fun `allergy label heading is shortened and keeps comma-containing symptoms whole`() {
        assertEquals(
            listOf(
                UsePoint(
                    "Relieves symptoms of hay fever or other upper respiratory allergies",
                    listOf("Runny nose", "Itchy, watery eyes", "Sneezing", "Itching of the nose or throat")
                )
            ),
            UseCaseParsers.summariseUses(
                "Uses temporarily relieves these symptoms due to hay fever or other upper respiratory allergies: • runny nose • itchy, watery eyes • sneezing • itching of the nose or throat"
            )
        )
    }

    @Test
    fun `unbulleted and all-caps uses still split into points`() {
        assertEquals(
            listOf(
                UsePoint("Relieves minor aches and pains", listOf("Headache the common cold backache toothache")),
                UsePoint("Reduces fever")
            ),
            UseCaseParsers.summariseUses("Uses temporarily relieves minor aches and pains due to: headache the common cold backache toothache temporarily reduces fever")
        )
        assertEquals(
            listOf(UsePoint("Relieves nasal congestion"), UsePoint("Reduces swelling of nasal passages")),
            UseCaseParsers.summariseUses("USES • TEMPORARILY RELIEVES NASAL CONGESTION • REDUCES SWELLING OF NASAL PASSAGES")
        )
    }

    @Test
    fun `notes block is bulleted and replaces an older one`() {
        val block = UsesNote.format(
            UseInfo(listOf(UsePoint("Relieves minor aches and pains", listOf("Headache", "Toothache")), UsePoint("Reduces fever")), "openFDA")
        )
        assertEquals(
            "Common uses (openFDA):\n• Relieves minor aches and pains\n   – Headache\n   – Toothache\n• Reduces fever",
            block
        )
        assertEquals("Contains Paracetamol\nBatch A1", UsesNote.remove("Contains Paracetamol\n$block\nBatch A1"))
        assertEquals("Contains Paracetamol", UsesNote.remove("Contains Paracetamol\nCommon uses (Wikipedia): old single line"))
    }

    @Test
    fun `wikipedia summary gives the sentence about use`() {
        val json = JSONObject(
            """{"type":"standard","extract":"Ibuprofen is a nonsteroidal anti-inflammatory drug (NSAID) that is used to relieve pain, fever, and inflammation. This includes painful menstrual periods, migraines, and rheumatoid arthritis."}"""
        )
        val info = UseCaseParsers.wikipedia(json)!!
        assertEquals("Wikipedia", info.source)
        assertEquals(
            listOf("Pain", "Fever", "Inflammation", "Painful menstrual periods", "Migraines", "Rheumatoid arthritis"),
            info.points.map { it.text }
        )
    }

    @Test
    fun `wikipedia sentence without a use list stays one point`() {
        val json = JSONObject("""{"type":"standard","extract":"Cetirizine is a second-generation antihistamine medication."}""")
        assertEquals(listOf("Cetirizine is a second-generation antihistamine medication."), UseCaseParsers.wikipedia(json)!!.points.map { it.text })
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
