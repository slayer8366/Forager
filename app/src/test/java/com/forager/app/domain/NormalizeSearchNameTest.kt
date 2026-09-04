package com.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeSearchNameTest {

    @Test
    fun `lowercases`() {
        assertEquals("lions mane", normalizeSearchName("Lions Mane"))
    }

    @Test
    fun `strips apostrophes without leaving a gap`() {
        assertEquals("lions mane", normalizeSearchName("lion's mane"))
        assertEquals("lions mane", normalizeSearchName("lion’s mane"))
    }

    @Test
    fun `turns hyphens into spaces`() {
        assertEquals("lion mane", normalizeSearchName("lion-mane"))
    }

    @Test
    fun `the stored common name and the motivating typed query normalize identically as a prefix relationship`() {
        // The exact case from the dispatch this was built from: "lion's-mane mushroom" (stored)
        // must be found by "lions mane" (typed).
        val stored = normalizeSearchName("lion's-mane mushroom")
        val typed = normalizeSearchName("lions mane")
        assertEquals("lions mane mushroom", stored)
        assertEquals("lions mane", typed)
        assertEquals(stored, typed + " mushroom")
    }

    @Test
    fun `strips diacritics`() {
        assertEquals("cafe", normalizeSearchName("café"))
    }

    @Test
    fun `collapses surrounding and internal punctuation to single spaces`() {
        assertEquals("black trumpet", normalizeSearchName("  Black, Trumpet!  "))
    }

    @Test
    fun `collapses repeated whitespace`() {
        assertEquals("candy cap", normalizeSearchName("candy   cap"))
    }

    @Test
    fun `a query that is only punctuation normalizes to empty`() {
        assertEquals("", normalizeSearchName("--"))
    }
}
