package com.zynergylabs.forager.app.ui.motion

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/*
 * The press bounce on every tappable icon in the app (Amendment 1 to dispatch 2026-09-28-652, RECORD -657; the owner: "Every
 * icon button"). Material's icon buttons take an interaction source but draw their own content, so the bounce is applied to the
 * content inside them: the button's touch box, ripple and semantics are Material's, unchanged. Every `IconButton` and
 * `FilledIconToggleButton` call in the app goes through these two; the map bar's own rows bounce in `MapBarIconButton`.
 */

/** Material's [IconButton], with the press bounce ([pressBounce]) on its content. Same parameters as the app's calls use. */
@Composable
fun BouncingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled, interactionSource = interactionSource) {
        Box(modifier = Modifier.pressBounce(interactionSource), contentAlignment = Alignment.Center) { content() }
    }
}

/** Material's [FilledIconToggleButton], with the press bounce on its content. */
@Composable
fun BouncingFilledIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledIconToggleButton(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, interactionSource = interactionSource) {
        Box(modifier = Modifier.pressBounce(interactionSource), contentAlignment = Alignment.Center) { content() }
    }
}
