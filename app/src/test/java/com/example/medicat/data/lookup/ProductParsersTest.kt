package com.example.medicat.data.lookup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fixtures are trimmed copies of real responses from each service. */
class ProductParsersTest {

    @Test
    fun `open products facts medicine`() {
        val json = JSONObject(
            """{"code":"5017123055232","product":{"brands":"Crescent Pharma",
            "categories_tags":["en:health-beauty","en:health-care","en:medicine-drugs","en:Paracetamol"],
            "generic_name":"Paracetamol tablets","product_name":"Paracetamol - 500MG tablets, box of 32",
            "quantity":"32 pcs"},"status":1}"""
        )
        val info = ProductParsers.openFacts(json, "Open Products Facts")!!
        assertEquals("Paracetamol", info.name)
        assertEquals("Pain relief", info.category)
        assertEquals("Crescent Pharma · Paracetamol tablets · 500MG tablets, box of 32 · 32 pcs", info.description)
    }

    @Test
    fun `open facts not found`() {
        assertNull(ProductParsers.openFacts(JSONObject("""{"status":0}"""), "x"))
    }

    @Test
    fun `openFDA label`() {
        val json = JSONObject(
            """{"results":[{"purpose":["Purpose Pain reliever/Fever reducer"],
            "openfda":{"brand_name":["ADVIL"],"generic_name":["IBUPROFEN"],"upc":["0305730150200"]}}]}"""
        )
        val info = ProductParsers.openFda(json)!!
        assertEquals("Advil", info.name)
        assertEquals("Pain relief", info.category)
        assertEquals("Ibuprofen · Pain reliever/Fever reducer", info.description)
    }

    @Test
    fun `upcitemdb title is split into name and details`() {
        val json = JSONObject(
            """{"code":"OK","items":[{"title":"Advil Pain Reliever/Fever Reducer Tablets - Ibuprofen (NSAID) - 24ct",
            "brand":"Advil","category":"Health & Beauty > Health Care"}]}"""
        )
        val info = ProductParsers.upcItemDb(json)!!
        assertEquals("Advil Pain Reliever/Fever Reducer Tablets", info.name)
        assertEquals("Ibuprofen (NSAID) · 24ct", info.description)
        assertEquals("Pain relief", info.category)
    }

    @Test
    fun `category tags are only a fallback`() {
        // "Hot & Cold" in the store category must not beat the product's own words.
        assertEquals("Pain relief", CategoryGuesser.guess("Paracetamol caplets", "First Aid > Hot & Cold Therapies"))
        assertEquals("Allergy", CategoryGuesser.guess("Allergex tablets", ""))
        assertEquals("", CategoryGuesser.guess("Mystery item", "Misc"))
    }
}
