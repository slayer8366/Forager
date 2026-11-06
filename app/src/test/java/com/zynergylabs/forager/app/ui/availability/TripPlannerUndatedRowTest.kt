package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.format.displayDate
import java.time.LocalDate
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
 * The Trip Planner's rows for an undated trip (schema 23, dispatch 2026-10-30-02, step 1), through [TripPlannerSection],
 * the section the drawer shows: no date line, and the delete button named in the owner's words, "Delete trip <name>"
 * (Amendment 1). A dated trip beside it keeps its date and its label. The delete is a semantic click: the claim here is
 * the label and its wiring, not where a finger lands.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TripPlannerUndatedRowTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(composeRule)

    private val dated = PlannedTrip("dated", "Chanterelle weekend", LatLng(45.4, -122.7), LocalDate.of(2099, 11, 14))
    private val undated = PlannedTrip("undated", "Ridge run", LatLng(45.2, -122.5), date = null)

    @Test
    fun `an undated trip shows no date, and its delete button reads Delete trip and its name`() {
        val deleted = mutableListOf<String>()
        composeRule.setContent { TripPlannerSection(AvailabilityUiState(plannedTrips = listOf(dated, undated)), onDeletePlannedTrip = { deleted += it }) }

        composeRule.onNodeWithText("Ridge run").assertIsDisplayed()
        composeRule.onNodeWithText(displayDate(dated.date!!)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove planned trip for ${displayDate(dated.date!!)}").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete trip Ridge run").performClick()

        assertEquals(listOf("undated"), deleted)
    }

    @Test
    fun `the row's labels, as a dated and an undated trip read`() {
        assertEquals("Remove planned trip for ${displayDate(dated.date!!)}", plannedTripDeleteLabel(dated))
        assertEquals("Delete trip Ridge run", plannedTripDeleteLabel(undated))
    }
}
