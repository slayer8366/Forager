package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.map.STREET_STYLE_URL
import com.zynergylabs.forager.app.map.STREET_STYLE_V3_URL
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The night pass picks its Street tables by the style source it loaded, through [streetUriOf] (Sorrel's S-1 on #462): a
 * plain URI's own URL, and for Street under Topographical the URL of the Street it draws, so v4 under the night topo takes
 * v4's tables. [streetNightTablesForSource] is that choice, named once; `MapStyleReload` is a composable no unit test can
 * run, so the last test holds it to calling this function and no other way of choosing.
 */
class NightTablesBySourceTest {

    @Test
    fun `each source takes the tables of the Street it draws`() {
        assertSame(STREET_V4_NIGHT_TABLES, streetNightTablesForSource(MapStyleSource.Uri(STREET_STYLE_URL)))
        assertSame(STREET_NIGHT_TABLES, streetNightTablesForSource(MapStyleSource.Uri(STREET_STYLE_V3_URL)))
        assertSame(STREET_NIGHT_TABLES, streetNightTablesForSource(MapStyleSource.Uri(OFFLINE_STYLE_URL)))
        assertSame(STREET_V4_NIGHT_TABLES, streetNightTablesForSource(MapStyleSource.StreetWithTopoOver(STREET_STYLE_URL, Basemap.OPEN_TOPO_MAP)))
        assertSame(STREET_NIGHT_TABLES, streetNightTablesForSource(MapStyleSource.StreetWithTopoOver(STREET_STYLE_V3_URL, Basemap.OPEN_TOPO_MAP)))
        assertSame(STREET_NIGHT_TABLES, streetNightTablesForSource(MapStyleSource.Json("{}")))
    }

    @Test
    fun `the app's own Street under the night topo takes v4's tables`() {
        assertSame(STREET_V4_NIGHT_TABLES, streetNightTablesForSource(ownStreetStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = true)!!))
    }

    @Test
    fun `the night pass chooses its tables only through streetNightTablesForSource`() {
        val reload = File("src/main/java/com/zynergylabs/forager/app/ui/map/MapStyleReload.kt").readText()
        assertEquals("calls of streetNightTablesForSource(source)", 1, Regex("""streetNightTablesForSource\(source\)""").findAll(reload).count())
        assertEquals("other ways of choosing the tables", 0, Regex("""streetNightTablesFor\(""").findAll(reload).count())
    }
}
