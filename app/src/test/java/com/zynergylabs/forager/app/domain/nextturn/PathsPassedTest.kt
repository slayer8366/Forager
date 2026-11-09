package com.zynergylabs.forager.app.domain.nextturn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which ways count as paths passed for "take the second left" (Amendment 1, B5): the owner, "Walkable ways only
 * (Recommended)"; the planner's reading of the tags. The values are the route data's own (`lookups.dat`'s `highway`).
 */
class PathsPassedTest {

    @Test
    fun `B5 - driveways and every other service way, motorways, motorway slips and trunk roads are not paths`() {
        for (highway in listOf("service", "motorway", "motorway_link", "trunk", "trunk_link")) {
            assertFalse(highway, countsAsPath(mapOf("highway" to highway)))
        }
        assertFalse("a driveway, by its highway tag", countsAsPath(mapOf("highway" to "service", "service" to "driveway", "access" to "private")))
        assertFalse("a parking aisle", countsAsPath(mapOf("highway" to "service", "service" to "parking_aisle")))
    }

    @Test
    fun `B5 - footpaths, trails, tracks and streets are paths, and so is every other way the data knows`() {
        val paths = listOf(
            "footway", "path", "track", "bridleway", "steps", "pedestrian", "cycleway", "living_street", "residential",
            "unclassified", "tertiary", "tertiary_link", "secondary", "secondary_link", "primary", "primary_link",
        )
        for (highway in paths) assertTrue(highway, countsAsPath(mapOf("highway" to highway)))
        assertTrue("private access does not make a footway any less a path", countsAsPath(mapOf("highway" to "footway", "access" to "private")))
        assertTrue("a way with no highway tag", countsAsPath(emptyMap()))
    }
}
