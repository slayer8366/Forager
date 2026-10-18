package com.zynergylabs.forager.app.ui.availability

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.repository.DataStoreMapPreferencesRepository
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.BasemapPreferenceRepository
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MAP_LAYERS_SHEET_TAG
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-708 through the real [AvailabilityScreen] and the real [AvailabilityViewModel], over
 * the real `map_preferences` DataStore file: Satellite is gone from the Layers sheet, a stored Satellite
 * opens on Topographical and is stored as Topographical, and a picked basemap survives a restart (a fresh
 * ViewModel and repository over the same file) with no style released for the default first.
 *
 * "Released" is what the map slot is handed with [MapRenderMode.nightModeLoaded] `true`: the gate
 * `SightingsMap` waits on before it loads any style (`requestedMapStyle` returns `null` while it is
 * `false`). A render mode handed while the gate is shut draws nothing, so "no first frame on the default"
 * is asserted as "no released render mode carries any basemap but the restored one". `SightingsMap`'s own
 * effect cannot run here (its MapView needs a native initialiser), so that last hop is
 * `OfflineStyleSwapTest`'s, at `requestedMapStyle`.
 *
 * A second live DataStore on one file is refused, so each "launch" releases the previous repository by
 * cancelling and joining its scope first, as `DataStoreMapLayerPreferencesTest` does. Every touch is a real
 * one (CLAUDE.md, Testing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenBasemapPersistenceTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/map_preferences.preferences_pb")

    private val scopes = mutableListOf<Job>()
    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logged += message }

    /** Every render mode the map slot is handed, in order. */
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

    /** What the file itself holds under the basemap key, read with no repository in between. */
    private fun rawStoredKey(): String? {
        val job = SupervisorJob().also { scopes += it }
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { dataStoreFile() }
        return runBlocking { raw.data.first()[stringPreferencesKey("map.basemap")].also { job.cancelAndJoin() } }
    }

    /**
     * Waits for [condition], letting the main looper run between checks. `composeRule.waitUntil` alone
     * timed out here with the condition already true once the looper had run (seen at the -710 build:
     * after `waitForIdle()` the store held "street" at once), because a ViewModel coroutine resuming on
     * the main thread, or a touch's effect, is not always run by its polling. Idling first each time is
     * what lets the work the condition waits for actually happen.
     */
    private fun awaitIdle(what: String = "condition", condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            composeRule.waitForIdle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "still waiting after 5 s for: $what" }
            Thread.sleep(20)
        }
    }

    /**
     * Lets a launch's repository go of the file: cancels its scope and waits for it to finish, idling
     * the main looper meanwhile. A `runBlocking { cancelAndJoin() }` here hung the -710 build for ten
     * minutes (thread dump: the test thread parked in `joinBlocking`), the main thread blocked while the
     * scope waited to finish; this cannot hang, it fails after five seconds.
     */
    private fun release(scope: Job) {
        scope.cancel()
        awaitIdle("the released store to close") { scope.isCompleted }
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

    private fun touchLayersRow() {
        composeRule.onNode(hasContentDescription("Layers:", substring = true)).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    @Test
    fun `the Layers sheet offers only Street and Topographical`() {
        val (repository, _) = launchRepository()
        val viewModel = mapLayersViewModel(errorLog = errorLog, basemapPreferences = repository)
        composeRule.setContent { MapLayersTestScreen(viewModel, slot, AbsentForecastCellStore) }
        composeRule.waitForIdle()

        touchLayersRow()

        composeRule.onNodeWithTag(MAP_LAYERS_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Street").assertIsDisplayed()
        composeRule.onNodeWithText("Topographical").assertIsDisplayed()
        composeRule.onAllNodesWithText("Satellite").assertCountEquals(0)
    }

    @Test
    fun `a stored Satellite opens on Topographical and is stored as Topographical`() {
        // A phone that held Satellite: the key is written through one repository, which is then released.
        val (seeder, seederScope) = launchRepository()
        runBlocking {
            seeder.setBasemapKey("satellite").getOrThrow()
            seederScope.cancelAndJoin()
        }

        val (repository, repositoryScope) = launchRepository()
        val viewModel = mapLayersViewModel(errorLog = errorLog, basemapPreferences = repository)
        composeRule.setContent { MapLayersTestScreen(viewModel, slot, AbsentForecastCellStore) }
        awaitIdle("Topographical released and stored") { released().isNotEmpty() && stored(repository) == "topographic" }

        assertEquals(
            "every render mode the map may draw is Topographical",
            listOf(Basemap.OPEN_TOPO_MAP),
            released().map { it.basemap }.distinct(),
        )
        composeRule.onNode(hasContentDescription("Layers: Topographical map.", substring = true)).assertExists()
        assertEquals(
            listOf("The stored basemap \"satellite\" names no map type; opening on Topographical and storing that."),
            logged.filter { "basemap" in it },
        )
        release(repositoryScope)
        assertEquals("the file now holds Topographical", "topographic", rawStoredKey())
    }

    @Test
    fun `a basemap picked in the sheet survives a restart, with no style released for the default first`() {
        // First launch: nothing stored, the map opens on Street, and Topographical is picked with real touches. (Was the
        // other way round, opening on Topographical with Street picked, until Street became the opening map:
        // dispatch 2026-10-18-04, Amendment 2; RECORD -1297.)
        val (firstRepository, firstScope) = launchRepository()
        val firstViewModel = mapLayersViewModel(errorLog = errorLog, basemapPreferences = firstRepository)
        var current by mutableStateOf(firstViewModel)
        composeRule.setContent { key(current) { MapLayersTestScreen(current, slot, AbsentForecastCellStore) } }
        awaitIdle("a style to be released to the map") { released().isNotEmpty() }
        assertEquals(Basemap.OSM_STANDARD, released().last().basemap)

        touchLayersRow()
        composeRule.onNodeWithText("Topographical").performTouchInput { click() }
        awaitIdle("Topographical to be stored") { stored(firstRepository) == "topographic" }
        assertEquals(Basemap.OPEN_TOPO_MAP, released().last().basemap)

        // The process ends: the first repository lets go of the file.
        release(firstScope)

        // Second launch: a fresh repository over the same file and a fresh ViewModel, whose read is held
        // until the test lets it through, so the frames before it lands are observable.
        val (secondRepository, _) = launchRepository()
        val held = HeldRead(secondRepository)
        val secondViewModel = mapLayersViewModel(errorLog = errorLog, basemapPreferences = held)
        composeRule.runOnIdle {
            modes.clear()
            current = secondViewModel
        }
        composeRule.waitForIdle()

        assertTrue("the second launch's screen is composed", modes.isNotEmpty())
        assertEquals("nothing may draw while the stored basemap is unread", emptyList<MapRenderMode>(), released())

        held.release()
        awaitIdle("a style to be released to the map") { released().isNotEmpty() }

        assertEquals(
            "every render mode the map may draw is the restored Topographical",
            listOf(Basemap.OPEN_TOPO_MAP),
            released().map { it.basemap }.distinct(),
        )
        composeRule.onNode(hasContentDescription("Layers: Topographical map.", substring = true)).assertExists()
        assertEquals(emptyList<String>(), logged.filter { "basemap" in it })
    }
}

/** [inner], with its read held until [release]: the time a cold DataStore read takes, made controllable. */
private class HeldRead(private val inner: BasemapPreferenceRepository) : BasemapPreferenceRepository {
    private val gate = CompletableDeferred<Unit>()

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun getBasemapKey(): Result<String?> {
        gate.await()
        return inner.getBasemapKey()
    }

    override suspend fun setBasemapKey(key: String): Result<Unit> = inner.setBasemapKey(key)
}
