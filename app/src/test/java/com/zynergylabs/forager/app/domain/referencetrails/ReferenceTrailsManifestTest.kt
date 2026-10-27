package com.zynergylabs.forager.app.domain.referencetrails

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 8d (dispatch 2026-10-26-24): the manifest's two fetched dates, and the card's Sources line in each state. The
 * fixtures are in the served file's shape (-1718) with invented values; each source's `dates` object and the file's
 * `built_at` carry other dates, so a reader that took either would show the wrong day.
 */
class ReferenceTrailsManifestTest {

    private val warnings = mutableListOf<String>()
    private val warn: (String) -> Unit = { warnings += it }

    private fun parse(text: String) = parseReferenceTrailsManifest(text, warn)

    @Test
    fun `each source's fetched date is read by its layer, and built_at, dates and unknown keys are ignored`() {
        val dates = parse(manifest()).getOrThrow()
        assertEquals(ReferenceTrailsSourceDates(forestService = LocalDate.of(2026, 10, 24), blm = LocalDate.of(2026, 10, 25)), dates)
        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun `the sources are matched by layer, not by their order in the file`() {
        val dates = parse(manifest(sources = listOf(BLM_SOURCE, FS_SOURCE))).getOrThrow()
        assertEquals(LocalDate.of(2026, 10, 24), dates.forestService)
        assertEquals(LocalDate.of(2026, 10, 25), dates.blm)
    }

    @Test
    fun `a missing source leaves only its date out, and says so`() {
        val dates = parse(manifest(sources = listOf(FS_SOURCE))).getOrThrow()
        assertEquals(LocalDate.of(2026, 10, 24), dates.forestService)
        assertNull(dates.blm)
        assertEquals(listOf("The manifest has no 'blm_trails' source; its date is left out."), warnings)
    }

    @Test
    fun `a source with no fetched date, or one that is not a date, leaves its date out, and says so`() {
        val noFetched = parse(manifest(sources = listOf(FS_SOURCE, BLM_SOURCE.replace(""""fetched":"2026-10-25",""", "")))).getOrThrow()
        assertNull(noFetched.blm)
        assertEquals(LocalDate.of(2026, 10, 24), noFetched.forestService)
        val notADate = parse(manifest(sources = listOf(FS_SOURCE.replace("2026-10-24", "last week"), BLM_SOURCE))).getOrThrow()
        assertNull(notADate.forestService)
        assertEquals(LocalDate.of(2026, 10, 25), notADate.blm)
        assertEquals(
            listOf(
                "The manifest's 'blm_trails' source has no fetched date; it is left out.",
                "The manifest's 'fs_trails' fetched value 'last week' is not a date; it is left out.",
            ),
            warnings,
        )
    }

    @Test
    fun `text that is not a manifest is a failure, so the caller keeps its copy`() {
        listOf("not json", "[]", """{"run_id":"x"}""", """{"sources":{}}""").forEach { text ->
            assertTrue("'$text' must fail", parse(text).isFailure)
        }
    }

    @Test
    fun `the Sources line with both dates, in the app's date style`() {
        val text = referenceTrailsSourcesText(ReferenceTrailsSourceDates(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25)), ::usDate)
        assertEquals("Sources: Forest Service data from Oct 24, 2026 · CC BY 4.0. BLM data from Oct 25, 2026 · public domain.", text.line)
        assertNull(text.hint)
    }

    @Test
    fun `the Sources line with no copy at all, and the line under it`() {
        val text = referenceTrailsSourcesText(null, ::usDate)
        assertEquals("Sources: Forest Service · CC BY 4.0. BLM · public domain.", text.line)
        assertEquals("Dates show once you've been online.", text.hint)
    }

    @Test
    fun `the Sources line with one source's date missing leaves only that date out, with no hint`() {
        val text = referenceTrailsSourcesText(ReferenceTrailsSourceDates(LocalDate.of(2026, 10, 24), null), ::usDate)
        assertEquals("Sources: Forest Service data from Oct 24, 2026 · CC BY 4.0. BLM · public domain.", text.line)
        assertNull("a copy exists, so the phone has been online", text.hint)
    }

    private fun usDate(date: LocalDate): String = date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US))

    companion object {
        const val FS_SOURCE =
            """{"layer":"fs_trails","name":"An invented Forest Service layer","licence":"CC BY 4.0","credit":"USDA Forest Service","fetched":"2026-10-24","dates":{"metadata_published":"2025-01-02","catalog":"2022-03-04"},"dates_note":"invented"}"""
        const val BLM_SOURCE =
            """{"layer":"blm_trails","name":"An invented BLM layer","layers":[2,3],"licence":"Public domain","credit":"Bureau of Land Management","fetched":"2026-10-25","dates":{"catalog_modified":"2026-09-30"},"dates_note":"invented"}"""

        /** A manifest in the served file's shape (-1718), with invented values and a key the app does not know. */
        fun manifest(sources: List<String> = listOf(FS_SOURCE, BLM_SOURCE)): String =
            """{"run_id":"reference-trails-20261001T000000Z","built_at":"2026-10-01T00:00:00Z","file":{"path":"reference-trails.pmtiles","size":1,"sha256":"00"},"features":{"fs_trails":1,"blm_trails":1},"sources":[${sources.joinToString(",")}],"coverage":{"note":"invented"},"a_key_added_later":{"x":1}}"""
    }
}
