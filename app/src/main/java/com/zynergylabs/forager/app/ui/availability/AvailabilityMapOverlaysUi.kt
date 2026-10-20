package com.zynergylabs.forager.app.ui.availability

// Split-AvailabilityScreen Stage F: the map's overlays and dialogs, moved verbatim out of
// AvailabilityScreen.kt. Three blocks, lines of the file as of 3bd0efe: 3076-3087
// (PendingMapAction, its KDoc moved as it was), 5008-5114 (TripDatePickerDialog,
// WaypointNameDialog) and 5345-5673 (OBSERVATION_DATE_FORMAT,
// OBSERVATION_BUBBLE_BASE_DIRECTION_DEG, OBSERVATION_BUBBLE_ARROW_TAIL_LENGTH,
// OBSERVATION_BUBBLE_ARROW_BASE_HALF_WIDTH, AnchoredAtScreenPoint, ObservationBubble,
// (M1 moved AnchoredAtScreenPoint and the three bubble constants to ui/map/MapBubble.kt with the
// shell ObservationBubble now draws on.)
// TaxonMapFilterChip, MapMessage). Same package as Stages A to E, so every same-package reference
// resolves unchanged. Pure move: no signature, name or body changed. Seven widenings, private ->
// internal: PendingMapAction, TripDatePickerDialog, WaypointNameDialog, AnchoredAtScreenPoint,
// ObservationBubble, TaxonMapFilterChip and MapMessage, each used by both map tabs, CompactMapTab
// (AvailabilityCompactMapUi.kt) and MapTab (AvailabilityWideLayoutUi.kt). No symbol left behind is
// reached from here. Seam F (the wide layout) was released by the owner for this split, as recorded
// in the Understory amendment merged in #130.

import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorDark
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorLight
import com.zynergylabs.forager.app.ui.map.MapBubbleShell
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * The map tab: the map itself takes the whole content area apart from the foraging-areas toggle
 * and detail below it, which are bounded so they can't repeat the starvation this layout exists
 * to fix.
 *
 * Which of the three-way menu's options is currently mid-pick, between the menu closing and the
 * centre-pin picker confirming — see [MapTab]/[CompactMapTab]'s own doc comments. There is no
 * "none, but the picker is still showing" state: the picker overlay's own visibility is driven by
 * this being non-null, not a separate flag, so the two can never disagree about whether a pick is
 * in progress.
 */
internal enum class PendingMapAction { PLAN_TRIP, LOG_FIND, DROP_WAYPOINT }

/**
 * Confirms a date and name for a trip pin placed via [com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker].
 *
 * The date is restricted to today-or-later — planning a trip in the past makes no sense, per the
 * user's own framing of this feature. [SavePlannedTripUseCase][com.zynergylabs.forager.app.domain.SavePlannedTripUseCase]
 * enforces the same floor independently; this is the UI convenience, not the invariant itself.
 *
 * The name field starts pre-filled with [defaultTripName] and stays freely editable; the confirm
 * button disables on a blank name so the "name is never blank" invariant
 * ([PlannedTrip.name][com.zynergylabs.forager.app.domain.model.PlannedTrip.name]) can't be violated from this
 * dialog, mirroring `SavePlannedTripUseCase`'s own `require` for the same reason the date floor is
 * mirrored there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripDatePickerDialog(
    defaultName: String,
    onConfirm: (LocalDate, String) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker works in UTC-midnight epoch millis regardless of device time zone, so both the
    // floor and the confirmed selection are converted through UTC to stay consistent with it.
    val todayUtcMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = todayUtcMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
        },
    )
    var name by remember { mutableStateOf(defaultName) }
    // Raised only after a centre-pin pick on a map (compact and wide): always over a map. The dialog's
    // default container at the map chrome's alpha. The picker paints its own container too
    // (material3 1.5.0-alpha26, `DateEntryContainer`'s background), which would stack to 0.96 over the
    // dialog's; it is left clear, so the dialog's one fill is what the map shows through (CLAUDE.md,
    // UX defaults: layered fills composite to the map chrome's value, they do not each carry it). The
    // dialog gives its surface `contentColorFor` of its container, which matches a colour-scheme role
    // exactly (see `MapLayersSheet`), so the content colour is pinned to the default container's own.
    val dialogColors = DatePickerDefaults.colors(
        containerColor = mapChromeFill(navigationBarContainerColor(), overMap = true),
    )
    val pickerColors = DatePickerDefaults.colors(containerColor = Color.Transparent)
    val dialogContentColor = contentColorFor(navigationBarContainerColor())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(TRIP_DATE_DIALOG_TAG).mapChromeContainerColor(dialogColors.containerColor),
        colors = dialogColors,
        confirmButton = {
            TextButton(
                // Guards the "name is never blank" invariant from this dialog — see this
                // function's own doc comment.
                enabled = name.isNotBlank(),
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date, name)
                },
            ) { Text("Plan trip") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        CompositionLocalProvider(LocalContentColor provides dialogContentColor) {
            Column(modifier = Modifier.mapChromeContentColor(LocalContentColor.current)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Trip name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                )
                DatePicker(
                    state = datePickerState,
                    colors = pickerColors,
                    modifier = Modifier.testTag(TRIP_DATE_PICKER_TAG).mapChromeContainerColor(pickerColors.containerColor),
                )
            }
        }
    }
}

/** [TripDatePickerDialog], for tests. */
internal const val TRIP_DATE_DIALOG_TAG = "trip-date-dialog"

/** The date picker inside [TripDatePickerDialog], for tests: its own container colour, clear over a map. */
internal const val TRIP_DATE_PICKER_TAG = "trip-date-picker"

/** [WaypointNameDialog]'s surface, for tests. */
internal const val WAYPOINT_NAME_DIALOG_TAG = "waypoint-name-dialog"

/**
 * Confirms a name for a waypoint placed via [com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker] —
 * [TripDatePickerDialog] without the date, since a waypoint has none ([Waypoint] carries no target date, unlike
 * [PlannedTrip]). Same blank-name guard, mirroring
 * [CreateWaypointUseCase][com.zynergylabs.forager.app.domain.CreateWaypointUseCase]'s own `require`.
 */
@Composable
internal fun WaypointNameDialog(defaultName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    // usePlatformDefaultWidth = false, matching M3's own DatePickerDialog default rather than
    // AlertDialog's (true): an AlertDialog/plain Dialog with an OutlinedTextField as its sole
    // focusable content, reached through the compact scaffold (ModalNavigationDrawer plus its own
    // BackHandlers), never settles under this Robolectric test setup — real, reproduced
    // AppNotIdleException, isolated by removing pieces one at a time (the text field alone is fine;
    // AddActionTile -> this dialog alone is fine; TripDatePickerDialog's own text field, reached
    // through this exact same scaffold, is fine). The one structural difference from
    // TripDatePickerDialog left once AlertDialog vs. plain Dialog was ruled out is this property.
    // Raised only after a centre-pin pick on a map (compact and wide): always over a map. The fill at the
    // map chrome's alpha, and its content colour pinned to the fill's own role (see `MapLayersSheet`).
    val dialogColor = mapChromeFill(navigationBarContainerColor(), overMap = true)
    val dialogContentColor = contentColorFor(navigationBarContainerColor())
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(Spacing.md),
            shadowElevation = 4.dp,
            color = dialogColor,
            contentColor = dialogContentColor,
            modifier = Modifier.testTag(WAYPOINT_NAME_DIALOG_TAG).mapChromeContainerColor(dialogColor),
        ) {
            Column(
                modifier = Modifier.mapChromeContentColor(LocalContentColor.current).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Name this waypoint", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Waypoint name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("Drop waypoint") }
                }
            }
        }
    }
}

/**
 * Tapping a sighting dot's own detail — species name and observed date, the two facts
 * [SightingsMap]'s doc comment describes this as rebuilding from the vendor-native
 * title/snippet popup MapLibre's style-layer geometry has no equivalent for. "View on iNaturalist"
 * hands off to [launchINaturalistObservation] rather than showing anything about the observation
 * this app doesn't already have cached in [Sighting] — no confidence score, no candidate species
 * list, nothing this app would need to fetch or judge itself.
 *
 * A small floating bubble over the map, not a modal [AlertDialog] (this composable's first form,
 * replaced after hardware feedback that a blocking dialog for something this minor "got in the
 * way of the UX"). No scrim, no window of its own: it sits in the same [Box] as the map and lets
 * every tap outside its own bounds fall straight through to the map beneath — dismissing itself is
 * the caller's job, wired to the map's plain `onTap`.
 *
 * **M1 (continuation `2026-09-28-30`, the planner's ruling on Q2):** the card, its tail and its close
 * button were extracted into [MapBubbleShell] (`ui/map/MapBubble.kt`), which every kind of map bubble
 * now shares, and its placement, [com.zynergylabs.forager.app.ui.map.AnchoredAtScreenPoint], moved
 * there with the tail fix: the tail is drawn to [tipInBubble], the tapped point in this bubble's own
 * coordinates, so the tip stays on the dot when the clamp moves the card. The content below, and
 * every test tag, is unchanged.
 */
@Composable
internal fun ObservationBubble(
    sighting: Sighting,
    onViewOnINaturalist: () -> Unit,
    onDismiss: () -> Unit,
    tipInBubble: State<Offset?>,
    /** Dispatch 2026-09-28-549 (Amendment 1, RECORD -557): the Units setting, which the accuracy note follows. */
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier,
) {
    MapBubbleShell(
        tipInBubble = tipInBubble,
        onDismiss = onDismiss,
        cardTag = "observation-bubble",
        closeTag = "observation-bubble-close",
        modifier = modifier,
    ) {
        Text(
            sighting.commonName ?: sighting.scientificName,
            style = MaterialTheme.typography.titleSmall,
        )
        if (sighting.commonName != null) {
            Text(sighting.scientificName, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
        }
        Row(
            // fillMaxWidth so SpaceBetween has real room to push the link to the far right.
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                sighting.observedOn?.let(::displayDate) ?: "Observation date unknown",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "View on iNaturalist",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickableWithShapedPress(onClick = onViewOnINaturalist)
                    .testTag("observation-bubble-view-on-inaturalist"),
            )
        }
        Text(
            accuracyLabel(sighting.positionalAccuracyMeters, unitSystem),
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier.testTag("observation-bubble-accuracy"),
        )
    }
}

/**
 * The standing indicator that [MapTab]/[CompactMapTab] are showing "View on Map"'s filtered
 * sightings rather than every mapped one — CLAUDE.md: a partial/filtered result has to say so, not
 * render identically to the unfiltered view. [label] is `"<species> (<count>)"`, computed by each
 * caller from the same [Sighting] list the map actually draws (see [MapTab]'s own doc comment on
 * `mapTaxonFilterLabel` for why it isn't read from [AvailabilityForecast] directly). Same
 * theme-aware, 80%-opacity fill as [ObservationBubble]/`MapIconBar`/`MapModePicker` (removed 2026-10-19, RECORD -1392) — one visual
 * language for every control floating over the map.
 *
 * No shadow (J8 follow-ups, continuation `2026-09-28-87`, item 3; the planner's ruling, "Layered fills
 * composite to that value; they do not each carry it"): a `Surface` shadow is drawn beneath its fill,
 * so under this translucent fill the shadow would add to the 0.8. This chip had J8's chip's 4 dp shadow,
 * which J8's device check inferred took that chip to 0.833 to 0.840 on the S22; this one was not
 * measured. Both are re-measured on the device.
 */
@Composable
internal fun TaxonMapFilterChip(label: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val isDarkTheme = LocalForagerDarkTheme.current
    Surface(
        modifier = modifier.testTag("map-taxon-filter-chip").mapChromeContainerColor(if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight),
        shape = RoundedCornerShape(percent = 50),
        color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
        contentColor = if (isDarkTheme) Color.White else Bark,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Planner message 2026-09-29-05 (the owner's "Option B"): one line, a long name ends in an ellipsis, and
            // the label gives way (a weight that does not fill) so the clear button keeps its 24 dp.
            Text(
                "Showing: $label",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            BouncingIconButton(
                onClick = onClear,
                modifier = Modifier.size(24.dp).testTag("map-taxon-filter-clear"),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Show all species", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun MapMessage(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
    )
}
