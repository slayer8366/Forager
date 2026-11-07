package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** THROWAWAY probe (dispatch 2026-11-06-01, A1, J2): where the next turn's space is drawn, per window, font and evening. Asserts nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NextTurnPlaceProbe : NavigationCardKindLineScreen() {

    private fun probe(where: String) {
        for (e in listOf(Evening.NONE, Evening.BEFORE_SUNSET, Evening.AFTER_SUNSET, Evening.DARK_SINCE, Evening.DARK_SINCE_LONG)) {
            show(State.TRAIL, e, Facing.TURN)
            val c = card("$where $e")
            val sp = composeRule.onAllNodes(hasTestTag(NEXT_TURN_SPACE_TAG) and hasAnyAncestor(hasTestTag(NAVIGATION_HUD_TAG)), useUnmergedTree = true).fetchSemanticsNodes()
            val r = sp.map { n -> with(composeRule.density) { "${n.boundsInRoot.left.toDp()}..${n.boundsInRoot.right.toDp()} x ${n.boundsInRoot.top.toDp()}..${n.boundsInRoot.bottom.toDp()}" } }
            val top = sp.singleOrNull()?.let { with(composeRule.density) { it.boundsInRoot.top.toDp() } }
            val place = when { top == null -> "none(${sp.size})"; top < c.needle.bottom -> "BESIDE"; else -> "OWN_LINE" }
            println("PLACE|$where|$e|$place|space $r|needle ${c.needle.left}..${c.needle.right} x ${c.needle.top}..${c.needle.bottom}|left part ${c.topLine.left}..${c.leftPartRight}|card height ${c.hud.bottom - c.hud.top}|pieces ${c.pieces.map { it.text }}")
        }
    }

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi") fun p320a() = probe("320/1.0")
    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 1.3f) fun p320b() = probe("320/1.3")
    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 2.0f) fun p320c() = probe("320/2.0")
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi") fun p360a() = probe("360/1.0")
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 1.3f) fun p360b() = probe("360/1.3")
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f) fun p360c() = probe("360/2.0")
    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi") fun p384a() = probe("384/1.0")
    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.3f) fun p384b() = probe("384/1.3")
    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f) fun p384c() = probe("384/2.0")
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi") fun p412a() = probe("412/1.0")
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f) fun p412b() = probe("412/1.3")
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 2.0f) fun p412c() = probe("412/2.0")
    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi") fun p480a() = probe("480/1.0")
    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi", fontScale = 1.3f) fun p480b() = probe("480/1.3")
    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi", fontScale = 2.0f) fun p480c() = probe("480/2.0")
    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi") fun l780a() = probe("780x360/1.0")
    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 1.3f) fun l780b() = probe("780x360/1.3")
    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f) fun l780c() = probe("780x360/2.0")
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi") fun l823a() = probe("823x384/1.0")
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.3f) fun l823b() = probe("823x384/1.3")
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f) fun l823c() = probe("823x384/2.0")
}
