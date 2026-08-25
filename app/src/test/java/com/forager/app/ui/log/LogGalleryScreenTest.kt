package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
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
 * No dedicated test file existed for [LogGalleryScreen] before this — Workstream G2
 * (`docs/plans/pr26-rework.md`) adds coverage for the one thing it touched: the cover-photo tile
 * now delegates to the shared [DecodedPhoto] rather than its own (former) `GalleryCoverThumbnail`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogGalleryScreenTest {

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

    @Test
    fun `an entry with a cover photo renders it`() {
        val entryWithPhoto = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1))
            .copy(photos = listOf(LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)))

        composeRule.setContent {
            LogGalleryScreen(entries = listOf(entryWithPhoto), isLoading = false, onOpenEntry = {}, onAddEntry = {})
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Log photo").assertIsDisplayed()
    }

    @Test
    fun `an entry with no photos shows the placeholder icon, not a broken image`() {
        val entryWithoutPhoto = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1))

        composeRule.setContent {
            LogGalleryScreen(entries = listOf(entryWithoutPhoto), isLoading = false, onOpenEntry = {}, onAddEntry = {})
        }

        composeRule.onNodeWithContentDescription("New log entry").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Log photo").assertCountEquals(0)
    }
}
