package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.availability.launchDirections
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * A Details card's Navigate and Directions (dispatch 2026-11-05-05, A5; RECORD -2140, the owner: "In the Details card add
 * the Directions button for external maps to the right of the in-app Navigate button and center the two buttons"): the
 * app's own navigation first, then Directions, which hands [name] at [at] to a maps app ([launchDirections], as the Trip
 * Planner row's does), side by side and centred in the card. The waypoint's card and the trip's share it.
 *
 * Shown only where the card has Navigate (C1, the coder's proposal the planner accepted): a card with no Navigate has
 * neither. Directions leaves the card open (A12, the planner's call); Navigate is [onNavigate]'s, which closes the card.
 *
 * A [FlowRow], as the bubble's actions are, so at a large font the pair wraps onto two centred lines rather than being
 * clipped; where both fit it is one row. The buttons are outlined, with no fill of their own, so over a map the card's
 * container alone carries the map chrome's alpha (CLAUDE.md, "Nothing fully obstructs the map view").
 */
@Composable
internal fun NavigateDirectionsRow(onNavigate: () -> Unit, name: String, at: LatLng) {
    val context = LocalContext.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedButton(onClick = onNavigate, modifier = Modifier.testTag(RECORD_DETAILS_NAVIGATE_TAG)) {
            Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Navigate", modifier = Modifier.padding(start = Spacing.sm))
        }
        OutlinedButton(onClick = { launchDirections(context, name, at) }, modifier = Modifier.testTag(RECORD_DETAILS_DIRECTIONS_TAG)) {
            Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Directions", modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}
