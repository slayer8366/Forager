# Dispatch — compass strip reverts + the bottom-right control pill

The fiddly bits, landing before the larger layout moves. Two parts: undo what
the last dispatch did to the compass strip, and give record and
return-to-vehicle their own home.

**Scope note:** this is the app's own compass/elevation/MGRS readout strip.
The Android system status bar is correct as-is — the theme fix in `16b67f9`
stays, do not touch it.

---

## Part A — revert the strip

```
1. COMPASS STRIP HEIGHT
   Return the strip to wrapping its text content's natural height.
   Remove COMPASS_STRIP_MIN_HEIGHT's pin to MIN_TOUCH_TARGET.

   DELETE the reason note recorded next to that constant. It reads
   roughly "if that control moves elsewhere again, this strip should
   return to wrapping its text content's natural height instead of
   staying pinned to a touch-target minimum it no longer needs." That
   is exactly what is happening, so the note has served its purpose.
   Leaving it would be the eighth stale comment this project has had
   to sweep.

2. COMPASS ICON
   Revert to its size before the last dispatch. It was enlarged to
   suit the taller strip; the strip is no longer taller.

   Commit 93dd177 moved the compass icon into its own fixed-size box,
   off the crowded text row. Report whether that move should stay —
   it solved a different problem (row crowding) and may still be
   worth keeping at the reverted size.

3. RETURN-TO-VEHICLE READOUT
   Remove it from the strip. Its new home is Part B.

   The strip returns to pure readout: heading, elevation,
   coordinates, and the existing tap-to-toggle-format affordance.

4. HORIZONTAL PADDING
   The strip's padding has been trimmed three times (Spacing.md ->
   Spacing.sm -> none), each round looking sufficient until the next
   screenshot. Before trimming again, find what actually bounds the
   strip's width. Commit ea6917f already found two unbounded
   fillMaxSize() calls propagating up an ancestor chain — check
   whether the remaining crowding has the same root cause rather than
   removing more padding. Report what you find; do not trim a fourth
   time without an explanation of why the previous three were
   insufficient.
```

---

## Part B — the bottom-right control pill

Record and return-to-vehicle are the two Trailhead/Return controls and belong
together. They get their own home rather than living among map verbs.

```
1. THE CONTROL PILL
   A vertical pill at the bottom right, below MapIconBar, holding
   record start/stop and return-to-vehicle. Both at
   MIN_TOUCH_TARGET (48dp).

   Remove the record row from MapIconBar. Report the bar's resulting
   row count — return-to-vehicle's row was already removed in
   e4e3be6, so confirm what is actually left rather than assuming.

   Match MapIconBar's visual language: same surface treatment, same
   opacity, same corner radius. This reads as a sibling of the bar,
   not a new kind of thing.

2. THE DISTANCE ARM
   When return-to-vehicle is active, a horizontal pill extends left
   from the bottom, converging with the control pill at the
   bottom-right corner. It holds the distance readout.

   TWO SEPARATE PILLS, not one L-shaped surface. Each is its own
   composable with its own rectangular bounds. An L-clipped single
   Surface still receives touches across its full bounding box, so
   the concave notch would silently swallow taps meant for the map —
   the same defect class this repo has hit four times.

   FLUSH, with the control pill (the one holding the icons) drawing
   on top at all times. The distance arm tucks under it at the
   junction, so the arm appearing and disappearing never redraws or
   shifts the controls.

   Two consequences to handle:
   - Composition order within the pair is now load-bearing, not
     cosmetic. Compose the arm first, the control pill second.
     Record that at the call site alongside the ordering note in
     item 6.
   - Corner radii at the junction: the arm's right end and the
     control pill's bottom edge meet. Report what you chose so the
     seam reads as one L rather than two overlapping rounded shapes.

   The control pill drawing on top also means it wins any hit-test
   overlap at the junction, which is the correct precedence — a tap
   near the corner should reach a control, not a readout.

3. THE NUMBER
   - Sized to the widest plausible string, not to content. Report
     which string you measured. With a decimal in the km range,
     "99.9 km" is likely wider than "999 m" — measure the rendered
     width in the actual typeface at the actual size rather than
     counting characters.
   - TABULAR FIGURES. This number updates live while someone walks
     toward their car. Proportional digits change width as values
     change, so the readout would shimmer sideways on exactly the leg
     where someone is watching it. The M3 design study already made
     this argument for positional data; this is the clearest case of
     it.
   - Format: metres below 1000, then km with one decimal.

4. THE ANIMATION
   Animate the arm's width in and out as return-to-vehicle
   activates and deactivates.

   Do NOT use shape morphing. It is listed as experimental in the M3
   study's own stable/experimental table, and a width animation gets
   the same read at a fraction of the cost. Stay inside whatever
   ADR-0002 settled for chrome motion — check it rather than
   assuming.

5. TOUCH ROUTING — the part that matters most
   This adds a floating surface to a screen corner in a codebase with
   ZERO performTouchInput coverage across the entire suite, which has
   now shipped pointer-interception four times, most recently in
   ea6917f/e4e3be6.

   Write a real touch-routing test for this corner. Specifically:
   - A tap in the region between/around the two pills reaches the
     map, not the pills.
   - A tap on each control reaches that control.
   - Both assertions hold at the smallest supported width, since the
     last interception bug only reproduced on a short viewport.

   Use performTouchInput with screen coordinates, not semantic node
   lookups. The point is to test hit routing and z-order, which
   node-based assertions cannot see. This is the first instance of
   that coverage in the suite and it is a prerequisite for the layout
   phase regardless — building it here, scoped to one corner, is
   cheaper than building it cold later.

6. COMPOSITION ORDER
   ea6917f's fix depends on MapIconBar composing before
   CompassElevationStrip, since composition order is hit-test order
   for overlapping Box siblings. Adding a third floating surface to
   the same Box means that ordering now governs three things.

   Record the required order and why, at the call site. The
   AvailabilityScreen split is coming and will move exactly these
   composables.
```

---

## Not in this dispatch

Items 3 through 8 from the owner's list — weather to Seasonal, search panel
changes, advanced search to the top, the Tools page, bottom nav rework. Those
are the layout phase and get scoped separately.

The collapsed-by-default coordinate entry belongs with item 5, not here. When
it lands: a labeled row that expands, not a bare chevron. Someone handed
coordinates in the field needs to find it first try, having never seen it.
