package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.CapSection
import com.forager.app.domain.model.Feature
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.VeilSection
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
 * The rule the plan states directly: [com.forager.app.domain.model.Observed.NotObserved]/
 * [Feature.NotObserved] must visibly read as "unrecorded" on screen, never as absence. This is
 * where that distinction actually reaches the user — the type-level guarantee is
 * [ObservedFeatureTypeSafetyTest], this is the rendering it's for.
 *
 * The two focused tests below compose a single section editor directly rather than the whole
 * [LogEntryDetailScreen]: every section there sits behind a [com.forager.app.ui.availability.CollapsibleSection]
 * that starts collapsed, so its fields aren't part of the tree at all until expanded — the third
 * test below covers that expand step once, the other two isolate the field-rendering rule itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MushroomLogNotObservedRenderingTest {

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
    fun `an entirely-unrecorded cap section shows Not recorded for each of its four fields`() {
        composeRule.setContent {
            CapEditor(section = CapSection.EMPTY, onChanged = {})
        }

        // shape, surface, decorations, margin — four independently unrecorded fields, none of
        // them rendered as blank or as an absent value.
        composeRule.onAllNodesWithText("Not recorded").assertCountEquals(4)
    }

    @Test
    fun `an explicitly Absent veil field reads differently from an unrecorded one`() {
        // annulus stays NotObserved; volva is recorded as explicitly absent (a real finding —
        // see VeilSection's doc comment) rather than never having been checked.
        val section = VeilSection.EMPTY.copy(volva = Feature.Absent)

        composeRule.setContent {
            VeilEditor(section = section, onChanged = {})
        }

        // Only annulus is still unrecorded — volva, now Absent, no longer reads as "Not recorded"
        // even though it also carries no value, which is exactly the distinction this test exists
        // to prove: two fields with no recorded value do not render identically.
        composeRule.onAllNodesWithText("Not recorded").assertCountEquals(1)
    }

    @Test
    fun `expanding the Cap section on the real entry screen reveals its Not recorded fields`() {
        val entry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))

        composeRule.setContent {
            LogEntryDetailScreen(
                entry = entry,
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                onEntryChanged = {},
                onAddPhoto = {},
                onRemovePhoto = {},
                onAddLocation = {},
                onDeleteEntry = {},
                onBack = {},
            )
        }

        // Collapsed by default (CollapsibleSection) — nothing from inside it is on screen yet.
        composeRule.onAllNodesWithText("Not recorded").assertCountEquals(0)

        composeRule.onNodeWithText("Cap").performClick()

        composeRule.onAllNodesWithText("Not recorded").assertCountEquals(4)
    }
}
