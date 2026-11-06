package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-05-06 (RECORD -2155, the owner: "App's own notice (Recommended)", whose option read "The app gets its own version:
 * the same layout, words and buttons, with no shadow and the thin outline"; -2156 for the one-row layout the host reaches). The
 * app's notice laid out beside Material3's own `Snackbar(snackbarData)` (1.5.0-alpha26, whose `ComposeMaterial3Flags.
 * isSnackbarStylingFixEnabled` is false, so `LegacyOneRowSnackbar`), with the same data, colours and width: the whole notice with its
 * 12 dp margin, the message, the action and the dismiss button, each at the same place and size within 0.5 dp. One, two and four
 * lines, each with no button, the action, the action and dismiss, and dismiss alone; at the phone's width and past Material's 600 dp
 * cap. The host never asks for the action on a line of its own (ScaffoldSnackbarHost.kt passes no `actionOnNewLine`), so that layout
 * is not built or compared.
 *
 * The action's colour is read from the colour its text was laid out with, on both sides, and printed: Material colours the action's
 * button with `actionColor`, not `actionContentColor`, and the host passes only the latter. Measured here for the planner, not
 * changed (RECORD -2156, item 3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w900dp-h1200dp-xxhdpi")
class MapChromeSnackbarParityTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private class Data(private val text: String, private val label: String?, private val withDismiss: Boolean) : SnackbarData {
        var actions = 0
        var dismissals = 0
        override val visuals: SnackbarVisuals = object : SnackbarVisuals {
            override val message: String = text
            override val actionLabel: String? = label
            override val withDismissAction: Boolean = withDismiss
            override val duration: SnackbarDuration = SnackbarDuration.Indefinite
        }
        override fun performAction() { actions++ }
        override fun dismiss() { dismissals++ }
    }

    private class Case(val message: String, val label: String?, val dismiss: Boolean, val width: Dp) {
        val data = Data(message, label, dismiss)
        override fun toString() = "${message.lines().size} line(s), action ${label != null}, dismiss $dismiss, ${width.value.toInt()} dp"
    }

    private var case by mutableStateOf(Case("Saved to Drafts", null, false, 360.dp))
    private var primary = Color.Unspecified
    private var inversePrimary = Color.Unspecified

    private fun setScreen() {
        composeRule.setContent {
            ForagerTheme(darkTheme = false) {
                primary = MaterialTheme.colorScheme.primary
                inversePrimary = MaterialTheme.colorScheme.inversePrimary
                // The host's own colours (ScaffoldSnackbarHost.kt), solid: the layout does not depend on them.
                val container = navigationBarContainerColor()
                val content = MaterialTheme.colorScheme.onSurfaceVariant
                val c = case
                Column {
                    Box(Modifier.width(c.width).testTag(MATERIAL)) {
                        Snackbar(
                            snackbarData = c.data,
                            containerColor = container,
                            contentColor = content,
                            actionContentColor = MaterialTheme.colorScheme.primary,
                            dismissActionContentColor = content,
                        )
                    }
                    Box(Modifier.width(c.width).testTag(OWN)) {
                        MapChromeSnackbar(
                            snackbarData = c.data,
                            containerColor = container,
                            contentColor = content,
                            actionContentColor = MaterialTheme.colorScheme.primary,
                            dismissActionContentColor = content,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun within(side: String, matcher: SemanticsMatcher): SemanticsNodeInteraction =
        composeRule.onNode(matcher and hasAnyAncestor(hasTestTag(side)), useUnmergedTree = true)

    /** [node]'s bounds relative to its side's own box. */
    private fun relative(side: String, node: SemanticsNodeInteraction): DpRect {
        val box = composeRule.onNodeWithTag(side).getUnclippedBoundsInRoot()
        val b = node.getUnclippedBoundsInRoot()
        return DpRect(b.left - box.left, b.top - box.top, b.right - box.left, b.bottom - box.top)
    }

    private fun assertSamePlace(what: String, material: DpRect, own: DpRect) {
        val off = listOf(own.left - material.left, own.top - material.top, own.right - material.right, own.bottom - material.bottom)
        assertTrue("$case: $what is where Material puts it: Material $material, own $own", off.all { abs(it.value) <= 0.5f })
    }

    private fun textColour(node: SemanticsNodeInteraction): Color {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first().layoutInput.style.color
    }

    @Test
    fun `the notice lays out its message, action and dismiss where Material's snackbar does, in one, two and four lines`() {
        setScreen()
        val messages = listOf("Saved to Drafts", "The first line of two\nand the second", "One\nTwo\nThree\nFour")
        val buttons = listOf(null to false, "Undo" to false, "Undo" to true, null to true)
        var compared = 0
        listOf(360.dp, 700.dp).forEach { width ->
            messages.forEach { message ->
                buttons.forEach { (label, dismiss) ->
                    composeRule.runOnIdle { case = Case(message, label, dismiss, width) }
                    composeRule.waitForIdle()
                    assertSamePlace("the whole notice, margin included", relative(MATERIAL, composeRule.onNodeWithTag(MATERIAL)), relative(OWN, composeRule.onNodeWithTag(OWN)))
                    assertSamePlace("the message", relative(MATERIAL, within(MATERIAL, hasText(message))), relative(OWN, within(OWN, hasText(message))))
                    assertEquals("$case: the message's colour", textColour(within(MATERIAL, hasText(message))), textColour(within(OWN, hasText(message))))
                    if (label != null) {
                        assertSamePlace("the action", relative(MATERIAL, within(MATERIAL, hasText(label))), relative(OWN, within(OWN, hasText(label))))
                    }
                    if (dismiss) {
                        assertSamePlace("the dismiss button", relative(MATERIAL, within(MATERIAL, hasContentDescription(DISMISS))), relative(OWN, within(OWN, hasContentDescription(DISMISS))))
                    }
                    compared++
                }
            }
        }
        assertEquals("every case was compared", 2 * messages.size * buttons.size, compared)
    }

    /** RECORD -2156, item 3: measured and reported, not changed. Material's and the notice's must be the same colour. */
    @Test
    fun `the action is drawn in the colour Material draws it in`() {
        case = Case("Saved to Drafts", "Undo", false, 360.dp)
        setScreen()
        val material = textColour(within(MATERIAL, hasText("Undo")))
        val own = textColour(within(OWN, hasText("Undo")))
        fun name(c: Color) = when (c) {
            primary -> "primary"
            inversePrimary -> "inversePrimary"
            else -> "neither"
        }
        println("EDGESHADE|action colour|Material ${edgeHex(material)} (${name(material)}), own ${edgeHex(own)} (${name(own)}); primary ${edgeHex(primary)}, inversePrimary ${edgeHex(inversePrimary)}")
        assertEquals("the action's colour is Material's", material, own)
    }

    /** The notice's buttons do what Material's do: a real touch on the action performs it, and on the dismiss button dismisses. */
    @Test
    fun `a touch on the notice's action performs it and a touch on its dismiss button dismisses it`() {
        val c = Case("Saved to Drafts", "Undo", true, 360.dp)
        case = c
        setScreen()
        within(OWN, hasText("Undo")).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals("the action was performed once", 1, c.data.actions)
        within(OWN, hasContentDescription(DISMISS)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals("the notice was dismissed once", 1, c.data.dismissals)
    }

    private companion object {
        const val MATERIAL = "material-snackbar"
        const val OWN = "own-notice"
        /** Material's dismiss button's description (its `Strings.SnackbarDismiss`), which SettingsResetSnackbarTest touches by. */
        const val DISMISS = "Dismiss"
    }
}
