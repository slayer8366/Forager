# Route plotting step 3: build handoff

Hazel (Opus), 2026-10-30, for dispatch 2026-10-30-08 (RECORD -1845) with Amendment 1 (-1852) and the planner's
Amendment 2 message (route colour magenta, -1853). Branch `route-plot-ui`, base pre-main **b493cffeb** (unchanged at the
last fetch). Design: `docs/audits/2026-10-30-route-plotting-step-3-first-stop.md`. Written at about the context limit the
dispatch sets, so this says what is built, what was verified and how, and what is left.

## What is built (commits dce06074c to 56cc94ab5)

- **Domain, headless:** `RoutePlotDraft` (stops and legs while plotting; repeat stop ignored, Undo, the route once every leg
  is in) and `defaultRouteName` ("<first> to <last>", "<first> and back" by position); `SavePlottedRouteUseCase` (new path;
  blank name or past date refused as a failed `Result`, not a throw); `routeFrame`; `EntryMapFrame.FitInsets`.
- **`RoutePlottingViewModel`**, Activity-scoped (MainActivity), on `PlotRouteUseCase.leg` and the save use case (AppContainer).
  Legs routed one at a time in order; Undo or Cancel cancels a leg whose stop they remove; Save waits for every leg; a failed
  save keeps the dialog and says so; a saved route opened mid-plot asks first; each open counted, with its origin.
- **Maps tab:** "Plot route" in a waypoint bubble (Navigate, Plot route, Details; the actions row is now a `FlowRow`); waypoint
  taps add stops while plotting; a route trip's pin opens the route with no bubble; the route card (`RouteCard`, non-modal,
  80% fill, peek and expanded by tap or drag on the handle, 640 dp cap); `SaveRouteDialog` and `DiscardRouteDialog`; the
  card's Back tier, composed before the bubble's; the map line (`PlottedRouteLayers`, below the way home's lines, trail solid
  with casing, joins and straight legs dashed, dots at a saved route's stops); the fit on open, which ends GPS following.
- **Trip Planner:** a route trip's row (`PlannedRouteRow`): name, distance, date; no MGRS or coordinates; Delete; tapping the
  body opens the route on the Maps tab and Back reopens the drawer over the tab it was opened from (`RoutePlottingTrips.kt`).
- **N8:** `StraightReasonNamesTest`. **N9:** a zero-length join is built as two points at one place (test). **N10:** not changed.
- **Route colour:** magenta, day `#FF00FF`, night `#FF40FF`, figures on `PLOTTED_ROUTE_COLOUR_DAY` (PlottedRouteLayers.kt).

## Verification

- **Fail-first:** run 41121293946 on dce06074c (stubs): 47 tests, 21 failed, every new test but four. Passed over stubs, so
  shown to bite only by revert: `StraightReasonNamesTest`, both `RoutePlotDraftTest` undo tests, and the ViewModel's "waypoint
  taps while not plotting do nothing" (not revert-checked). The screen and drawing tests were written after the build and
  are shown to bite only by the reverts below.
- **Route suites on 56cc94ab5:** run 41124417041, 64 tests, 0 failed, counted from the JUnit XML per class.
- **Full CI on 56cc94ab5:** run 41125705343, success: 5995 tests, 0 failures, 0 errors, 24 skipped; lint and release passed.
- **Revert checks:** twelve, one edit each on its own branch, built from a temporary git index so the worktree was never
  edited; the forward file was saved first and compared byte for byte afterwards. Each build log read first: 0 compile
  errors in all twelve, and each ran the same 64 tests as the forward run. Every failure is one its edit can cause.

| Edit | Run | Failed |
|---|---|---|
| Swap two `StraightReason` names | 41125937513 | the N8 pin |
| Drop the repeat-stop check | 41125941059 | draft repeat, ViewModel repeat, screen glyph touches |
| Undo keeps every leg | 41125944099 | draft undo |
| Undo does not cancel the leg in flight | 41125948043 | ViewModel Undo-in-flight (expected 1 cancel, was 0) |
| Waypoint tap not taken while plotting | 41125951698 | screen glyph, Save, Back, handle (each adds Ridge by a glyph tap) |
| Route trip's pin not taken | 41125956762 | both pin tests |
| Fit does not end following | 41125961444 | pin test, at `stopsFollowing` |
| Card's Back handler off | 41125965315 | both Back tests |
| No "Plot route" in the bubble | 41125968427 | Plot route touches (node not found) |
| Card given a 2000 dp minimum height | 41125971967 | long-press above the card (0 of 1), and the four tests whose glyphs it covers |
| `cameraRequestStopsFollowing` ignores the flag | 41125974663 | the drawing test, at "an entry map's frame" |
| No blank-name refusal | 41125978277 | the save refusal test |

## Left to do (step 3 as amended)

1. **Touch tests not written:** the Trip Planner route row (touches across its body; Delete only deletes; Back reopens the
   drawer over the tab it came from); the Discard dialog's buttons; the save dialog's Add date and Remove date and the failure
   line; landscape (rail on, card beside the rail) and tablet windows; a fan and a bubble drawn over the card closing first on
   Back; fullscreen plus plotting (Q3, Back cancels first). The Back order for these is by composition only, unverified.
2. **The icon cluster's drag clamp does not know the card.** In portrait the card is full width and is composed after the
   cluster, so it can cover the cluster's lower part. The first stop proposed treating the card's top as the clamp's bottom;
   not built.
3. **"Return to Route" over the card:** while navigating with the view moved away, the pill sits at the card's place and is
   drawn over it (composed after). Inferred from the order, not laid out.
4. **`MapChromeAlphaTest`** does not cover the card or the two dialogs; each sets its fill through `mapChromeFill(..., overMap
   = true)` and marks it, but no test reads it.
5. **Bubble width:** the `FlowRow` change is not tested at font scale 2.0.

## Calls made beyond the dispatch (for the planner)

- Save ends plotting and closes the card; the saved route is not shown afterwards.
- A leg whose routing throws (never expected: `PlotRouteUseCase` answers failures with a straight leg) removes its stop and
  any after it, logged, rather than a straight leg with an untrue reason.
- The card's handle moves by tap, or by a drag past 24 dp, using `draggable` rather than `AnchoredDraggableState`.
- The expanded rows are capped at half the map's height in every orientation (the first stop proposed 60% in landscape).
- The card sits above the attribution clearance, as "Return to Route" does, not on the nav's top edge.
- A route row has no Directions button; Directions is on the card (Q7).
- The date picker's confirm reads "Add date" (approved) rather than a new word.

## Not verified

- Anything a real map does: MapLibre drawing the layers, their order under the way home, the dots, a zero-length join (N9),
  the fit's padding, and ending GPS following. `MapDataRefresh` cannot run headless. Device-only.
- Real insets: the card's bottom in fullscreen and above a real nav bar (Robolectric reports zero insets).
- Contrast: the dashed parts carry no casing. Against the 27 day grounds in `MapPaletteTest`, `#FF00FF` runs from 1.05:1 to
  3.05:1 (median 1.86); the trail part's white casing is 3.14:1. By night `#FF40FF` runs 1.09:1 to 7.31:1 (median 2.27), with
  a black casing at 7.43:1. Whether the dashed legs read on the pale grounds is for the phone.
- N10 (decoding every route per Trip Planner load) is unmeasured, as ruled, for step 5.
