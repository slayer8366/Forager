package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor

/**
 * Where the wide tree's Journal opens what the person opened (J6a, ruling 1: list-detail). The list
 * stays in the 360 dp left column, and an opened entry, find, record's details, picker or pull-photo
 * picker takes the *whole right side*, in place of the results pane and its search bar.
 *
 * **Why a slot, not a parameter.** Each of those details is composed by code that lives inside the
 * Journal (`CartographyScreen` for a day entry, `JournalTab` for a find and its pickers, `RecordsTab`
 * for a record's details), which `AvailabilityScreen` composes in the drawer. The right side is
 * `AvailabilityScreen`'s. A detail therefore *registers* its content here while it is open and
 * `AvailabilityScreen` draws the top layer in [JournalDetailPane]; the detail's own state (the
 * entry's report-or-editor mode, the open find, the picker flags) stays where it always was, so
 * nothing about an open detail moves. The alternative considered was lifting every one of those
 * states and the three composables up into `AvailabilityScreen`, which is the same code moved a long
 * way for the same behaviour.
 *
 * **Only the wide tree passes a slot.** Every composable that takes one defaults it to `null`, and a
 * `null` slot is exactly the code as it was: the detail is drawn in place. The compact tree never
 * builds one.
 */
@Stable
internal class JournalDetailSlot {
    private val layers = mutableStateListOf<JournalDetailLayer>()

    /** The layer the right side shows: the highest [JournalDetailLayer.priority], the latest registered among equals. Read in composition, so a change recomposes the reader. */
    val top: JournalDetailLayer? get() = layers.maxWithOrNull(compareBy { it.priority })

    fun add(layer: JournalDetailLayer) {
        layers.add(layer)
    }

    fun remove(layer: JournalDetailLayer) {
        layers.remove(layer)
    }
}

/** One open detail: [content] is drawn by [JournalDetailPane]; a higher [priority] covers a lower one (a find opened over a day entry covers it). */
@Stable
internal class JournalDetailLayer(val priority: Int, val content: @Composable () -> Unit)

/** Priorities of the details that register with a [JournalDetailSlot]: a later-opened kind covers an earlier one. */
internal object JournalDetailPriority {
    const val ENTRY = 0
    const val FIND = 1
    const val RECORD_DETAILS = 2
}

/**
 * Registers [content] as an open detail for as long as [active] is true and this composable stays in
 * the composition. A no-op for a `null` [slot]. The layer registered is stable across recompositions
 * (it reads the latest [content] through state), so an open detail updating does not re-register it
 * and does not write to the slot: the pane recomposes because its own content reads state.
 */
@Composable
internal fun JournalDetail(
    slot: JournalDetailSlot?,
    active: Boolean,
    priority: Int,
    content: @Composable () -> Unit,
) {
    if (slot == null) return
    val latest by rememberUpdatedState(content)
    DisposableEffect(slot, active, priority) {
        if (!active) return@DisposableEffect onDispose { }
        val layer = JournalDetailLayer(priority) { latest() }
        slot.add(layer)
        onDispose { slot.remove(layer) }
    }
}

/**
 * The wide tree's right side while a detail is open: an opaque surface over the whole right side, so
 * no touch reaches what it covers, that takes its own system-bar insets (top, bottom and end: its
 * start edge meets the drawer, which draws its own). Robolectric reports zero insets, so the insets
 * are device-only in the J6 report. [JournalDetailLayer.content] is drawn full size.
 */
@Composable
internal fun JournalDetailPane(layer: JournalDetailLayer, modifier: Modifier = Modifier) {
    // Solid: no map is drawn beneath it (the results pane under it is covered), so it takes none of the map
    // chrome's 80% fill; the container colour is marked so a test reads it the way it reads the sheets'.
    Surface(modifier = modifier.fillMaxSize().testTag(JOURNAL_DETAIL_PANE_TAG).mapChromeContainerColor(MaterialTheme.colorScheme.surface)) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End)),
        ) {
            layer.content()
        }
    }
}

/** The pane [JournalDetailPane] draws. */
internal const val JOURNAL_DETAIL_PANE_TAG = "journal-detail-pane"
