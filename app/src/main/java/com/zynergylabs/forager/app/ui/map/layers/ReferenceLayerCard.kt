package com.zynergylabs.forager.app.ui.map.layers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.zynergylabs.forager.app.domain.referencetrails.REFERENCE_SOURCES_LEAD
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailsCopy
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailsSourceDates
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailsSources
import com.zynergylabs.forager.app.domain.referencetrails.referenceTrailsSourcesText
import com.zynergylabs.forager.app.ui.format.displayDate
import com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * Step 8d (dispatch 2026-10-26-24; the owner's card, RECORD -1717): the official-trails card, opened from the (i)
 * beside the Layers sheet's "Official trails" row. It is shown in the Layers sheet itself, in place of the sheet's
 * list, so it is drawn on the sheet's own surface at the map chrome's 80% (`MapLayersSheet`'s container) and adds no
 * second layer over the map. Back closes only the card and the list returns as it was (-1717: "Back closes the card,
 * back on the Layers sheet").
 *
 * The owner shortened the card: no limits line and no coverage list (-1717, "Shorter card").
 */
internal const val REFERENCE_LAYER_CARD_TAG = "reference-layer-card"
internal const val REFERENCE_TRAILS_INFO_TAG = "reference-trails-info"
internal const val REFERENCE_LAYER_CARD_SOURCES_TAG = "reference-layer-card-sources"
internal const val REFERENCE_LAYER_CARD_HINT_TAG = "reference-layer-card-hint"

/** The (i)'s description for TalkBack. */
internal const val REFERENCE_TRAILS_INFO_DESCRIPTION = "About Official trails"

/** The card's three body lines, the owner's wording verbatim (RECORD -1717). The title is the row's own label. */
internal const val REFERENCE_CARD_WHAT = "Forest Service and BLM trail records, drawn as a faint violet band under the trail map's lines."
internal const val REFERENCE_CARD_READING =
    "Where the agency and the trail map agree, the trail sits inside the band. A band with no trail is an official trail the trail map lacks."
internal const val REFERENCE_CARD_ROUTES = "Routes follow the trail map, not this band."

/**
 * The app's one [ReferenceTrailsSources] (`AppContainer`), provided at `MainActivity`'s root. `null` where nothing
 * provides it (a test composing a sheet alone): the card then fetches nothing and shows the no-copy text.
 */
internal val LocalReferenceTrailsSources = staticCompositionLocalOf<ReferenceTrailsSources?> { null }

@Composable
internal fun ProvideReferenceTrailsSources(sources: ReferenceTrailsSources, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReferenceTrailsSources provides sources, content = content)
}

/** The (i): a [MIN_TOUCH_TARGET] square of its own, so its touches never reach the row's switch. */
@Composable
internal fun ReferenceTrailsInfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(MIN_TOUCH_TARGET)
            .clickable(role = Role.Button, onClickLabel = REFERENCE_TRAILS_INFO_DESCRIPTION, onClick = onClick)
            .testTag(REFERENCE_TRAILS_INFO_TAG),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = REFERENCE_TRAILS_INFO_DESCRIPTION)
    }
}

/**
 * The card, with Back closing it through [onClose]. Composed after the sheet's own Back handling, so this one is asked
 * first while the card shows (the innermost-enabled-`BackHandler`-wins convention, `CameraSettingsPanel.kt`).
 *
 * Each opening asks [LocalReferenceTrailsSources] to fetch the manifest; the dates on show are the kept copy until an
 * answer arrives, and stay so if none does.
 */
@Composable
internal fun ReferenceLayerCard(title: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val sources = LocalReferenceTrailsSources.current
    LaunchedEffect(sources) { sources?.refresh() }
    val copy = sources?.copy?.collectAsState()?.value ?: ReferenceTrailsCopy.None
    val text = when (copy) {
        // Only until the kept copy is read at start: no dates, and no claim that the phone was never online.
        ReferenceTrailsCopy.Loading -> referenceTrailsSourcesText(ReferenceTrailsSourceDates(null, null), ::displayDate)
        ReferenceTrailsCopy.None -> referenceTrailsSourcesText(null, ::displayDate)
        is ReferenceTrailsCopy.Known -> referenceTrailsSourcesText(copy.dates, ::displayDate)
    }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(REFERENCE_LAYER_CARD_TAG),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(REFERENCE_CARD_WHAT, style = MaterialTheme.typography.bodyMedium)
        Text(REFERENCE_CARD_READING, style = MaterialTheme.typography.bodyMedium)
        Text(REFERENCE_CARD_ROUTES, style = MaterialTheme.typography.bodyMedium)
        Text(
            buildAnnotatedString {
                val rest = text.line.removePrefix(REFERENCE_SOURCES_LEAD)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(REFERENCE_SOURCES_LEAD) }
                append(rest)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(REFERENCE_LAYER_CARD_SOURCES_TAG),
        )
        text.hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(REFERENCE_LAYER_CARD_HINT_TAG)) }
    }
}
