package com.zynergylabs.forager.app.ui.credits

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.ui.availability.SETTINGS_SOURCES_ROW_TAG
import com.zynergylabs.forager.app.ui.availability.SettingsPageHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Settings > Sources & credits (dispatch 2026-10-13-05), reached the way a person reaches it: the real
 * [com.zynergylabs.forager.app.ui.availability.AvailabilityScreen], Tools, Settings, then the row.
 *
 * The expected sources and links are written out here as values, not read back from
 * [DATA_SOURCE_SECTIONS], so a changed link fails this rather than moving the goalposts with it; the one
 * read of that list is the check that it holds exactly these sources and no others. Every link is opened
 * by real touches at points across its row and at its own "›" label, read as the Intent the app starts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SourcesAndCreditsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SettingsPageHarness.declareHostActivity()).around(composeRule)

    @Test
    fun `Sources and credits is Settings' last row`() {
        SettingsPageHarness.openSettings(composeRule)
        val settings = composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText(SettingsPageHarness.SETTINGS_CONTROL_ROW)))
        settings.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()

        val row = composeRule.onNodeWithTag(SETTINGS_SOURCES_ROW_TAG).fetchSemanticsNode()
        assertEquals("the row's label", listOf("Sources & credits", "›"), row.texts())
        val others = descendantsOf(settings.fetchSemanticsNode()).filter { it.id != row.id && it.boundsInRoot.height > 0f }
        assertTrue("Settings has rows above it", others.isNotEmpty())
        val below = others.filter { it.boundsInRoot.bottom > row.boundsInRoot.top + 0.5f }
        assertTrue("nothing in Settings is drawn below the row, but found ${below.map { it.texts() }}", below.isEmpty())
    }

    @Test
    fun `the page lists exactly these sources`() {
        assertEquals(EXPECTED.map { it.key }, DATA_SOURCE_SECTIONS.flatMap { section -> section.credits.map { it.key } })
        assertEquals(listOf("Maps", "Weather", "Sightings and species names"), DATA_SOURCE_SECTIONS.map { it.title })
    }

    @Test
    fun `each source shows its credit and opens its own link, from anywhere across its row`() {
        openSources()
        registerBrowser()
        onPage("Sources & credits").assertIsDisplayed()
        listOf("Maps", "Weather", "Sightings and species names").forEach { onPage(it).performScrollTo().assertIsDisplayed() }
        onPage("Rain and soil figures in Forager are summaries calculated from this data.").performScrollTo().assertIsDisplayed()
        // CC BY 4.0's change note for the Forest Service's trails (dispatch 2026-10-26-21, RECORD -1703: "Approve as drafted").
        onPage("Trail lines in Forager are simplified from this data.").performScrollTo().assertIsDisplayed()

        EXPECTED.filter { it.link == null }.forEach { source ->
            // Public domain asks for no credit and names no licence to link to: the row is the credit alone, and a touch on
            // it opens nothing.
            val row = composeRule.onNodeWithTag(sourceCreditRowTag(source.key)).performScrollTo()
            assertEquals("the ${source.key} row's text", listOf(source.text), row.fetchSemanticsNode().texts())
            composeRule.onNodeWithTag(sourceCreditLinkTag(source.key), useUnmergedTree = true).assertDoesNotExist()
            FRACTIONS.forEach { fraction ->
                row.performTouchInput { click(Offset(width * fraction, height / 2f)) }
                composeRule.waitForIdle()
                assertEquals("a touch at $fraction across the ${source.key} row opens nothing", null, Shadows.shadowOf(composeRule.activity).nextStartedActivity)
            }
        }
        EXPECTED.filter { it.link != null }.forEach { source ->
            val link = checkNotNull(source.link)
            val url = checkNotNull(source.url)
            val row = composeRule.onNodeWithTag(sourceCreditRowTag(source.key)).performScrollTo()
            assertEquals("the ${source.key} row's text", listOf(source.text, link), row.fetchSemanticsNode().texts())
            composeRule.onNodeWithTag(sourceCreditLinkTag(source.key), useUnmergedTree = true).assertTextEquals(link)

            FRACTIONS.forEach { fraction ->
                row.performTouchInput { click(Offset(width * fraction, height / 2f)) }
                assertOpened(url, "a touch at $fraction across the ${source.key} row")
            }
            touchCentreOf(composeRule.onNodeWithTag(sourceCreditLinkTag(source.key), useUnmergedTree = true))
            assertOpened(url, "a touch on the ${source.key} row's \"›\"")
        }
    }

    @Test
    fun `Back from Sources and credits returns to Settings, still at its last row`() {
        openSources()

        pressBack()

        composeRule.onNodeWithText(SettingsPageHarness.SETTINGS_CONTROL_ROW).assertExists()
        composeRule.onNodeWithTag(SOURCES_PAGE_TAG).assertDoesNotExist()
        // Not scrolled to: Settings kept its place, so the row that was tapped is still on screen.
        composeRule.onNodeWithTag(SETTINGS_SOURCES_ROW_TAG).assertIsDisplayed()
    }

    @Test
    fun `the open-source software page lists every library, each with a licence text that is not empty`() {
        val groups = SoftwareCredits.load(composeRule.activity.assets).getOrThrow()
        openSources()
        openSoftware()
        val list = composeRule.onNodeWithTag(SOFTWARE_PAGE_TAG)

        groups.flatMap { it.libraries }.forEach { library ->
            list.performScrollToNode(hasText(library.name))
            composeRule.onNodeWithText(library.name).assertIsDisplayed()
        }
        groups.forEach { group ->
            val expectedFirst = licenceParagraphs(readAsset("credits/${group.licence.file}")).first()
            list.performScrollToNode(hasTestTag(licenceTextTag(group.licence.id)))
            val shown = composeRule.onNodeWithTag(licenceTextTag(group.licence.id)).assertIsDisplayed().fetchSemanticsNode().texts().single()
            assertTrue("${group.licence.id}'s text on screen is not blank", shown.isNotBlank())
            assertEquals("${group.licence.id}'s text on screen is its file's own", expectedFirst, shown)
            assertTrue("${group.licence.id}'s text begins with its heading", shown.trimStart().startsWith(HEADINGS.getValue(group.licence.id)))
        }
        assertEquals("one licence group per licence the libraries use", HEADINGS.keys, groups.map { it.licence.id }.toSet())
    }

    @Test
    fun `Back from the software page returns to Sources and credits, and Back again to Settings`() {
        openSources()
        openSoftware()

        pressBack()
        composeRule.onNodeWithTag(SOURCES_PAGE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SOFTWARE_PAGE_TAG).assertDoesNotExist()

        pressBack()
        composeRule.onNodeWithTag(SOURCES_PAGE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SETTINGS_SOURCES_ROW_TAG).assertIsDisplayed()
    }

    /** Text on the Sources & credits page itself: "Maps" is also the bottom bar's tab. */
    private fun onPage(text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(SOURCES_PAGE_TAG)))

    private fun openSources() {
        SettingsPageHarness.openSettings(composeRule)
        touchCentreOf(composeRule.onNodeWithTag(SETTINGS_SOURCES_ROW_TAG).performScrollTo())
        composeRule.onNodeWithTag(SOURCES_PAGE_TAG).assertIsDisplayed()
    }

    private fun openSoftware() {
        touchCentreOf(composeRule.onNodeWithTag(OPEN_SOURCE_SOFTWARE_ROW_TAG).performScrollTo())
        composeRule.onNodeWithTag(SOFTWARE_PAGE_TAG).assertIsDisplayed()
    }

    /** A real touch at the centre of [node]'s bounds, given to the root so it is hit-tested like a finger. */
    private fun touchCentreOf(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput { click(Offset(bounds.center.x, bounds.center.y)) }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    /** Something to open https links with: Robolectric resolves nothing by default, and the app checks before it launches. */
    private fun registerBrowser() {
        val activity = composeRule.activity
        val browser = ComponentName(activity, "com.example.browser.BrowserActivity")
        val packageManager = Shadows.shadowOf(activity.packageManager)
        packageManager.addActivityIfNotPresent(browser)
        packageManager.addIntentFilterForActivity(
            browser,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
            },
        )
    }

    private fun assertOpened(url: String, what: String) {
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        assertEquals("$what started a view", Intent.ACTION_VIEW, started?.action)
        assertEquals("$what opened its link", url, started?.data.toString())
        assertFalse("$what started one activity, not two", Shadows.shadowOf(composeRule.activity).nextStartedActivity != null)
    }

    private fun readAsset(path: String): String = composeRule.activity.assets.open(path).bufferedReader().use { it.readText() }

    private fun descendantsOf(node: SemanticsNode): List<SemanticsNode> = node.children.flatMap { listOf(it) + descendantsOf(it) }

    private fun SemanticsNode.texts(): List<String> = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    /** [link] and [url] are `null` for a source whose row has no link (public domain: nothing to link to). */
    private data class ExpectedSource(val key: String, val text: String, val link: String?, val url: String?)

    private companion object {
        val FRACTIONS = listOf(0.05f, 0.5f, 0.95f)

        val EXPECTED = listOf(
            ExpectedSource("openstreetmap", "© OpenStreetMap contributors", "licence ›", "https://www.openstreetmap.org/copyright"),
            ExpectedSource(
                "openstreetmap-routes",
                "Trail routes: © OpenStreetMap contributors",
                "ODbL ›",
                "https://opendatacommons.org/licenses/odbl/1-0/",
            ),
            ExpectedSource(
                "opentopomap",
                "Map data: © OpenStreetMap contributors, SRTM; map style: © OpenTopoMap (CC-BY-SA)",
                "licence ›",
                "https://opentopomap.org/about",
            ),
            ExpectedSource("protomaps", "Protomaps (Forager Trail Map and offline maps)", "licence ›", "https://github.com/protomaps/basemaps"),
            ExpectedSource(
                "usfs-trails",
                "Trails: National Forest System Trails, USDA Forest Service, CC BY 4.0",
                "CC BY 4.0 ›",
                "https://creativecommons.org/licenses/by/4.0/",
            ),
            ExpectedSource("blm-trails", "Trails: Bureau of Land Management, public domain", null, null),
            // Trail Map's hill shading (dispatch 2026-11-01-coder-11): the heights it is drawn from.
            ExpectedSource("usgs-3dep", "Heights (hill shading): USGS 3D Elevation Program, public domain", null, null),
            ExpectedSource(
                "nrcan-mrdem",
                "Heights in Canada (hill shading): Natural Resources Canada, MRDEM. Contains information licensed under the Open Government Licence – Canada.",
                "licence ›",
                "https://open.canada.ca/en/open-government-licence-canada",
            ),
            ExpectedSource("open-meteo", "Weather data by Open-Meteo.com", "CC BY 4.0 ›", "https://open-meteo.com/en/licence"),
            ExpectedSource("inaturalist", "iNaturalist and its observers", "site ›", "https://www.inaturalist.org/"),
        )

        /** Each licence text's own first line, as its publisher writes it. */
        val HEADINGS = mapOf(
            "Apache-2.0" to "Apache License",
            "BSD-2-Clause" to "BSD 2-Clause License",
            "MIT" to "MIT License",
            "MPL-2.0" to "Mozilla Public License Version 2.0",
            "OFL-1.1" to "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
        )
    }
}
