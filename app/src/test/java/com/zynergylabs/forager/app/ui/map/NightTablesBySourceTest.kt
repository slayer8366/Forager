package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_V3_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_V4_STREET_URL
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The night pass picks its Street tables by the style source it loaded, through [trailUriOf] (Sorrel's S-1 on #462): a
 * plain URI's own URL, and for Street under Topographical the URL of the Street it draws, so v4 under the night topo takes
 * v4's tables. [trailNightTablesForSource] is that choice, named once; `MapStyleReload` is a composable no unit test can
 * run, so the last test holds it to calling this function and no other way of choosing.
 */
class NightTablesBySourceTest {

    @Test
    fun `each source takes the tables of the Street it draws`() {
        assertSame(TRAIL_V4_NIGHT_TABLES, trailNightTablesForSource(MapStyleSource.Uri(TRAIL_STYLE_URL)))
        assertSame(TRAIL_NIGHT_TABLES, trailNightTablesForSource(MapStyleSource.Uri(TRAIL_STYLE_V3_URL)))
        assertSame(TRAIL_NIGHT_TABLES, trailNightTablesForSource(MapStyleSource.Uri(OFFLINE_STYLE_URL)))
        assertSame(TRAIL_V4_NIGHT_TABLES, trailNightTablesForSource(MapStyleSource.TrailWithTopoOver(TRAIL_STYLE_URL, Basemap.OPEN_TOPO_MAP)))
        assertSame(TRAIL_NIGHT_TABLES, trailNightTablesForSource(MapStyleSource.TrailWithTopoOver(TRAIL_STYLE_V3_URL, Basemap.OPEN_TOPO_MAP)))
        assertSame(TRAIL_NIGHT_TABLES, trailNightTablesForSource(MapStyleSource.Json("{}")))
    }

    @Test
    fun `trail-v4 takes v4's night tables, by its route, its repository file and under the night topo, as street-v4 does`() {
        // Dispatch 2026-11-03-05 D1 (RECORD -2000): trail-v4 is v4 under the Trail Map's own code name.
        assertSame("the route", TRAIL_V4_NIGHT_TABLES, trailNightTablesFor("https://forager-pmtiles.brandonlee1-894.workers.dev/style/trail-v4.json"))
        assertSame("the repository file", TRAIL_V4_NIGHT_TABLES, trailNightTablesFor("file:///map-style/forager-trail-v4.json"))
        assertSame("street-v4 still", TRAIL_V4_NIGHT_TABLES, trailNightTablesFor(TRAIL_STYLE_V4_STREET_URL))
        assertSame("forager-street-v4.json still", TRAIL_V4_NIGHT_TABLES, trailNightTablesFor("file:///map-style/forager-street-v4.json"))
        assertSame(
            "trail-v4 under the night topo",
            TRAIL_V4_NIGHT_TABLES,
            trailNightTablesForSource(MapStyleSource.TrailWithTopoOver("https://forager-pmtiles.brandonlee1-894.workers.dev/style/trail-v4.json", Basemap.OPEN_TOPO_MAP)),
        )
    }

    @Test
    fun `street-v4 and trail-v4 both take the Trail Map's sprite night`() {
        assertEquals(true, usesTrailSprite(MapStyleSource.Uri(TRAIL_STYLE_V4_STREET_URL)))
        assertEquals(true, usesTrailSprite(MapStyleSource.Uri("https://forager-pmtiles.brandonlee1-894.workers.dev/style/trail-v4.json")))
    }

    @Test
    fun `the app's own Street under the night topo takes v4's tables`() {
        assertSame(TRAIL_V4_NIGHT_TABLES, trailNightTablesForSource(ownTrailStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = true)!!))
    }

    @Test
    fun `the night pass chooses its tables only through trailNightTablesForSource`() {
        val reload = File("src/main/java/com/zynergylabs/forager/app/ui/map/MapStyleReload.kt").readText()
        assertEquals("calls of trailNightTablesForSource(source)", 1, Regex("""trailNightTablesForSource\(source\)""").findAll(reload).count())
        assertEquals("other ways of choosing the tables", 0, Regex("""trailNightTablesFor\(""").findAll(reload).count())
    }
}
