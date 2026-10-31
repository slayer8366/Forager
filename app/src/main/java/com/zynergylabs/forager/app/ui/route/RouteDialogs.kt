package com.zynergylabs.forager.app.ui.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zynergylabs.forager.app.ui.format.displayDate
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * The route's two dialogs (dispatch 2026-10-30-08, step 3, Amendment 1): saving a plotted route, and "Discard this route?"
 * when a saved route is opened mid-plot. Both are raised over the Maps tab's map, so their fill is the map chrome's 80%.
 * Every word is approved: "Save route", "Route name", "Add date", "Remove date", "Couldn't save the route. Try again.",
 * "Cancel", "Save", "Discard this route?", "Discard", "Keep plotting".
 */

/**
 * "Save route": the name, pre-filled with [defaultName], and an optional date floored at [today] (RECORD -1817: "Date
 * optional"). Save is off while the name is blank or a save is running. A failed save keeps the dialog and the name and says so
 * ([saveFailed]). Shaped as `WaypointNameDialog` is, a plain Dialog with `usePlatformDefaultWidth = false`, for the reason
 * recorded there (a text field in an AlertDialog never settles under Robolectric through the compact scaffold).
 */
@Composable
fun SaveRouteDialog(
    defaultName: String,
    today: LocalDate,
    saving: Boolean,
    saveFailed: Boolean,
    onSave: (name: String, date: LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val dialogColor = mapChromeFill(navigationBarContainerColor(), overMap = true)
    val dialogContentColor = contentColorFor(navigationBarContainerColor())
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(Spacing.md),
            shadowElevation = 4.dp,
            color = dialogColor,
            contentColor = dialogContentColor,
            modifier = Modifier.padding(horizontal = Spacing.lg).testTag(SAVE_ROUTE_DIALOG_TAG).mapChromeContainerColor(dialogColor),
        ) {
            Column(
                modifier = Modifier.mapChromeContentColor(LocalContentColor.current).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Save route", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Route name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(SAVE_ROUTE_NAME_TAG),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val picked = date
                    if (picked == null) {
                        TextButton(onClick = { pickingDate = true }, modifier = Modifier.testTag(SAVE_ROUTE_ADD_DATE_TAG)) { Text("Add date") }
                    } else {
                        Text(displayDate(picked), modifier = Modifier.weight(1f).testTag(SAVE_ROUTE_DATE_TAG))
                        TextButton(onClick = { date = null }, modifier = Modifier.testTag(SAVE_ROUTE_REMOVE_DATE_TAG)) { Text("Remove date") }
                    }
                }
                if (saveFailed) {
                    Text(SAVE_ROUTE_FAILED, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(SAVE_ROUTE_FAILED_TAG))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.testTag(SAVE_ROUTE_CANCEL_TAG)) { Text("Cancel") }
                    TextButton(
                        onClick = { onSave(name, date) },
                        enabled = name.isNotBlank() && !saving,
                        modifier = Modifier.testTag(SAVE_ROUTE_CONFIRM_TAG),
                    ) { Text("Save") }
                }
            }
        }
    }
    if (pickingDate) {
        RouteDatePicker(today = today, onPicked = { date = it; pickingDate = false }, onDismiss = { pickingDate = false })
    }
}

/**
 * The optional date: M3's date picker floored at [today], as the trip planner's is (`TripDatePickerDialog`), its confirm
 * labelled "Add date" (approved) rather than a new word. Its fill as that dialog's: the dialog's at the map chrome's alpha and
 * the picker's own clear, so the two do not stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteDatePicker(today: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // DatePicker works in UTC-midnight epoch millis, so the floor and the pick both go through UTC (as TripDatePickerDialog).
    val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = todayUtcMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
        },
    )
    val dialogColors = DatePickerDefaults.colors(containerColor = mapChromeFill(navigationBarContainerColor(), overMap = true))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SAVE_ROUTE_DATE_PICKER_TAG).mapChromeContainerColor(dialogColors.containerColor),
        colors = dialogColors,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
            }) { Text("Add date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = Color.Transparent))
    }
}

/** "Discard this route?" (Amendment 1, Q5): Discard drops the route being plotted and opens the saved one; Keep plotting closes this. */
@Composable
fun DiscardRouteDialog(onDiscard: () -> Unit, onKeepPlotting: () -> Unit) {
    val dialogColor = mapChromeFill(navigationBarContainerColor(), overMap = true)
    AlertDialog(
        onDismissRequest = onKeepPlotting,
        title = { Text("Discard this route?") },
        confirmButton = { TextButton(onClick = onDiscard, modifier = Modifier.testTag(DISCARD_ROUTE_CONFIRM_TAG)) { Text("Discard") } },
        dismissButton = { TextButton(onClick = onKeepPlotting, modifier = Modifier.testTag(DISCARD_ROUTE_KEEP_TAG)) { Text("Keep plotting") } },
        modifier = Modifier.testTag(DISCARD_ROUTE_DIALOG_TAG).mapChromeContainerColor(dialogColor),
        containerColor = dialogColor,
    )
}

const val SAVE_ROUTE_FAILED = "Couldn't save the route. Try again."

const val SAVE_ROUTE_DIALOG_TAG = "save-route-dialog"
const val SAVE_ROUTE_NAME_TAG = "save-route-name"
const val SAVE_ROUTE_ADD_DATE_TAG = "save-route-add-date"
const val SAVE_ROUTE_DATE_TAG = "save-route-date"
const val SAVE_ROUTE_REMOVE_DATE_TAG = "save-route-remove-date"
const val SAVE_ROUTE_FAILED_TAG = "save-route-failed"
const val SAVE_ROUTE_CANCEL_TAG = "save-route-cancel"
const val SAVE_ROUTE_CONFIRM_TAG = "save-route-confirm"
const val SAVE_ROUTE_DATE_PICKER_TAG = "save-route-date-picker"
const val DISCARD_ROUTE_DIALOG_TAG = "discard-route-dialog"
const val DISCARD_ROUTE_CONFIRM_TAG = "discard-route-confirm"
const val DISCARD_ROUTE_KEEP_TAG = "discard-route-keep"
