# Pulse — map/icon claims in the layout-phase draft

**Date:** 2026-08-28. Read-only against `main` at `d432660`. No code changed. Filed after the fact
(see the note at the end of `2026-08-28-mapiconbar-q5-provenance-pulse.md`, its own follow-up) —
this pulse's findings originally existed only as a chat reply in this session, not as a repo file.

**Context:** the owner shared a draft layout-phase document ("Layout phase — 5S applied to the UI")
applying 5S to the app's arrangement, with a "Decisions needed before this phase is written
properly" section listing five open questions. This pulse verified the document's factual claims
against the tree before those questions were answered, per its own note that "counts and
current-placement claims below are read from three design documents, not from the tree."

---

## Central finding: the document's 1S claim is stale

**"The compass strip carries two controls (record toggle, return-to-vehicle) inside a read-mostly
display" is false against current source.** `CompassElevationStripContent`
(`AvailabilityScreen.kt:4086-4176`) is pure readout — heading, elevation, coordinates, and one
tap-to-toggle-format affordance on the coordinates text only. No record control, no return-to-vehicle
control.

Both moved out, per `docs/plans/map-redesign.md:329-373`'s dated history:
- **2026-08-26, stopgap**: record + return-to-vehicle moved from the strip into the icon stack
  (5→7 icons), because they were visually overlapping MapLibre's own native compass control.
- **2026-08-26, same session**: the "panel bar" landed — `MapIconStack` became `MapIconBar`, one
  `Surface` with 8 uniform rows (fullscreen, orientation-reset, GPS/locate-me, topo/plain, record,
  return-to-vehicle, search, add). MapLibre's native compass was disabled in favor of the bar's own
  orientation-reset row.

Confirmed directly in `MapBarIconButton` (`AvailabilityScreen.kt:3942-3980`): every row, including
record and return, sizes to `Modifier.size(MIN_TOUCH_TARGET)` — 48dp, identical to every other row.
So the document's claim that record is **"currently the smallest interactive target in the app"**
is also false — it's tied for the largest.

**The finding relocates rather than dissolves.** The real defect isn't strip-vs-target-size: it's
that 8 controls spanning `per-interaction` (locate-me) through `per-find`/at-specimen (add) through
`per-trip`/Return-phase safety (record, return-to-vehicle) all get identical visual weight in one
uniform bar — the same critique the draft already made for the Add button, just applying to two
more rows than the draft knew about.

## Other claims checked, all confirmed accurate

- **Permanent drawer width**: `PERMANENT_DRAWER_WIDTH = 360.dp`
  (`AvailabilityScreen.kt:1254`, exact literal match) against `WindowWidthClass.MEDIUM`'s 600dp
  floor (`WindowWidthClass.kt:24`, `MEDIUM_BREAKPOINT_DP = 600`) — the "360dp of a 600dp window"
  claim is exact, not approximate.
- **`showCloseButton`**: one call site, `drawerSheetContent(false)` at `AvailabilityScreen.kt:1195`
  — always `false`, confirmed.
- **`MIN_TOUCH_TARGET` already exists**: `AvailabilityScreen.kt:2210`, `= 48.dp`, reused across
  multiple composables (`:2198`, `:3970`, `:4041`, and others) — confirmed exists; no assertion
  test found that checks interactive targets against it (matches the draft's own 4S proposal that
  this needs a new check).

## Answers to the five "Decisions needed" questions

1. **Frequency column** — two factual corrections, not estimates: move Record start/stop and
   Return-to-vehicle from "Compass strip" to "Icon stack" in the Current-home column. Everything
   else in the table reads as reasonable domain judgment, not independently verifiable without
   usage data (as the draft itself says).
2. **Does the six-tab bottom nav survive?** — Tab count confirmed correct: `CompactTab` has 6
   entries (`AvailabilityScreen.kt:247-261`: List, Maps, Seasonal, Journal, Album, Settings).
   Understory's "Correct" verdict looks stale, not settled: `map-redesign.md:270-328` already has
   an open, owner-flagged question asking essentially the same thing from the `MEDIUM`/`EXPANDED`
   side (three of six destinations nested in a drawer panel vs. three as permanent peers,
   explicitly unresolved) — independent corroboration, not just this draft's own estimate.
3. **Icon stack: recolour or rearrangement?** — The panel bar (`MapIconBar`) already *is* a
   rearrangement (5→7→8, floating circles consolidated into one bar), but it solved a different
   problem (native-compass overlap) and never addressed weight-by-consequence. "Rearrangement" is
   the right premise for this phase, framed as a second, distinct pass — not the first one redone.
4. **Q5, bottom sheet vs tab switch** — not found anywhere in the committed docs at the time of
   this pulse (a full `docs/qc/pulses/reports/2026-08-28-mapiconbar-q5-provenance-pulse.md` follow-up
   later located it on an unmerged branch — see that file).
5. **Is `map-redesign.md` still authoritative?** — Yes: the single most current, actively-maintained
   document on this exact surface, with dated entries through 2026-08-26, and the source that
   surfaced this pulse's central finding.

## Net effect on the draft

§1S's compass-strip bullet and 2S's "Compass strip" rows for Record and Return-to-vehicle needed
rewriting before the phase could be handed off — the underlying concern stood, but the target had
moved to `MapIconBar` and the "smallest target" framing didn't hold. Drawer width, `showCloseButton`,
and `MIN_TOUCH_TARGET` all checked out exactly as written.
