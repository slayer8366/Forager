package com.zynergylabs.forager.app.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/*
 * Motion Part 2, item 6 (dispatch 2026-09-28-666; the owner, RECORD -651: "Numbers instant, words fade": "Numbers change
 * instantly; changed words and status lines crossfade quickly").
 */

/**
 * The words of [text] with every number in it reduced to one mark: what [WordSwap] keys its crossfade on. "Fix 5 s ago" and
 * "Fix 12 s ago" have the same words; "0.4 mi" and "Arrived" do not. A number is a run of digits, with any decimal point or
 * comma and digits after it ("0.4", "1,234"), so a value gaining a digit is still the same words.
 */
fun wordsOf(text: String): String = NUMBER.replace(text, "#")

private val NUMBER = Regex("""\d+(?:[.,]\d+)*""")

/**
 * A line of text that crossfades, on [MotionTokens.wordSwapSpec], when its words change, and changes at once when only its
 * numbers do ([wordsOf]). [content] draws the line from the text it is handed.
 *
 * No size animation: while two lines cross, the box is the larger of the two and then takes the new line's size, so the row
 * around it is laid out at most twice, never on every frame. Under reduced motion the crossfade stays, as an icon swap's does
 * (docs/motion-spec.md §4: a fade is the reduced form).
 *
 * A test tag belongs inside [content], on the text itself, so a reader finds the text's own node; for the length of a
 * crossfade there are two.
 */
@Composable
fun WordSwap(
    text: String,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable (String) -> Unit,
) {
    val fade = MotionTokens.wordSwapSpec<Float>()
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = { (fadeIn(animationSpec = fade) togetherWith fadeOut(animationSpec = fade)).using(null) },
        contentAlignment = contentAlignment,
        contentKey = { wordsOf(it) },
        label = "wordSwap",
    ) { shown -> content(shown) }
}
