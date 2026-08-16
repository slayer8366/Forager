package com.forager.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class INaturalistMushroomRepositoryParsingTest {

    @Test
    fun `parseLocation reads a valid lat,lng pair`() {
        assertEquals(45.4202583333 to -122.3877216667, parseLocation("45.4202583333,-122.3877216667"))
    }

    @Test
    fun `parseLocation trims whitespace around each component`() {
        assertEquals(45.0 to -122.0, parseLocation(" 45.0 , -122.0 "))
    }

    @Test
    fun `parseLocation returns null for missing location`() {
        assertNull(parseLocation(null))
    }

    @Test
    fun `parseLocation returns null for malformed strings`() {
        assertNull(parseLocation("not-a-location"))
        assertNull(parseLocation("45.0"))
        assertNull(parseLocation("45.0,-122.0,0"))
        assertNull(parseLocation(""))
    }

    @Test
    fun `parseLocation returns null for out-of-range coordinates`() {
        assertNull(parseLocation("91.0,0.0"))
        assertNull(parseLocation("0.0,-181.0"))
    }

    @Test
    fun `parseObservedOn reads a valid ISO date`() {
        assertEquals(LocalDate.of(2025, 9, 22), parseObservedOn("2025-09-22"))
    }

    @Test
    fun `parseObservedOn returns null for missing or malformed dates`() {
        assertNull(parseObservedOn(null))
        assertNull(parseObservedOn("not-a-date"))
        assertNull(parseObservedOn("2025-13-99"))
    }
}
