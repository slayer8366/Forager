package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.ui.map.MapSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The List tab's species rows offer the same "View on iNaturalist" hand-off the map's observation
 * bubble gives a tapped marker — see [SpeciesRow]'s own doc comment and [launchINaturalistTaxon]'s
 * for why a species row (an aggregate keyed on [SpeciesObservationCount.taxonId], not a single
 * observation) links to the taxon page rather than an observation page. Same "real screen under
 * Robolectric, real Intent captured via Shadows" discipline
 * [AvailabilityScreenMapIconStackTest]'s own bubble test uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenListTabInaturalistTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private fun setScreen(uiState: AvailabilityUiState) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = uiState,
                onUseCurrentLocation = {},
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onToggleForagingAreas = {},
                onCategorySelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                onNightModeMapsChanged = {},
                onThemeModeChanged = {},
                mapSlot = StubMapSlot,
            )
        }
    }

    private fun openListTab() {
        composeRule.onNodeWithText("List").performClick()
    }

    /**
     * Registers a fake `https:` handler on [composeRule]'s own host activity, same reason
     * [AvailabilityScreenMapIconStackTest]'s own `registerFakeBrowser` documents: Robolectric's
     * package manager starts with nothing able to resolve an implicit intent, so
     * [launchINaturalistTaxon]'s resolve-then-launch guard would otherwise always take its
     * "nothing installed" branch and never actually call `startActivity`.
     */
    private fun registerFakeBrowser() {
        val componentName = ComponentName(composeRule.activity, "com.example.fakebrowser.BrowserActivity")
        val shadowPackageManager = Shadows.shadowOf(composeRule.activity.packageManager)
        shadowPackageManager.addActivityIfNotPresent(componentName)
        shadowPackageManager.addIntentFilterForActivity(
            componentName,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
            },
        )
    }

    @Test
    fun `a species row shows View on iNaturalist on the same line as the observation count`() {
        setScreen(SEARCHED_STATE)

        openListTab()

        composeRule.onNodeWithText("42 observations").assertIsDisplayed()
        composeRule.onNodeWithText("View on iNaturalist").assertIsDisplayed()
    }

    @Test
    fun `tapping the View on iNaturalist text launches the taxon's web page`() {
        setScreen(SEARCHED_STATE)
        registerFakeBrowser()

        openListTab()
        composeRule.onNodeWithText("View on iNaturalist").performClick()
        composeRule.waitForIdle()

        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://www.inaturalist.org/taxa/47348", started?.data.toString())
    }

    @Test
    fun `tapping anywhere else on the row also launches the taxon's web page`() {
        setScreen(SEARCHED_STATE)
        registerFakeBrowser()

        openListTab()
        composeRule.onNodeWithTag("species-row").performClick()
        composeRule.waitForIdle()

        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://www.inaturalist.org/taxa/47348", started?.data.toString())
    }

    @Test
    fun `with nothing installed to open it, tapping the row shows a message instead of crashing`() {
        setScreen(SEARCHED_STATE)

        openListTab()
        composeRule.onNodeWithTag("species-row").performClick()
        composeRule.waitForIdle()

        assertNull(Shadows.shadowOf(composeRule.activity).nextStartedActivity)
    }
}

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private val SPECIES = SpeciesObservationCount(
    taxonId = 47348,
    scientificName = "Cantharellus formosus",
    commonName = "Pacific Golden Chanterelle",
    rank = "species",
    observationCount = 42,
    photoUrl = null,
    wikipediaUrl = null,
)

private val FORECAST = AvailabilityForecast(
    region = REGION,
    month = 8,
    filter = TaxonFilter.FUNGI,
    entries = listOf(AvailabilityEntry(species = SPECIES, relativeLikelihood = 1f)),
)

private val SEARCHED_STATE = AvailabilityUiState(region = REGION, forecast = FORECAST)

private val StubMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("map-slot")) }
