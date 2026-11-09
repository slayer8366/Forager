package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilitySearchUi.kt (dispatch 2026-10-19-17, UI split Phase 1, PR 10a).

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.mapChromeContentColor
import com.zynergylabs.forager.app.ui.theme.Spacing


/**
 * The species search controls themselves: the species text field and its suggestion dropdown. One
 * caller, [SearchEntryBar], the compact top bar that hosts this field directly (map/navigation
 * redesign dispatch D's "the top bar should be the entry field"). Factored out when the old
 * `AvailabilitySearchTopBar` and [CompactToolsDrawerContent] hosted it too; neither does now (the
 * first is gone, the second holds no search), so the parameters below that existed for them have
 * been removed or are defaulted for this one caller (dispatch 2026-09-28-658, scout item F3: the
 * location trailing icon, which only those callers showed, and the callback that fed it).
 *
 * The category chip row this composable used to render alongside the field is gone (owner
 * decision): the app is fungi-only now, so there is nothing left to choose between. See
 * [AvailabilityUiState.taxonFilter]'s default and [activeSearchSummary].
 *
 * [queryFieldModifier]/[onQueryFieldFocusChanged]: [SearchEntryBar] needs a stable [testTag] on
 * the real field to tap/focus, and a way to know when that focus changes so it can drive
 * [SearchDropdown]'s own visibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeciesSearchControls(
    uiState: AvailabilityUiState,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    // The root's modifier, by Compose's convention; the field's own is queryFieldModifier (lint ModifierParameter, dispatch 2026-10-17-03).
    modifier: Modifier = Modifier,
    queryFieldModifier: Modifier = Modifier,
    onQueryFieldFocusChanged: (Boolean) -> Unit = {},
    /**
     * Placeholder text shown while the field is empty, focused or not — map/navigation search-UI
     * redo dispatch, the owner's own direct call: the bar reads as the current filter summary
     * ("August · 9 mi", or a searched species' name ahead of it) always, not a generic hint that
     * blanks out what's currently searched the moment someone taps in to search. The generic hint
     * this used to swap to on focus is gone from the app entirely, by direct owner instruction.
     */
    restingPlaceholder: String = "",
    fieldColors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    /**
     * Text style for the entered/placeholder text — map/navigation search-UI redo dispatch:
     * [SearchEntryBar] pins its field to a short, fixed height (twice the compass strip's own
     * measured row height), and the default [OutlinedTextField] text style (`bodyLarge`, sized for
     * a full ~56dp Material field) doesn't fit inside it — the text was clipped away entirely, not
     * merely cramped. The owner's own direct call: scale the text down to fit the box, rather than
     * the box up to fit default-sized text. Defaulted to Material's own ([LocalTextStyle.current]).
     */
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    /**
     * Vertical (and horizontal) padding inside the field's own box — map/navigation search-UI
     * redo dispatch: shrinking [textStyle] alone wasn't enough to fit [SearchEntryBar]'s pinned
     * field height; [OutlinedTextField] reserves its own fixed padding around the text line
     * regardless of that style, so even a correctly-sized line was still being clipped by the box
     * itself. This is the owner's own direct follow-up call — reduce the padding, not the text
     * further. Implemented via the low-level [BasicTextField] + [OutlinedTextFieldDefaults.DecorationBox]
     * pair (the only Material3 path that exposes content padding at all) for every call site, but
     * defaulted to [OutlinedTextFieldDefaults.contentPaddingWithoutLabel], Material's own stock value (what the deprecated `contentPadding` returned).
     */
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPaddingWithoutLabel(),
    /** Whether the suggestions open over a map drawn on screen; they are then at the map chrome's alpha (owner, "1 A"). */
    suggestionsOverMap: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val suggestionsOpen = uiState.taxonSearchResults.isNotEmpty() || uiState.taxonSearchHasNoResults
        val queryFieldInteractionSource = remember { MutableInteractionSource() }
        ExposedDropdownMenuBox(
            expanded = suggestionsOpen,
            onExpandedChange = {},
            modifier = Modifier.padding(horizontal = Spacing.sm),
        ) {
            // BasicTextField + OutlinedTextFieldDefaults.DecorationBox, not the plain
            // OutlinedTextField composable — see [contentPadding]'s own doc comment for why: it's
            // the only Material3 path that exposes the field's internal padding at all.
            BasicTextField(
                value = uiState.taxonSearchQuery,
                onValueChange = onTaxonSearchQueryChanged,
                textStyle = textStyle.copy(color = fieldColors.focusedTextColor),
                singleLine = true,
                cursorBrush = SolidColor(fieldColors.cursorColor(isError = false)),
                interactionSource = queryFieldInteractionSource,
                modifier = queryFieldModifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .onFocusChanged {
                        onQueryFieldFocusChanged(it.isFocused)
                    },
            ) { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = uiState.taxonSearchQuery,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = queryFieldInteractionSource,
                    placeholder = {
                        // The filter summary ("Fungi · August · 9 mi"), not a generic hint,
                        // whether focused or not — the owner's own direct call: focusing the
                        // field to search shouldn't blank out what's currently searched, only
                        // typing should.
                        Text(
                            restingPlaceholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = textStyle,
                        )
                    },
                    trailingIcon = if (uiState.isSearchingTaxa) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                    } else {
                        null
                    },
                    colors = fieldColors,
                    contentPadding = contentPadding,
                )
            }
            // A no-op onDismissRequest here before this fix meant the standard "tap outside
            // the popup" dismissal ExposedDropdownMenu already implements never actually
            // closed anything — the only way to get rid of the list was to pick a result or
            // clear the query back below MIN_QUERY_LENGTH. Wiring the real dismiss action in
            // is the fix, not new behavior invented on top of the component.
            // The menu's default container role, passed explicitly, and its content colour pinned to
            // the role's own (`contentColorFor` matches a colour-scheme role exactly; see
            // `MapLayersSheet`), as J8's menus do (`journalMenuContentColor`).
            // Owner, verbatim "1 A" (planner message 2026-09-28-77, Q3): at the map chrome's alpha on its
            // own over the Maps tab, stacking over the 0.8 search panel; solid where no map is drawn.
            val suggestionsColor = mapChromeFill(navigationBarContainerColor(), suggestionsOverMap)
            val suggestionsContentColor = contentColorFor(navigationBarContainerColor())
            ExposedDropdownMenu(
                expanded = suggestionsOpen,
                onDismissRequest = onDismissTaxonSuggestions,
                containerColor = suggestionsColor,
                modifier = Modifier
                    .testTag(TAXON_SUGGESTIONS_MENU_TAG)
                    .mapChromeContainerColor(suggestionsColor),
            ) {
                CompositionLocalProvider(LocalContentColor provides suggestionsContentColor) {
                    // The content colour as read inside the menu, on its first row, for tests.
                    val readInside = Modifier.mapChromeContentColor(LocalContentColor.current)
                    if (uiState.taxonSearchResults.isEmpty() && uiState.taxonSearchHasNoResults) {
                        DropdownMenuItem(
                            text = { Text("No matches for “${uiState.taxonSearchQuery.trim()}”") },
                            onClick = {},
                            enabled = false,
                            modifier = readInside,
                        )
                    } else {
                        uiState.taxonSearchResults.forEachIndexed { index, result ->
                            DropdownMenuItem(
                                text = { TaxonSuggestionContent(result) },
                                onClick = { onTaxonSearchResultSelected(result) },
                                modifier = if (index == 0) readInside else Modifier,
                            )
                        }
                    }
                }
            }
        }
        // Back closes the suggestions first, as it does other popups (dispatch 2026-09-28-104, item 4; the
        // map-chrome device check saw three Backs change nothing). Diagnosed, not assumed: under Robolectric,
        // with the list open, Back reached none of this screen's handlers (the home handler, the search
        // panel's, and a first version of this one all stayed silent), and `ExposedDropdownMenuBox`'s bytecode
        // (material3 1.5.0-alpha26) calls its own internal `BackHandler` after composing its content. That
        // handler answers with `onExpandedChange(false)`, which this box leaves a no-op on purpose (a tap on
        // the field must not toggle the list), so Back was taken and did nothing. Handlers win in
        // registration order, so this one is composed after the box, not inside it or before it.
        BackHandler(enabled = suggestionsOpen) { onDismissTaxonSuggestions() }
    }
}

/** One suggestion's content inside a [DropdownMenuItem], which supplies the click and padding. */
@Composable
private fun TaxonSuggestionContent(result: TaxonSearchResult) {
    Column {
        Text(result.commonName ?: result.scientificName, style = MaterialTheme.typography.bodyMedium)
        val subtitle = result.scientificName + (result.iconicTaxonName?.let { " · $it" } ?: "")
        Text(subtitle, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
    }
}

/** The species suggestions' menu ([SpeciesSearchControls]), for tests. */
internal const val TAXON_SUGGESTIONS_MENU_TAG = "taxon-suggestions-menu"
