package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.map.STREET_STYLE_URL
import com.zynergylabs.forager.app.map.STREET_STYLE_V1_URL
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * No signal on OSM or Topo draws Forager Trail Map from the downloaded areas (dispatch 2026-10-22-01; RECORD -1554,
 * -1556), through the offline branch so the -1513 fallback never fires for it. Read through [rememberDrawsFromDownloads],
 * the call `SightingsMap` makes, with [LocalNoSignal] provided as `MainActivity` provides it; then through the style
 * decision that value feeds ([mapStyleSourceFor], [fallsBack], [mapCreditsFor]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoSignalTrailMapTest {
    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    @Test
    fun `with no signal OSM and Topo draw from the downloads, Trail does not, and the signal back undoes it`() {
        val noSignal = MutableStateFlow(false)
        val seen = mutableMapOf<Basemap, Boolean>()
        composeRule.setContent {
            CompositionLocalProvider(LocalNoSignal provides noSignal, LocalHasDownloadedArea provides true) {
                Basemap.entries.forEach { basemap -> seen[basemap] = rememberDrawsFromDownloads(useOfflineTiles = false, basemap = basemap) }
            }
        }
        composeRule.waitForIdle()
        assertEquals("with signal, every map draws itself", Basemap.entries.associateWith { false }, seen.toMap())

        composeRule.runOnIdle { noSignal.value = true }
        composeRule.waitForIdle()
        assertEquals(
            "no signal: OSM and Topo from the downloads; Trail is already Trail Map",
            mapOf(Basemap.FORAGER_TRAIL to false, Basemap.OPEN_TOPO_MAP to true, Basemap.OSM_STANDARD to true),
            seen.toMap(),
        )

        composeRule.runOnIdle { noSignal.value = false }
        composeRule.waitForIdle()
        assertEquals("signal back: every map draws itself again", Basemap.entries.associateWith { false }, seen.toMap())
    }

    /**
     * Dispatch 2026-10-22-01 Amendment 3 (Sorrel's review): a Trail user whose Trail Map has fallen back to OSM's raster
     * (`OwnStreetFallback`, -1513) gets what an OSM user gets when the signal goes. Driven through the fallback's own
     * entry point, a failed online load of Trail Map's style, on a fallback of the test's own.
     */
    @Test
    fun `a Trail user fallen back to OSM draws from the downloads with no signal, like an OSM user`() {
        val noSignal = MutableStateFlow(true)
        val fallback = OwnStreetFallback(log = {})
        var trail: Boolean? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalNoSignal provides noSignal, LocalHasDownloadedArea provides true) {
                trail = rememberDrawsFromDownloads(useOfflineTiles = false, basemap = Basemap.FORAGER_TRAIL, fallback = fallback)
            }
        }
        composeRule.waitForIdle()
        assertEquals("Trail Map drawing, no signal: its own style, unchanged", false, trail)

        composeRule.runOnIdle {
            fallback.onStyleLoadFailed(MapStyleRequest(MapStyleSource.Uri(STREET_STYLE_URL), useOfflineTiles = false), "Failed to load style: 404")
        }
        composeRule.waitForIdle()
        assertEquals("fallen back to OSM, no signal: from the downloads", true, trail)

        composeRule.runOnIdle { noSignal.value = false }
        composeRule.waitForIdle()
        assertEquals("fallen back, signal back: the OSM raster again", false, trail)
    }

    /**
     * Elder's item 3 (dispatch 2026-10-23-07; the owner: "Only switch to Trail Map when a downloaded area exists", "Keep
     * their map as it was"): with nothing downloaded there is nothing to draw Trail Map from, so OSM and Topo stay as
     * chosen, with whatever MapLibre has cached, rather than reloading to a style no region holds (a blank map).
     */
    @Test
    fun `with no signal and no downloaded area, OSM and Topo keep the chosen map`() {
        val noSignal = MutableStateFlow(true)
        val seen = mutableMapOf<Basemap, Boolean>()
        composeRule.setContent {
            CompositionLocalProvider(LocalNoSignal provides noSignal, LocalHasDownloadedArea provides false) {
                Basemap.entries.forEach { basemap -> seen[basemap] = rememberDrawsFromDownloads(useOfflineTiles = false, basemap = basemap) }
            }
        }
        composeRule.waitForIdle()
        assertEquals("no signal, nothing downloaded: every map draws itself", Basemap.entries.associateWith { false }, seen.toMap())
    }

    /**
     * The same rule through `MainActivity`'s root, [ForagerThemeWithNoSignal], fed the Offline maps list as `MainActivity`
     * feeds it: a region restored from a backup with no tiles here does not count; a downloaded one appearing (a download
     * finishing while out of signal) switches OSM and Topo to the downloads; its delete puts the chosen map back.
     */
    @Test
    fun `through the app's root, only a region with tiles here switches OSM and Topo with no signal`() {
        val noSignal = MutableStateFlow(true)
        var regions by mutableStateOf(listOf(region(1, isDownloaded = false)))
        val seen = mutableMapOf<Basemap, Boolean>()
        composeRule.setContent {
            ForagerThemeWithNoSignal(darkTheme = false, noSignal = noSignal, offlineRegions = regions) {
                Basemap.entries.forEach { basemap -> seen[basemap] = rememberDrawsFromDownloads(useOfflineTiles = false, basemap = basemap) }
            }
        }
        composeRule.waitForIdle()
        assertEquals("only a restored region with no tiles: every map draws itself", Basemap.entries.associateWith { false }, seen.toMap())

        composeRule.runOnIdle { regions = regions + region(2, isDownloaded = true) }
        composeRule.waitForIdle()
        assertEquals(
            "a downloaded region: OSM and Topo from the downloads; Trail is already Trail Map",
            mapOf(Basemap.FORAGER_TRAIL to false, Basemap.OPEN_TOPO_MAP to true, Basemap.OSM_STANDARD to true),
            seen.toMap(),
        )

        composeRule.runOnIdle { regions = regions.filterNot { it.id == 2L } }
        composeRule.waitForIdle()
        assertEquals("the downloaded region deleted: every map draws itself again", Basemap.entries.associateWith { false }, seen.toMap())
    }

    @Test
    fun `a Trail user fallen back to OSM with no downloaded area keeps the OSM raster with no signal`() {
        val fallback = OwnStreetFallback(log = {})
        var trail: Boolean? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalNoSignal provides MutableStateFlow(true), LocalHasDownloadedArea provides false) {
                trail = rememberDrawsFromDownloads(useOfflineTiles = false, basemap = Basemap.FORAGER_TRAIL, fallback = fallback)
            }
        }
        composeRule.runOnIdle {
            fallback.onStyleLoadFailed(MapStyleRequest(MapStyleSource.Uri(STREET_STYLE_URL), useOfflineTiles = false), "Failed to load style: 404")
        }
        composeRule.waitForIdle()
        assertEquals("fallen back, no signal, nothing downloaded: the OSM raster stays", false, trail)
    }

    private fun region(id: Long, isDownloaded: Boolean) = OfflineRegionSummary(
        id = id, name = "Region $id", region = Region(45.5, -122.6, 5), minZoom = 10.0, maxZoom = 15.0,
        tileCount = if (isDownloaded) 100 else 0, sizeBytes = if (isDownloaded) 2_000_000L else 0L, createdAtEpochMillis = 1_000L,
        isDownloaded = isDownloaded,
    )

    @Test
    fun `with nothing provided, as in a test with no MainActivity, the chosen map is always drawn`() {
        val seen = mutableMapOf<Basemap, Boolean>()
        composeRule.setContent {
            Basemap.entries.forEach { basemap -> seen[basemap] = rememberDrawsFromDownloads(useOfflineTiles = false, basemap = basemap) }
        }
        composeRule.waitForIdle()
        assertEquals(Basemap.entries.associateWith { false }, seen.toMap())
    }

    @Test
    fun `the offline flag a screen sets wins whatever the signal, downloaded area or not`() {
        listOf(false, true).forEach { noSignal ->
            listOf(false, true).forEach { area ->
                Basemap.entries.forEach { basemap ->
                    assertEquals(true, drawsFromDownloads(useOfflineTiles = true, basemap = basemap, noSignal = noSignal, hasDownloadedArea = area))
                }
            }
        }
    }

    @Test
    fun `drawn from the downloads, OSM and Topo load the regions' style by URL, credited as such, and never fall back`() {
        for (basemap in listOf(Basemap.OSM_STANDARD, Basemap.OPEN_TOPO_MAP)) for (night in listOf(false, true)) {
            val offline = drawsFromDownloads(useOfflineTiles = false, basemap = basemap, noSignal = true, hasDownloadedArea = true)
            for (regionsStyle in listOf(OFFLINE_STYLE_URL, STREET_STYLE_URL, STREET_STYLE_V1_URL)) {
                val source = mapStyleSourceFor(basemap, night, useOfflineTiles = offline, offlineStyleUrl = regionsStyle)
                assertEquals("$basemap night=$night", MapStyleSource.Uri(regionsStyle), source)
                // OwnStreetFallback's rule, unchanged: never in offline mode. So no signal cannot lock the map on OSM.
                assertFalse("$basemap night=$night falls back", fallsBack(source, useOfflineTiles = offline))
            }
            assertEquals(listOf("Protomaps © OpenStreetMap"), mapCreditsFor(basemap, useOfflineTiles = offline, nightMode = night))
        }
    }
}
