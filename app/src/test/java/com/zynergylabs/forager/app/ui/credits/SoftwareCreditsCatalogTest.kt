package com.zynergylabs.forager.app.ui.credits

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What `assets/credits/` says, read through the loader the Open-source software page uses (dispatch
 * 2026-10-13-05). The libraries the dispatch names are checked by name and licence, written out here;
 * that every shipped module is covered is the build's own check (`verifyCreditsCoverEveryDependency`),
 * which reads the artifacts this test cannot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoftwareCreditsCatalogTest {

    private val assets = ApplicationProvider.getApplicationContext<Application>().assets
    private val groups = SoftwareCredits.load(assets).getOrThrow()
    private val libraries = groups.flatMap { it.libraries }

    private fun licenceOf(namePart: String): String {
        val matches = libraries.filter { it.name.startsWith(namePart) }
        assertEquals("exactly one library's name starts \"$namePart\": ${matches.map { it.name }}", 1, matches.size)
        return matches.single().licence
    }

    @Test
    fun `the libraries the dispatch names are credited under their own licences`() {
        assertEquals("BSD-2-Clause", licenceOf("MapLibre Native"))
        assertEquals("BSD-2-Clause", licenceOf("MapLibre Android Gestures"))
        assertEquals("MIT", licenceOf("NGA MGRS"))
        assertEquals("MIT", licenceOf("NGA Grid"))
        assertEquals("MIT", licenceOf("NGA Simple Features"))
        assertEquals("MIT", licenceOf("NGA Color"))
        assertEquals("OFL-1.1", licenceOf("Noto Sans"))
        assertEquals("MPL-2.0", licenceOf("The Public Suffix List"))
        listOf("AndroidX", "Retrofit", "OkHttp", "kotlinx").forEach { assertEquals(it, "Apache-2.0", licenceOf(it)) }
        listOf("Compose", "Room", "CameraX").forEach { part ->
            assertTrue("$part is named in the AndroidX entry", libraries.single { it.name.startsWith("AndroidX") }.name.contains(part))
        }
    }

    /**
     * Dispatch 2026-10-18-04, item 1: the map fetches no fonts any more (no style declares `glyphs`), so the app ships
     * and draws no Open Sans, and the page no longer credits a font it does not use. This replaces the line above that
     * pinned Open Sans's licence, which held while the map fetched its glyphs from MapLibre's demo server.
     */
    @Test
    fun `no map label font is credited, now the map fetches none`() {
        assertTrue("still credited: ${libraries.map { it.name }}", libraries.none { it.name.startsWith("Open Sans") })
    }

    @Test
    fun `every library has a credit line, and every licence and notice text is there and is its own`() {
        libraries.forEach { assertTrue("${it.name} has a credit line", it.credit.isNotEmpty() && it.credit.all(String::isNotBlank)) }
        groups.forEach { group ->
            assertTrue("${group.licence.id} has libraries under it", group.libraries.isNotEmpty())
            assertTrue("${group.licence.id}'s text is not blank", group.text.isNotBlank())
        }
        val text = groups.associate { it.licence.id to it.text }
        assertTrue(text.getValue("Apache-2.0").contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"))
        assertTrue(text.getValue("MPL-2.0").contains("1.1. \"Contributor\""))
        assertTrue(text.getValue("OFL-1.1").contains("SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007"))
        assertTrue(text.getValue("MIT").contains("THE SOFTWARE IS PROVIDED \"AS IS\""))
        assertTrue(text.getValue("BSD-2-Clause").contains("Redistributions in binary form must reproduce the above copyright"))

        val notices = groups.flatMap { it.notices }.associate { (notice, body) -> notice.file to body }
        val mapLibre = notices.getValue("notices/maplibre-native-android-v13.5.0.txt")
        assertTrue("MapLibre's notices start with its own licence", mapLibre.startsWith("MapLibre Native Android"))
        // Native components compiled into the SDK, which no POM names: two of them, by their own names.
        assertTrue("MapLibre's notices carry its native components", mapLibre.contains("earcut") && mapLibre.contains("FreeType"))
        assertTrue(notices.getValue("notices/okhttp-publicsuffix-NOTICE.txt").contains("Mozilla Public License, v. 2.0"))
        assertTrue(notices.getValue("notices/jakarta-inject-api-NOTICE.md").startsWith("# Notices for Eclipse Jakarta Dependency Injection"))
    }

    @Test
    fun `a library naming a licence the list does not have fails the load, rather than vanishing from the page`() {
        val catalog = SoftwareCatalog(
            about = "",
            licences = listOf(LicenceText("MIT", "MIT License", "licenses/mit.txt")),
            libraries = listOf(CreditedLibrary(name = "Orphan", licence = "GPL-3.0", credit = listOf("Copyright nobody"))),
        )
        val result = runCatching { SoftwareCredits.groups(catalog) { "text" } }
        assertTrue("the load failed", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("GPL-3.0"))
    }

    @Test
    fun `a blank licence file fails the load`() {
        val catalog = SoftwareCatalog(
            about = "",
            licences = listOf(LicenceText("MIT", "MIT License", "licenses/mit.txt")),
            libraries = listOf(CreditedLibrary(name = "Some", licence = "MIT", credit = listOf("Copyright someone"))),
        )
        assertTrue(runCatching { SoftwareCredits.groups(catalog) { "  \n" } }.isFailure)
    }

    @Test
    fun `licence paragraphs join back to the text`() {
        val text = "First line\nstill first\n\n  Second\n\n\nThird\n"
        assertEquals(listOf("First line\nstill first", "  Second", "Third"), licenceParagraphs(text))
    }
}
