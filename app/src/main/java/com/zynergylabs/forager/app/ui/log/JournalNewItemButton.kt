package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The Journal's "new" floating button: "New entry" on the Entries timeline (J2, T4) and, since RECORD -751, "New find" on the
 * Finds grid (the owner: "The New Find button should look like the New Entry button found in the journal entry section").
 * One component, so the two cannot drift apart: the same Material extended button, colours, icon-to-label spacing and
 * label. The caller places it, at the content's bottom end with [Spacing.lg] of margin, and gives its list [FAB_CLEARANCE]
 * of bottom padding so the last row scrolls clear of it.
 *
 * The content-lambda overload, not the (icon, text) one: under material3 1.5.0-alpha26 the (icon, text) overload wraps its
 * label in clearAndSetSemantics, so the button's merged semantics, what TalkBack reads, carry no label at all (seen in a
 * Robolectric semantics dump while building J2; the content overload exposes the Text).
 */
@Composable
internal fun JournalNewItemButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(Spacing.md))
        Text(text)
    }
}
