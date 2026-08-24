package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

/**
 * [LogEntryListScreen]'s own [loadErrorMessage] parameter — the drawer-hosted [LogPanel]'s
 * counterpart to [JournalTabTest]'s coverage of [LogGalleryScreen]'s same field. Drives the
 * composable directly (it takes no ViewModel of its own; [LogPanel] only unpacks
 * [MushroomLogUiState] into these same parameters — see that composable's call site), with a real
 * [MushroomLogEntry] rather than a hand-built stand-in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogEntryListScreenTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val existingEntry = MushroomLogEntry.draft(
        id = "existing-1",
        location = LatLng(45.326, -122.634),
        date = LocalDate.of(2026, 8, 1),
    )

    @Test
    fun `a set loadErrorMessage shows the unavailable text when there are no entries to show`() {
        composeRule.setContent {
            LogEntryListScreen(
                entries = emptyList(),
                isLoading = false,
                onOpenEntry = {},
                loadErrorMessage = "Log entries unavailable.",
            )
        }

        composeRule.onNodeWithText("Log entries unavailable.").assertIsDisplayed()
    }

    @Test
    fun `with loadErrorMessage unset, the plain no-finds text shows instead`() {
        composeRule.setContent {
            LogEntryListScreen(entries = emptyList(), isLoading = false, onOpenEntry = {})
        }

        composeRule.onNodeWithText("No finds logged yet. Tap the add button on the map to log one.").assertIsDisplayed()
    }

    /**
     * L3 (`docs/plans/pr26-rework.md`'s Workstream L) makes [MushroomLogEntry.foundAt] nullable —
     * this is the display side of that: a location-less entry shows the owner-decided "No location
     * set." text (see [MushroomLogEntry.draft]'s own doc comment on why nothing yet constructs one
     * this way in production; the test still needs to prove the render path itself is correct).
     */
    @Test
    fun `an entry with no location shows the no-location text instead of coordinates`() {
        val locationLessEntry = MushroomLogEntry.draft(id = "no-location-1", location = null, date = LocalDate.of(2026, 8, 1))

        composeRule.setContent {
            LogEntryListScreen(entries = listOf(locationLessEntry), isLoading = false, onOpenEntry = {})
        }

        composeRule.onNodeWithText("No location set.").assertIsDisplayed()
    }

    @Test
    fun `a set loadErrorMessage does not hide entries already showing`() {
        composeRule.setContent {
            LogEntryListScreen(
                entries = listOf(existingEntry),
                isLoading = false,
                onOpenEntry = {},
                loadErrorMessage = "Log entries unavailable.",
            )
        }

        composeRule.onNodeWithText("Find on ${existingEntry.foundOn}").assertIsDisplayed()
    }
}
