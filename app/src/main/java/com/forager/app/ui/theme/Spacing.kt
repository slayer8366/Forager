package com.forager.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The app's spacing scale. Padding and gap values used to be picked ad hoc (2dp here, 10dp there)
 * with no relationship to each other, which is why visually-similar things — card internal
 * padding, the gap between a card's rows — didn't quite line up card to card.
 *
 * Promoted from a `private object Spacing` that lived in `AvailabilityScreen.kt`
 * (`docs/plans/understory-design-system.md`'s Set in Order step, which named this one). Three more
 * near-identical copies had accumulated where that original wasn't reachable from, none recorded
 * in the design doc — found by searching for the pattern once, not by the doc naming them:
 * `LogSpacing` (`internal`, in `LogFieldEditors.kt`, 67 call sites across 9 files in `ui/log`),
 * `CrashLogSpacing` (`private`, in `CrashLogPanel.kt`, 10 call sites, missing `md` since nothing
 * there had needed it), and `MapPickerSpacing` (`private`, in `CentrePinLocationPicker.kt`, 6 call
 * sites, only `sm`/`lg` — its own doc comment said it existed because that file "sits in neither
 * package, so it keeps its own copy rather than depending sideways on either"; promoting the
 * original into a package every file can reach is what removes the reason for that copy to exist).
 * All four used the same values under the same names — every spacing change to one had to be
 * remembered and repeated in the others, or silently drifted out of step instead.
 *
 * [xl] and [xxl] are new steps this promotion adds, not a promotion of something that already
 * existed: `ui/` has no existing 24dp or 32dp literal for either to replace. They are built now,
 * unwired, the same way `MotionTokens.kt` carries specs with no production caller yet — a named
 * step for "sheet and dialog internal padding" and "empty-state and first-run breathing room" to
 * reach for the next time either is needed, rather than another ad hoc value invented at that call
 * site the way the four steps below were before this file existed.
 */
object Spacing {
    /** Within a tightly related group — a line and its own subtext, one card's internal rows. */
    val xs = 4.dp

    /** Between related but distinct items — chips in a row, a card's own sub-sections. */
    val sm = 8.dp

    /** A card's outer padding, and the standard gap between sibling cards. */
    val md = 12.dp

    /** Screen-level padding, and the gap between major regions of a tab. */
    val lg = 16.dp

    /** Sheet and dialog internal padding. Unwired — see this object's own doc comment. */
    val xl = 24.dp

    /** Empty-state and first-run breathing room. Unwired — see this object's own doc comment. */
    val xxl = 32.dp
}
