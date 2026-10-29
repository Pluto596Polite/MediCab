package com.example.medicat.data.lookup

import com.example.medicat.domain.LabelFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fixture rows are copied from the generated assets/sa_medicines.tsv. */
class MedicineCatalogueTest {

    private val catalogue = MedicineCatalogue.parse(
        """
        # comment
        Adco-Dol	Codeine phosphate + Doxylamine + Paracetamol	10 mg + 5 mg + 450 mg	Tablet	20	S3	Pain relief	0
        Allergex	Chlorphenamine	4 mg	Tablet	30	S2	Allergy	0	CARTON
        Allergex Non Drowsy Tablets	Loratadine	10 mg	Tablet	30	S2	Allergy	0
        Med-Lemon Flu & Congestion Hot Medication with Vitamin C	Phenylephrine hydrochloride + Paracetamol	10 mg + 600 mg		10	S1	Cold & flu	1
        Panado Plus	Ibuprofen + Paracetamol	200 mg + 250 mg	Capsule	20	S2	Inflammation	0
        Panado Tablets	Paracetamol	500 mg	Tablet	100	S1	Pain relief	0
        Panado Tablets	Paracetamol	500 mg	Tablet	50	S1	Pain relief	0
        Paracetamol 500 Austell	Paracetamol	500 mg	Tablet	20	S0	Pain relief	0
        broken row
        """.trimIndent().lineSequence()
    )

    @Test
    fun `parses rows and skips comments and broken lines`() {
        assertEquals(8, catalogue.medicines.size)
        val med = catalogue.medicines.first { it.name == "Panado Plus" }
        assertEquals("Capsule", med.form)
        assertEquals(20, med.packSize)
        assertEquals("Inflammation", med.category)
    }

    @Test
    fun `template column is optional`() {
        assertEquals("CARTON", catalogue.search("allergex").first { it.ingredients == "Chlorphenamine" }.template)
        assertEquals("", catalogue.medicines.first { it.name == "Panado Plus" }.template)
    }

    @Test
    fun `name prefix comes first, shortest name and smallest pack first`() {
        val names = catalogue.search("pan").map { "${it.name} ${it.packSize}" }
        assertEquals(listOf("Panado Plus 20", "Panado Tablets 50", "Panado Tablets 100"), names)
    }

    @Test
    fun `every typed word must start a word of the name, strength or ingredients`() {
        assertEquals(listOf("Allergex Non Drowsy Tablets"), catalogue.search("allergex lorat").map { it.name })
        assertEquals(listOf("Panado Tablets"), catalogue.search("panado 500").map { it.name }.distinct())
    }

    @Test
    fun `ingredient search finds products named after it before others containing it`() {
        val names = catalogue.search("paracetamol").map { it.name }
        assertEquals("Paracetamol 500 Austell", names.first())
        assertTrue("Panado Tablets" in names)
    }

    @Test
    fun `hyphens and case don't matter`() {
        assertEquals("Med-Lemon Flu & Congestion Hot Medication with Vitamin C", catalogue.search("MED LEMON").single().name)
        assertEquals("Adco-Dol", catalogue.search("adco-d").single().name)
    }

    @Test
    fun `one letter is too short to search`() {
        assertTrue(catalogue.search("p").isEmpty())
    }

    @Test
    fun `spots the most specific listed product in label text`() {
        assertEquals("Panado Tablets", catalogue.productIn("PANADO\nParacetamol 500 mg\n24 TABLETS"))
        assertEquals("Panado Plus", catalogue.productIn("Panado® Plus\n20 capsules"))
        assertEquals("Allergex", catalogue.productIn("ALLERGEX 4mg\n30 tablets"))
    }

    @Test
    fun `ingredient words aren't mistaken for a brand`() {
        assertNull(catalogue.productIn("Paracetamol 500 mg tablets"))
    }

    @Test
    fun `best match uses strength and pack size`() {
        val med = catalogue.bestMatch(LabelFields(name = "Panado", strength = "500 mg", quantity = 100))!!
        assertEquals("Panado Tablets", med.name)
        assertEquals(100, med.packSize)
        assertEquals("Panado Plus", catalogue.bestMatch(LabelFields(name = "Panado", strength = "200 mg"))!!.name)
    }

    @Test
    fun `best match needs the listed name`() {
        assertNull(catalogue.bestMatch(LabelFields(name = "Pain Relief Tablets")))
        assertNull(catalogue.bestMatch(LabelFields(strength = "500 mg")))
    }

    @Test
    fun `bundled list loads and finds common brands`() {
        val asset = listOf(File("src/main/assets/sa_medicines.tsv"), File("app/src/main/assets/sa_medicines.tsv")).first { it.exists() }
        val real = asset.bufferedReader().useLines(MedicineCatalogue::parse)
        assertTrue(real.medicines.size > 10_000)
        listOf("Panado", "Myprodol", "Allergex", "Corenza", "Sinutab").forEach { brand ->
            assertTrue(brand, real.search(brand).isNotEmpty())
        }
        // Hand-added unregistered products, and every template is one the app knows.
        assertEquals("SYRUP", real.search("bioplus").first().template)
        assertEquals("CARTON", real.search("allergex 4 mg").first().template)
        val templates = com.example.medicat.ui.pack.PackTemplate.entries.map { it.name } + ""
        assertTrue(real.medicines.all { it.template in templates })
    }
}
