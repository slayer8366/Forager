package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.repository.DataStoreMapPreferencesRepository
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.CrowdedIconsMode
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 * Settings > Map > "Crowded icons" (dispatch 2026-10-27-14 Amendment 3, RECORD -1758; the owner: "New \"Map\" section
 * (Recommended)"), through the real [AvailabilityScreen] and the real [AvailabilityViewModel] over the real `map_preferences`
 * DataStore file: the section sits after Night Mode and before Backup, "Zoom in, then fan" is chosen on a new install, a
 * real touch across either row picks it, and the pick survives a restart (a fresh repository and ViewModel over the same
 * file). A stored value that names no mode opens on the default, and says so.
 *
 * Each "launch" releases the previous repository first, as `AvailabilityScreenBasemapPersistenceTest` does: a second live
 * DataStore on one file is refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenCrowdedIconsSettingTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/map_preferences.preferences_pb")

    private val scopes = mutableListOf<Job>()
    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logged += message }
    private val slot: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("crowded-icons-setting-map")) }

    private fun launchRepository(): Pair<DataStoreMapPreferencesRepository, Job> {
        val job = SupervisorJob()
        scopes += job
        return DataStoreMapPreferencesRepository(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    /** What the file itself holds under `map.crowded_icons`, read with no repository in between. */
    private fun rawStored(): String? {
        val job = SupervisorJob().also { scopes += it }
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { dataStoreFile() }
        return runBlocking { raw.data.first()[stringPreferencesKey("map.crowded_icons")].also { job.cancelAndJoin() } }
    }

    /** Waits for [condition], idling the main looper between checks (AvailabilityScreenBasemapPersistenceTest.awaitIdle's reason). */
    private fun awaitIdle(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            composeRule.waitForIdle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "still waiting after 5 s for: $what (logged $logged)" }
            Thread.sleep(20)
        }
    }

    /** Lets a launch's repository go of the file, failing after five seconds rather than hanging. */
    private fun release(scope: Job) {
        scope.cancel()
        awaitIdle("the released store to close") { scope.isCompleted }
    }

    @Before fun setUp() { dataStoreFile().delete() }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        dataStoreFile().delete()
    }

    private fun openSettings(viewModel: AvailabilityViewModel) {
        composeRule.setContent { MapLayersTestScreen(viewModel, slot, AbsentForecastCellStore) }
        composeRule.waitForIdle()
        // Opening Tools > Settings is not the claim here; the touches on the Crowded icons rows are.
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
    }

    private fun selected(mode: CrowdedIconsMode): Boolean {
        val node = composeRule.onNodeWithTag(crowdedIconsTag(mode)).fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.Selected) == true ||
            node.children.any { it.config.getOrNull(SemanticsProperties.Selected) == true }
    }

    private fun touchRow(mode: CrowdedIconsMode, fraction: Float) {
        composeRule.onNodeWithTag(crowdedIconsTag(mode)).performScrollTo().performTouchInput { click(Offset(width * fraction, height / 2f)) }
        composeRule.waitForIdle()
    }

    @Test
    fun `Settings has a Map section after Night Mode and before Backup, with Zoom in, then fan chosen on a new install`() {
        val (repository, _) = launchRepository()
        openSettings(mapLayersViewModel(errorLog = errorLog, crowdedIcons = repository))

        composeRule.onNodeWithTag(crowdedIconsTag(CrowdedIconsMode.ZOOM_THEN_FAN)).performScrollTo()
        assertTrue(composeRule.onAllNodesWithText("Crowded icons").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Zoom in, then fan").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("List").fetchSemanticsNodes().isNotEmpty())
        assertEquals(listOf(true, false), CrowdedIconsMode.entries.map(::selected))

        val nightMaps = composeRule.onNodeWithText("Night Maps").fetchSemanticsNode().boundsInRoot
        val heading = composeRule.onNodeWithText("Crowded icons").fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithTag(crowdedIconsTag(CrowdedIconsMode.LIST)).fetchSemanticsNode().boundsInRoot
        val backup = composeRule.onAllNodesWithText("Backup").onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue("after Night Maps: $heading, $nightMaps", heading.top >= nightMaps.bottom)
        assertTrue("before Backup: $list, $backup", list.bottom <= backup.top)
    }

    @Test
    fun `real touches across each row pick it, and the pick survives a restart`() {
        val (repository, scope) = launchRepository()
        val viewModel = mapLayersViewModel(errorLog = errorLog, crowdedIcons = repository)
        openSettings(viewModel)

        val picks = listOf(
            CrowdedIconsMode.LIST to 0.1f,
            CrowdedIconsMode.ZOOM_THEN_FAN to 0.5f,
            CrowdedIconsMode.LIST to 0.9f,
        )
        for ((mode, fraction) in picks) {
            touchRow(mode, fraction)
            assertEquals("a touch at $fraction across the $mode row picks it", CrowdedIconsMode.entries.map { it == mode }, CrowdedIconsMode.entries.map(::selected))
            assertEquals(mode, viewModel.uiState.value.crowdedIconsMode)
        }
        awaitIdle("List stored") { runBlocking { repository.getCrowdedIconsMode { v -> throw AssertionError("the stored List read as an unknown value: \"$v\"") }.getOrNull() } == CrowdedIconsMode.LIST }
        release(scope)

        assertEquals("the file holds it under map.crowded_icons", "list", rawStored())
        val (relaunched, _) = launchRepository()
        val restarted = mapLayersViewModel(errorLog = errorLog, crowdedIcons = relaunched)
        awaitIdle("a new ViewModel reads List") { restarted.uiState.value.crowdedIconsMode == CrowdedIconsMode.LIST }
        assertTrue("nothing logged: $logged", logged.none { "rowded" in it })
    }

    @Test
    fun `a stored value that names no mode opens on Zoom in, then fan, and says so`() {
        val (seeder, seederScope) = launchRepository()
        runBlocking { seeder.setCrowdedIconsMode(CrowdedIconsMode.LIST).getOrThrow() }
        release(seederScope)
        val job = SupervisorJob().also { scopes += it }
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { dataStoreFile() }
        runBlocking {
            raw.updateData { prefs -> prefs.toMutablePreferences().apply { this[stringPreferencesKey("map.crowded_icons")] = "spiral" } }
            job.cancelAndJoin()
        }

        val (repository, _) = launchRepository()
        val viewModel = mapLayersViewModel(errorLog = errorLog, crowdedIcons = repository)
        awaitIdle("the unknown value reported") { logged.any { "spiral" in it } }

        assertEquals(CrowdedIconsMode.ZOOM_THEN_FAN, viewModel.uiState.value.crowdedIconsMode)
        assertEquals(
            listOf("Crowded icons: stored value \"spiral\" names no mode; using ZOOM_THEN_FAN."),
            logged.filter { "rowded" in it },
        )
    }
}
