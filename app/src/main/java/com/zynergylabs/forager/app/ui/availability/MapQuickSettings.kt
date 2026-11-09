package com.zynergylabs.forager.app.ui.availability

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.BACK_BY_QUICK_HOURS
import com.zynergylabs.forager.app.domain.BackByChoice
import com.zynergylabs.forager.app.domain.BackByShown
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.SystemCurrentTimeProvider
import com.zynergylabs.forager.app.ui.map.journalMenuColours
import com.zynergylabs.forager.app.ui.map.journalMenuContainerColor
import com.zynergylabs.forager.app.ui.map.journalMenuContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.motion.PressHighlight
import com.zynergylabs.forager.app.ui.motion.pressBounce
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import java.time.Instant
import java.time.ZoneId

/**
 * The map's quick settings (dispatch 2026-09-28-645, Amendments 1 to 3, RECORD -646 to -648): a
 * three-dot button at the far right of the map's top strip, and in the navigation display while
 * navigating, that opens a small menu over the map at 80%. The owner, on where Back by goes: "a small
 * settings (gear) icon at the end of the map's top strip opens a quick menu", holding Back by, the
 * sundown settings and "other navigation settings that don't fit on the map"; the three sections as
 * the coder laid them out, "All three sections (Recommended)". Amendment 3 (RECORD -648) made the gear
 * a three-dot menu: "Instead of a gear, have it be a 3 dot menu at the far right." Far right **in the
 * strip**, in every orientation, not on the screen (RECORD -649; the owner: "Far right in the strip
 * (Recommended)", and "Not because it's on the far right on the screen, but because it's far right in
 * the strip"): in landscape, with the strip in a top corner, it is at the strip's own right end.
 *
 * Its sundown and off-track rows are Settings' own [SundownSettings] and [OffTrackReminderSettings],
 * the same values and the same callbacks, so the two places can never disagree; Settings is unchanged.
 */
internal data class MapQuickSettings(
    /** Back by exists only while recording (the owner: "During a recording only (Recommended)"). */
    val isRecording: Boolean,
    /** This recording's Back by, or `null` with none set. */
    val backBy: BackByShown?,
    val onBackByChoice: (BackByChoice) -> Unit,
    val onClearBackBy: () -> Unit,
    val sundown: SundownSettings,
    val offTrackReminder: OffTrackReminderSettings,
    /** The Battery saver switch (dispatch 2026-09-28-767); in this menu only (the owner: "Quick menu, logger off (Recommended)", RECORD -768). */
    val batterySaver: BatterySaverSettings = BatterySaverSettings(),
    /**
     * The clock the time picker's starting time reads (an hour from now): [AvailabilityScreen]'s own
     * `currentTime`, so all three buttons (the strip's and the navigation display's two) read the clock
     * the screen was given. It was a parameter of [MapQuickSettingsButton] that no caller passed, so the
     * picker read the real clock even under a test that pinned the screen's (RECORD -850).
     */
    val currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
)

/** The quick menu's "Battery saver" switch, from [com.zynergylabs.forager.app.domain.BatterySaverPreferenceRepository]. Off by default. */
internal data class BatterySaverSettings(
    val enabled: Boolean = false,
    val onEnabledChanged: (Boolean) -> Unit = {},
)

internal const val MAP_QUICK_SETTINGS_BUTTON_TAG = "map-quick-settings-button"
/**
 * The quick-settings button's tap target, which is also the strip's least height. The owner, Amendment
 * 3 (RECORD -648), verbatim: "48 is a lot for the strip. It needs to be thin to keep the UI compact.
 * This matters more for smaller phones. Let's start with 36dp on the gear tap." "Start with": the
 * owner may change it after seeing it on a phone, so it is this one value. Below Material's 48 dp
 * guideline by the owner's choice, for a thinner strip.
 */
internal val QUICK_SETTINGS_TAP_TARGET = 36.dp

/** The three-dot icon's size inside [QUICK_SETTINGS_TAP_TARGET]. */
internal val QUICK_SETTINGS_ICON_SIZE = 20.dp

/**
 * How far the three dots' leading edge sits inside the button's leading edge: the icon is centred in the tap target, and
 * Material's MoreVert draws its dots as circles of radius 2 about x = 12 on its 24-unit grid, so they start at 10/24 of the
 * icon. Dispatch 2026-10-13-02, RECORD -983: the landscape strip centres its readout on the dots the eye sees, not on the
 * button's invisible tap target.
 */
internal val QUICK_SETTINGS_DOTS_INSET: Dp = (QUICK_SETTINGS_TAP_TARGET - QUICK_SETTINGS_ICON_SIZE) / 2 + QUICK_SETTINGS_ICON_SIZE * (10f / 24f)

internal const val MAP_QUICK_SETTINGS_DOT_TAG = "map-quick-settings-dot"
/** The three-dot button's round press (motion Part 1), for tests. */
internal const val MAP_QUICK_SETTINGS_PRESS_HIGHLIGHT_TAG = "map-quick-settings-press-highlight"
internal const val MAP_QUICK_SETTINGS_MENU_TAG = "map-quick-settings-menu"
internal const val QUICK_BACK_BY_SET_TAG = "quick-back-by-set"
internal const val QUICK_BACK_BY_CLEAR_TAG = "quick-back-by-clear"
internal const val QUICK_BACK_BY_PICK_TAG = "quick-back-by-pick"
internal const val QUICK_BACK_BY_NOT_RECORDING_TAG = "quick-back-by-not-recording"
internal const val QUICK_SUNDOWN_ALERTS_TAG = "quick-sundown-alerts"
internal const val QUICK_OFF_TRACK_TAG = "quick-off-track-reminder"
internal const val QUICK_BATTERY_SAVER_TAG = "quick-battery-saver"
internal const val BACK_BY_TIME_PICKER_TAG = "back-by-time-picker"
internal const val BACK_BY_TIME_PICKER_SET_TAG = "back-by-time-picker-set"
internal fun quickBackByHoursTag(hours: Int) = "quick-back-by-${hours}h"
internal fun quickDarknessMarginTag(minutes: Int) = "quick-darkness-margin-$minutes"

internal const val MAP_QUICK_SETTINGS_DESCRIPTION = "Quick settings"
internal const val BACK_BY_TITLE = "Back by"
internal const val BACK_BY_PICK_LABEL = "Pick a time…"
internal const val BACK_BY_CLEAR_LABEL = "Clear"
internal const val BACK_BY_NOT_RECORDING_TEXT = "Start a recording to set a Back by time."
internal const val QUICK_OFF_TRACK_LABEL = "Off-track reminder"
/** The owner's chosen wording, verbatim (RECORD -768). No claim of a saving: none is measured into it. */
internal const val QUICK_BATTERY_SAVER_LABEL = "Battery saver"
internal const val QUICK_BATTERY_SAVER_EXPLANATION = "Checks your position every 5 seconds instead of every second. Alerts can come a few seconds later."
internal fun backByQuickLabel(hours: Int) = "+$hours h"

/** "Back by 3:30 PM", in the phone's clock (the sundown line's [SundownClock.full]). */
internal fun backBySetText(backByAtEpochMillis: Long, clock: SundownClock): String = "$BACK_BY_TITLE ${clock.full(backByAtEpochMillis)}"

/**
 * The strip's (and the navigation display's) back-by line: "Back by 3:30 PM" in the last hour before
 * the time and after it while still set ([BackByShown.lineShown]); `null` otherwise.
 */
internal fun backByLineText(backBy: BackByShown?, clock: SundownClock): String? =
    backBy?.takeIf { it.lineShown }?.let { backBySetText(it.backByAtEpochMillis, clock) }

/**
 * The three-dot button (Amendment 3, RECORD -648): a [QUICK_SETTINGS_TAP_TARGET] square inside the
 * strip, which grows to fit it and nothing hangs over the map (Q2, "Taller strip (Recommended)", with
 * Amendment 3's 36 dp in place of 48). Not a `Surface`, so it takes touches in its own square and
 * nowhere else (CLAUDE.md, the `Surface` pitfall). A dot at its corner while a Back by time is set
 * (Q3: "Menu shows it + dot (Recommended)"; kept by Amendment 3).
 *
 * Motion Part 1 (RECORD -687): the press bounce and the rounded press, from Part 1's own pieces, as the map bar's
 * rows have them (`MapBarIconButton`): the icon dips and springs back ([pressBounce]), and the press is drawn
 * in a circle ([PressHighlight]), not a square. Not through `BouncingIconButton`, which the planner named: that
 * is Material's `IconButton`, which clips itself to a circle, and a clip is part of hit testing, so the corners
 * of the owner's 36 dp square stopped taking touches. Measured on the first build: a real touch 3 dp in from
 * the top-left corner did not open the menu. Here the `clickable` is on the unclipped square, exactly as
 * before, and only the drawn press is round.
 */
@Composable
internal fun MapQuickSettingsButton(
    settings: MapQuickSettings,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val backBy = settings.backBy
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(QUICK_SETTINGS_TAP_TARGET)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClickLabel = MAP_QUICK_SETTINGS_DESCRIPTION) { expanded = true }
            .testTag(MAP_QUICK_SETTINGS_BUTTON_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = if (backBy != null) "$MAP_QUICK_SETTINGS_DESCRIPTION, Back by set" else MAP_QUICK_SETTINGS_DESCRIPTION,
            modifier = Modifier.size(QUICK_SETTINGS_ICON_SIZE).pressBounce(interactionSource),
        )
        PressHighlight(interactionSource = interactionSource, shape = CircleShape, testTag = MAP_QUICK_SETTINGS_PRESS_HIGHLIGHT_TAG)
        if (backBy != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 6.dp, y = (-8).dp)
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .testTag(MAP_QUICK_SETTINGS_DOT_TAG),
            )
        }
        MapQuickSettingsMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            settings = settings,
            onPickTime = { pickingTime = true },
        )
    }
    if (pickingTime) {
        BackByTimePickerDialog(
            initialAtEpochMillis = backBy?.backByAtEpochMillis ?: (settings.currentTime.nowEpochMillis() + 60L * 60L * 1_000L),
            onDismiss = { pickingTime = false },
            onSet = { hour, minute ->
                pickingTime = false
                settings.onBackByChoice(BackByChoice.AtTime(hour, minute))
            },
        )
    }
}

/**
 * The menu: Back by, Sundown, Off-track, and Battery saver last (dispatch 2026-09-28-767), as laid out in the -645 Amendment 1 report and confirmed by
 * the owner. At the map chrome's 80% (dialogs and menus over a map, the owner's ruling recorded in
 * `docs/plans/journal-redesign.md`), its content colour opaque, the J8 menus' colours. It stays open
 * after a choice, so the time just set shows at its top (Q3); a tap on the map or Back closes it.
 */
@Composable
private fun MapQuickSettingsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    settings: MapQuickSettings,
    onPickTime: () -> Unit,
) {
    val container = journalMenuContainerColor()
    val content = journalMenuContentColor()
    val clock = rememberSundownClock()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = container,
        modifier = Modifier
            .testTag(MAP_QUICK_SETTINGS_MENU_TAG)
            .journalMenuColours(container, content)
            .mapChromeContainerColor(container),
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .mapChromeContentColor(content),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                BackBySection(settings, clock, onPickTime)
                HorizontalDivider()
                QuickSundownSection(settings.sundown)
                HorizontalDivider()
                QuickCheckboxRow(
                    label = QUICK_OFF_TRACK_LABEL,
                    checked = settings.offTrackReminder.enabled,
                    onCheckedChange = settings.offTrackReminder.onEnabledChanged,
                    tag = QUICK_OFF_TRACK_TAG,
                )
                HorizontalDivider()
                // Dispatch 2026-09-28-767: a checkbox row like the two walk settings above it, with its
                // one line beneath, as "Dark under trees" has its label.
                QuickCheckboxRow(
                    label = QUICK_BATTERY_SAVER_LABEL,
                    checked = settings.batterySaver.enabled,
                    onCheckedChange = settings.batterySaver.onEnabledChanged,
                    tag = QUICK_BATTERY_SAVER_TAG,
                )
                Text(QUICK_BATTERY_SAVER_EXPLANATION, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BackBySection(settings: MapQuickSettings, clock: SundownClock, onPickTime: () -> Unit) {
    if (!settings.isRecording) {
        Text(BACK_BY_TITLE, style = MaterialTheme.typography.titleSmall)
        // Honest copy plus the next step: what the user can do, not only that it is unavailable.
        Text(BACK_BY_NOT_RECORDING_TEXT, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(QUICK_BACK_BY_NOT_RECORDING_TAG))
        return
    }
    val backBy = settings.backBy
    if (backBy == null) {
        Text(BACK_BY_TITLE, style = MaterialTheme.typography.titleSmall)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                backBySetText(backBy.backByAtEpochMillis, clock),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).testTag(QUICK_BACK_BY_SET_TAG),
            )
            TextButton(onClick = settings.onClearBackBy, modifier = Modifier.testTag(QUICK_BACK_BY_CLEAR_TAG)) { Text(BACK_BY_CLEAR_LABEL) }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        BACK_BY_QUICK_HOURS.forEach { hours ->
            TextButton(
                onClick = { settings.onBackByChoice(BackByChoice.HoursFromNow(hours)) },
                modifier = Modifier.testTag(quickBackByHoursTag(hours)),
            ) { Text(backByQuickLabel(hours)) }
        }
        TextButton(onClick = onPickTime, modifier = Modifier.testTag(QUICK_BACK_BY_PICK_TAG)) { Text(BACK_BY_PICK_LABEL) }
    }
}

/** The "Sundown" section in short: its checkbox, and its four margins in one row. The Tools drawer's section (`ToolsSundownSection`, Settings' until dispatch 2026-09-28-707) keeps the explanation line. */
@Composable
private fun QuickSundownSection(sundown: SundownSettings) {
    QuickCheckboxRow(
        label = SUNDOWN_ALERTS_LABEL,
        checked = sundown.alertsEnabled,
        onCheckedChange = sundown.onAlertsEnabledChanged,
        tag = QUICK_SUNDOWN_ALERTS_TAG,
    )
    Text(DARK_UNDER_TREES_LABEL, style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        DARKNESS_MARGIN_CHOICES.forEach { (minutes, label) ->
            FilterChip(
                selected = sundown.darknessMarginMinutes == minutes,
                onClick = { sundown.onDarknessMarginChanged(minutes) },
                label = { Text(label) },
                modifier = Modifier.testTag(quickDarknessMarginTag(minutes)),
            )
        }
    }
}

@Composable
private fun QuickCheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * "Pick a time…": Material's own clock picker in a dialog, over the map at 80% as the other dialogs
 * there are. Starts at the time already set, or an hour from now; follows the phone's 12/24-hour
 * setting. A time not later than now is tomorrow's ([com.zynergylabs.forager.app.domain.backByAtFor]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackByTimePickerDialog(initialAtEpochMillis: Long, onDismiss: () -> Unit, onSet: (hour: Int, minute: Int) -> Unit) {
    val context = LocalContext.current
    val initial = remember(initialAtEpochMillis) { Instant.ofEpochMilli(initialAtEpochMillis).atZone(ZoneId.systemDefault()) }
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = DateFormat.is24HourFormat(context))
    val dialogColor = mapChromeFill(navigationBarContainerColor(), overMap = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(BACK_BY_TITLE) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onSet(state.hour, state.minute) }, modifier = Modifier.testTag(BACK_BY_TIME_PICKER_SET_TAG)) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        modifier = Modifier.testTag(BACK_BY_TIME_PICKER_TAG).mapChromeContainerColor(dialogColor),
        containerColor = dialogColor,
    )
}
