package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The find form's Notes field. This file used to hold the shared chip/text editors for the seven
 * characteristic sections (`ObservedEnumField`, `FeatureEnumField`, `CapDecorationsField`,
 * `FeatureTextField`, `NotRecordedIndicator`); those sections were removed from the form on
 * 2026-09-13 (see [LogEntryDetailScreen]'s own doc comment) and the editors with them, having no
 * caller left. The "Not recorded" rendering rule they carried lives on only in
 * `ObservedFeatureTypeSafetyTest`'s type-level guarantee and in [LogEntryReportScreen], which
 * still omits an unrecorded field rather than printing an absence.
 */
@Composable
internal fun NotesField(value: String, modifier: Modifier = Modifier, onValueChanged: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text("Notes") },
        modifier = modifier.fillMaxWidth(),
    )
}
