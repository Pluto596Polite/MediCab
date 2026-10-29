package com.example.medicat.data.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are trimmed copies of GitHub's releases/latest response. */
class ReleaseParsersTest {

    private val arm64Phone = listOf("arm64-v8a", "armeabi-v7a", "armeabi")

    private fun release(tag: String = "v1.1.0", asset: String = "MediCab-1.1.0.apk", url: String? = null, prerelease: Boolean = false) = JSONObject(
        """{"tag_name":"$tag","name":"MediCab 1.1.0","body":"- Export to Excel\r\n- Faster search","draft":false,"prerelease":$prerelease,
        "assets":[{"name":"checksums.txt","browser_download_url":"https://github.com/Pluto596Polite/MediCab/releases/download/$tag/checksums.txt","size":90},
                  {"name":"$asset","size":31457280,
                   "browser_download_url":"${url ?: "https://github.com/Pluto596Polite/MediCab/releases/download/$tag/$asset"}"}]}"""
    )

    @Test
    fun `latest release with an apk`() {
        val r = ReleaseParsers.latest(release(), arm64Phone)!!
        assertEquals("1.1.0", r.version)
        assertEquals("- Export to Excel\r\n- Faster search", r.notes)
        assertTrue(r.apkUrl.endsWith("/MediCab-1.1.0.apk"))
        assertEquals(31457280L, r.sizeBytes)
    }

    @Test
    fun `releases that can't be installed are ignored`() {
        assertNull(ReleaseParsers.latest(release(asset = "MediCab-1.1.0.aab"), arm64Phone))
        assertNull(ReleaseParsers.latest(release(url = "https://example.com/MediCab.apk"), arm64Phone))
        assertNull(ReleaseParsers.latest(release(prerelease = true), arm64Phone))
        assertNull(ReleaseParsers.latest(JSONObject("""{"message":"Not Found"}"""), arm64Phone))
    }

    @Test
    fun `the apk for the phone's processor wins, then the universal one`() {
        val names = listOf("MediCab-1.1.0-universal.apk", "MediCab-1.1.0-armeabi-v7a.apk", "MediCab-1.1.0-arm64-v8a.apk")
        val json = JSONObject(
            """{"tag_name":"v1.1.0","assets":[${names.joinToString(",") {
                """{"name":"$it","size":1,"browser_download_url":"https://github.com/Pluto596Polite/MediCab/releases/download/v1.1.0/$it"}"""
            }}]}"""
        )
        assertTrue(ReleaseParsers.latest(json, arm64Phone)!!.apkUrl.endsWith("arm64-v8a.apk"))
        assertTrue(ReleaseParsers.latest(json, listOf("armeabi-v7a", "armeabi"))!!.apkUrl.endsWith("armeabi-v7a.apk"))
        assertTrue(ReleaseParsers.latest(json, listOf("x86"))!!.apkUrl.endsWith("universal.apk"))
    }

    @Test
    fun `version comparison`() {
        assertTrue(ReleaseParsers.isNewer("1.1.0", "1.0.0"))
        assertTrue(ReleaseParsers.isNewer("v1.10.0", "1.9.2"))
        assertTrue(ReleaseParsers.isNewer("2.0", "1.9.9"))
        assertTrue(ReleaseParsers.isNewer("1.0.1", "1.0"))
        assertFalse(ReleaseParsers.isNewer("1.0.0", "1.0.0"))
        assertFalse(ReleaseParsers.isNewer("1.0.0", "1.0.1"))
        assertFalse(ReleaseParsers.isNewer("1.1.0-beta", "1.1.0"))
    }
}
