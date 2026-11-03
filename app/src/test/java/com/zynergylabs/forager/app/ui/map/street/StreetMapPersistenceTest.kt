package com.zynergylabs.forager.app.ui.map.street

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.repository.DataStoreMapPreferencesRepository
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.BasemapPreferenceRepository
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.ui.availability.MapLayersTestScreen
import com.zynergylabs.forager.app.ui.availability.mapChromeHostActivityRule
import com.zynergylabs.forager.app.ui.availability.mapLayersViewModel
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MAP_TYPE_PANEL_TAG
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.mapModeChipTag
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Street map's chip and stored choice through the real Maps tab, the real ViewModel and the real `map_preferences`
 * file (dispatch 2026-11-03-05 A2, B1, C2): the panel's chips read Trail, Topo and Street; a phone whose file says
 * "openstreetmap" from before this update opens on the Street map, stored as "street"; a real touch at several points
 * across the Street chip stores "street", and a restart keeps it. Every touch is a real one at screen coordinates.
 * Deleted with `ui/map/street/` (D7), the core half being `AvailabilityScreenBasemapPersistenceTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class StreetMapPersistenceTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/map_preferences.preferences_pb")

    private val scopes = mutableListOf<Job>()
    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logged += message }

    private val modes = mutableListOf<MapRenderMode>()
    private val slot: MapSlot = { _, _, renderMode, _, _, onTap, _, _, modifier ->
        SideEffect { modes += renderMode }
        Box(
            modifier
                .testTag("map-slot")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap() },
        )
    }

    private fun released(): List<MapRenderMode> = modes.filter { it.nightModeLoaded }

    private fun launchRepository(): Pair<DataStoreMapPreferencesRepository, Job> {
        val job = SupervisorJob()
        scopes += job
        return DataStoreMapPreferencesRepository(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    private fun stored(repository: BasemapPreferenceRepository): String? = runBlocking { repository.getBasemapKey().getOrNull() }

    /** The file as a build before this update left it, with no migration run. */
    private fun phoneFromBeforeTheUpdate(key: String) {
        val job = SupervisorJob().also { scopes += it }
        val oldBuild = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { dataStoreFile() }
        runBlocking {
            oldBuild.edit { it[stringPreferencesKey("map.basemap")] = key }
            job.cancelAndJoin()
        }
    }

    private fun rawStoredKey(): String? {
        val job = SupervisorJob().also { scopes += it }
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { dataStoreFile() }
        return runBlocking { raw.data.first()[stringPreferencesKey("map.basemap")].also { job.cancelAndJoin() } }
    }

    private fun awaitIdle(what: String = "condition", condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            composeRule.waitForIdle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "still waiting after 5 s for: $what" }
            Thread.sleep(20)
        }
    }

    private fun release(scope: Job) {
        scope.cancel()
        awaitIdle("the released store to close") { scope.isCompleted }
    }

    private fun touchLayersRow() {
        composeRule.onNode(hasContentDescription("Layers:", substring = true)).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    private fun touchAt(r: DpRect, fx: Float, fy: Float) {
        val at = with(composeRule.density) {
            Offset((r.left + (r.right - r.left) * fx).toPx(), (r.top + (r.bottom - r.top) * fy).toPx())
        }
        composeRule.onRoot().performTouchInput { click(at) }
        composeRule.waitForIdle()
    }

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        dataStoreFile().delete()
    }

    @Test
    fun `the map type panel's chips read Trail, Topo and Street`() {
        val (repository, _) = launchRepository()
        val viewModel = mapLayersViewModel(errorLog = errorLog, basemapPreferences = repository)
        composeRule.setContent { MapLayersTestScreen(viewModel, slot, AbsentForecastCellStore) }
        composeRule.waitForIdle()

        touchLayersRow()

        composeRule.onNodeWithTag(MAP_TYPE_PANEL_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Trail").assertIsDisplayed()
        composeRule.onNodeWithText("Topo").assertIsDisplayed()
        composeRule.onNodeWithText("Street").assertIsDisplayed()
        composeRule.onAllNodesWithText("OSM").assertCountEquals(0)
    }

    @Test
    fun `OpenStreetMap's old key from before the update opens on the Street map, stored as street`() {
        phoneFromBeforeTheUpdate("openstreetmap")
        val (repository, _) = launchRepository()
        val viewModel = mapLayersViewModel(errorLog = errorLog, basemapPreferences = repository)
        composeRule.setContent { MapLayersTestScreen(viewModel, slot, AbsentForecastCellStore) }
        awaitIdle("a style to be released to the map") { released().isNotEmpty() }

        assertEquals(listOf(StreetMap.BASEMAP), released().map { it.basemap }.distinct())
        assertEquals("street", stored(repository))
        composeRule.onNode(hasContentDescription("Layers: Street map.", substring = true)).assertExists()
        assertEquals(emptyList<String>(), logged.filter { "basemap" in it })
    }

    @Test
    fun `a real touch at several points across the Street chip stores street, and a restart keeps it`() {
        val (firstRepository, firstScope) = launchRepository()
        val firstViewModel = mapLayersViewModel(errorLog = errorLog, basemapPreferences = firstRepository)
        var current by mutableStateOf(firstViewModel)
        composeRule.setContent { key(current) { MapLayersTestScreen(current, slot, AbsentForecastCellStore) } }
        awaitIdle("a style to be released to the map") { released().isNotEmpty() }

        // A finger is not a point: each touch lands somewhere else on the chip, and each pass goes back to Trail first.
        listOf(0.15f to 0.5f, 0.5f to 0.5f, 0.85f to 0.5f, 0.5f to 0.25f, 0.5f to 0.75f).forEach { (fx, fy) ->
            touchLayersRow()
            touchAt(composeRule.onNodeWithTag(mapModeChipTag(MapMode.TRAIL)).getUnclippedBoundsInRoot(), 0.5f, 0.5f)
            awaitIdle("Trail to be stored") { stored(firstRepository) == "trail" }

            touchLayersRow()
            touchAt(composeRule.onNodeWithTag(mapModeChipTag(StreetMap.mapMode)).getUnclippedBoundsInRoot(), fx, fy)
            awaitIdle("Street to be stored after a touch at ($fx, $fy)") { stored(firstRepository) == "street" }
            assertEquals("the touch at ($fx, $fy) draws the Street map", StreetMap.BASEMAP, released().last().basemap)
        }
        release(firstScope)

        val (secondRepository, secondScope) = launchRepository()
        val secondViewModel = mapLayersViewModel(errorLog = errorLog, basemapPreferences = secondRepository)
        composeRule.runOnIdle {
            modes.clear()
            current = secondViewModel
        }
        awaitIdle("a style to be released to the map") { released().isNotEmpty() }

        assertEquals("after the restart, the Street map", listOf(StreetMap.BASEMAP), released().map { it.basemap }.distinct())
        assertEquals("street", stored(secondRepository))
        release(secondScope)
        assertEquals("the file says street", "street", rawStoredKey())
        assertEquals(listOf(Basemap.FORAGER_TRAIL, Basemap.OPEN_TOPO_MAP, StreetMap.BASEMAP), MapMode.entries.map { it.basemap })
    }
}
