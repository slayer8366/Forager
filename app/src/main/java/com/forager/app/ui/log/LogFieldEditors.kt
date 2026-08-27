package com.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import com.forager.app.domain.model.CapDecoration
import com.forager.app.domain.model.Feature
import com.forager.app.domain.model.Observed
import com.forager.app.ui.theme.Spacing

/**
 * The one visual cue that makes [Observed.NotObserved]/[Feature.NotObserved] read as "unrecorded"
 * rather than as an absent value — every field editor below shows this exactly when its value is
 * the not-observed state, never otherwise. Distinct in wording and style from how [Feature.Absent]
 * renders (a selected "Absent" chip, not this line) so the two states can't be mistaken for each
 * other on screen either.
 */
@Composable
internal fun NotRecordedIndicator() {
    Text(
        "Not recorded",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontStyle = FontStyle.Italic,
    )
}

/**
 * An [Observed]`<E>` field: one chip per option in [options]. Tapping the selected chip again
 * clears back to [Observed.NotObserved] — the only way this field ever reaches that state after
 * having recorded something, besides never having touched it.
 */
@Composable
internal fun <E : Enum<E>> ObservedEnumField(
    label: String,
    value: Observed<E>,
    options: List<E>,
    optionLabel: (E) -> String,
    modifier: Modifier = Modifier,
    onValueChanged: (Observed<E>) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            options.forEach { option ->
                val selected = (value as? Observed.Recorded<E>)?.value == option
                FilterChip(
                    selected = selected,
                    onClick = { onValueChanged(if (selected) Observed.NotObserved else Observed.Recorded(option)) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
        if (value is Observed.NotObserved) NotRecordedIndicator()
    }
}

/**
 * A [Feature]`<E>` field: one chip per option in [options], plus a distinct "Absent" chip —
 * [Feature] has a real third state [options] alone can't represent. Tapping whichever chip is
 * currently selected clears back to [Feature.NotObserved].
 */
@Composable
internal fun <E : Enum<E>> FeatureEnumField(
    label: String,
    value: Feature<E>,
    options: List<E>,
    optionLabel: (E) -> String,
    modifier: Modifier = Modifier,
    onValueChanged: (Feature<E>) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            options.forEach { option ->
                val selected = (value as? Feature.Present<E>)?.value == option
                FilterChip(
                    selected = selected,
                    onClick = { onValueChanged(if (selected) Feature.NotObserved else Feature.Present(option)) },
                    label = { Text(optionLabel(option)) },
                )
            }
            val absentSelected = value is Feature.Absent
            FilterChip(
                selected = absentSelected,
                onClick = { onValueChanged(if (absentSelected) Feature.NotObserved else Feature.Absent) },
                label = { Text("Absent") },
            )
        }
        if (value is Feature.NotObserved) NotRecordedIndicator()
    }
}

/**
 * A [Feature]`<Set<CapDecoration>>` field: multiple decoration kinds can co-occur (see
 * [com.forager.app.domain.model.CapSection]'s doc comment), so chips toggle membership rather than
 * replacing a single selection. "None" is this field's explicit-absence chip, playing the role
 * [FeatureEnumField]'s "Absent" chip plays for single-valued fields.
 */
@Composable
internal fun CapDecorationsField(
    value: Feature<Set<CapDecoration>>,
    modifier: Modifier = Modifier,
    onValueChanged: (Feature<Set<CapDecoration>>) -> Unit,
) {
    val selectedSet = (value as? Feature.Present<Set<CapDecoration>>)?.value.orEmpty()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Decorations", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CapDecoration.entries.forEach { option ->
                val selected = option in selectedSet
                FilterChip(
                    selected = selected,
                    onClick = {
                        val next = if (selected) selectedSet - option else selectedSet + option
                        onValueChanged(if (next.isEmpty()) Feature.NotObserved else Feature.Present(next))
                    },
                    label = { Text(option.label) },
                )
            }
            val absentSelected = value is Feature.Absent
            FilterChip(
                selected = absentSelected,
                onClick = { onValueChanged(if (absentSelected) Feature.NotObserved else Feature.Absent) },
                label = { Text("None") },
            )
        }
        if (value is Feature.NotObserved) NotRecordedIndicator()
    }
}

/**
 * A [Feature]`<String>` field, for characteristics where the value itself is free text once
 * present (a colour change, a latex/exudate colour) — see [com.forager.app.domain.model.ContextFleshSection].
 */
@Composable
internal fun FeatureTextField(
    label: String,
    value: Feature<String>,
    modifier: Modifier = Modifier,
    onValueChanged: (Feature<String>) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val absentSelected = value is Feature.Absent
            FilterChip(
                selected = absentSelected,
                onClick = { onValueChanged(if (absentSelected) Feature.NotObserved else Feature.Absent) },
                label = { Text("Absent") },
            )
            OutlinedTextField(
                value = (value as? Feature.Present<String>)?.value.orEmpty(),
                onValueChange = { text -> onValueChanged(if (text.isBlank()) Feature.NotObserved else Feature.Present(text)) },
                label = { Text("Describe") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (value is Feature.NotObserved) NotRecordedIndicator()
    }
}

@Composable
internal fun NotesField(value: String, modifier: Modifier = Modifier, onValueChanged: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text("Notes") },
        modifier = modifier.fillMaxWidth(),
    )
}
