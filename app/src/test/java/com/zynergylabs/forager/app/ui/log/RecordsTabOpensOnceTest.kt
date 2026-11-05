package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.map.MapSlot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-05-01, T3 at `RecordsTab` (A3, Amendment 1 A8): composed with the chip latch already set, as `JournalTab`
 * composes it for the Tools drawer's Offline maps row and for a GPX import's Tracks, the chip's opening callback fires
 * once. The Phase 1 report, section 5: the latch's effect selects the chip, and the selection's effect, launched in the same
 * composition with the old chip as its key, read the chip live and opened it, then ran again for the new key. The touch
 * path through the real screen is `OfflineMapsOpenedOnceTest`; a chip touch's one call is `RecordsFilterChipsTest`'s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class RecordsTabOpensOnceTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private var offlineMapsOpened = 0
    private var tracksOpened = 0

    /** `RecordsTab` on [chip], with [latch] pending, cleared when consumed as `JournalTab` clears its own. */
    private fun show(latch: RecordsSubTab?, chip: RecordsSubTab = DEFAULT_RECORDS_FILTER) {
        composeRule.setContent {
            var pending by remember { mutableStateOf(latch) }
            val selected = remember { mutableStateOf(chip) }
            RecordsTab(
                waypoints = emptyList(),
                waypointsErrorMessage = null,
                onDeleteWaypoint = {},
                availabilityUiState = AvailabilityUiState(),
                distanceUnit = DistanceUnit.MILES,
                currentTime = CurrentTimeProvider { 0L },
                mapSlot = STUB_MAP,
                night = false,
                onOfflineMapRegionPicked = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = { offlineMapsOpened++ },
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                tracks = emptyList(),
                onTracksOpened = { tracksOpened++ },
                findsContent = {},
                pendingSubTab = pending,
                onPendingSubTabConsumed = { pending = null },
                selectedTabState = selected,
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `composed with the Offline maps latch set, as the Tools row composes it, Offline maps opens once`() {
        show(latch = RecordsSubTab.OFFLINE_MAPS)
        assertEquals("Offline maps openings", 1, offlineMapsOpened)
        assertEquals("Tracks openings", 0, tracksOpened)
    }

    @Test
    fun `composed with the Tracks latch set, as a GPX import composes it, Tracks opens once`() {
        show(latch = RecordsSubTab.RECORDED_TRACKS)
        assertEquals("Tracks openings", 1, tracksOpened)
        assertEquals("Offline maps openings", 0, offlineMapsOpened)
    }

    // Guards: these pass before and after the change (one call each, today too), so they are not shown to bite.

    @Test
    fun `composed already on Offline maps with no latch, Offline maps opens once`() {
        show(latch = null, chip = RecordsSubTab.OFFLINE_MAPS)
        assertEquals(1, offlineMapsOpened)
    }

    @Test
    fun `composed already on Offline maps with its own latch set, Offline maps opens once`() {
        show(latch = RecordsSubTab.OFFLINE_MAPS, chip = RecordsSubTab.OFFLINE_MAPS)
        assertEquals(1, offlineMapsOpened)
    }

    private companion object {
        val STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }
    }
}
