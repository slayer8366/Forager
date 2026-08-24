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
import com.forager.app.photo.CameraCaptureFiles
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * [LogEntryDetailScreen]'s own location line — L3 (`docs/plans/pr26-rework.md`'s Workstream L)
 * makes [MushroomLogEntry.foundAt] nullable, so this composable's "Found at ..." coordinate line
 * has a second state to prove: the owner-decided "No location set." text for an entry with none.
 * No dedicated test file existed for this screen before this addition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogEntryDetailScreenTest {

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

    private fun setScreen(entry: MushroomLogEntry) {
        composeRule.setContent {
            LogEntryDetailScreen(
                entry = entry,
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                onEntryChanged = {},
                onAddPhoto = {},
                onRemovePhoto = {},
                onDeleteEntry = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `an entry with a location shows its coordinates`() {
        val entry = MushroomLogEntry.draft(id = "with-location-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1))

        setScreen(entry)

        composeRule.onNodeWithText("Found at 45.3260, -122.6340").assertIsDisplayed()
    }

    @Test
    fun `an entry with no location shows the no-location text instead of coordinates`() {
        val locationLessEntry = MushroomLogEntry.draft(id = "no-location-1", location = null, date = LocalDate.of(2026, 8, 1))

        setScreen(locationLessEntry)

        composeRule.onNodeWithText("No location set.").assertIsDisplayed()
    }
}
